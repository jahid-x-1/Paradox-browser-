package com.paradox.gamingtools.core

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import java.io.File
import java.util.UUID

data class AppStorageInfo(
    val packageName: String,
    val label: String,
    val cacheBytes: Long,
    val dataBytes: Long
)

/**
 * Per-app cache sizes. Android only reveals these when the user grants "Usage access"
 * (PACKAGE_USAGE_STATS) — and even then no normal app may delete another app's cache,
 * so the UI offers "Manage" which opens the system App info screen.
 */
class AppCacheScanner(context: Context) {

    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager

    fun hasUsageAccess(): Boolean {
        val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            appContext.startActivity(intent)
        } catch (e: Exception) {
            // Some ROMs hide this screen; the user can reach it from system Settings.
        }
    }

    fun ownCacheBytes(): Long {
        var total = 0L
        for (directory in listOfNotNull(
            appContext.cacheDir,
            appContext.codeCacheDir,
            appContext.externalCacheDir
        )) {
            total += BoostEngine.sizeOf(directory)
        }
        return total
    }

    fun clearOwnCache(): Long {
        var freed = 0L
        for (directory in listOfNotNull(
            appContext.cacheDir,
            appContext.codeCacheDir,
            appContext.externalCacheDir
        )) {
            val children = directory.listFiles() ?: continue
            for (child in children) {
                freed += BoostEngine.sizeOf(child)
                child.deleteRecursively()
            }
        }
        return freed
    }

    /** Heavy call — run it off the main thread. Returns an empty list without usage access. */
    fun scan(limit: Int = 15): List<AppStorageInfo> {
        if (!hasUsageAccess()) return emptyList()
        val storageManager = appContext.getSystemService(StorageManager::class.java)
            ?: return emptyList()
        val volumeUuid: UUID = try {
            storageManager.getUuidForPath(Environment.getDataDirectory())
        } catch (e: Exception) {
            return emptyList()
        }
        val statsManager = appContext.getSystemService(StorageStatsManager::class.java)
            ?: return emptyList()

        val result = ArrayList<AppStorageInfo>()
        for ((packageName, label) in launchablePackages()) {
            val stats = try {
                statsManager.queryStatsForPackage(volumeUuid, packageName, Process.myUserHandle())
            } catch (e: Exception) {
                continue
            }
            result.add(
                AppStorageInfo(
                    packageName = packageName,
                    label = label,
                    cacheBytes = stats.cacheBytes,
                    dataBytes = stats.dataBytes
                )
            )
        }
        return result.sortedByDescending { it.cacheBytes + it.dataBytes }.take(limit)
    }

    private fun launchablePackages(): List<Pair<String, String>> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, 0)
            }
        } catch (e: Exception) {
            emptyList()
        }
        val seen = HashSet<String>()
        val out = ArrayList<Pair<String, String>>()
        for (info in resolved) {
            val packageName = info.activityInfo?.packageName ?: continue
            if (packageName == appContext.packageName) continue
            if (!seen.add(packageName)) continue
            val label = try {
                info.loadLabel(packageManager).toString()
            } catch (e: Exception) {
                packageName
            }
            out.add(packageName to label)
        }
        return out
    }
}
