package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.provider.Settings
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.service.OrderAlarmService

object NotificationHelper {

    const val CHANNEL_ID_NEW_ORDERS = "new_order_alerts_v2"
    const val CHANNEL_NAME_NEW_ORDERS = "New Order Alerts"

    const val CHANNEL_ID_GENERAL_UPDATES = "general_updates"
    const val CHANNEL_NAME_GENERAL_UPDATES = "General Updates"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // 1. New Orders Channel: High importance, silent sound (OrderAlarmService plays the 1-minute alarm)
            val newOrdersChannel = NotificationChannel(
                CHANNEL_ID_NEW_ORDERS,
                CHANNEL_NAME_NEW_ORDERS,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent alert ringtone notifications for incoming customer orders"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500, 200, 1000)
                setSound(null, null) // Silent channel: the alarm sound comes from OrderAlarmService
            }

            // 2. General Updates Channel: Standard notifications (rejected, cancelled, withdrawals, broadcasts) with default short sound
            val generalChannel = NotificationChannel(
                CHANNEL_ID_GENERAL_UPDATES,
                CHANNEL_NAME_GENERAL_UPDATES,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General notifications for order status changes, withdrawals, and updates"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(newOrdersChannel)
            notificationManager.createNotificationChannel(generalChannel)
        }
    }

    /**
     * Android 14+ only grants full-screen notifications when the user allows them; older versions
     * always allow it. When false, the alarm still rings and shows a heads-up notification.
     */
    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return manager?.canUseFullScreenIntent() == true
    }

    /** System page where the user allows full-screen notifications for this app (Android 14+). */
    fun fullScreenSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    /**
     * True when the app must ask for full-screen alerts before it can be used: Android 14+, the
     * permission is off, and the phone has the settings page to turn it on (so nobody gets stuck).
     */
    fun isFullScreenPermissionRequired(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        if (canUseFullScreenIntent(context)) return false
        return fullScreenSettingsIntent(context).resolveActivity(context.packageManager) != null
    }

    // Fallback alarm used only when the foreground alarm service cannot start.
    @Volatile
    private var fallbackPlayer: MediaPlayer? = null
    private val fallbackHandler = Handler(Looper.getMainLooper())
    private val stopFallbackRunnable = Runnable { stopLongAlertSound() }

    fun playLongAlertSound(context: Context) {
        try {
            stopLongAlertSound()
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
            mediaPlayer.isLooping = true
            mediaPlayer.prepare()
            mediaPlayer.start()
            fallbackPlayer = mediaPlayer
            fallbackHandler.postDelayed(stopFallbackRunnable, OrderAlarmService.ALARM_DURATION_MS)
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Failed to play alert sound", e)
        }
    }

    fun stopLongAlertSound() {
        fallbackHandler.removeCallbacks(stopFallbackRunnable)
        val player = fallbackPlayer ?: return
        fallbackPlayer = null
        try {
            if (player.isPlaying) player.stop()
            player.release()
        } catch (e: Exception) {
            Log.w("NotificationHelper", "Error stopping alert sound: ${e.message}")
        }
    }

    fun showGeneralNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        targetTab: String = "orders"
    ) {
        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("NAVIGATE_TO", targetTab)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_GENERAL_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            Log.w("NotificationHelper", "Missing POST_NOTIFICATIONS permission: ${e.message}")
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Failed to post general notification", e)
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
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
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
