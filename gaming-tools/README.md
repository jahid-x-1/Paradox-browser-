# Paradox Gaming Tools 1.0.0

> **Debug APK:** built automatically on every push —
> https://github.com/jahid-x-1/Paradox-browser-/releases/tag/apk-latest

**Real, on-device lag reduction for Android — no root, no internet, no ads.**

Built by Jahid Hasan (paradox) for Linear issue `JAH-5`.

---

## What it actually does

Most "game booster" apps are placebo: they vibrate, draw a fake rocket and change nothing.
Paradox Gaming Tools only does things that measurably move the needle on a normal phone:

| Feature | How it works | Real effect |
|---|---|---|
| **Boost** | Asks `ActivityManager` to trim background processes of every launchable app, drops this app's caches, forces a GC, optionally turns on Do Not Disturb | Frees RAM pages the game would otherwise compete for |
| **Lag Lab** | Runs a 15 s render stress test and reads the real Android frame clock (`Choreographer`) | You get true FPS, worst frame time, 1% low and jank % |
| **Game Library** | Lists launcher apps, lets you pin favourites, boosts before launching | Starts the game with a clean memory state |
| **Storage** | Cleans its own caches, and with *Usage access* shows per-app cache/data sizes via `StorageStatsManager` | Finds the apps eating your storage |
| **Settings** | Boost behaviour, DND permission, lifetime stats | Nothing hidden |

### What it honestly cannot do

* It cannot force-kill other apps. `killBackgroundProcesses()` is a *request*; Android decides.
* It cannot change CPU/GPU clocks, thermal limits or refresh rate. That needs root or a custom kernel.
* It cannot delete other apps' caches. Android blocks that — the Storage screen opens the system
  App info page instead, so you clear them in two taps.

The app says all of this inside Settings instead of pretending. No fake "cooling" animations.

---

## Privacy

* **No `INTERNET` permission.** The app physically cannot upload anything.
* No analytics, no ads, no third-party SDK, no crash reporter.
* Everything is read locally and thrown away when the process dies.

## Permissions

| Permission | Why |
|---|---|
| `KILL_BACKGROUND_PROCESSES` | Send the trim request to background apps |
| `ACCESS_NOTIFICATION_POLICY` | Optional Do Not Disturb during a boost |
| `PACKAGE_USAGE_STATS` | Optional; only to read per-app storage sizes |
| `VIBRATE` | Button feedback |

`<queries>` is limited to `MAIN`/`LAUNCHER` intents, so the app sees exactly what a launcher sees —
never the full package list.

---

## Project layout

```
gaming-tools/
├─ app/src/main/java/com/paradox/gamingtools/
│  ├─ ParadoxApp.kt              Application entry
│  ├─ core/
│  │  ├─ DeviceMetrics.kt        RAM, CPU (/proc/stat), thermal (/sys), battery, storage
│  │  ├─ BoostEngine.kt          The boost pipeline (suspend, off-main-thread)
│  │  ├─ AppRepository.kt        Launchable apps + launching
│  │  ├─ AppCacheScanner.kt      StorageStatsManager per-app sizes
│  │  ├─ Prefs.kt                Settings + lifetime stats
│  │  └─ Fmt.kt                  Locale-stable byte/fps formatting
│  └─ ui/
│     ├─ DashboardActivity.kt    Live metrics + BOOST
│     ├─ LagTestActivity.kt      Frame-clock stress test
│     ├─ GamesActivity.kt        Game library
│     ├─ CleanerActivity.kt      Storage screen
│     ├─ SettingsActivity.kt     Preferences, stats, about
│     ├─ RingView.kt             Custom circular gauge (canvas, no deps)
│     ├─ StressView.kt           Canvas load generator
│     ├─ GameAdapter.kt          RecyclerView adapter
│     └─ CacheAdapter.kt         RecyclerView adapter
├─ app/src/main/res/             Layouts, strings, colours, adaptive icon
└─ docs/HOW_IT_REDUCES_LAG.md    The engineering reasoning
```

## Build

Requirements: Android Studio (Hedgehog or newer) or Gradle 8.2+, JDK 17, Android SDK 34.

```bash
# Android Studio: File → Open → select the gaming-tools folder → Sync → Run ▶
# CLI:
gradle assembleDebug          # or generate the wrapper first: gradle wrapper
# APK → app/build/outputs/apk/debug/app-debug.apk
```

The repository intentionally ships **without** `gradle/wrapper/gradle-wrapper.jar`
(binary files are not committed). Either let Android Studio regenerate it, or run
`gradle wrapper --gradle-version 8.2.1` once.

* `applicationId`: `com.paradox.gamingtools`
* `minSdk 26` (Android 8.0) — adaptive icons only, no legacy PNGs
* `targetSdk 34`, `compileSdk 34`

## Testing checklist (on a real device)

1. Open the dashboard → RAM ring, CPU %, thermal °C, free storage all update every 2 s.
2. Tap **BOOST NOW** → a step label changes, then a result dialog with MB freed.
3. **Lag Lab** → start the test, raise the render load slider to 60 objects, watch jank rise.
4. **Game Library** → pin a game with ☆, tap it → boost dialog, then the game opens.
5. **Storage** → grant usage access, see per-app cache sizes, tap Manage → system App info.
6. **Settings** → toggle the switches, grant DND, reset stats.

## License

MIT — Paradox Gaming Tools, Jahid Hasan (paradox).
