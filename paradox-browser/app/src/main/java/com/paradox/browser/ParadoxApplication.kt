package com.paradox.browser

import android.app.Application
import android.os.SystemClock

/**
 * Paradox Browser — by Jahid Hasan (paradox)
 * Fixes Via bugs: proper init, no obfuscation, clear logging
 */
class ParadoxApplication : Application() {
    companion object {
        const val DEVELOPER = "Jahid Hasan (paradox)"
        const val APP_NAME = "Paradox Browser"
    }

    override fun onCreate() {
        super.onCreate()
        val start = SystemClock.elapsedRealtime()
        // Clean init — Via had obfuscated i(), h(), j() etc — we make readable
        initWebViewDebugConfig()
        // Cost logging
        android.util.Log.i("Paradox", "ParadoxApplication::onCreate cost=${SystemClock.elapsedRealtime() - start}ms by $DEVELOPER")
    }

    private fun initWebViewDebugConfig() {
        // Enable WebView debugging only in debug builds
        if (BuildConfig.DEBUG) {
            android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
