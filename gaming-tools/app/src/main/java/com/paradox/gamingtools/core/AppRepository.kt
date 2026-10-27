package com.paradox.gamingtools.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings

data class GameApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

/**
 * Installed launchable apps. Because the manifest only declares a <queries> entry for
 * MAIN/LAUNCHER we can see exactly what a launcher needs to see — never the full package list.
 */
class AppRepository(context: Context) {

    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager

    fun launchableApps(): List<GameApp> {
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
        val apps = ArrayList<GameApp>()
        for (info in resolved) {
            val packageName = info.activityInfo?.packageName ?: continue
            if (packageName == appContext.packageName) continue
            if (!seen.add(packageName)) continue
            val label = try {
                info.loadLabel(packageManager).toString()
            } catch (e: Exception) {
                packageName
            }
            apps.add(GameApp(packageName, label, iconOf(packageName)))
        }
        return apps.sortedBy { it.label.lowercase() }
    }

    fun iconOf(packageName: String): Drawable? = try {
        packageManager.getApplicationIcon(packageName)
    } catch (e: Exception) {
        null
    }

    fun launch(packageName: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            appContext.startActivity(intent)
        } catch (e: Exception) {
            // Setting screen missing on some heavily modified ROMs — nothing to do.
        }
    }
}
