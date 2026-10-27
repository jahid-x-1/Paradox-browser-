package com.paradox.gamingtools.core

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

data class BoostResult(
    val usedBeforeBytes: Long,
    val usedAfterBytes: Long,
    val freedBytes: Long,
    val processesTrimmed: Int,
    val cacheClearedBytes: Long,
    val dndEnabled: Boolean,
    val durationMs: Long
)

/**
 * What a normal (non-root) Android app can honestly do to "reduce lag":
 *
 *  1. Ask the system to trim background processes (ActivityManager.killBackgroundProcesses).
 *     This is a *request*, the system decides. Foreground apps and system apps are untouched.
 *  2. Drop our own caches so storage I/O is not fighting the game.
 *  3. Collect our own garbage and yield a moment so the kernel can reclaim pages.
 *  4. Optionally suppress interruptions (Do Not Disturb) so no heads-up animation
 *     stalls the game's render thread.
 *
 * It can NOT change CPU governors, GPU clocks or refresh rate — that needs root.
 * The Settings screen says this out loud instead of pretending otherwise.
 */
class BoostEngine(context: Context, private val prefs: Prefs) {

    private val appContext = context.applicationContext
    private val metrics = DeviceMetrics(appContext)
    private val activityManager =
        appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    suspend fun boost(onStep: (String) -> Unit): BoostResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val before = metrics.ram()

        step(onStep, "Trimming background processes…")
        val trimmed = if (prefs.killBackgroundApps) trimBackgroundProcesses() else 0

        step(onStep, "Clearing Paradox caches…")
        val cacheBytes = clearOwnCaches()

        step(onStep, "Releasing memory pages…")
        System.runFinalization()
        Runtime.getRuntime().gc()
        delay(650L)

        val dnd = applyDoNotDisturb()

        step(onStep, "Measuring result…")
        val after = metrics.ram()

        val freed = (before.usedBytes - after.usedBytes).coerceAtLeast(0L)
        val result = BoostResult(
            usedBeforeBytes = before.usedBytes,
            usedAfterBytes = after.usedBytes,
            freedBytes = freed,
            processesTrimmed = trimmed,
            cacheClearedBytes = cacheBytes,
            dndEnabled = dnd,
            durationMs = System.currentTimeMillis() - startedAt
        )

        prefs.lastBoostMillis = System.currentTimeMillis()
        prefs.boostCount = prefs.boostCount + 1
        prefs.totalFreedBytes = prefs.totalFreedBytes + freed
        result
    }

    private suspend fun step(onStep: (String) -> Unit, text: String) {
        withContext(Dispatchers.Main) { onStep(text) }
    }

    /**
     * Walks every launchable app and asks the system to free its background processes.
     * Returns how many packages we requested a trim for (the system may ignore some).
     */
    private fun trimBackgroundProcesses(): Int {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.queryIntentActivities(intent, 0)
            }
        } catch (e: Exception) {
            emptyList()
        }

        var requested = 0
        for (info in resolved) {
            val packageName = info.activityInfo?.packageName ?: continue
            if (packageName == appContext.packageName) continue
            try {
                activityManager.killBackgroundProcesses(packageName)
                requested++
            } catch (e: Exception) {
                // System refused (protected app) — ignore and keep going.
            }
        }
        return requested
    }

    private fun clearOwnCaches(): Long {
        var freed = 0L
        val directories = listOfNotNull(
            appContext.cacheDir,
            appContext.codeCacheDir,
            appContext.externalCacheDir
        )
        for (directory in directories) {
            val children = directory.listFiles() ?: continue
            for (child in children) {
                freed += sizeOf(child)
                child.deleteRecursively()
            }
        }
        return freed
    }

    private fun applyDoNotDisturb(): Boolean {
        if (!prefs.dndDuringBoost) return false
        return try {
            val manager =
                appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!manager.isNotificationPolicyAccessGranted) return false
            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        fun sizeOf(file: File): Long {
            if (!file.exists()) return 0L
            if (file.isFile) return file.length()
            val children = file.listFiles() ?: return 0L
            var total = 0L
            for (child in children) total += sizeOf(child)
            return total
        }
    }
}
