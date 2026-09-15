package com.example.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.ui.orders.IncomingOrderActivity

object NotificationHelper {

    const val CHANNEL_ID_INCOMING_ORDERS = "sndmart_incoming_orders_v2"
    const val CHANNEL_NAME_INCOMING_ORDERS = "Incoming Food Orders"
    const val CHANNEL_DESC_INCOMING_ORDERS = "Urgent full-screen alerts for new customer food orders"
    const val NOTIFICATION_ID_INCOMING_ORDER = 1001

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID_INCOMING_ORDERS,
                CHANNEL_NAME_INCOMING_ORDERS,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC_INCOMING_ORDERS
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 1000)
                setSound(soundUri, audioAttributes)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                setBypassDnd(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun showIncomingOrderAlert(
        context: Context,
        orderId: String,
        orderNumber: String,
        customerName: String,
        customerPhone: String? = null,
        itemsCount: Int = 1,
        totalAmount: Double = 0.0,
        deliveryAddress: String? = null,
        itemsSummary: String? = null
    ) {
        createNotificationChannels(context)

        // 1. Full-screen Intent directly to IncomingOrderActivity
        val fullScreenIntent = Intent(context, IncomingOrderActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(IncomingOrderActivity.EXTRA_ORDER_ID, orderId)
            putExtra(IncomingOrderActivity.EXTRA_ORDER_NUMBER, orderNumber)
            putExtra(IncomingOrderActivity.EXTRA_CUSTOMER_NAME, customerName)
            putExtra(IncomingOrderActivity.EXTRA_CUSTOMER_PHONE, customerPhone)
            putExtra(IncomingOrderActivity.EXTRA_ITEMS_COUNT, itemsCount)
            putExtra(IncomingOrderActivity.EXTRA_TOTAL_AMOUNT, totalAmount)
            putExtra(IncomingOrderActivity.EXTRA_DELIVERY_ADDRESS, deliveryAddress)
            putExtra(IncomingOrderActivity.EXTRA_ITEMS_SUMMARY, itemsSummary)
            putExtra("order_id", orderId)
            putExtra("orderId", orderId)
            putExtra("title", customerName)
            putExtra("body", itemsSummary ?: "New Order")
        }

        try {
            context.startActivity(fullScreenIntent)
        } catch (_: Exception) {
            // Foreground launch might be restricted if invoked from strict background context
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            orderId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Content Intent for tap
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "orders")
            putExtra("SELECTED_ORDER_ID", orderId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            orderId.hashCode() + 1,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val formattedAmount = "₹${String.format(java.util.Locale.US, "%.2f", totalAmount)}"
        val titleText = "🛎️ NEW ORDER TO PREPARE #$orderNumber • $formattedAmount"
        val bodyText = "$customerName • $itemsCount item(s) • Tap to start preparing"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_INCOMING_ORDERS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$bodyText\nAddress: ${deliveryAddress ?: "Takeaway / Dine-in"}\nItems: ${itemsSummary ?: "$itemsCount items"}")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 1000))
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_input_add,
                "START PREPARING",
                fullScreenPendingIntent
            )

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(NOTIFICATION_ID_INCOMING_ORDER, builder.build())
        } catch (e: SecurityException) {
            // Missing POST_NOTIFICATIONS on Android 13+
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun dismissIncomingOrderAlert(context: Context) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(NOTIFICATION_ID_INCOMING_ORDER)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
