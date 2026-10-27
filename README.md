# Paradox — workspace of Jahid Hasan (paradox)

This repository holds two Android projects.

| Folder | What it is | State |
|---|---|---|
| [`gaming-tools/`](gaming-tools/) | **Paradox Gaming Tools** — real on-device lag reduction (Linear `JAH-5`) | Complete Gradle project — `gradle assembleDebug` on a machine with the Android SDK |
| [`paradox-browser/`](paradox-browser/) | Paradox Browser — hardened WebView browser (Via REA fixes) | **Fixed** — 19 build/runtime defects resolved, every tool implemented |
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
