package com.paradox.gamingtools.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import java.io.File

data class RamInfo(
    val totalBytes: Long,
    val availBytes: Long,
    val lowMemory: Boolean
) {
    val usedBytes: Long get() = (totalBytes - availBytes).coerceAtLeast(0L)
    val usedPercent: Int get() = if (totalBytes <= 0L) 0 else ((usedBytes * 100.0) / totalBytes).toInt()
}

data class BatteryInfo(val levelPercent: Int, val temperatureC: Float, val charging: Boolean)

data class StorageInfo(val totalBytes: Long, val freeBytes: Long) {
    val usedPercent: Int
        get() = if (totalBytes <= 0L) 0 else (((totalBytes - freeBytes) * 100.0) / totalBytes).toInt()
}

/**
 * Everything here is read from public Android APIs (or /proc) — no root, no hacks.
 * Every call is defensive: some OEM builds hide /proc or /sys, so failures return null
 * and the UI shows a dash instead of crashing.
 */
class DeviceMetrics(context: Context) {

    private val appContext = context.applicationContext
    private val activityManager =
        appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val memoryInfo = ActivityManager.MemoryInfo()
    private var lastCpuSnapshot: CpuSnapshot? = null

    private data class CpuSnapshot(val idle: Long, val total: Long)

    fun ram(): RamInfo {
        activityManager.getMemoryInfo(memoryInfo)
        return RamInfo(memoryInfo.totalMem, memoryInfo.availMem, memoryInfo.lowMemory)
    }

    fun battery(): BatteryInfo {
        val intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else 0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return BatteryInfo(percent, tempTenths / 10f, charging)
    }

    fun storage(): StorageInfo = try {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        StorageInfo(stat.totalBytes, stat.availableBytes)
    } catch (e: Exception) {
        StorageInfo(0L, 0L)
    }

    /**
     * CPU load from /proc/stat. The first call returns null (no baseline yet),
     * every later call compares against the previous sample.
     */
    fun cpuPercent(): Int? {
        val snapshot = readCpuSnapshot() ?: return null
        val previous = lastCpuSnapshot
        lastCpuSnapshot = snapshot
        if (previous == null) return null
        val totalDelta = snapshot.total - previous.total
        val idleDelta = snapshot.idle - previous.idle
        if (totalDelta <= 0L) return null
        return (((totalDelta - idleDelta) * 100.0) / totalDelta).toInt().coerceIn(0, 100)
    }

    private fun readCpuSnapshot(): CpuSnapshot? {
        return try {
            val line = File("/proc/stat").bufferedReader().use { reader ->
                var found: String? = null
                while (true) {
                    val current = reader.readLine() ?: break
                    if (current.startsWith("cpu ")) {
                        found = current
                        break
                    }
                }
                found
            } ?: return null

            val parts = line.trim().split(WHITESPACE)
            var total = 0L
            var idle = 0L
            for (index in 1 until parts.size) {
                val value = parts[index].toLongOrNull() ?: continue
                total += value
                // fields: user nice system idle iowait irq softirq steal
                if (index == 4 || index == 5) idle += value
            }
            if (total <= 0L) null else CpuSnapshot(idle, total)
        } catch (e: Exception) {
            null
        }
    }

    /** Highest readable thermal zone temperature in °C, or null when the OEM locks /sys. */
    fun thermalCelsius(): Float? {
        val zones = File("/sys/class/thermal").listFiles() ?: return null
        var hottest = -1f
        for (zone in zones) {
            if (!zone.name.startsWith("thermal_zone")) continue
            val raw = try {
                File(zone, "temp").readText().trim()
            } catch (e: Exception) {
                null
            } ?: continue
            val value = raw.toFloatOrNull() ?: continue
            val celsius = if (value > 1000f) value / 1000f else value
            if (celsius in 5f..120f && celsius > hottest) hottest = celsius
        }
        return if (hottest < 0f) null else hottest
    }

    /** Android's own thermal headroom report (API 29+). */
    fun thermalStatusLabel(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "n/a"
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        return when (powerManager.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "Cool"
            PowerManager.THERMAL_STATUS_LIGHT -> "Light"
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
            else -> "Unknown"
        }
    }

    fun deviceSummary(): String = "${Build.MANUFACTURER} ${Build.MODEL}"

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
