package com.paradox.browser.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.webkit.URLUtil
import androidx.core.app.NotificationCompat
import com.paradox.browser.DevConsole
import com.paradox.browser.MainActivity
import com.paradox.browser.R

/**
 * Handles page downloads through the system DownloadManager and keeps a visible
 * foreground notification so Android does not kill the transfer.
 *
 * Why DownloadManager instead of writing the stream ourselves: it survives app death,
 * respects the system download policy and handles retries — exactly what a browser needs.
 */
class DownloadService : Service() {

    private var downloadId: Long = -1L
    private var fileName: String = ""

    private val completionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_DOWNLOAD_COMPLETE) return
            val id = intent.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != downloadId) return
            finishWith(null)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                completionReceiver,
                IntentFilter(ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(completionReceiver, IntentFilter(ACTION_DOWNLOAD_COMPLETE))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        fileName = URLUtil.guessFileName(
            url,
            intent.getStringExtra(EXTRA_CONTENT_DISPOSITION),
            intent.getStringExtra(EXTRA_MIME_TYPE)
        )

        startForegroundCompat(fileName)

        try {
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            val request = android.app.DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setDescription(getString(R.string.download_progress, fileName))
                .setNotificationVisibility(
                    android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)

            downloadId = manager.enqueue(request)
            DevConsole.info("Download", "queued #$downloadId → $fileName")
        } catch (e: Exception) {
            DevConsole.info("Download", "failed: ${e.message}")
            finishWith(getString(R.string.download_failed, fileName))
        }

        return START_NOT_STICKY
    }

    private fun startForegroundCompat(name: String) {
        val notification = buildNotification(name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(name: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.download_progress, name))
            .setOngoing(true)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun finishWith(message: String?) {
        if (message != null) {
            DevConsole.info("Download", message)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.download_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.download_channel)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(completionReceiver)
        } catch (e: Exception) {
            // Already unregistered.
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_CONTENT_DISPOSITION = "extra_content_disposition"
        const val EXTRA_MIME_TYPE = "extra_mime_type"

        private const val CHANNEL_ID = "paradox_downloads"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_DOWNLOAD_COMPLETE = "android.intent.action.DOWNLOAD_COMPLETE"
    }
}
