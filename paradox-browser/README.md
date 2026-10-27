# Paradox Browser 1.0.0

> **Debug APK:** built automatically on every push —
> https://github.com/jahid-x-1/Paradox-browser-/releases/tag/apk-latest

**Fast, Private & Secure — built from the Via Browser REA with every found bug fixed.**

By Jahid Hasan (paradox). Package `com.paradox.browser`.

> This folder used to be an incomplete source dump that could not compile.
> Every missing piece is now implemented — see [What was broken](#what-was-broken-and-what-changed).

---

## Build

Requires JDK 17 and Android SDK 34.

```bash
# Android Studio: File → Open → paradox-browser → Sync → Run ▶
# CLI (needs the Gradle wrapper JAR, which is not committed — generate it once):
gradle wrapper --gradle-version 8.2.1
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

`compileSdk 34` / `targetSdk 34` / `minSdk 21` · AGP 8.2.2 · Kotlin 1.9.22.

## Features

| Area | Behaviour |
|---|---|
| **Address bar** | URL or search (DuckDuckGo) with `actionGo`, restores the last page on cold start |
| **Tools (☰)** | Adblock ON/OFF, Night Mode ON/OFF, Find in Page, Developer Settings |
| **Adblock** | Real request blocking through `shouldInterceptRequest` + a curated host list; downloads from blocked hosts are refused |
| **Night Mode** | `WebSettingsCompat` algorithmic darkening (AndroidX WebKit), falls back to `forceDark` on API 29+ |
| **Find in Page** | `findAllAsync` with a live "3 of 12" toast from `setFindListener` |
| **Downloads** | `DownloadService` (foreground) hands the URL to the system DownloadManager |
| **Developer Section** | 7 working tools — see below |
| **Security** | `file://` never loads, mixed content never allowed, Safe Browsing on, SSL errors cancel the connection, third-party cookies off, geolocation refused |

### Developer Section

1. **WebView Inspector (Eruda)** — injects Eruda 3.0.1 into the live page
2. **User-Agent Switcher** — 5 presets + validated custom input, applied and reloaded instantly
3. **Console Logs** — every `onConsoleMessage` captured in a 500-entry ring buffer
4. **Network Monitor** — every `shouldInterceptRequest` URL, with `BLOCKED` marked
5. **Enable Eruda** — toggle on/off (`eruda.destroy()` when switched off)
6. **Clear WebView Data** — cache, cookies, form data, history, SSL preferences
7. **Export Debug Logs** — writes `paradox-log-<timestamp>.txt` to `Android/data/com.paradox.browser/files/logs/`

## Security model

```kotlin
ws.allowFileAccess = false
ws.allowContentAccess = false
ws.allowFileAccessFromFileURLs = false
ws.allowUniversalAccessFromFileURLs = false
ws.mixedContentMode = MIXED_CONTENT_NEVER_ALLOW
handler.cancel()               // on any SSL error
cookies.setAcceptThirdPartyCookies(webView, false)
```

`file://` is also absent from the browsable intent-filters, so no other app can make Paradox open a local file.

## Permissions

| Permission | Reason |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Browsing |
| `CAMERA`, `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS` | WebRTC / file input, requested at runtime |
| `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion=28`) | Downloads into public `Download/` on Android 9 and older |
| `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Download notification |
| `VIBRATE` | Feedback |

Removed versus Via: `QUERY_ALL_PACKAGES`, `REQUEST_INSTALL_PACKAGES`, `ACCESS_FINE_LOCATION`.

---

## What was broken, and what changed

The uploaded source could not build. Each item below is fixed:

| # | Problem | Fix |
|---|---|---|
| 1 | `app/build.gradle` declared `namespace` twice | One declaration; `compileSdk`/`targetSdk` aligned to AGP 8.2.2 (34) |
| 2 | Missing `res/layout/activity_developer_settings.xml` (needed by `ActivityDeveloperSettingsBinding`) | Created with a real toolbar + RecyclerView |
| 3 | Missing `res/layout/item_dev_tool.xml` (needed by `ItemDevToolBinding`) | Created |
| 4 | Missing `res/drawable/bg_urlbar.xml` | Created |
| 5 | Missing launcher icons (`@mipmap/ic_launcher`) | Adaptive icon (API 26+) **and** a legacy `mipmap-anydpi` fallback for API 21-25 |
| 6 | `activity_main.xml` used `app:title` without declaring `xmlns:app` | Namespace added — this alone broke the build |
| 7 | Missing `com.paradox.browser.service.DownloadService` (declared in the manifest) | Implemented: foreground notification + DownloadManager |
| 8 | Missing `proguard-rules.pro` (referenced by the release build) | Added |
| 9 | `ws.safeBrowsingEnabled` is API 26+ but `minSdk` is 21 | Runtime version guard (would have crashed on Android 5-7) |
| 10 | `shouldOverrideUrlLoading(WebView, WebResourceRequest)` is API 24+ | Deprecated `String` overload added for API 21-23 |
| 11 | Tools menu listed "Adblock (On)", "Night Mode", "Find in Page" but only Developer Settings did anything | Real `AdBlocker` engine, real algorithmic darkening, real `findAllAsync` |
| 12 | UA switcher wrote `ua_preset`, while `MainActivity` read a different key (`ua_add_via_suffix`) | `UserAgents` resolves one source of truth and applies it on start, on switch and after reload |
| 13 | Eruda "injection" was a toast plus a commented-out line | Injects into the live WebView through `WebViewRegistry` |
| 14 | Log export claimed it wrote `/Download/Paradox/logs.txt` and wrote nothing | Writes a real timestamped file to app external storage and shows the path |
| 15 | "Clear WebView Data" cleared a throwaway `WebView(this)` instead of the real one | Clears the live WebView plus `WebStorage`, cookies, history and SSL prefs |
| 16 | `FileProvider` authority was hardcoded, so debug and release collide when both are installed | `authorities="${applicationId}.fileprovider"` |
| 17 | Theme was `Theme.Material3.DayNight` (has an ActionBar) on top of a layout that already had its own `MaterialToolbar` | `NoActionBar` theme + `setSupportActionBar(binding.toolbar)` |
| 18 | No download handling at all — pages could not save files | `setDownloadListener` → `DownloadService` → DownloadManager |
| 19 | Geolocation would have been silently refused with no explanation | Explicitly refused and explained in a toast — Paradox declares no location permission |

## Project layout

```
app/src/main/java/com/paradox/browser/
├─ MainActivity.kt           WebView host, tools menu, find-in-page, downloads
├─ ParadoxApplication.kt     WebView debug flag (debug builds only)
├─ BrowserPrefs.kt           UA preset, adblock, night mode, Eruda, last URL
├─ UserAgents.kt             Five presets + 512-char validated custom UA
├─ AdBlocker.kt              Curated host blocklist (exact + subdomain match)
├─ DevConsole.kt             500-entry console/network ring buffers + export
├─ WebViewRegistry.kt        WeakReference to the live WebView for Dev Tools
├─ developer/
│  ├─ DeveloperSettingsActivity.kt   Seven working tools
│  └─ DeveloperToolsAdapter.kt
└─ service/
   └─ DownloadService.kt     Foreground download handler
```

## License

MIT — Paradox Browser by Jahid Hasan (paradox).
