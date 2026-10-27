package com.paradox.gamingtools.core

import java.util.Locale

/** Locale-stable formatting so a device in Bengali/Arabic digits still shows sane numbers. */
object Fmt {

    fun bytes(value: Long): String {
        if (value <= 0L) return "0 MB"
        val kb = value / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.0f MB", mb)
            else -> String.format(Locale.US, "%.0f KB", kb)
        }
    }

    fun mb(value: Long): String = String.format(Locale.US, "%.0f MB", value / (1024.0 * 1024.0))

    fun oneDecimal(value: Float): String = String.format(Locale.US, "%.1f", value)

    fun whole(value: Float): String = String.format(Locale.US, "%.0f", value)
}
