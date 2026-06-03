package com.clipintent.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.clipintent.app.ContentAnalyzer.ContentType

/**
 * Helper class for building and managing notifications.
 */
object NotificationHelper {

    private const val CHANNEL_MONITOR = "clipboard_monitor"
    private const val CHANNEL_ACTION = "clipboard_action"
    const val NOTIFICATION_ID_LISTENING = 1001
    const val NOTIFICATION_ID_ACTION = 1002

    /**
     * Create notification channels (required for Android 8+).
     */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val monitorChannel = NotificationChannel(
                CHANNEL_MONITOR,
                context.getString(R.string.notification_channel_monitor),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_monitor_desc)
                setShowBadge(false)
            }

            val actionChannel = NotificationChannel(
                CHANNEL_ACTION,
                "Clipboard Actions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Action notifications for clipboard content"
                setShowBadge(true)
            }

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(monitorChannel)
            nm.createNotificationChannel(actionChannel)
        }
    }

    /**
     * Build the persistent "listening" notification shown when the service is active.
     */
    fun buildListeningNotification(context: Context): Notification {
        val stopIntent = Intent(context, ClipboardService::class.java).apply {
            action = ClipboardService.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setContentTitle(context.getString(R.string.clipboard_listening))
            .setContentText(context.getString(R.string.clipboard_listening_desc))
            .setSmallIcon(android.R.drawable.ic_menu_edit) // Use system icon
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.stop_service),
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Build an action notification for a newly detected clipboard item.
     */
    fun buildActionNotification(
        context: Context,
        content: String,
        type: ContentType,
        actionUri: String?
    ): Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ACTION)
            .setContentTitle(type.label)
            .setContentText(content.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // Add smart action button if an actionable URI exists
        if (actionUri != null) {
            val actionIntent = Intent(Intent.ACTION_VIEW, Uri.parse(actionUri))
            val pendingAction = PendingIntent.getActivity(
                context, 2, actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_menu_directions,
                type.actionLabel,
                pendingAction
            )
        } else {
            // Special handling for types without direct URI
            when (type) {
                ContentType.ADDRESS -> {
                    // Launch maps with search query
                    val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(content)}"))
                    val pendingMap = PendingIntent.getActivity(
                        context, 3, mapIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(
                        android.R.drawable.ic_menu_directions,
                        type.actionLabel,
                        pendingMap
                    )
                }
                ContentType.TRACKING -> {
                    // Open browser with search
                    val searchIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/search?q=${Uri.encode("track package $content")}")
                    )
                    val pendingSearch = PendingIntent.getActivity(
                        context, 4, searchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(
                        android.R.drawable.ic_menu_search,
                        type.actionLabel,
                        pendingSearch
                    )
                }
                else -> {
                    // No action for plain text or crypto (no internet permission)
                    builder.addAction(
                        android.R.drawable.ic_menu_edit,
                        "Copy",
                        null
                    )
                }
            }
        }

        return builder.build()
    }

    /**
     * Show or update the listening notification.
     */
    fun showListeningNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID_LISTENING, buildListeningNotification(context))
    }

    /**
     * Cancel the listening notification.
     */
    fun cancelListeningNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID_LISTENING)
    }
}