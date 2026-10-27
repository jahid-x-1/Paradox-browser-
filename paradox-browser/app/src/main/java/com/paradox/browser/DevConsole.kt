package com.paradox.browser

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * In-memory ring buffers for the Developer Section.
 * Nothing is written to disk unless the user taps "Export Debug Logs".
 */
object DevConsole {

    data class Entry(val time: String, val tag: String, val message: String)

    private const val MAX_ENTRIES = 500
    private val stamp = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val consoleLines = ArrayDeque<Entry>()
    private val networkLines = ArrayDeque<Entry>()

    @Synchronized
    fun console(message: String, source: String = "", line: Int = 0) {
        push(consoleLines, "JS", if (source.isEmpty()) message else "$message  ($source:$line)")
    }

    @Synchronized
    fun network(url: String, note: String = "") {
        push(networkLines, "NET", if (note.isEmpty()) url else "$url  [$note]")
    }

    @Synchronized
    fun info(tag: String, message: String) {
        push(consoleLines, tag, message)
    }

    @Synchronized
    fun clear() {
        consoleLines.clear()
        networkLines.clear()
    }

    @Synchronized
    fun consoleSnapshot(): List<Entry> = consoleLines.toList()

    @Synchronized
    fun networkSnapshot(): List<Entry> = networkLines.toList()

    fun consoleText(): String = render(consoleSnapshot(), "CONSOLE")
    fun networkText(): String = render(networkSnapshot(), "NETWORK")

    /** Writes both buffers to app-specific external storage — no permission needed. */
    fun export(context: Context): File? {
        return try {
            val dir = File(context.getExternalFilesDir(null), "logs")
            if (!dir.exists() && !dir.mkdirs()) return null
            val name = "paradox-log-" +
                SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
            val file = File(dir, name)
            file.writeText(buildString {
                appendLine(consoleText())
                appendLine()
                appendLine(networkText())
            })
            file
        } catch (e: Exception) {
            null
        }
    }

    private fun render(entries: List<Entry>, header: String): String = buildString {
        appendLine("=== $header (${entries.size}) ===")
        if (entries.isEmpty()) {
            appendLine("(empty)")
        } else {
            for (entry in entries) appendLine("[${entry.time}] ${entry.tag}: ${entry.message}")
        }
    }

    private fun push(target: ArrayDeque<Entry>, tag: String, message: String) {
        if (target.size >= MAX_ENTRIES) target.removeFirst()
        target.addLast(Entry(stamp.format(Date()), tag, message))
    }
}
