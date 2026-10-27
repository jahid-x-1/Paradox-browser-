# Paradox — workspace of Jahid Hasan (paradox)

[![Build APK](https://github.com/jahid-x-1/Paradox-browser-/actions/workflows/build-apk.yml/badge.svg)](https://github.com/jahid-x-1/Paradox-browser-/actions/workflows/build-apk.yml)

## 📲 Download the APKs

Every push builds both apps on GitHub Actions and attaches the debug APKs to the
**`apk-latest`** release:

> **https://github.com/jahid-x-1/Paradox-browser-/releases/tag/apk-latest**

| File | App | Size |
|---|---|---|
| `Paradox-Gaming-Tools-1.0.0-debug.apk` | Paradox Gaming Tools (`com.paradox.gamingtools.debug`, minSdk 26) | ~5.7 MB |
| `Paradox-Browser-1.0.0-debug.apk` | Paradox Browser (`com.paradox.browser.debug`, minSdk 21) | ~6.0 MB |
| `SHA256SUMS.txt` | Checksums for both | — |

Both APKs are signed with the standard Android debug certificate (V2 scheme), so the
phone warns about an unknown source. Allow **Install unknown apps** for your browser or
file manager, then tap the downloaded file.

CI status: [all runs](https://github.com/jahid-x-1/Paradox-browser-/actions) ·
Workflow: [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml)

This repository holds two Android projects.

| Folder | What it is | State |
|---|---|---|
| [`gaming-tools/`](gaming-tools/) | **Paradox Gaming Tools** — real on-device lag reduction (Linear `JAH-5`) | Builds green in CI — debug APK published |
| [`paradox-browser/`](paradox-browser/) | Paradox Browser — hardened WebView browser (Via REA fixes) | **Fixed** — 19 defects resolved, builds green in CI, debug APK published |
| `paradox-browser.zip` | Original upload of the browser project | Reference copy, untouched |

---

## 1. Paradox Gaming Tools (`gaming-tools/`)

The current work: a real gaming-performance app with **no root, no internet, no ads, no fake boost**.

* **Boost** — trim background processes, drop caches, optional Do Not Disturb, measured before/after
* **Lag Lab** — real frame-clock stress test: live FPS, avg FPS, worst frame, 1% low, jank %, score
* **Game Library** — launchable apps, favourites, boost-then-launch
* **Storage** — own-cache cleanup plus per-app cache sizes via Usage access
* **Settings** — behaviour switches, lifetime stats, honest limits, ADB animation-scale tip

Full details: [`gaming-tools/README.md`](gaming-tools/README.md) ·
Engineering notes: [`gaming-tools/docs/HOW_IT_REDUCES_LAG.md`](gaming-tools/docs/HOW_IT_REDUCES_LAG.md)

```bash
cd gaming-tools
gradle wrapper --gradle-version 8.2.1   # once, if you have no wrapper
gradle assembleDebug
```

No `INTERNET` permission is declared, so the app cannot send anything anywhere.

---

## 2. Paradox Browser (`paradox-browser/`)

The Via-derived browser rewrite, now a complete, buildable Gradle project.

Originally it could not compile at all. Everything that was missing or fake has been
implemented — the full list of 19 fixes is in
[`paradox-browser/README.md`](paradox-browser/README.md#what-was-broken-and-what-changed).

Highlights:

* Missing layouts (`activity_developer_settings.xml`, `item_dev_tool.xml`), `bg_urlbar.xml`,
  launcher icons and `DownloadService` — all written
* `activity_main.xml` used `app:` attributes without `xmlns:app` — fixed
* Duplicate `namespace` in `app/build.gradle` — fixed
* Tools that were dead menu items (Adblock, Night Mode, Find in Page) — now real
* Developer Section — 7 working tools, including Eruda injection into the live page,
  console/network log buffers and a real log exporter
* Downloads — `setDownloadListener` → foreground `DownloadService` → DownloadManager
* API-level guards so `minSdk 21` devices do not crash on API 26/24-only calls

```bash
cd paradox-browser
gradle wrapper --gradle-version 8.2.1   # once, if you have no wrapper
./gradlew assembleDebug
```

---

## License

MIT — Jahid Hasan (paradox)
