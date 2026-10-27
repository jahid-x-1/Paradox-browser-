# Paradox — workspace of Jahid Hasan (paradox)

This repository holds two Android projects.

| Folder | What it is | State |
|---|---|---|
| [`gaming-tools/`](gaming-tools/) | **Paradox Gaming Tools** — real on-device lag reduction (Linear `JAH-5`) | Complete, buildable Gradle project |
| [`paradox-browser/`](paradox-browser/) | Paradox Browser — hardened WebView browser (Via REA fixes) | Source only, **does not build yet** |
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

The original Via-based browser rewrite. The uploaded source is **incomplete** and will not compile
as-is — these pieces are referenced but missing:

* `res/layout/activity_developer_settings.xml` and `res/layout/item_dev_tool.xml`
  (used by `ActivityDeveloperSettingsBinding` / `ItemDevToolBinding`)
* `res/drawable/bg_urlbar.xml`, launcher icons (`@mipmap/ic_launcher`)
* `com/paradox/browser/service/DownloadService.kt` (declared in the manifest)
* `app/build.gradle` declares `namespace` twice (lines 7 and 43) — a Gradle configuration error

Fixing it is a separate task from `JAH-5`; the folder is kept untouched so nothing is lost.

---

## License

MIT — Jahid Hasan (paradox)
