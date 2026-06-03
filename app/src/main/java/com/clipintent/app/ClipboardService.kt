package com.clipintent.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.clipintent.app.ContentAnalyzer.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that monitors the system clipboard for changes.
 */
class ClipboardService : LifecycleService() {

    companion object {
        private const val TAG = "ClipboardService"
        private const val POLL_INTERVAL_MS = 500L

        const val ACTION_START = "com.clipintent.app.action.START"
        const val ACTION_STOP = "com.clipintent.app.action.STOP"

        private var isRunning = false

        fun isRunning(): Boolean = isRunning

        fun startService(context: Context) {
            val intent = Intent(context, ClipboardService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ClipboardService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private lateinit var clipboardManager: ClipboardManager
    private var lastClipContent: String? = null
    private var isPolling = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        NotificationHelper.createChannels(this)
        clipboardManager.addPrimaryClipChangedListener(clipListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isRunning) {
                    startForegroundService()
                }
            }
            ACTION_STOP -> {
                stopForegroundService()
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        isRunning = true
        val notification = NotificationHelper.buildListeningNotification(this)
        startForeground(NotificationHelper.NOTIFICATION_ID_LISTENING, notification)
        startPolling()
    }

    private fun stopForegroundService() {
        isRunning = false
        isPolling = false
        stopForeground(true)
        stopSelf()
    }

    private fun startPolling() {
        if (isPolling) return
        isPolling = true
        lifecycleScope.launch {
            while (isActive && isPolling && isRunning) {
                delay(POLL_INTERVAL_MS)
                checkClipboard()
            }
        }
    }

    private fun checkClipboard() {
        val clip = clipboardManager.primaryClip
        if (clip == null || clip.itemCount == 0) return
        val item = clip.getItemAt(0)
        val text = item?.text?.toString() ?: return
        if (text.isBlank()) return
        if (text == lastClipContent) return
        lastClipContent = text
        processClipContent(text)
    }

    private fun processClipContent(text: String) {
        val type = ContentAnalyzer.analyze(text)
        val actionUri = ContentAnalyzer.getActionUri(text, type)
        Log.d(TAG, "Clipboard content detected: type=${type.name}")
        lifecycleScope.launch(Dispatchers.IO) {
            val db = HistoryDatabase.getInstance(this@ClipboardService)
            db.insertClip(text, type.name)
            withContext(Dispatchers.Main) {
                if (isRunning && NotificationManagerCompat.from(this@ClipboardService)
                        .areNotificationsEnabled()
                ) {
                    val actionNotif = NotificationHelper.buildActionNotification(
                        this@ClipboardService, text, type, actionUri
                    )
                    NotificationManagerCompat.from(this@ClipboardService)
                        .notify(System.currentTimeMillis().toInt(), actionNotif)
                }
            }
        }
    }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        checkClipboard()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        isRunning = false
        isPolling = false
        try {
            clipboardManager.removePrimaryClipChangedListener(clipListener)
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}

