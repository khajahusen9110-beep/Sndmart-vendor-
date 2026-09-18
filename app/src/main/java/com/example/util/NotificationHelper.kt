package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {

    const val CHANNEL_ID_NEW_ORDERS = "new_order_alerts_v2"
    const val CHANNEL_NAME_NEW_ORDERS = "New Order Alerts"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_NEW_ORDERS,
                CHANNEL_NAME_NEW_ORDERS,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alert notifications for new customer orders"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500, 200, 1000)
                setSound(null, null) // Silent channel so custom 10s MediaPlayer sound plays without overlap
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun playLongAlertSound(context: Context) {
        try {
            val mediaPlayer = MediaPlayer()
            val afd = context.resources.openRawResourceFd(R.raw.order_alert_sound)
            mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mediaPlayer.isLooping = false
            mediaPlayer.setOnCompletionListener { it.release() }
            mediaPlayer.prepare()
            mediaPlayer.start()
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Failed to play alert sound", e)
        }
    }

    fun showNewOrderNotification(
        context: Context,
        orderId: String,
        title: String = "New Order",
        body: String = "You have a new order",
        playSound: Boolean = true
    ) {
        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("order_id", orderId)
            putExtra("NAVIGATE_TO", "orders")
            putExtra("SELECTED_ORDER_ID", orderId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            orderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_NEW_ORDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(null)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(orderId.hashCode(), builder.build())
            if (playSound) {
                playLongAlertSound(context)
            }
        } catch (e: SecurityException) {
            Log.w("NotificationHelper", "Missing POST_NOTIFICATIONS permission: ${e.message}")
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Failed to post order notification", e)
        }
    }
}
