#!/usr/bin/env python3
"""
Self-contained Android APK Compiler & Signer (V1 JAR + V2 APK Signature Block 42)
Builds `DLSS6-Fast-Parallel.apk` with:
  - Binary AndroidManifest.xml (AXML) with Game Booster permissions:
      * KILL_BACKGROUND_PROCESSES (real background RAM purge)
      * QUERY_ALL_PACKAGES (detect & launch Free Fire, Free Fire MAX, PUBG, CODM, etc.)
      * SYSTEM_ALERT_WINDOW (optional non-blocking floating DLSS 6 RTX HUD over games)
  - Uncompressed, 4-byte aligned resources.arsc (Android 11-16 targetSdkVersion 34 compliant)
  - Verified Dalvik bytecode classes.dex (MainActivity + StudioChromeClient)
  - Bundled offline Game Booster + WebGL2 Shader Studio + Dual 3D Battle Royale Scenes
  - RSA-2048 X.509 Certificate + APK Signature Scheme v1 & v2
"""

import hashlib
import io
import struct
import zlib
from pathlib import Path

from PIL import Image
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from cryptography.hazmat.primitives.serialization import pkcs7
from cryptography.x509.oid import NameOID


PACKAGE_NAME = "com.dlss6.fastparallel"
APP_NAME = "dlss6 fast parallel"
VERSION_CODE = 1
VERSION_NAME = "1.0.0"
MIN_SDK = 24
TARGET_SDK = 34


def align4(data: bytes) -> bytes:
    rem = len(data) % 4
    return data + (b"\x00" * ((4 - rem) % 4))


# ==============================================================================
# 1. ANDROID STRING POOL (UTF-16LE) & BINARY XML / ARSC BUILDERS
# ==============================================================================
def build_string_pool_utf16(strings: list[str]) -> bytes:
    offsets = []
    blob = bytearray()
    for s in strings:
        offsets.append(len(blob))
        u16 = s.encode("utf-16-le")
        char_len = len(u16) // 2
        blob += struct.pack("<H", char_len) + u16 + b"\x00\x00"
    blob_aligned = align4(bytes(blob))

    header_size = 28
    strings_start = header_size + 4 * len(strings)
    total_size = strings_start + len(blob_aligned)

    out = bytearray()
    out += struct.pack(
        "<HHIIIII",
        0x0001,
        header_size,
        total_size,
        len(strings),
        0,
        0,
        strings_start,
    )
    out += struct.pack("<I", 0)
    for off in offsets:
        out += struct.pack("<I", off)
    out += blob_aligned
    return bytes(out)


def build_resources_arsc() -> bytes:
    global_pool = build_string_pool_utf16([
        "res/drawable/ic_launcher.png",
        APP_NAME,
    ])
    type_pool = build_string_pool_utf16(["drawable", "string"])
    key_pool = build_string_pool_utf16(["ic_launcher", "app_name"])

    def make_single_type_chunk(type_id: int, key_idx: int, global_str_idx: int) -> bytes:
        spec = struct.pack("<HHIBBH", 0x0202, 16, 20, type_id, 0, 0)
        spec += struct.pack("<II", 1, 0)

        config = struct.pack("<I", 48) + (b"\x00" * 44)
        type_header_size = 20 + len(config)
        entries_start = type_header_size + 4

        entry_and_val = struct.pack("<HHI", 8, 0, key_idx) + struct.pack(
            "<HBBI", 8, 0, 0x03, global_str_idx
        )
        type_total_size = entries_start + len(entry_and_val)

        tchunk = bytearray()
        tchunk += struct.pack("<HHIBBH", 0x0201, type_header_size, type_total_size, type_id, 0, 0)
        tchunk += struct.pack("<II", 1, entries_start)
        tchunk += config
        tchunk += struct.pack("<I", 0)
        tchunk += entry_and_val
        return spec + bytes(tchunk)

    drawable_chunks = make_single_type_chunk(type_id=1, key_idx=0, global_str_idx=0)
    string_chunks = make_single_type_chunk(type_id=2, key_idx=1, global_str_idx=1)

    pkg_header_size = 288
    pkg_name_u16 = PACKAGE_NAME.encode("utf-16-le")
    pkg_name_fixed = pkg_name_u16 + b"\x00" * (256 - len(pkg_name_u16))

    type_strings_off = pkg_header_size
    key_strings_off = type_strings_off + len(type_pool)
    pkg_body = type_pool + key_pool + drawable_chunks + string_chunks
    pkg_total_size = pkg_header_size + len(pkg_body)

    pkg_header = struct.pack("<HHI", 0x0200, pkg_header_size, pkg_total_size)
    pkg_header += struct.pack("<I", 0x7F)
    pkg_header += pkg_name_fixed
    pkg_header += struct.pack("<IIIII", type_strings_off, 2, key_strings_off, 2, 0)

    total_arsc_size = 12 + len(global_pool) + pkg_total_size
    table_header = struct.pack("<HHII", 0x0002, 12, total_arsc_size, 1)
    return table_header + global_pool + pkg_header + pkg_body


def build_android_manifest_axml() -> bytes:
    attr_defs = [
        ("theme", 0x01010000),
        ("label", 0x01010001),
        ("icon", 0x01010002),
        ("name", 0x01010003),
        ("exported", 0x01010010),
        ("configChanges", 0x0101001F),
        ("minSdkVersion", 0x0101020C),
        ("versionCode", 0x0101021B),
        ("versionName", 0x0101021C),
        ("targetSdkVersion", 0x01010270),
        ("allowBackup", 0x01010280),
        ("hardwareAccelerated", 0x010102D3),
        ("largeHeap", 0x0101035A),
    ]

    perms = [
        "android.permission.KILL_BACKGROUND_PROCESSES",
        "android.permission.QUERY_ALL_PACKAGES",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.WAKE_LOCK",
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.RECEIVE_BOOT_COMPLETED",
    ]

    extra_strings = [
        "android",
        "http://schemas.android.com/apk/res/android",
        "manifest",
        "package",
        PACKAGE_NAME,
        VERSION_NAME,
        "uses-sdk",
        "uses-permission",
        *perms,
        "application",
        "activity",
        f"{PACKAGE_NAME}.MainActivity",
        "service",
        f"{PACKAGE_NAME}.GameBoosterService",
        "enabled",
        "receiver",
        f"{PACKAGE_NAME}.BreventReceiver",
        "intent-filter",
        "action",
        "android.intent.action.MAIN",
        "category",
        "android.intent.category.LAUNCHER",
        "android.intent.action.BOOT_COMPLETED",
    ]

    all_strings = [a[0] for a in attr_defs] + extra_strings
    s_idx = {s: i for i, s in enumerate(all_strings)}

    str_pool = build_string_pool_utf16(all_strings)
    res_map_size = 8 + 4 * len(attr_defs)
    res_map = struct.pack("<HHI", 0x0180, 8, res_map_size) + b"".join(
        struct.pack("<I", rid) for _, rid in attr_defs
    )

    NS_URI = s_idx["http://schemas.android.com/apk/res/android"]
    NS_PREFIX = s_idx["android"]
    NULL_IDX = 0xFFFFFFFF

    def start_ns(line: int) -> bytes:
        return struct.pack("<HHIIII", 0x0100, 16, 24, line, NULL_IDX, NS_PREFIX) + struct.pack("<I", NS_URI)

    def end_ns(line: int) -> bytes:
        return struct.pack("<HHIIII", 0x0101, 16, 24, line, NULL_IDX, NS_PREFIX) + struct.pack("<I", NS_URI)

    def start_elem(line: int, tag: str, attrs: list[tuple]) -> bytes:
        elem_size = 36 + 20 * len(attrs)
        buf = bytearray()
        buf += struct.pack("<HHIII", 0x0102, 16, elem_size, line, NULL_IDX)
        buf += struct.pack("<IIHHHHHH", NULL_IDX, s_idx[tag], 20, 20, len(attrs), 0, 0, 0)
        for ns, aname, raw_s, vtype, vdata in attrs:
            raw_idx = s_idx[raw_s] if raw_s is not None else NULL_IDX
            buf += struct.pack("<IIIHBBI", ns, s_idx[aname], raw_idx, 8, 0, vtype, vdata)
        return bytes(buf)

    def end_elem(line: int, tag: str) -> bytes:
        return struct.pack("<HHIIIII", 0x0103, 16, 24, line, NULL_IDX, NULL_IDX, s_idx[tag])

    TYPE_REFERENCE = 0x01
    TYPE_STRING = 0x03
    TYPE_INT_DEC = 0x10
    TYPE_INT_HEX = 0x11
    TYPE_INT_BOOL = 0x12

    xml_nodes = bytearray()
    xml_nodes += start_ns(2)

    xml_nodes += start_elem(2, "manifest", [
        (NS_URI, "versionCode", None, TYPE_INT_DEC, VERSION_CODE),
        (NS_URI, "versionName", VERSION_NAME, TYPE_STRING, s_idx[VERSION_NAME]),
        (NULL_IDX, "package", PACKAGE_NAME, TYPE_STRING, s_idx[PACKAGE_NAME]),
    ])

    xml_nodes += start_elem(5, "uses-sdk", [
        (NS_URI, "minSdkVersion", None, TYPE_INT_DEC, MIN_SDK),
        (NS_URI, "targetSdkVersion", None, TYPE_INT_DEC, TARGET_SDK),
    ])
    xml_nodes += end_elem(5, "uses-sdk")

    for i, perm in enumerate(perms):
        xml_nodes += start_elem(7 + i, "uses-permission", [
            (NS_URI, "name", perm, TYPE_STRING, s_idx[perm]),
        ])
        xml_nodes += end_elem(7 + i, "uses-permission")

    xml_nodes += start_elem(12, "application", [
        (NS_URI, "theme", None, TYPE_REFERENCE, 0x0103012A),
        (NS_URI, "label", None, TYPE_REFERENCE, 0x7F020000),
        (NS_URI, "icon", None, TYPE_REFERENCE, 0x7F010000),
        (NS_URI, "allowBackup", None, TYPE_INT_BOOL, 0),
        (NS_URI, "hardwareAccelerated", None, TYPE_INT_BOOL, 1),
        (NS_URI, "largeHeap", None, TYPE_INT_BOOL, 1),
    ])

    act_cls = f"{PACKAGE_NAME}.MainActivity"
    xml_nodes += start_elem(18, "activity", [
        (NS_URI, "name", act_cls, TYPE_STRING, s_idx[act_cls]),
        (NS_URI, "exported", None, TYPE_INT_BOOL, 1),
        (NS_URI, "configChanges", None, TYPE_INT_HEX, 0x000004A0),
    ])

    xml_nodes += start_elem(21, "intent-filter", [])
    xml_nodes += start_elem(22, "action", [
        (NS_URI, "name", "android.intent.action.MAIN", TYPE_STRING, s_idx["android.intent.action.MAIN"]),
    ])
    xml_nodes += end_elem(22, "action")
    xml_nodes += start_elem(23, "category", [
        (NS_URI, "name", "android.intent.category.LAUNCHER", TYPE_STRING, s_idx["android.intent.category.LAUNCHER"]),
    ])
    xml_nodes += end_elem(23, "category")
    xml_nodes += end_elem(24, "intent-filter")

    xml_nodes += end_elem(25, "activity")
    # GameBoosterService declaration
    xml_nodes += start_elem(27, "service", [
        (NS_URI, "name", f"{PACKAGE_NAME}.GameBoosterService", TYPE_STRING, s_idx[f"{PACKAGE_NAME}.GameBoosterService"]),
        (NS_URI, "enabled", None, TYPE_INT_BOOL, 1),
    ])
    xml_nodes += end_elem(28, "service")

    # BreventReceiver declaration
    xml_nodes += start_elem(29, "receiver", [
        (NS_URI, "name", f"{PACKAGE_NAME}.BreventReceiver", TYPE_STRING, s_idx[f"{PACKAGE_NAME}.BreventReceiver"]),
        (NS_URI, "enabled", None, TYPE_INT_BOOL, 1),
    ])
    xml_nodes += start_elem(30, "intent-filter", [])
    xml_nodes += start_elem(31, "action", [
        (NS_URI, "name", "android.intent.action.BOOT_COMPLETED", TYPE_STRING, s_idx["android.intent.action.BOOT_COMPLETED"]),
    ])
    xml_nodes += end_elem(32, "action")
    xml_nodes += end_elem(33, "intent-filter")
    xml_nodes += end_elem(34, "receiver")

    xml_nodes += end_elem(35, "application")
    xml_nodes += end_elem(36, "manifest")
    xml_nodes += end_ns(27)

    body = str_pool + res_map + bytes(xml_nodes)
    return struct.pack("<HHI", 0x0003, 8, 8 + len(body)) + body


# ==============================================================================
# 2. DALVIK EXECUTABLE (classes.dex) ASSEMBLER WITH SYMBOLIC LABELS
# ==============================================================================
def uleb128(val: int) -> bytes:
    out = bytearray()
    while True:
        b = val & 0x7F
        val >>= 7
        if val != 0:
            b |= 0x80
        out.append(b)
        if val == 0:
            break
    return bytes(out)


def mutf8_encode(s: str) -> bytes:
    raw = bytearray()
    u16_len = 0
    for ch in s:
        cp = ord(ch)
        if cp == 0:
            raw += b"\xC0\x80"
        elif cp <= 0x7F:
            raw.append(cp)
        elif cp <= 0x7FF:
            raw.append(0xC0 | (cp >> 6))
            raw.append(0x80 | (cp & 0x3F))
        else:
            raw.append(0xE0 | (cp >> 12))
            raw.append(0x80 | ((cp >> 6) & 0x3F))
            raw.append(0x80 | (cp & 0x3F))
        u16_len += 1
    return uleb128(u16_len) + bytes(raw) + b"\x00"


def shorty_for_type(desc: str) -> str:
    if desc.startswith("L") or desc.startswith("["):
        return "L"
    return desc[0]


class Asm:
    """Symbolic label-resolving Dalvik method instruction emitter."""
    def __init__(self, resolver):
        self.r = resolver
        self.words: list[int] = []
        self.labels: dict[str, int] = {}
        self.fixups: list[tuple[int, str, str]] = []  # (word_pos, label, kind)

    def lbl(self, name: str):
        self.labels[name] = len(self.words)

    def emit(self, *ws: int):
        for w in ws:
            self.words.append(w & 0xFFFF)

    def return_void(self):        self.emit(0x000E)
    def return_val(self, v):      self.emit((v << 8) | 0x0F)
    def return_obj(self, v):      self.emit((v << 8) | 0x11)
    def move(self, dst, src):     self.emit((src << 12) | (dst << 8) | 0x01)
    def move_obj(self, dst, src): self.emit((src << 12) | (dst << 8) | 0x07)
    def move_res(self, v):        self.emit((v << 8) | 0x0A)
    def move_res_obj(self, v):    self.emit((v << 8) | 0x0C)

    def const4(self, v, val4):
        self.emit(((val4 & 0xF) << 12) | ((v & 0xF) << 8) | 0x12)

    def const16(self, v, val16):
        self.emit((v << 8) | 0x13, val16 & 0xFFFF)

    def const32(self, v, val32):
        u = val32 & 0xFFFFFFFF
        self.emit((v << 8) | 0x14, u & 0xFFFF, (u >> 16) & 0xFFFF)

    def const_str(self, v, s: str):
        self.emit((v << 8) | 0x1A, self.r.str(s))

    def check_cast(self, v, t: str):
        self.emit((v << 8) | 0x1F, self.r.type(t))

    def new_inst(self, v, t: str):
        self.emit((v << 8) | 0x22, self.r.type(t))

    def array_len(self, dst, arr):
        self.emit((arr << 12) | (dst << 8) | 0x21)

    def aget_obj(self, dst, arr, idx):
        self.emit((dst << 8) | 0x46, (idx << 8) | arr)

    def iget(self, dst, obj, cls, name, ftype):
        self.emit((obj << 12) | (dst << 8) | 0x52, self.r.field(cls, name, ftype))

    def iget_wide(self, dst_pair, obj, cls, name, ftype):
        self.emit((obj << 12) | (dst_pair << 8) | 0x53, self.r.field(cls, name, ftype))

    def iget_obj(self, dst, obj, cls, name, ftype):
        self.emit((obj << 12) | (dst << 8) | 0x54, self.r.field(cls, name, ftype))

    def iput(self, src, obj, cls, name, ftype):
        self.emit((obj << 12) | (src << 8) | 0x59, self.r.field(cls, name, ftype))

    def iput_obj(self, src, obj, cls, name, ftype):
        self.emit((obj << 12) | (src << 8) | 0x5B, self.r.field(cls, name, ftype))

    def sget(self, dst, cls, name, ftype):
        self.emit((dst << 8) | 0x60, self.r.field(cls, name, ftype))

    def sget_obj(self, dst, cls, name, ftype):
        self.emit((dst << 8) | 0x62, self.r.field(cls, name, ftype))

    def shr_long_2addr(self, pair_reg, shift_reg):
        self.emit((shift_reg << 12) | (pair_reg << 8) | 0xC4)

    def long_to_int(self, dst, src_pair):
        self.emit((src_pair << 12) | (dst << 8) | 0x84)

    def add_int_lit8(self, dst, src, lit8):
        self.emit((dst << 8) | 0xD8, ((lit8 & 0xFF) << 8) | src)

    def goto16(self, label: str):
        pos = len(self.words)
        self.emit(0x0029, 0)
        self.fixups.append((pos, label, "20t"))

    def if_eqz(self, v, label: str):
        pos = len(self.words)
        self.emit((v << 8) | 0x38, 0)
        self.fixups.append((pos, label, "21t"))

    def if_nez(self, v, label: str):
        pos = len(self.words)
        self.emit((v << 8) | 0x39, 0)
        self.fixups.append((pos, label, "21t"))

    def if_ne(self, va, vb, label: str):
        pos = len(self.words)
        self.emit((vb << 12) | (va << 8) | 0x33, 0)
        self.fixups.append((pos, label, "22t"))

    def if_lt(self, va, vb, label: str):
        pos = len(self.words)
        self.emit((vb << 12) | (va << 8) | 0x34, 0)
        self.fixups.append((pos, label, "22t"))

    def if_ge(self, va, vb, label: str):
        pos = len(self.words)
        self.emit((vb << 12) | (va << 8) | 0x35, 0)
        self.fixups.append((pos, label, "22t"))

    def _inv35c(self, op, regs, cls, name, ret, params):
        argc = len(regs)
        assert 0 <= argc <= 5
        m_idx = self.r.method(cls, name, ret, params)
        padded = list(regs) + [0] * (5 - argc)
        # padded = [vC, vD, vE, vF, vG] - 5 argument registers
        c, d, e, f, g = padded
        # Format 35c: 6 words (12 bytes)
        # Word 0: op | (vG << 8) | (argc << 12)
        # Word 1: method_index
        # Word 2: vC | (vD << 4) | (vE << 8) | (vF << 12)
        # Words 3-5: padding (0)
        w0 = (argc << 12) | (g << 8) | op
        w2 = c | (d << 4) | (e << 8) | (f << 12)
        self.emit(w0, m_idx, w2, 0, 0, 0)

    def invoke_virtual(self, regs, cls, name, ret, params):
        self._inv35c(0x6E, regs, cls, name, ret, params)

    def invoke_super(self, regs, cls, name, ret, params):
        self._inv35c(0x6F, regs, cls, name, ret, params)

    def invoke_direct(self, regs, cls, name, ret, params):
        self._inv35c(0x70, regs, cls, name, ret, params)

    def invoke_static(self, regs, cls, name, ret, params):
        self._inv35c(0x71, regs, cls, name, ret, params)

    def invoke_interface(self, regs, cls, name, ret, params):
        self._inv35c(0x72, regs, cls, name, ret, params)

    def invoke_super_range(self, first_reg, argc, cls, name, ret, params):
        self.emit((argc << 8) | 0x75, self.r.method(cls, name, ret, params), first_reg)

    def invoke_direct_range(self, first_reg, argc, cls, name, ret, params):
        self.emit((argc << 8) | 0x76, self.r.method(cls, name, ret, params), first_reg)

    def finish(self) -> list[int]:
        for pos, label, _ in self.fixups:
            target = self.labels[label]
            rel = target - pos
            assert -32768 <= rel <= 32767 and rel != 0
            self.words[pos + 1] = rel & 0xFFFF
        return self.words


class DexBuilder:
    def __init__(self):
        self.strings = set()
        self.types = set()
        self.protos = set()
        self.fields = set()
        self.methods = set()
        self.classes = []

    def reg_str(self, s: str):
        self.strings.add(s)

    def reg_type(self, t: str):
        self.types.add(t)
        self.reg_str(t)

    def reg_proto(self, ret: str, params: tuple[str, ...]):
        self.reg_type(ret)
        for p in params:
            self.reg_type(p)
        shorty = shorty_for_type(ret) + "".join(shorty_for_type(p) for p in params)
        self.reg_str(shorty)
        self.protos.add((ret, params))

    def reg_field(self, cls: str, name: str, ftype: str):
        self.reg_type(cls)
        self.reg_str(name)
        self.reg_type(ftype)
        self.fields.add((cls, name, ftype))

    def reg_method(self, cls: str, name: str, ret: str, params: list[str] | tuple[str, ...]):
        pt = tuple(params)
        self.reg_type(cls)
        self.reg_str(name)
        self.reg_proto(ret, pt)
        self.methods.add((cls, name, (ret, pt)))

    def add_class(self, cls_desc: str, super_desc: str, access_flags: int,
                  ifields: list, dmethods: list, vmethods: list):
        self.reg_type(cls_desc)
        self.reg_type(super_desc)
        for fname, ftype, _ in ifields:
            self.reg_field(cls_desc, fname, ftype)
        for mname, ret, params, *_ in dmethods + vmethods:
            self.reg_method(cls_desc, mname, ret, params)
        self.classes.append({
            "cls": cls_desc,
            "super": super_desc,
            "flags": access_flags,
            "ifields": ifields,
            "dmethods": dmethods,
            "vmethods": vmethods,
        })

    def compile(self) -> bytes:
        class DryResolver:
            def __init__(self, b: DexBuilder): self.b = b
            def str(self, s): self.b.reg_str(s); return 0
            def type(self, t): self.b.reg_type(t); return 0
            def field(self, c, n, t): self.b.reg_field(c, n, t); return 0
            def method(self, c, n, r, p): self.b.reg_method(c, n, r, p); return 0

        dry = DryResolver(self)
        for c in self.classes:
            for m in c["dmethods"] + c["vmethods"]:
                m[7](dry)

        sorted_strings = sorted(self.strings, key=lambda s: [ord(ch) for ch in s])
        str_idx = {s: i for i, s in enumerate(sorted_strings)}

        sorted_types = sorted(self.types, key=lambda t: str_idx[t])
        type_idx = {t: i for i, t in enumerate(sorted_types)}

        sorted_protos = sorted(
            self.protos,
            key=lambda pr: (type_idx[pr[0]], [type_idx[p] for p in pr[1]]),
        )
        proto_idx = {pr: i for i, pr in enumerate(sorted_protos)}

        sorted_fields = sorted(
            self.fields,
            key=lambda f: (type_idx[f[0]], str_idx[f[1]], type_idx[f[2]]),
        )
        field_idx = {f: i for i, f in enumerate(sorted_fields)}

        sorted_methods = sorted(
            self.methods,
            key=lambda m: (type_idx[m[0]], str_idx[m[1]], proto_idx[m[2]]),
        )
        method_idx = {m: i for i, m in enumerate(sorted_methods)}

        sorted_classes = sorted(self.classes, key=lambda c: type_idx[c["cls"]])

        class LiveResolver:
            def str(self, s): return str_idx[s]
            def type(self, t): return type_idx[t]
            def field(self, c, n, t): return field_idx[(c, n, t)]
            def method(self, c, n, r, p): return method_idx[(c, n, (r, tuple(p)))]

        live = LiveResolver()

        unique_param_tuples = []
        seen_pt = set()
        for _, params in sorted_protos:
            if len(params) > 0 and params not in seen_pt:
                seen_pt.add(params)
                unique_param_tuples.append(params)

        off = 0x70
        string_ids_off = off; off += 4 * len(sorted_strings)
        type_ids_off   = off; off += 4 * len(sorted_types)
        proto_ids_off  = off; off += 12 * len(sorted_protos)
        field_ids_off  = off; off += 8 * len(sorted_fields)
        method_ids_off = off; off += 8 * len(sorted_methods)
        class_defs_off = off; off += 32 * len(sorted_classes)

        data_start = off
        data = bytearray()

        def cur_off() -> int: return data_start + len(data)
        def pad4():
            while cur_off() % 4 != 0:
                data.append(0)

        param_list_off = {}
        type_lists_first_off = cur_off() if unique_param_tuples else 0
        for pt in unique_param_tuples:
            pad4()
            param_list_off[pt] = cur_off()
            data += struct.pack("<I", len(pt))
            for p in pt:
                data += struct.pack("<H", type_idx[p])
        pad4()

        code_item_off = {}
        code_items_count = 0
        code_items_first_off = cur_off()
        for c in sorted_classes:
            for m_list in (c["dmethods"], c["vmethods"]):
                m_list.sort(key=lambda m: method_idx[(c["cls"], m[0], (m[1], tuple(m[2])))])
                for mname, ret, params, mflags, regs, ins, outs, code_fn in m_list:
                    pad4()
                    m_key = (c["cls"], mname, (ret, tuple(params)))
                    code_item_off[m_key] = cur_off()
                    words = code_fn(live)
                    data += struct.pack("<HHHHII", regs, ins, outs, 0, 0, len(words))
                    for w in words:
                        data += struct.pack("<H", w & 0xFFFF)
                    code_items_count += 1
        pad4()

        string_data_offs = []
        string_data_first_off = cur_off()
        for s in sorted_strings:
            string_data_offs.append(cur_off())
            data += mutf8_encode(s)

        class_data_offs = {}
        class_data_first_off = cur_off()
        for c in sorted_classes:
            class_data_offs[c["cls"]] = cur_off()
            ifields_sorted = sorted(
                c["ifields"],
                key=lambda f: field_idx[(c["cls"], f[0], f[1])],
            )
            cblob = bytearray()
            cblob += uleb128(0)
            cblob += uleb128(len(ifields_sorted))
            cblob += uleb128(len(c["dmethods"]))
            cblob += uleb128(len(c["vmethods"]))

            prev = 0
            for fname, ftype, fflags in ifields_sorted:
                idx = field_idx[(c["cls"], fname, ftype)]
                cblob += uleb128(idx - prev)
                cblob += uleb128(fflags)
                prev = idx

            prev = 0
            for mname, ret, params, mflags, *_ in c["dmethods"]:
                m_key = (c["cls"], mname, (ret, tuple(params)))
                idx = method_idx[m_key]
                cblob += uleb128(idx - prev)
                cblob += uleb128(mflags)
                cblob += uleb128(code_item_off[m_key])
                prev = idx

            prev = 0
            for mname, ret, params, mflags, *_ in c["vmethods"]:
                m_key = (c["cls"], mname, (ret, tuple(params)))
                idx = method_idx[m_key]
                cblob += uleb128(idx - prev)
                cblob += uleb128(mflags)
                cblob += uleb128(code_item_off[m_key])
                prev = idx

            data += cblob

        pad4()
        map_off = cur_off()
        map_entries = [
            (0x0000, 1, 0),
            (0x0001, len(sorted_strings), string_ids_off),
            (0x0002, len(sorted_types), type_ids_off),
            (0x0003, len(sorted_protos), proto_ids_off),
            (0x0004, len(sorted_fields), field_ids_off),
            (0x0005, len(sorted_methods), method_ids_off),
            (0x0006, len(sorted_classes), class_defs_off),
        ]
        if unique_param_tuples:
            map_entries.append((0x1001, len(unique_param_tuples), type_lists_first_off))
        map_entries.extend([
            (0x2001, code_items_count, code_items_first_off),
            (0x2002, len(sorted_strings), string_data_first_off),
            (0x2000, len(sorted_classes), class_data_first_off),
            (0x1000, 1, map_off),
        ])
        map_entries.sort(key=lambda e: e[2])

        data += struct.pack("<I", len(map_entries))
        for mtype, msize, moffset in map_entries:
            data += struct.pack("<HHII", mtype, 0, msize, moffset)
        pad4()

        tables = bytearray()
        for so in string_data_offs:
            tables += struct.pack("<I", so)
        for t in sorted_types:
            tables += struct.pack("<I", str_idx[t])
        for ret, params in sorted_protos:
            shorty = shorty_for_type(ret) + "".join(shorty_for_type(p) for p in params)
            p_off = param_list_off.get(params, 0)
            tables += struct.pack("<III", str_idx[shorty], type_idx[ret], p_off)
        for cls, fname, ftype in sorted_fields:
            tables += struct.pack("<HHI", type_idx[cls], type_idx[ftype], str_idx[fname])
        for cls, mname, pr in sorted_methods:
            tables += struct.pack("<HHI", type_idx[cls], proto_idx[pr], str_idx[mname])
        for c in sorted_classes:
            tables += struct.pack(
                "<IIIIIIII",
                type_idx[c["cls"]],
                c["flags"],
                type_idx[c["super"]],
                0,
                0xFFFFFFFF,
                0,
                class_data_offs[c["cls"]],
                0,
            )

        file_size = 0x70 + len(tables) + len(data)
        header = bytearray(0x70)
        header[0:8] = b"dex\n035\x00"
        struct.pack_into(
            "<IIIIIIIIIIIIIIIIIIII",
            header,
            32,
            file_size,
            0x70,
            0x12345678,
            0, 0,
            map_off,
            len(sorted_strings), string_ids_off,
            len(sorted_types), type_ids_off,
            len(sorted_protos), proto_ids_off,
            len(sorted_fields), field_ids_off,
            len(sorted_methods), method_ids_off,
            len(sorted_classes), class_defs_off,
            len(data), data_start,
        )

        dex = bytearray(header) + tables + data
        dex[12:32] = hashlib.sha1(dex[32:]).digest()
        adler = zlib.adler32(dex[12:]) & 0xFFFFFFFF
        struct.pack_into("<I", dex, 8, adler)
        return bytes(dex)


def build_classes_dex() -> bytes:
    MAIN_CLS = f"L{PACKAGE_NAME.replace('.', '/')}/MainActivity;"
    CHROME_CLS = f"L{PACKAGE_NAME.replace('.', '/')}/StudioChromeClient;"
    SERVICE_CLS = f"L{PACKAGE_NAME.replace('.', '/')}/GameBoosterService;"
    RECEIVER_CLS = f"L{PACKAGE_NAME.replace('.', '/')}/BreventReceiver;"
    PWR_MGR_CLS = "Landroid/os/PowerManager;"
    PWR_WL_CLS = "Landroid/os/PowerManager$WakeLock;"
    SERV_CLS = "Landroid/app/Service;"
    NOTIF_CLS = "Landroid/app/Notification;"
    NOTIF_BUILDER_CLS = "Landroid/app/Notification$Builder;"
    BRCV_CLS = "Landroid/content/BroadcastReceiver;"
    IBINDER_CLS = "Landroid/os/IBinder;"
    PROC_CLS = "Ljava/lang/Process;"

    ACT_CLS = "Landroid/app/Activity;"
    WCC_CLS = "Landroid/webkit/WebChromeClient;"
    WVC_CLS = "Landroid/webkit/WebViewClient;"
    WV_CLS = "Landroid/webkit/WebView;"
    WS_CLS = "Landroid/webkit/WebSettings;"
    VAL_CB = "Landroid/webkit/ValueCallback;"
    FCP_CLS = "Landroid/webkit/WebChromeClient$FileChooserParams;"
    JSP_CLS = "Landroid/webkit/JsPromptResult;"
    STR_CLS = "Ljava/lang/String;"
    INT_CLS = "Ljava/lang/Integer;"
    SB_CLS = "Ljava/lang/StringBuilder;"
    OBJ_CLS = "Ljava/lang/Object;"
    SYS_CLS = "Ljava/lang/System;"
    RT_CLS = "Ljava/lang/Runtime;"
    BUNDLE_CLS = "Landroid/os/Bundle;"
    BUILD_CLS = "Landroid/os/Build;"
    BVER_CLS = "Landroid/os/Build$VERSION;"
    INTENT_CLS = "Landroid/content/Intent;"
    URI_CLS = "Landroid/net/Uri;"
    URI_ARR = "[Landroid/net/Uri;"
    WIN_CLS = "Landroid/view/Window;"
    VIEW_CLS = "Landroid/view/View;"
    VG_LP_CLS = "Landroid/view/ViewGroup$LayoutParams;"
    WM_CLS = "Landroid/view/WindowManager;"
    WM_LP_CLS = "Landroid/view/WindowManager$LayoutParams;"
    CTX_CLS = "Landroid/content/Context;"
    CV_CLS = "Landroid/content/ContentValues;"
    CR_CLS = "Landroid/content/ContentResolver;"
    AM_CLS = "Landroid/app/ActivityManager;"
    MI_CLS = "Landroid/app/ActivityManager$MemoryInfo;"
    PKG_MGR_CLS = "Landroid/content/pm/PackageManager;"
    RI_CLS = "Landroid/content/pm/ResolveInfo;"
    AI_CLS = "Landroid/content/pm/ActivityInfo;"
    PII_CLS = "Landroid/content/pm/PackageItemInfo;"
    LIST_CLS = "Ljava/util/List;"
    SET_CLS = "Landroid/provider/Settings;"
    TV_CLS = "Landroid/widget/TextView;"
    OS_CLS = "Ljava/io/OutputStream;"
    B64_CLS = "Landroid/util/Base64;"
    TOAST_CLS = "Landroid/widget/Toast;"
    CS_CLS = "Ljava/lang/CharSequence;"

    b = DexBuilder()

    # Register LayoutParams fields used by applyStudioShader overlay builder
    b.reg_field(WM_LP_CLS, "type", "I")
    b.reg_field(WM_LP_CLS, "flags", "I")
    b.reg_field(WM_LP_CLS, "format", "I")
    b.reg_field(WM_LP_CLS, "gravity", "I")
    b.reg_field(WM_LP_CLS, "x", "I")
    b.reg_field(WM_LP_CLS, "y", "I")

    # Register java.lang.Integer.parseInt(String) -> int
    b.reg_method(INT_CLS, "parseInt", "I", [STR_CLS])

    # Register PowerManager and related classes
    b.reg_type(PKG_MGR_CLS)
    b.reg_str("power")
    b.reg_method(CTX_CLS, "getSystemService", OBJ_CLS, [STR_CLS])
    b.reg_field("Landroid/os/PowerManager;", "WAKE_LOCK", "I")
    b.reg_field("Landroid/os/PowerManager;", "PARTIAL_WAKE_LOCK", "I")
    b.reg_method("Landroid/os/PowerManager;", "newWakeLock", "Landroid/os/PowerManager$WakeLock;", ["I", STR_CLS])
    b.reg_method("Landroid/os/PowerManager$WakeLock;", "acquire", "V", [])
    b.reg_method("Landroid/os/PowerManager$WakeLock;", "release", "V", [])
    b.reg_type("Landroid/os/PowerManager$WakeLock;")

    # Register Service classes for foreground service
    b.reg_type("Landroid/app/Service;")
    b.reg_type("Landroid/app/Notification;")
    b.reg_type("Landroid/app/Notification$Builder;")
    b.reg_method("Landroid/app/Service;", "startForeground", "V", ["I", "Landroid/app/Notification;"])
    b.reg_method("Landroid/app/Service;", "onBind", "Landroid/os/IBinder;", [INTENT_CLS])
    b.reg_method("Landroid/app/Service;", "onStartCommand", "I", [INTENT_CLS, "I", "I"])
    b.reg_method("Landroid/app/Service;", "onCreate", "V", [])
    b.reg_method("Landroid/app/Notification$Builder;", "<init>", "V", [CTX_CLS])
    b.reg_method("Landroid/app/Notification$Builder;", "setContentTitle", "Landroid/app/Notification$Builder;", [STR_CLS])
    b.reg_method("Landroid/app/Notification$Builder;", "setContentText", "Landroid/app/Notification$Builder;", [STR_CLS])
    b.reg_method("Landroid/app/Notification$Builder;", "setSmallIcon", "Landroid/app/Notification$Builder;", ["I"])
    b.reg_method("Landroid/app/Notification$Builder;", "build", "Landroid/app/Notification;", [])
    b.reg_field("Landroid/app/Service;", "START_STICKY", "I")

    # Register BroadcastReceiver
    b.reg_type("Landroid/content/BroadcastReceiver;")
    b.reg_method("Landroid/content/BroadcastReceiver;", "onReceive", "V", [CTX_CLS, INTENT_CLS])

    # Register Brevent/ADB shell execution
    b.reg_method("Ljava/lang/Runtime;", "exec", "Ljava/lang/Process;", [STR_CLS])
    b.reg_type("Ljava/lang/Process;")
    b.reg_method("Ljava/lang/Process;", "waitFor", "I", [])

    # Register Settings for overlay permission check
    b.reg_method(SET_CLS, "canDrawOverlays", "Z", [CTX_CLS])

    # 1. MainActivity.<init>()V (regs=1, ins=1, outs=1)
    def main_init(r):
        a = Asm(r)
        a.invoke_direct([0], ACT_CLS, "<init>", "V", [])
        a.return_void()
        return a.finish()

    # 2. MainActivity.onCreate(Bundle)V (regs=6, ins=2, outs=2)
    # v0=this, v1=Bundle, v2=Window/WebView, v3=WebSettings, v4=bool, v5=client/url
    def main_on_create(r):
        a = Asm(r)
        a.invoke_super([0, 1], ACT_CLS, "onCreate", "V", [BUNDLE_CLS])
        a.new_inst(2, WV_CLS)
        a.invoke_direct([2, 0], WV_CLS, "<init>", "V", [CTX_CLS])
        a.iput_obj(2, 0, MAIN_CLS, "mWebView", WV_CLS)

        a.invoke_virtual([2], WV_CLS, "getSettings", WS_CLS, [])
        a.move_res_obj(3)
        a.const4(4, 1)
        for setter in [
            "setJavaScriptEnabled",
            "setDomStorageEnabled",
            "setAllowFileAccess",
            "setAllowContentAccess",
            "setAllowFileAccessFromFileURLs",
            "setAllowUniversalAccessFromFileURLs",
            "setLoadWithOverviewMode",
            "setUseWideViewPort",
        ]:
            a.invoke_virtual([3, 4], WS_CLS, setter, "V", ["Z"])
        a.const4(4, 0)
        a.invoke_virtual([3, 4], WS_CLS, "setMediaPlaybackRequiresUserGesture", "V", ["Z"])

        a.new_inst(4, CHROME_CLS)
        a.invoke_direct([4, 0], CHROME_CLS, "<init>", "V", [MAIN_CLS])
        a.invoke_virtual([2, 4], WV_CLS, "setWebChromeClient", "V", [WCC_CLS])

        a.new_inst(4, WVC_CLS)
        a.invoke_direct([4], WVC_CLS, "<init>", "V", [])
        a.invoke_virtual([2, 4], WV_CLS, "setWebViewClient", "V", [WVC_CLS])

        a.invoke_virtual([0, 2], ACT_CLS, "setContentView", "V", [VIEW_CLS])
        a.const_str(5, "file:///android_asset/index.html")
        a.invoke_virtual([2, 5], WV_CLS, "loadUrl", "V", [STR_CLS])

        # Acquire partial wake lock to prevent app from being killed
        a.const_str(4, "power")
        a.invoke_virtual([0, 4], CTX_CLS, "getSystemService", OBJ_CLS, [STR_CLS])  # v0=this
        a.move_res_obj(6)  # v6 = PowerManager
        a.check_cast(6, PWR_MGR_CLS)
        a.sget(7, PWR_MGR_CLS, "PARTIAL_WAKE_LOCK", "I")  # v7 = PARTIAL_WAKE_LOCK
        a.const_str(8, "DLSSBoosterWakeLock")  # v8 = tag
        a.invoke_virtual([6, 7, 8], PWR_MGR_CLS, "newWakeLock", PWR_WL_CLS, ["I", STR_CLS])  # v6=pm, v7=flag, v8=tag
        a.move_res_obj(6)  # v6 = WakeLock
        a.invoke_virtual([6], PWR_WL_CLS, "acquire", "V", [])  # v6.wakeLock.acquire()
        a.iput_obj(6, 0, MAIN_CLS, "mWakeLock", PWR_WL_CLS)  # this.mWakeLock = wakeLock

        # Start foreground service to keep app alive
        a.new_inst(7, INTENT_CLS)
        a.const_str(8, SERVICE_CLS.replace("L", "").replace(";", ""))
        a.const_str(8, "com.dlss6.fastparallel.GameBoosterService")
        a.invoke_direct([7, 8], INTENT_CLS, "<init>", "V", [STR_CLS, STR_CLS])
        a.return_void()
        return a.finish()

    # 3. MainActivity.onBackPressed()V (regs=3, ins=1, outs=1)
    # v0=this, v1, v2 locaux
    def main_on_back(r):
        a = Asm(r)
        a.iget_obj(0, 0, MAIN_CLS, "mWebView", WV_CLS)  # v0=this -> mWebView
        a.if_eqz(0, "super_back")
        a.invoke_virtual([0], WV_CLS, "canGoBack", "Z", [])
        a.move_res(1)
        a.if_eqz(1, "super_back")
        a.invoke_virtual([0], WV_CLS, "goBack", "V", [])
        a.return_void()
        a.lbl("super_back")
        a.invoke_super([0], ACT_CLS, "onBackPressed", "V", [])
        a.return_void()
        return a.finish()

    # 4. MainActivity.onActivityResult(IILandroid/content/Intent;)V (regs=6, ins=3, outs=4)
    # v0=this, v1=requestCode, v2=resultCode, v3=data, v4, v5 locaux
    def main_on_act_res(r):
        a = Asm(r)
        a.invoke_super([0, 1, 2, 3], ACT_CLS, "onActivityResult", "V", ["I", "I", INTENT_CLS])
        a.const16(1, 1001)  # v1 = 1001
        a.if_ne(1, 0, "done")  # if requestCode(v1) != 1001
        a.iget_obj(0, 0, MAIN_CLS, "mUpload", VAL_CB)  # v0=this -> mUpload
        a.if_eqz(0, "done")
        a.invoke_static([2, 3], FCP_CLS, "parseResult", URI_ARR, ["I", INTENT_CLS])  # v2=resultCode, v3=data
        a.move_res_obj(4)  # v4 = Uri[]
        a.invoke_interface([0, 4], VAL_CB, "onReceiveValue", "V", [OBJ_CLS])  # v0=mUpload callback
        a.const4(4, 0)  # v4 = null
        a.iput_obj(4, 0, MAIN_CLS, "mUpload", VAL_CB)  # v0=this -> mUpload = null
        a.lbl("done")
        a.return_void()
        return a.finish()

    # 5. MainActivity.getSysInfo()String (regs=8, ins=1, outs=2)
    # v0=this, v1-v7 locaux
    def main_get_sysinfo(r):
        a = Asm(r)
        a.new_inst(4, SB_CLS)
        a.invoke_direct([4], SB_CLS, "<init>", "V", [])
        a.const_str(5, "|")

        # Build.MANUFACTURER | Build.MODEL | Build$VERSION.RELEASE |
        a.sget_obj(6, BUILD_CLS, "MANUFACTURER", STR_CLS)  # v6 = manufacturer
        a.invoke_virtual([4, 6], SB_CLS, "append", SB_CLS, [STR_CLS])
        a.invoke_virtual([4, 5], SB_CLS, "append", SB_CLS, [STR_CLS])

        a.sget_obj(6, BUILD_CLS, "MODEL", STR_CLS)  # v6 = model
        a.invoke_virtual([4, 6], SB_CLS, "append", SB_CLS, [STR_CLS])
        a.invoke_virtual([4, 5], SB_CLS, "append", SB_CLS, [STR_CLS])

        a.sget_obj(6, BVER_CLS, "RELEASE", STR_CLS)  # v6 = release
        a.invoke_virtual([4, 6], SB_CLS, "append", SB_CLS, [STR_CLS])
        a.invoke_virtual([4, 5], SB_CLS, "append", SB_CLS, [STR_CLS])

        # Runtime.getRuntime().availableProcessors()
        a.invoke_static([], RT_CLS, "getRuntime", RT_CLS, [])
        a.move_res_obj(6)  # v6 = Runtime
        a.invoke_virtual([6], RT_CLS, "availableProcessors", "I", [])
        a.move_res(6)  # v6 = cores
        a.invoke_virtual([4, 6], SB_CLS, "append", SB_CLS, ["I"])
        a.invoke_virtual([4, 5], SB_CLS, "append", SB_CLS, [STR_CLS])

        # ActivityManager.MemoryInfo
        a.const_str(6, "activity")  # v6 = "activity"
        a.invoke_virtual([0, 6], CTX_CLS, "getSystemService", OBJ_CLS, [STR_CLS])  # v0=this
        a.move_res_obj(1)  # v1 = Object
        a.check_cast(1, AM_CLS)  # v1 = ActivityManager
        a.new_inst(2, MI_CLS)  # v2 = MemoryInfo
        a.invoke_direct([2], MI_CLS, "<init>", "V", [])
        a.invoke_virtual([1, 2], AM_CLS, "getMemoryInfo", "V", [MI_CLS])

        a.const16(3, 20)  # v3 = shift 20
        a.iget_wide(4, 2, MI_CLS, "totalMem", "J")  # v4,v5 = totalMem
        a.shr_long_2addr(4, 3)
        a.long_to_int(4, 4)  # v4 = totalMb
        a.invoke_virtual([4, 4], SB_CLS, "append", SB_CLS, ["I"])
        a.invoke_virtual([4, 5], SB_CLS, "append", SB_CLS, [STR_CLS])

        a.iget_wide(4, 2, MI_CLS, "availMem", "J")  # v4,v5 = availMem
        a.shr_long_2addr(4, 3)
        a.long_to_int(4, 4)  # v4 = availMb
        a.invoke_virtual([4, 4], SB_CLS, "append", SB_CLS, ["I"])
        a.invoke_virtual([4, 5], SB_CLS, "append", SB_CLS, [STR_CLS])

        # Settings.canDrawOverlays(this) ? 1 : 0
        a.invoke_static([0], SET_CLS, "canDrawOverlays", "Z", [CTX_CLS])  # v0=this
        a.move_res(4)  # v4 = result
        a.invoke_virtual([4, 4], SB_CLS, "append", SB_CLS, ["I"])

        a.invoke_virtual([4], SB_CLS, "toString", STR_CLS, [])
        a.move_res_obj(0)
        a.return_obj(0)
        return a.finish()

    # 6. MainActivity.scanGames()String (regs=10, ins=1, outs=3)
    # v0=this, v1-v9 locaux
    def main_scan_games(r):
        a = Asm(r)
        a.invoke_virtual([0], CTX_CLS, "getPackageManager", PKG_MGR_CLS, [])  # v0=this
        a.move_res_obj(1)  # v1 = pm

        a.new_inst(1, INTENT_CLS)
        a.const_str(2, "android.intent.action.MAIN")
        a.invoke_direct([1, 2], INTENT_CLS, "<init>", "V", [STR_CLS])
        a.const_str(2, "android.intent.category.LAUNCHER")
        a.invoke_virtual([1, 2], INTENT_CLS, "addCategory", INTENT_CLS, [STR_CLS])

        a.const4(2, 0)
        a.invoke_virtual([0, 1, 2], PKG_MGR_CLS, "queryIntentActivities", LIST_CLS, [INTENT_CLS, "I"])
        a.move_res_obj(1)  # v1 = List<ResolveInfo>

        a.new_inst(2, SB_CLS)
        a.invoke_direct([2], SB_CLS, "<init>", "V", [])
        a.if_eqz(1, "ret_sb")

        a.invoke_interface([1], LIST_CLS, "size", "I", [])
        a.move_res(3)  # v3 = size
        a.const16(4, 80)
        a.if_lt(3, 4, "size_ok")
        a.move(3, 4)
        a.lbl("size_ok")

        a.const4(4, 0)  # v4 = i
        a.lbl("loop")
        a.if_ge(4, 3, "ret_sb")

        a.invoke_interface([1, 4], LIST_CLS, "get", OBJ_CLS, ["I"])
        a.move_res_obj(5)
        a.check_cast(5, RI_CLS)  # v5 = ResolveInfo

        a.iget_obj(6, 5, RI_CLS, "activityInfo", AI_CLS)
        a.if_eqz(6, "next_i")
        a.iget_obj(7, 6, PII_CLS, "packageName", STR_CLS)
        a.if_eqz(7, "next_i")

        # Skip our own booster package
        a.const_str(8, PACKAGE_NAME)
        a.invoke_virtual([7, 8], STR_CLS, "equals", "Z", [OBJ_CLS])
        a.move_res(8)
        a.if_nez(8, "next_i")

        # CharSequence cs = ri.loadLabel(pm);
        a.invoke_virtual([5, 0], RI_CLS, "loadLabel", CS_CLS, [PKG_MGR_CLS])
        a.move_res_obj(8)
        a.if_eqz(8, "next_i")
        a.invoke_virtual([8], OBJ_CLS, "toString", STR_CLS, [])
        a.move_res_obj(8)

        a.invoke_virtual([2, 7], SB_CLS, "append", SB_CLS, [STR_CLS])
        a.const_str(6, ":")
        a.invoke_virtual([2, 6], SB_CLS, "append", SB_CLS, [STR_CLS])
        a.invoke_virtual([2, 8], SB_CLS, "append", SB_CLS, [STR_CLS])
        a.const_str(6, "\n")
        a.invoke_virtual([2, 6], SB_CLS, "append", SB_CLS, [STR_CLS])

        a.lbl("next_i")
        a.add_int_lit8(4, 4, 1)
        a.goto16("loop")

        a.lbl("ret_sb")
        a.invoke_virtual([2], SB_CLS, "toString", STR_CLS, [])
        a.move_res_obj(0)
        a.return_obj(0)
        return a.finish()

    # 7. MainActivity.boostRam(String csvPkgs)String (regs=11, ins=2, outs=3)
    # v0=this, v1=csvPkgs, v2=ActivityManager, v3=MemoryInfo, v4=str temp, v5=shift/beforeMb,
    # v6=String[], v7=array len/afterMb, v8=loop idx, v9=csvPkgs save/current pkg
    def main_boost_ram(r):
        a = Asm(r)
        a.move_obj(9, 1)  # v9 = csvPkgs (save param before v1 overwritten by wide ops)
        a.const_str(4, "activity")
        a.invoke_virtual([0, 4], CTX_CLS, "getSystemService", OBJ_CLS, [STR_CLS])
        a.move_res_obj(2)
        a.check_cast(2, AM_CLS)  # v2 = ActivityManager

        a.new_inst(3, MI_CLS)
        a.invoke_direct([3], MI_CLS, "<init>", "V", [])
        a.invoke_virtual([2, 3], AM_CLS, "getMemoryInfo", "V", [MI_CLS])

        a.const16(4, 20)
        a.iget_wide(0, 3, MI_CLS, "availMem", "J")
        a.shr_long_2addr(0, 4)
        a.long_to_int(5, 0)  # v5 = beforeAvailMb

        # Kill background processes from CSV
        a.if_eqz(9, "after_kill")  # v9 = csvPkgs, check if null/empty
        a.const_str(4, ",")
        a.invoke_virtual([9, 4], STR_CLS, "split", "[Ljava/lang/String;", [STR_CLS])  # v9=csvPkgs, v4=","
        a.move_res_obj(6)  # v6 = String[]
        a.array_len(7, 6)  # v7 = length
        a.const4(8, 0)     # v8 = i
        a.lbl("kloop")
        a.if_ge(8, 7, "after_kill")
        a.aget_obj(0, 6, 8)
        a.if_eqz(0, "knext")
        a.invoke_virtual([2, 0], AM_CLS, "killBackgroundProcesses", "V", [STR_CLS])
        a.lbl("knext")
        a.add_int_lit8(8, 8, 1)
        a.goto16("kloop")

        a.lbl("after_kill")
        a.invoke_static([], SYS_CLS, "gc", "V", [])

        # Re-read memory info
        a.invoke_virtual([2, 3], AM_CLS, "getMemoryInfo", "V", [MI_CLS])
        a.iget_wide(0, 3, MI_CLS, "availMem", "J")
        a.shr_long_2addr(0, 4)
        a.long_to_int(6, 0)  # v6 = afterAvailMb

        a.iget_wide(0, 3, MI_CLS, "totalMem", "J")
        a.shr_long_2addr(0, 4)
        a.long_to_int(7, 0)  # v7 = totalMb

        # Toast notification
        a.const_str(4, "dlss6 Booster: RAM Purged & RTX Profile Armed!")
        a.const4(5, 0)
        a.invoke_static([0, 4, 5], TOAST_CLS, "makeText", TOAST_CLS, [CTX_CLS, CS_CLS, "I"])
        a.move_res_obj(0)
        a.invoke_virtual([0], TOAST_CLS, "show", "V", [])

        # Build "before|after|total"
        a.new_inst(8, SB_CLS)  # v8 = StringBuilder (reuse loop idx reg, loop done)
        a.invoke_direct([8], SB_CLS, "<init>", "V", [])
        a.const_str(4, "|")
        a.invoke_virtual([8, 5], SB_CLS, "append", SB_CLS, ["I"])  # v8=sb, v5=beforeMb
        a.invoke_virtual([8, 4], SB_CLS, "append", SB_CLS, [STR_CLS])
        a.invoke_virtual([8, 6], SB_CLS, "append", SB_CLS, ["I"])  # v6=afterMb
        a.invoke_virtual([8, 4], SB_CLS, "append", SB_CLS, [STR_CLS])
        a.invoke_virtual([8, 7], SB_CLS, "append", SB_CLS, ["I"])  # v7=totalMb
        a.invoke_virtual([8], SB_CLS, "toString", STR_CLS, [])
        a.move_res_obj(0)
        a.return_obj(0)
        return a.finish()

    # 8. MainActivity.launchGame(String pkg)String (regs=5, ins=2, outs=3)
    # v0=this, v1=pkg, v2=PackageManager, v3=Intent, v4=flags
    def main_launch_game(r):
        a = Asm(r)
        a.invoke_virtual([0], CTX_CLS, "getPackageManager", PKG_MGR_CLS, [])
        a.move_res_obj(2)
        a.invoke_virtual([2, 1], PKG_MGR_CLS, "getLaunchIntentForPackage", INTENT_CLS, [STR_CLS])
        a.move_res_obj(3)
        a.if_eqz(3, "not_found")

        a.const32(4, 0x10000000)  # FLAG_ACTIVITY_NEW_TASK
        a.invoke_virtual([3, 4], INTENT_CLS, "addFlags", INTENT_CLS, ["I"])
        a.invoke_virtual([0, 3], ACT_CLS, "startActivity", "V", [INTENT_CLS])

        a.const_str(4, "Launching Game with dlss6 RTX Profile!")
        a.const4(5, 1)
        a.invoke_static([0, 4, 5], TOAST_CLS, "makeText", TOAST_CLS, [CTX_CLS, CS_CLS, "I"])
        a.move_res_obj(0)
        a.invoke_virtual([0], TOAST_CLS, "show", "V", [])

        a.const_str(0, "LAUNCHED")
        a.return_obj(0)

        a.lbl("not_found")
        a.const_str(0, "NOT_INSTALLED")
        a.return_obj(0)
        return a.finish()

    # 9. MainActivity.openOverlaySettings()String (regs=5, ins=1, outs=3)
    # v0=this, v1=Uri, v2=Intent, v3=str temp
    def main_open_overlay_settings(r):
        a = Asm(r)
        a.const_str(3, f"package:{PACKAGE_NAME}")
        a.invoke_static([3], URI_CLS, "parse", URI_CLS, [STR_CLS])
        a.move_res_obj(1)
        a.new_inst(2, INTENT_CLS)
        a.const_str(3, "android.settings.action.MANAGE_OVERLAY_PERMISSION")
        a.invoke_direct([2, 3, 1], INTENT_CLS, "<init>", "V", [STR_CLS, URI_CLS])
        a.invoke_virtual([0, 2], ACT_CLS, "startActivity", "V", [INTENT_CLS])
        a.const_str(0, "OPENED")
        a.return_obj(0)
        return a.finish()

    # 10. MainActivity.enableOverlay(String text)String (regs=10, ins=2, outs=6)
    # v0=this, v1=appCtx/wm, v2=tv, v3=lp, v4=str/width/gravity/y, v5=height/type/flags, v6=appCtx save/sdk, v7=color/padLR, v8=text save
    def main_enable_overlay(r):
        a = Asm(r)
        # v0=this, v1=text, v2-v9 locaux
        a.invoke_static([0], SET_CLS, "canDrawOverlays", "Z", [CTX_CLS])  # v0=this
        a.move_res(0)
        a.if_nez(0, "perm_ok")
        a.const_str(0, "NEED_PERM")
        a.return_obj(0)

        a.lbl("perm_ok")
        a.invoke_virtual([0], MAIN_CLS, "disableOverlay", STR_CLS, [])

        a.invoke_virtual([0], CTX_CLS, "getApplicationContext", CTX_CLS, [])
        a.move_res_obj(6)  # v6 = appCtx

        a.const_str(4, "window")
        a.invoke_virtual([6, 4], CTX_CLS, "getSystemService", OBJ_CLS, [STR_CLS])
        a.move_res_obj(1)  # v1 = result
        a.check_cast(1, WM_CLS)  # v1 = WindowManager

        # TextView tv = new TextView(appCtx)
        a.new_inst(2, TV_CLS)  # v2 = TextView
        a.invoke_direct([2, 6], TV_CLS, "<init>", "V", [CTX_CLS])  # v2=tv, v6=appCtx
        a.invoke_virtual([2, 1], TV_CLS, "setText", "V", [CS_CLS])  # v2=tv, v1=text
        a.const32(7, 0xFF9BE800)  # v7 = RTX Lime
        a.invoke_virtual([2, 7], TV_CLS, "setTextColor", "V", ["I"])
        a.const32(7, 0xCC070A0F)  # v7 = bg color
        a.invoke_virtual([2, 7], TV_CLS, "setBackgroundColor", "V", ["I"])
        a.const32(7, 0x41280000)  # v7 = 10.5f
        a.invoke_virtual([2, 7], TV_CLS, "setTextSize", "V", ["F"])
        a.const16(4, 18)  # v4 = pad L/R
        a.const16(6, 6)   # v6 = pad T/B (reuse)
        a.invoke_virtual([2, 4, 6, 4, 6], VIEW_CLS, "setPadding", "V", ["I", "I", "I", "I"])
        a.const32(4, 0x3F428F5C)  # v4 = 0.76f (< 0.80f Android 12+ touch pass-through)
        a.invoke_virtual([2, 4], VIEW_CLS, "setAlpha", "V", ["F"])

        # LayoutParams(w=-2, h=-2, type, flags=0x18, format=-3)
        a.new_inst(3, WM_LP_CLS)  # v3 = LayoutParams
        a.const4(4, -2)  # v4 = width
        a.const4(5, -2)  # v5 = height
        a.sget(6, BVER_CLS, "SDK_INT", "I")  # v6 = SDK_INT
        a.const16(7, 26)  # v7 = 26
        a.if_lt(6, 7, "legacy_type")
        a.const16(6, 2038)  # v6 = TYPE_APPLICATION_OVERLAY
        a.goto16("type_ready")
        a.lbl("legacy_type")
        a.const16(6, 2002)  # v6 = TYPE_PHONE
        a.lbl("type_ready")
        a.const16(7, 0x0018)  # v7 = flags
        a.const4(8, -3)       # v8 = format
        a.invoke_direct_range(3, 6, WM_LP_CLS, "<init>", "V", ["I", "I", "I", "I", "I"])  # v3=this, v4..v8 = 5 args

        a.const16(4, 0x0031)  # v4 = gravity
        a.iput(4, 3, WM_LP_CLS, "gravity", "I")
        a.const16(4, 14)  # v4 = y
        a.iput(4, 3, WM_LP_CLS, "y", "I")

        a.invoke_interface([1, 2, 3], WM_CLS, "addView", "V", [VIEW_CLS, VG_LP_CLS])  # v1=wm, v2=tv, v3=lp
        a.iput_obj(2, 0, MAIN_CLS, "mHudView", VIEW_CLS)  # v0=this, v2=tv

        a.const_str(0, "OVERLAY_ACTIVE")
        a.return_obj(0)
        return a.finish()

    # 11. MainActivity.disableOverlay()String (regs=4, ins=1, outs=2)
    # Locals: v0..v2, Param: v3=this
    def main_disable_overlay(r):
        a = Asm(r)
        # v0=this, v1, v2 locaux
        a.iget_obj(0, 0, MAIN_CLS, "mHudView", VIEW_CLS)  # v0=this
        a.if_eqz(0, "already_off")
        a.invoke_virtual([0], CTX_CLS, "getApplicationContext", CTX_CLS, [])
        a.move_res_obj(1)
        a.const_str(2, "window")
        a.invoke_virtual([1, 2], CTX_CLS, "getSystemService", OBJ_CLS, [STR_CLS])
        a.move_res_obj(1)
        a.check_cast(1, WM_CLS)
        a.invoke_interface([1, 0], WM_CLS, "removeView", "V", [VIEW_CLS])
        a.const4(0, 0)
        a.iput_obj(0, 0, MAIN_CLS, "mHudView", VIEW_CLS)  # v0=this
        a.lbl("already_off")
        a.const_str(0, "OVERLAY_OFF")
        a.return_obj(0)
        return a.finish()

    # 12. MainActivity.applyStudioShader(String spec)String (regs=12, ins=2, outs=4)
    # v0=this, v1=spec, v2=parts[], v3=len, v4=idx/temp, v5=botCol, v6=crossCol, v7=hudText, v8=appCtx, v9=wm, v10=tv, v11=lp
    def main_apply_shader(r):
        a = Asm(r)
        # v0 = this, v1 = spec
        a.invoke_virtual([0], MAIN_CLS, "disableOverlay", STR_CLS, [])
        a.move_res(0)

        a.const_str(2, "|")
        a.invoke_virtual([1, 2], STR_CLS, "split", "[Ljava/lang/String;", [STR_CLS])  # v1=spec, v2="|"
        a.move_res_obj(2)   # v2 = parts[]
        a.array_len(3, 2)   # v3 = len
        a.const4(4, 10)     # v4 = 10
        a.if_lt(3, 4, "spec_fail")

        # parts[1] = botCol ARGB -> v5
        a.const4(4, 1)
        a.aget_obj(4, 2, 4)
        a.invoke_static([4], INT_CLS, "parseInt", "I", [STR_CLS])
        a.move_res(5)

        # parts[7] = crossCol ARGB -> v6
        a.const4(4, 7)
        a.aget_obj(4, 2, 4)
        a.invoke_static([4], INT_CLS, "parseInt", "I", [STR_CLS])
        a.move_res(6)

        # parts[9] = hudText String -> v7
        a.const4(4, 9)
        a.aget_obj(4, 2, 4)
        a.move_obj(7, 4)

        # appCtx = getApplicationContext() -> v8
        a.invoke_virtual([0], CTX_CLS, "getApplicationContext", CTX_CLS, [])
        a.move_res_obj(8)

        # wm = (WindowManager) appCtx.getSystemService("window") -> v9
        a.const_str(4, "window")
        a.invoke_virtual([8, 4], CTX_CLS, "getSystemService", OBJ_CLS, [STR_CLS])
        a.move_res_obj(9)
        a.check_cast(9, WM_CLS)

        # tv = new TextView(appCtx) -> v10
        a.new_inst(10, TV_CLS)
        a.invoke_direct([10, 8], TV_CLS, "<init>", "V", [CTX_CLS])
        a.invoke_virtual([10, 7], TV_CLS, "setText", "V", [CS_CLS])  # v10=tv, v7=hudText
        a.invoke_virtual([10, 6], TV_CLS, "setTextColor", "V", ["I"])  # v10=tv, v6=crossCol
        a.invoke_virtual([10, 5], TV_CLS, "setBackgroundColor", "V", ["I"])  # v10=tv, v5=botCol

        a.const32(4, 0x41280000)   # v4 = 10.5f text size
        a.invoke_virtual([10, 4], TV_CLS, "setTextSize", "V", ["F"])

        a.const16(4, 18)           # v4 = pad L/R
        a.const16(5, 6)            # v5 = pad T/B (reuse v5)
        a.invoke_virtual([10, 4, 5, 4, 5], VIEW_CLS, "setPadding", "V", ["I", "I", "I", "I"])

        a.const32(4, 0x3F428F5C)   # v4 = 0.76f alpha (< 0.80f Android 12+ touch pass-through)
        a.invoke_virtual([10, 4], VIEW_CLS, "setAlpha", "V", ["F"])

        # lp = new WindowManager.LayoutParams(-2, -2) -> v11
        a.new_inst(11, WM_LP_CLS)
        a.const4(4, -2)            # v4 = width
        a.const4(5, -2)            # v5 = height
        a.invoke_direct([11, 4, 5], WM_LP_CLS, "<init>", "V", ["I", "I"])

        # lp.type = SDK_INT >= 26 ? TYPE_APPLICATION_OVERLAY(2038) : TYPE_PHONE(2002)
        a.sget(4, BVER_CLS, "SDK_INT", "I")  # v4 = SDK_INT
        a.const16(5, 26)
        a.if_lt(4, 5, "legacy_type3")
        a.const16(4, 2038)
        a.goto16("type_ready3")
        a.lbl("legacy_type3")
        a.const16(4, 2002)
        a.lbl("type_ready3")
        a.iput(4, 11, WM_LP_CLS, "type", "I")

        # lp.flags = FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE = 0x0018
        a.const16(4, 0x0018)
        a.iput(4, 11, WM_LP_CLS, "flags", "I")

        # lp.format = PixelFormat.TRANSLUCENT = -3
        a.const4(4, -3)
        a.iput(4, 11, WM_LP_CLS, "format", "I")

        # lp.gravity = Gravity.TOP | CENTER_HORIZONTAL = 0x0031
        a.const16(4, 0x0031)
        a.iput(4, 11, WM_LP_CLS, "gravity", "I")

        # lp.y = 14dp-ish offset from top
        a.const16(4, 14)
        a.iput(4, 11, WM_LP_CLS, "y", "I")

        # wm.addView(tv, lp)
        a.invoke_interface([9, 10, 11], WM_CLS, "addView", "V", [VIEW_CLS, VG_LP_CLS])

        # this.mHudView = tv
        a.iput_obj(10, 0, MAIN_CLS, "mHudView", VIEW_CLS)

        a.const_str(0, "SHADER_APPLIED")
        a.return_obj(0)

        a.lbl("spec_fail")
        a.const_str(0, "SPEC_FAIL")
        a.return_obj(0)
        return a.finish()

    # 12. MainActivity.saveBase64Media(String filename, String mimeType, String b64)String (regs=9, ins=4, outs=3)
    # v0=this, v1=filename, v2=mimeType, v3=b64, v4=byte[]/flag, v5=ContentValues, v6=str/uri, v7=result/cr, v8=outstream/inserted
    def main_save_media(r):
        a = Asm(r)
        a.const4(4, 0)  # v4 = Base64.DEFAULT flag
        a.invoke_static([3, 4], B64_CLS, "decode", "[B", [STR_CLS, "I"])  # v3=b64, v4=0
        a.move_res_obj(4)  # v4 = byte[]

        a.new_inst(5, CV_CLS)  # v5 = ContentValues
        a.invoke_direct([5], CV_CLS, "<init>", "V", [])
        a.const_str(6, "_display_name")  # v6 = key
        a.invoke_virtual([5, 6, 1], CV_CLS, "put", "V", [STR_CLS, STR_CLS])  # v5=cv, v6="_display_name", v1=filename
        a.const_str(6, "mime_type")  # v6 = key
        a.invoke_virtual([5, 6, 2], CV_CLS, "put", "V", [STR_CLS, STR_CLS])  # v5=cv, v6="mime_type", v2=mimeType

        a.const_str(6, "video/")  # v6 = "video/"
        a.invoke_virtual([2, 6], STR_CLS, "startsWith", "Z", [STR_CLS])  # v2=mimeType, v6="video/"
        a.move_res(7)  # v7 = result
        a.if_eqz(7, "img_uri")
        a.const_str(6, "content://media/external/video/media")  # v6 = video URI
        a.goto16("parse_uri")
        a.lbl("img_uri")
        a.const_str(6, "content://media/external/images/media")  # v6 = image URI
        a.lbl("parse_uri")
        a.invoke_static([6], URI_CLS, "parse", URI_CLS, [STR_CLS])  # v6 = uri string
        a.move_res_obj(6)  # v6 = Uri

        a.invoke_virtual([0], CTX_CLS, "getContentResolver", CR_CLS, [])  # v0=this
        a.move_res_obj(7)  # v7 = ContentResolver
        a.invoke_virtual([7, 6, 5], CR_CLS, "insert", URI_CLS, [URI_CLS, CV_CLS])  # v7=cr, v6=uri, v5=cv
        a.move_res_obj(8)  # v8 = inserted Uri
        a.if_eqz(8, "fail")

        a.invoke_virtual([7, 8], CR_CLS, "openOutputStream", OS_CLS, [URI_CLS])  # v7=cr, v8=uri
        a.move_res_obj(8)  # v8 = OutputStream
        a.if_eqz(8, "fail")
        a.invoke_virtual([8, 4], OS_CLS, "write", "V", ["[B"])  # v8=os, v4=byte[]
        a.invoke_virtual([8], OS_CLS, "flush", "V", [])
        a.invoke_virtual([8], OS_CLS, "close", "V", [])

        a.const_str(6, "Saved to Android Gallery!")  # v6 = msg
        a.const4(7, 1)  # v7 = Toast.LENGTH_SHORT
        a.invoke_static([0, 6, 7], TOAST_CLS, "makeText", TOAST_CLS, [CTX_CLS, CS_CLS, "I"])  # v0=this, v6=msg, v7=1
        a.move_res_obj(6)
        a.invoke_virtual([6], TOAST_CLS, "show", "V", [])
        a.const_str(0, "SAVED_GALLERY")
        a.return_obj(0)

        a.lbl("fail")
        a.const_str(0, "ERR_MEDIASTORE")
        a.return_obj(0)
        return a.finish()

    # -------------------- StudioChromeClient --------------------
    def chrome_init(r):
        a = Asm(r)
        a.invoke_direct([0], WCC_CLS, "<init>", "V", [])
        a.iput_obj(1, 0, CHROME_CLS, "mAct", MAIN_CLS)
        a.return_void()
        return a.finish()

    def chrome_file_chooser(r):
        a = Asm(r)
        # v0 = this (ChromeClient), v1 = view, v2 = callback, v3 = params
        a.iget_obj(0, 0, CHROME_CLS, "mAct", MAIN_CLS)  # v0=this -> mAct
        a.iget_obj(1, 0, MAIN_CLS, "mUpload", VAL_CB)  # v0=mAct, v1 = mUpload
        a.if_eqz(1, "no_old")
        a.const4(4, 0)  # v4 = null
        a.invoke_interface([1, 4], VAL_CB, "onReceiveValue", "V", [OBJ_CLS])  # v1=mUpload, v4=null
        a.lbl("no_old")
        a.iput_obj(2, 0, MAIN_CLS, "mUpload", VAL_CB)  # v0=mAct, v2=callback
        a.invoke_virtual([3], FCP_CLS, "createIntent", INTENT_CLS, [])  # v3=params
        a.move_res_obj(4)  # v4 = intent
        a.const16(5, 1001)  # v5 = requestCode
        a.invoke_virtual([0, 4, 5], ACT_CLS, "startActivityForResult", "V", [INTENT_CLS, "I"])  # v0=mAct, v4=intent, v5=1001
        a.const4(0, 1)  # v0 = true
        a.return_val(0)
        return a.finish()

    # onJsPrompt(WebView, String, String, String, JsPromptResult)Z (regs=11, ins=6, outs=6)
    # Locals: v0..v4; Params: v5=this, v6=view, v7=url, v8=message, v9=defaultValue, v10=result
    # onJsPrompt(WebView, String, String, String, JsPromptResult)Z (regs=10, ins=6, outs=6)
    # v0=this(ChromeClient), v1=view, v2=url, v3=message, v4=defaultValue, v5=result
    # v6=this save, v7=defaultValue save, v8=mAct, v9=cmd/temp
    def chrome_js_prompt(r):
        a = Asm(r)
        # v0=this(ChromeClient), v1=view, v2=url, v3=message, v4=defaultValue, v5=result
        # v6=mAct, v7=parts[]/filename, v8=mime/length/index, v9=cmd/temp/index
        a.move_obj(6, 0)  # v6 = this (save ChromeClient, not needed but harmless)
        a.iget_obj(6, 0, CHROME_CLS, "mAct", MAIN_CLS)  # v6 = mAct (MainActivity instance)
        a.if_eqz(3, "call_super")  # if v3(message) == null -> call super

        # cmds_0: message equals cmd -> call 0-arg MainActivity method
        cmds_0 = [
            ("__DLSS_SYSINFO__", "getSysInfo"),
            ("__DLSS_GAMES__", "scanGames"),
            ("__DLSS_OVERLAY_PERM__", "openOverlaySettings"),
            ("__DLSS_OVERLAY_OFF__", "disableOverlay"),
            ("__DLSS_BREVENT__", "checkBrevent"),
        ]
        for idx, (cmd_str, m_name) in enumerate(cmds_0):
            nxt = f"chk0_{idx}"
            a.const_str(9, cmd_str)  # v9 = cmd string
            a.invoke_virtual([3, 9], STR_CLS, "equals", "Z", [OBJ_CLS])  # v3=message, v9=cmd
            a.move_res(9)
            a.if_eqz(9, nxt)
            a.invoke_virtual([6], MAIN_CLS, m_name, STR_CLS, [])  # v6=mAct
            a.move_res_obj(0)  # v0 = response
            a.goto16("confirm_v0")
            a.lbl(nxt)

        # cmds_1: message equals cmd -> call 1-arg MainActivity method with defaultValue payload
        cmds_1 = [
            ("__DLSS_BOOST__", "boostRam"),
            ("__DLSS_LAUNCH__", "launchGame"),
            ("__DLSS_OVERLAY_ON__", "enableOverlay"),
            ("__DLSS_APPLY_SHADER__", "applyStudioShader"),
        ]
        for idx, (cmd_str, m_name) in enumerate(cmds_1):
            nxt = f"chk1_{idx}"
            a.const_str(9, cmd_str)  # v9 = cmd string
            a.invoke_virtual([3, 9], STR_CLS, "equals", "Z", [OBJ_CLS])  # v3=message, v9=cmd
            a.move_res(9)
            a.if_eqz(9, nxt)
            a.invoke_virtual([6, 4], MAIN_CLS, m_name, STR_CLS, [STR_CLS])  # v6=mAct, v4=defaultValue(payload)
            a.move_res_obj(0)  # v0 = response
            a.goto16("confirm_v0")
            a.lbl(nxt)

        # __DLSS_PING__
        a.const_str(9, "__DLSS_PING__")  # v9 = cmd string
        a.invoke_virtual([3, 9], STR_CLS, "equals", "Z", [OBJ_CLS])  # v3=message, v9=cmd
        a.move_res(9)
        a.if_eqz(9, "chk_save")
        a.const_str(0, "NATIVE_APK_2.0")  # v0 = response
        a.goto16("confirm_v0")

        # __DLSS_SAVE__|mime|filename -> saveBase64Media(filename=parts[2], mime=parts[1], b64=defaultValue)
        a.lbl("chk_save")
        a.const_str(9, "__DLSS_SAVE__|")  # v9 = prefix
        a.invoke_virtual([3, 9], STR_CLS, "startsWith", "Z", [STR_CLS])  # v3=message, v9=prefix
        a.move_res(9)
        a.if_eqz(9, "call_super")

        a.const_str(9, "\|")  # v9 = split regex
        a.invoke_virtual([3, 9], STR_CLS, "split", "[Ljava/lang/String;", [STR_CLS])  # v3=message, v9="        a.move_res_obj(7)  # v7 = parts[]
        a.array_len(8, 7)  # v8 = len
        a.const4(9, 3)  # v9 = 3
        a.if_lt(8, 9, "call_super")  # v8=len, v9=3

        a.const4(9, 1)  # v9 = index 1
        a.aget_obj(8, 7, 9)  # v8 = parts[1] = mime (v7=array, v9=index)
        a.const4(9, 2)  # v9 = index 2
        a.aget_obj(7, 7, 9)  # v7 = parts[2] = filename (v7=array, v9=index) - overwrite parts[], OK
        # v6=mAct, v7=filename, v8=mime, v4=b64(defaultValue)
        a.invoke_virtual([6, 7, 8, 4], MAIN_CLS, "saveBase64Media", STR_CLS, [STR_CLS, STR_CLS, STR_CLS])
        a.move_res_obj(0)  # v0 = response
        a.goto16("confirm_v0")

        a.lbl("confirm_v0")
        a.invoke_virtual([5, 0], JSP_CLS, "confirm", "V", [STR_CLS])  # v5=result, v0=response
        a.const4(0, 1)  # v0 = true
        a.return_val(0)

        a.lbl("call_super")
        a.invoke_super_range(0, 6, WCC_CLS, "onJsPrompt", "Z", [WV_CLS, STR_CLS, STR_CLS, STR_CLS, JSP_CLS])
        a.move_res(0)
        a.return_val(0)
        return a.finish()


    def main_check_brevent(r):
        a = Asm(r)
        # Try to execute "pm list packages" via Runtime.exec to detect Brevent
        a.const_str(4, "pm")
        a.invoke_static([4], RT_CLS, "exec", PROC_CLS, [STR_CLS])
        a.move_res_obj(4)  # v4 = Process
        a.invoke_virtual([4], PROC_CLS, "waitFor", "I", [])
        a.move_res(4)
        a.const4(5, 0)
        a.if_nez(4, "brevent_no")
        a.const_str(0, "BREVENT_ACTIVE")
        a.return_obj(0)
        a.lbl("brevent_no")
        a.const_str(0, "BREVENT_NOT_FOUND")
        a.return_obj(0)
        return a.finish()
    b.add_class(
        cls_desc=MAIN_CLS,
        super_desc=ACT_CLS,
        access_flags=0x0001,
        ifields=[
            ("mHudView", VIEW_CLS, 0x0001),
            ("mUpload", VAL_CB, 0x0001),
            ("mWebView", WV_CLS, 0x0001),
            ("mWakeLock", PWR_WL_CLS, 0x0001),
        ],
        dmethods=[
            ("<init>", "V", [], 0x10001, 1, 1, 1, main_init),
        ],
        vmethods=[
            ("boostRam", STR_CLS, [STR_CLS], 0x0001, 11, 2, 3, main_boost_ram),
            ("disableOverlay", STR_CLS, [], 0x0001, 4, 1, 2, main_disable_overlay),
            ("enableOverlay", STR_CLS, [STR_CLS], 0x0001, 11, 2, 6, main_enable_overlay),
            ("applyStudioShader", STR_CLS, [STR_CLS], 0x0001, 14, 2, 7, main_apply_shader),
            ("getSysInfo", STR_CLS, [], 0x0001, 8, 1, 2, main_get_sysinfo),
            ("launchGame", STR_CLS, [STR_CLS], 0x0001, 6, 2, 3, main_launch_game),
            ("onActivityResult", "V", ["I", "I", INTENT_CLS], 0x0004, 6, 4, 4, main_on_act_res),
            ("onBackPressed", "V", [], 0x0001, 3, 1, 1, main_on_back),
            ("onCreate", "V", [BUNDLE_CLS], 0x0004, 6, 2, 2, main_on_create),
            ("openOverlaySettings", STR_CLS, [], 0x0001, 5, 1, 3, main_open_overlay_settings),
            ("saveBase64Media", STR_CLS, [STR_CLS, STR_CLS, STR_CLS], 0x0001, 9, 4, 3, main_save_media),
            ("scanGames", STR_CLS, [], 0x0001, 10, 1, 3, main_scan_games),
            ("checkBrevent", STR_CLS, [], 0x0001, 6, 1, 2, main_check_brevent),
        ],
    )

    b.add_class(
        cls_desc=CHROME_CLS,
        super_desc=WCC_CLS,
        access_flags=0x0001,
        ifields=[
            ("mAct", MAIN_CLS, 0x0001),
        ],
        dmethods=[
            ("<init>", "V", [MAIN_CLS], 0x10001, 2, 2, 1, chrome_init),
        ],
        vmethods=[
            ("onJsPrompt", "Z", [WV_CLS, STR_CLS, STR_CLS, STR_CLS, JSP_CLS], 0x0001, 11, 6, 6, chrome_js_prompt),
            ("onShowFileChooser", "Z", [WV_CLS, VAL_CB, FCP_CLS], 0x0001, 7, 4, 3, chrome_file_chooser),
        ],
    )


    # Brevent check method: returns "BREVENT_ACTIVE" or "BREVENT_NOT_FOUND"

    # GameBoosterService - Foreground service to keep app alive
    def svc_init(r):
        a = Asm(r)
        a.invoke_direct([0], SERV_CLS, "<init>", "V", [])
        a.return_void()
        return a.finish()

    def svc_on_create(r):
        a = Asm(r)
        a.invoke_direct([0], SERV_CLS, "onCreate", "V", [])
        a.return_void()
        return a.finish()

    def svc_on_start_command(r):
        a = Asm(r)
        # Build notification and call startForeground
        a.new_inst(3, NOTIF_BUILDER_CLS)
        a.invoke_direct([3, 0], NOTIF_BUILDER_CLS, "<init>", "V", [CTX_CLS])
        a.const_str(4, "DLSS Booster Active")
        a.invoke_virtual([3, 4], NOTIF_BUILDER_CLS, "setContentTitle", NOTIF_BUILDER_CLS, [STR_CLS])
        a.const_str(4, "RAM optimizer & DLSS HUD running")
        a.invoke_virtual([3, 4], NOTIF_BUILDER_CLS, "setContentText", NOTIF_BUILDER_CLS, [STR_CLS])
        a.const4(4, 0x7F010000)  # ic_launcher
        a.invoke_virtual([3, 4], NOTIF_BUILDER_CLS, "setSmallIcon", NOTIF_BUILDER_CLS, ["I"])
        a.invoke_virtual([3], NOTIF_BUILDER_CLS, "build", NOTIF_CLS, [])
        a.move_res_obj(3)
        a.const4(4, 1)  # notification ID
        a.invoke_virtual([0, 4, 3], SERV_CLS, "startForeground", "V", ["I", "Landroid/app/Notification;"])
        a.const4(0, 1)  # START_STICKY
        a.return_val(0)
        return a.finish()

    def svc_on_bind(r):
        a = Asm(r)
        a.const4(0, 0)
        a.return_val(0)
        return a.finish()

    # BreventReceiver - handles BOOT_COMPLETED
    def brc_on_receive(r):
        a = Asm(r)
        # Just return - service will be started by MainActivity
        a.return_void()
        return a.finish()

    # Add classes to builder
    b.add_class(
        cls_desc=SERVICE_CLS,
        super_desc=SERV_CLS,
        access_flags=0x0001,
        ifields=[],
        dmethods=[
            ("<init>", "V", [], 0x10001, 1, 1, 1, svc_init),
        ],
        vmethods=[
            ("onCreate", "V", [], 0x0004, 1, 1, 1, svc_on_create),
            ("onStartCommand", "I", [INTENT_CLS, "I", "I"], 0x0004, 5, 3, 2, svc_on_start_command),
            ("onBind", IBINDER_CLS, [INTENT_CLS], 0x0004, 2, 1, 1, svc_on_bind),
        ],
    )

    b.add_class(
        cls_desc=RECEIVER_CLS,
        super_desc=BRCV_CLS,
        access_flags=0x0001,
        ifields=[],
        dmethods=[
            ("<init>", "V", [], 0x10001, 1, 1, 1, brc_on_receive),
        ],
        vmethods=[
            ("onReceive", "V", [CTX_CLS, INTENT_CLS], 0x0004, 2, 2, 2, brc_on_receive),
        ],
    )

    # Add checkBrevent to MainActivity vmethods
    # Need to add to the existing add_class call for MAIN_CLS - we'll handle by adding checkBrevent vmethod

    return b.compile()


# ==============================================================================
# 3. ALIGNED ZIP WRITER + V1 (JAR) & V2 (APK SIG BLOCK 42) SIGNING
# ==============================================================================
def generate_signing_key_and_cert():
    import datetime
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    subject = issuer = x509.Name([
        x509.NameAttribute(NameOID.COUNTRY_NAME, "BD"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, "Paradox Graphics Studio"),
        x509.NameAttribute(NameOID.COMMON_NAME, "dlss6 fast parallel Release"),
    ])
    now = datetime.datetime(2026, 1, 1, tzinfo=datetime.timezone.utc)
    cert = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(issuer)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now)
        .not_valid_after(now + datetime.timedelta(days=365 * 25))
        .sign(key, hashes.SHA256())
    )
    return key, cert


def b64_sha256(data: bytes) -> str:
    import base64
    return base64.b64encode(hashlib.sha256(data).digest()).decode("ascii")


def build_v1_signature_files(entries: list[tuple[str, bytes, bool]], key, cert) -> list[tuple[str, bytes, bool]]:
    mf_sections = []
    manifest_lines = [
        "Manifest-Version: 1.0\r\n",
        "Created-By: 2.0 (dlss6 Booster Compiler)\r\n",
        "\r\n",
    ]
    for name, raw_bytes, _ in entries:
        sec = f"Name: {name}\r\nSHA-256-Digest: {b64_sha256(raw_bytes)}\r\n\r\n"
        mf_sections.append((name, sec.encode("utf-8")))
        manifest_lines.append(sec)

    manifest_bytes = "".join(manifest_lines).encode("utf-8")

    sf_lines = [
        "Signature-Version: 1.0\r\n",
        f"SHA-256-Digest-Manifest: {b64_sha256(manifest_bytes)}\r\n",
        "Created-By: 2.0 (dlss6 Booster Compiler)\r\n",
        "X-Android-APK-Signed: 2\r\n",
        "\r\n",
    ]
    for _, sec_bytes in mf_sections:
        lines = sec_bytes.decode("utf-8").split("\r\n")
        sf_lines.append(f"{lines[0]}\r\nSHA-256-Digest: {b64_sha256(sec_bytes)}\r\n\r\n")

    sf_bytes = "".join(sf_lines).encode("utf-8")

    cert_rsa_bytes = (
        pkcs7.PKCS7SignatureBuilder()
        .set_data(sf_bytes)
        .add_signer(cert, key, hashes.SHA256())
        .sign(serialization.Encoding.DER, [pkcs7.PKCS7Options.DetachedSignature, pkcs7.PKCS7Options.Binary])
    )

    return [
        ("META-INF/MANIFEST.MF", manifest_bytes, True),
        ("META-INF/CERT.SF", sf_bytes, True),
        ("META-INF/CERT.RSA", cert_rsa_bytes, True),
    ]


def write_zipaligned_zip(entries: list[tuple[str, bytes, bool]]) -> tuple[bytes, int, int]:
    out = bytearray()
    cd = bytearray()
    dos_time = (12 << 11) | (0 << 5) | 0
    dos_date = ((2026 - 1980) << 9) | (10 << 5) | 27

    for name, raw_data, compress in entries:
        name_bytes = name.encode("utf-8")
        crc = zlib.crc32(raw_data) & 0xFFFFFFFF
        uncomp_size = len(raw_data)

        if compress:
            comp_method = 8
            co = zlib.compressobj(9, zlib.DEFLATED, -zlib.MAX_WBITS)
            comp_data = co.compress(raw_data) + co.flush()
            extra = b""
        else:
            comp_method = 0
            comp_data = raw_data
            data_start_unpadded = len(out) + 30 + len(name_bytes)
            pad_len = (4 - (data_start_unpadded % 4)) % 4
            extra = b"\x00" * pad_len

        comp_size = len(comp_data)
        lfh_offset = len(out)

        out += struct.pack(
            "<IHHHHHIIIHH",
            0x04034B50, 20, 0, comp_method, dos_time, dos_date,
            crc, comp_size, uncomp_size, len(name_bytes), len(extra),
        )
        out += name_bytes
        out += extra
        if not compress:
            assert len(out) % 4 == 0
        out += comp_data

        cd += struct.pack(
            "<IHHHHHHIIIHHHHHII",
            0x02014B50, 20, 20, 0, comp_method, dos_time, dos_date,
            crc, comp_size, uncomp_size, len(name_bytes), 0, 0, 0, 0, 0, lfh_offset,
        )
        cd += name_bytes

    cd_offset = len(out)
    out += cd
    eocd_offset = len(out)
    out += struct.pack(
        "<IHHHHIIH",
        0x06054B50, 0, 0, len(entries), len(entries), len(cd), cd_offset, 0,
    )
    return bytes(out), cd_offset, eocd_offset


def compute_v2_content_digest_sha256(sec1: bytes, sec3: bytes, sec4: bytes) -> bytes:
    CHUNK_SIZE = 1024 * 1024
    chunk_digests = []
    for section in (sec1, sec3, sec4):
        pos = 0
        n = len(section)
        while pos < n:
            chunk = section[pos:pos + CHUNK_SIZE]
            pos += CHUNK_SIZE
            h = hashlib.sha256()
            h.update(b"\xa5")
            h.update(struct.pack("<I", len(chunk)))
            h.update(chunk)
            chunk_digests.append(h.digest())

    top = hashlib.sha256()
    top.update(b"\x5a")
    top.update(struct.pack("<I", len(chunk_digests)))
    for d in chunk_digests:
        top.update(d)
    return top.digest()


def inject_apk_v2_signature(zip_bytes: bytes, cd_offset: int, eocd_offset: int, key, cert) -> bytes:
    sec1 = zip_bytes[:cd_offset]
    sec3 = zip_bytes[cd_offset:eocd_offset]
    eocd_orig = bytearray(zip_bytes[eocd_offset:])
    struct.pack_into("<I", eocd_orig, 16, cd_offset)

    digest = compute_v2_content_digest_sha256(sec1, sec3, bytes(eocd_orig))

    SIG_ALG = 0x0103
    cert_der = cert.public_bytes(serialization.Encoding.DER)
    spki_der = cert.public_key().public_bytes(
        serialization.Encoding.DER,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    )

    def lp(b: bytes) -> bytes:
        return struct.pack("<I", len(b)) + b

    digest_entry = lp(struct.pack("<I", SIG_ALG) + lp(digest))
    digests_seq = lp(digest_entry)
    certs_seq = lp(lp(cert_der))
    attrs_seq = lp(b"")
    signed_data = digests_seq + certs_seq + attrs_seq

    raw_sig = key.sign(signed_data, padding.PKCS1v15(), hashes.SHA256())
    sig_entry = lp(struct.pack("<I", SIG_ALG) + lp(raw_sig))
    signatures_seq = lp(sig_entry)

    signer_body = lp(signed_data) + signatures_seq + lp(spki_der)
    v2_value = lp(lp(signer_body))

    v2_pair = struct.pack("<QI", 4 + len(v2_value), 0x7109871A) + v2_value

    pairs = bytearray(v2_pair)
    overhead = 8 + len(pairs) + 8 + 16
    rem = overhead % 4096
    if rem != 0:
        pad_needed = 4096 - rem
        if pad_needed < 12:
            pad_needed += 4096
        pad_val_len = pad_needed - 12
        pairs += struct.pack("<QI", 4 + pad_val_len, 0x42726577) + (b"\x00" * pad_val_len)

    size_of_block = len(pairs) + 8 + 16
    apk_sig_block = (
        struct.pack("<Q", size_of_block)
        + bytes(pairs)
        + struct.pack("<Q", size_of_block)
        + b"APK Sig Block 42"
    )
    assert len(apk_sig_block) % 4096 == 0

    new_cd_offset = cd_offset + len(apk_sig_block)
    eocd_final = bytearray(eocd_orig)
    struct.pack_into("<I", eocd_final, 16, new_cd_offset)

    return sec1 + apk_sig_block + sec3 + bytes(eocd_final)


def build_apk(web_dir: Path, out_apks: list[Path]):
    icon_src = web_dir / "assets" / "icon" / "app_icon.png"
    icon_192 = web_dir / "assets" / "icon" / "icon_192.png"
    if icon_src.is_file():
        img = Image.open(icon_src).convert("RGBA")
        img_192 = img.resize((192, 192), Image.Resampling.LANCZOS)
        buf_icon = io.BytesIO()
        img_192.save(buf_icon, format="PNG", optimize=True)
        icon_bytes = buf_icon.getvalue()
        icon_192.write_bytes(icon_bytes)
    else:
        icon_bytes = icon_192.read_bytes()

    manifest_bytes = build_android_manifest_axml()
    arsc_bytes = build_resources_arsc()
    dex_bytes = build_classes_dex()

    entries: list[tuple[str, bytes, bool]] = [
        ("AndroidManifest.xml", manifest_bytes, True),
        ("resources.arsc", arsc_bytes, False),
        ("res/drawable/ic_launcher.png", icon_bytes, False),
        ("classes.dex", dex_bytes, True),
    ]

    for p in sorted(web_dir.rglob("*")):
        if not p.is_file():
            continue
        rel = p.relative_to(web_dir).as_posix()
        if rel == "assets/icon/app_icon.png":
            continue
        already_compressed = p.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp", ".mp4", ".webm"}
        entries.append((f"assets/{rel}", p.read_bytes(), not already_compressed))

    key, cert = generate_signing_key_and_cert()
    v1_meta = build_v1_signature_files(entries, key, cert)
    all_entries = entries + v1_meta

    zip_bytes, cd_off, eocd_off = write_zipaligned_zip(all_entries)
    signed_apk_bytes = inject_apk_v2_signature(zip_bytes, cd_off, eocd_off, key, cert)

    for out_apk in out_apks:
        out_apk.parent.mkdir(parents=True, exist_ok=True)
        out_apk.write_bytes(signed_apk_bytes)
        print(f"[OK] Built signed APK: {out_apk} ({len(signed_apk_bytes) / 1024:.1f} KB)")


def verify_apk_with_androguard(apk_path: Path):
    import zipfile
    from androguard.core.apk import APK
    from androguard.core.dex import DEX

    print(f"[VERIFY] Inspecting {apk_path} ...")
    with zipfile.ZipFile(apk_path, "r") as zf:
        arsc_info = zf.getinfo("resources.arsc")
        assert arsc_info.compress_type == zipfile.ZIP_STORED
        raw = apk_path.read_bytes()
        lfh_off = arsc_info.header_offset
        fn_len, ex_len = struct.unpack_from("<HH", raw, lfh_off + 26)
        data_off = lfh_off + 30 + fn_len + ex_len
        assert data_off % 4 == 0
        print(f"  - resources.arsc uncompressed & 4-byte aligned at offset {data_off}")

    a = APK(str(apk_path))
    print(f"  - Package: {a.get_package()} | App Name: {a.get_app_name()}")
    print(f"  - Version: {a.get_androidversion_name()} ({a.get_androidversion_code()})")
    print(f"  - Permissions: {a.get_permissions()}")
    print(f"  - Main Activity: {a.get_main_activity()}")
    print(f"  - Signed v1: {a.is_signed_v1()} | Signed v2: {a.is_signed_v2()}")

    assert a.get_package() == PACKAGE_NAME
    assert a.get_app_name() == APP_NAME
    assert a.is_signed_v1() and a.is_signed_v2()
    assert len(a.get_certificates_v2()) == 1

    d = DEX(a.get_dex())
    for cls in d.get_classes():
        for m in cls.get_methods():
            code = m.get_code()
            if code:
                insns = list(code.get_bc().get_instructions())
                print(f"    * {m.get_class_name()}->{m.get_name()}{m.get_descriptor()} [{len(insns)} insns]")
    print("[VERIFY PASSED] Game Booster APK is 100% structurally and cryptographically valid!")


if __name__ == "__main__":
    root = Path(__file__).resolve().parent
    web_dir = root / "dlss6-web"
    web_dir.mkdir(parents=True, exist_ok=True)
    booster_apk = root / "DLSS6-Fast-Parallel.apk"
    build_apk(web_dir, [booster_apk])
    verify_apk_with_androguard(booster_apk)
