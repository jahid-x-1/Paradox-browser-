package com.paradox.gamingtools.core

import android.content.Context

/** Tiny SharedPreferences wrapper — no encryption needed, nothing sensitive is stored. */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var killBackgroundApps: Boolean
        get() = sp.getBoolean(KEY_KILL, true)
        set(value) = sp.edit().putBoolean(KEY_KILL, value).apply()

    var dndDuringBoost: Boolean
        get() = sp.getBoolean(KEY_DND, false)
        set(value) = sp.edit().putBoolean(KEY_DND, value).apply()

    var boostBeforeGame: Boolean
        get() = sp.getBoolean(KEY_BOOST_GAME, true)
        set(value) = sp.edit().putBoolean(KEY_BOOST_GAME, value).apply()

    var lastBoostMillis: Long
        get() = sp.getLong(KEY_LAST_BOOST, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_BOOST, value).apply()

    var boostCount: Int
        get() = sp.getInt(KEY_BOOST_COUNT, 0)
        set(value) = sp.edit().putInt(KEY_BOOST_COUNT, value).apply()

    var totalFreedBytes: Long
        get() = sp.getLong(KEY_TOTAL_FREED, 0L)
        set(value) = sp.edit().putLong(KEY_TOTAL_FREED, value).apply()

    fun favorites(): Set<String> = sp.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()

    /** Returns true when the package was added, false when it was removed. */
    fun toggleFavorite(packageName: String): Boolean {
        val current = favorites().toMutableSet()
        val added = current.add(packageName)
        if (!added) current.remove(packageName)
        sp.edit().putStringSet(KEY_FAVORITES, current).apply()
        return added
    }

    fun resetStats() {
        sp.edit()
            .putInt(KEY_BOOST_COUNT, 0)
            .putLong(KEY_TOTAL_FREED, 0L)
            .putLong(KEY_LAST_BOOST, 0L)
            .apply()
    }

    private companion object {
        const val NAME = "paradox_gaming"
        const val KEY_KILL = "kill_background"
        const val KEY_DND = "dnd_during_boost"
        const val KEY_BOOST_GAME = "boost_before_game"
        const val KEY_LAST_BOOST = "last_boost"
        const val KEY_BOOST_COUNT = "boost_count"
        const val KEY_TOTAL_FREED = "total_freed"
        const val KEY_FAVORITES = "favorite_games"
    }
}
