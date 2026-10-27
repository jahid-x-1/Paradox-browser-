package com.paradox.gamingtools

import android.app.Application
import android.util.Log

/**
 * Paradox Gaming Tools — by Jahid Hasan (paradox).
 *
 * No analytics, no network stack, no third-party SDK. Everything this app knows
 * about the device is read locally and thrown away when the process dies.
 */
class ParadoxApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Paradox Gaming Tools started on ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
    }

    companion object {
        const val TAG = "ParadoxGaming"
    }
}
