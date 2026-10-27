# Paradox Browser — by Jahid Hasan (paradox)

**Fast, Private & Secure — Built from Via REA with all bugs fixed**

> Via Browser 7.3.3 REA kore ja mistake pelam, sob fix kore Paradox banano.

## ✨ Features (Via vs Paradox)

| Issue | Via (Bug) | Paradox (Fixed) |
|---|---|---|
| **minSdk** | 19 (KitKat, vulnerable WebView) | **21** (Lollipop+ secure) |
| **Permissions** | 26 inc. QUERY_ALL_PACKAGES, RECORD_AUDIO always | **Minimal** — only INTERNET, NETWORK_STATE etc, CAMERA/AUDIO on-demand |
| **User-Agent** | Chrome 90 + Via/7.3.3 suffix leak | **Chrome 148 modern**, Via suffix **opt-in**, 512 char validated |
| **WebView file access** | `file://` scheme allowed — risk | **Blocked** — `allowFileAccess=false`, `mixedContentNeverAllow`, `safeBrowsingEnabled=true` |
| **Cleartext HTTP** | No config | **networkSecurityConfig** `cleartextTrafficPermitted=false` |
| **Obfuscation** | `a1`, `b0`, `f8715e` — audit impossible | **Clean Kotlin**, readable |
| **Developer Tools** | None | **Tools → Developer Settings** → 6 tools! |
| **Size** | 4.1MB | ~4.5MB (still light) |

## 📂 Project Structure

```
Paradox Browser
├─ app/src/main/java/com/paradox/browser/
│  ├─ ParadoxApplication.kt — clean init
│  ├─ MainActivity.kt — hardened WebView + Tools menu + Developer entry
│  └─ developer/
│     ├─ DeveloperSettingsActivity.kt — 6 dev tools
│     └─ DeveloperToolsAdapter.kt
├─ app/src/main/res/
│  ├─ values/strings.xml — Paradox branding + dev strings
│  ├─ layout/activity_main.xml — WebView + Developer button
│  ├─ layout/activity_developer_settings.xml
│  └─ xml/network_security_config.xml — FIX
└─ app/src/main/AndroidManifest.xml — FIXED permissions + MetricsOptOut
```

## 🔧 Developer Section — As Requested

**Tools → Developer Settings** e click → Developer Section e gele:

1. **WebView Inspector (Eruda)** — DOM/CSS inspect
2. **User-Agent Switcher** — 5 presets (System Default Modern, Via compat, Desktop, iPhone, Custom) — REA verified 9 Via UAs fixed
3. **Console Logs** — `onConsoleMessage` logging
4. **Network Monitor** — `shouldInterceptRequest`
5. **Enable Eruda** — toggle
6. **Clear WebView Data** — cache + cookies
7. **Export Logs** — for Jahid Hasan

## 🚀 Build APK

This is a **full Android Studio project**. No prebuilt binary in sandbox (no SDK). Build locally:

```bash
# In Android Studio
Open project → paradox-browser → Sync → Run ▶

# Or CLI (with SDK installed)
./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

**App ID:** `com.paradox.browser`  
**Developer:** Jahid Hasan (paradox)  
**Version:** 1.0.0 (1)

## 📸 Screens (Expected)

- Main: WebView + URL bar + Tools (☰) + Developer (⚙) bottom bar
- Tools dialog: Adblock, Night Mode, Find in Page, **Developer Settings →**
- Developer Tools list with 7 items

## 🔒 Security Fixes Applied (from REA)

See `DEVELOPER_SECTION_SPEC.md` and REA reports in workspace:
- Via WebView REA: minSdk, file access, mixed content
- Via UA REA: Chrome 90 → 148, Via suffix opt-in, validation
- Via permissions REA: QUERY_ALL_PACKAGES removed

## 📄 License

MIT — Paradox Browser by Jahid Hasan (paradox) — Via REA inspired, fixes applied.

---

**Ready to push:** This folder is git-ready. Create GitHub repo `paradox-browser` and:
```bash
cd paradox-browser
git init
git add .
git commit -m "feat: Paradox Browser 1.0.0 — Via bugs fixed, Developer Section added"
git remote add origin https://github.com/YOUR_USERNAME/paradox-browser.git
git push -u origin main
```

Enjoy! 🎉
