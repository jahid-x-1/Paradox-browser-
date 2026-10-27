package com.paradox.browser

import android.content.Context
import android.webkit.WebSettings

/**
 * FIX: Via shipped Chrome 90 UAs with nine near-duplicates and a leaking "Via/7.3.3"
 * suffix. Paradox keeps a small, current set and validates custom input.
 */
object UserAgents {

    const val PRESET_SYSTEM = 0
    const val PRESET_VIA_COMPAT = 1
    const val PRESET_MODERN = 2
    const val PRESET_DESKTOP = 3
    const val PRESET_IPHONE = 4
    const val PRESET_CUSTOM = 5

    private const val MODERN =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/148.0.0.0 Mobile Safari/537.36"

    private const val DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/148.0.0.0 Safari/537.36"

    private const val IPHONE =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1"

    /** Kept only for sites that still sniff the old Via string. Never the default. */
    private const val VIA_COMPAT =
        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/90.0.4430.91 Mobile Safari/537.36"

    fun labels(): Array<String> = arrayOf(
        "System Default (Recommended)",
        "Via Compatibility (Chrome/90)",
        "Modern Chrome 148 — Pixel 7",
        "Desktop (Windows Chrome 148)",
        "iPhone Safari 18.5",
        "Custom…"
    )

    fun resolve(context: Context, prefs: BrowserPrefs): String {
        val base = when (prefs.uaPreset) {
            PRESET_VIA_COMPAT -> VIA_COMPAT
            PRESET_MODERN -> MODERN
            PRESET_DESKTOP -> DESKTOP
            PRESET_IPHONE -> IPHONE
            PRESET_CUSTOM -> prefs.customUa.takeIf { it.isNotBlank() }
                ?: WebSettings.getDefaultUserAgent(context)
            else -> WebSettings.getDefaultUserAgent(context)
        }
        val cleaned = base.trim().take(BrowserPrefs.MAX_UA_LENGTH)
        return if (prefs.addViaSuffix) "$cleaned Paradox/1.0.0" else cleaned
    }

    fun isValid(candidate: String): Boolean =
        candidate.isNotBlank() && candidate.length <= BrowserPrefs.MAX_UA_LENGTH
}
