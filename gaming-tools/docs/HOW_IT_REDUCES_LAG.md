# How Paradox Gaming Tools reduces lag

Android frame drops in games come from four places. This app attacks the two it is allowed to
touch, measures the one you can feel, and is honest about the one it cannot.

| Source of lag | Can a normal app help? | What Paradox does |
|---|---|---|
| **Memory pressure** — the kernel evicts pages while the game renders | Partly | Sends background-process trim requests before a session, drops its own caches so it is not holding pages |
| **Interruptions** — heads-up notifications, sound effects, activity transitions | Yes | Optional Do Not Disturb during a boost |
| **Render performance** — frame time, jank, GPU load | Only measured | Lag Lab reads the real frame clock and reports FPS, 1% low, worst frame, jank % |
| **Thermal throttling / CPU governors** | No | Explained in Settings; an ADB snippet is provided for animation scale |

## Why the frame clock, not a timer

`Choreographer.postFrameCallback()` fires once per **composited** frame. The delta between two
callbacks is exactly the time the user waited for the next picture. That makes it the only honest
way to measure lag from inside an app:

```
frameTime_ms = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000
fps          = 1000 / frameTime_ms
jank         = frameTime_ms > 1.5 × targetFrameTime
1% low       = 1000 / mean(worst 1% of frame times)
```

A timer-based "FPS counter" that just divides a counter by a stopwatch hides stutters; the 1% low
and worst-frame numbers expose them.

## Why `killBackgroundProcesses` is a request, not a kill

`ActivityManager.killBackgroundProcesses(pkg)` requires `KILL_BACKGROUND_PROCESSES` and lets the
system reclaim an app's background processes. The system may ignore the call for protected or
foreground apps, and since Android 8 background limits already do most of this work. The boost
report therefore says **"background apps trimmed"**, not "apps killed", and the freed number is a
measured before/after of `MemoryInfo.usedBytes` — not a made-up estimate.

## Why per-app cache needs Usage access

`StorageStatsManager.queryStatsForPackage()` is gated behind `PACKAGE_USAGE_STATS` because app
storage sizes leak usage patterns. Even with the grant, deleting another app's cache is forbidden,
so the Storage screen shows sizes and opens the system App info page for the two-tap manual clear.

## What would need root

* Writing `Settings.Global` animation scales (ADB workaround documented in the app).
* Changing `/sys/devices/system/cpu/...` governors or GPU frequencies.
* Controlling screen refresh rate per app.
* Truly killing apps with `am force-stop`.

Any app claiming those without root is lying. This one does not.
