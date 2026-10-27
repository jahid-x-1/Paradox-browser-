package com.paradox.browser

import android.content.Context

/** All browser settings in one place — nothing leaves the device. */
class BrowserPrefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** 0 = system default, 1 = Via compat, 2 = modern mobile, 3 = desktop, 4 = iPhone, 5 = custom. */
    var uaPreset: Int
        get() = sp.getInt(KEY_UA_PRESET, 0)
        set(value) = sp.edit().putInt(KEY_UA_PRESET, value).apply()

    var customUa: String
        get() = sp.getString(KEY_CUSTOM_UA, "") ?: ""
        set(value) = sp.edit().putString(KEY_CUSTOM_UA, value.take(MAX_UA_LENGTH)).apply()

    /** FIX: the "Via/" suffix leaks the browser identity, so it is opt-in. */
    var addViaSuffix: Boolean
        get() = sp.getBoolean(KEY_VIA_SUFFIX, false)
        set(value) = sp.edit().putBoolean(KEY_VIA_SUFFIX, value).apply()

    var adblockEnabled: Boolean
        get() = sp.getBoolean(KEY_ADBLOCK, true)
        set(value) = sp.edit().putBoolean(KEY_ADBLOCK, value).apply()

    var nightMode: Boolean
        get() = sp.getBoolean(KEY_NIGHT, false)
        set(value) = sp.edit().putBoolean(KEY_NIGHT, value).apply()

    var erudaEnabled: Boolean
        get() = sp.getBoolean(KEY_ERUDA, false)
        set(value) = sp.edit().putBoolean(KEY_ERUDA, value).apply()

    var lastUrl: String
        get() = sp.getString(KEY_LAST_URL, BuildConfig.HOME_URL) ?: BuildConfig.HOME_URL
        set(value) = sp.edit().putString(KEY_LAST_URL, value).apply()

    companion object {
        private const val NAME = "paradox_prefs"
        private const val KEY_UA_PRESET = "ua_preset"
        private const val KEY_CUSTOM_UA = "ua_custom"
        private const val KEY_VIA_SUFFIX = "ua_add_via_suffix"
        private const val KEY_ADBLOCK = "adblock"
        private const val KEY_NIGHT = "night_mode"
        private const val KEY_ERUDA = "eruda"
        private const val KEY_LAST_URL = "last_url"

        /** FIX: Via accepted unbounded UA strings; Paradox validates the length. */
        const val MAX_UA_LENGTH = 512
    }
}
