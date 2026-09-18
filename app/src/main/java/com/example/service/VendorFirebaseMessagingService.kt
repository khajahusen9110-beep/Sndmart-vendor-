package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.SessionManager
import com.example.data.network.SupabaseClient
import com.example.data.repository.VendorRepository
import com.example.util.OrderEventBus
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VendorFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("VendorFCMService", "Refreshed FCM registration token: $token")
        val sessionManager = SessionManager(applicationContext)
        sessionManager.fcmToken = token

        // If vendor is logged in, upsert the refreshed token into vendor_fcm_tokens table
        val cachedVendor = sessionManager.cachedVendor
        val vendorId = cachedVendor?.id ?: sessionManager.userId
        val vendorEmail = sessionManager.userEmail ?: cachedVendor?.ownerName ?: "vendor@sndmart.com"

        if (!vendorId.isNullOrEmpty()) {
            serviceScope.launch {
                try {
                    val supabaseClient = SupabaseClient(sessionManager)
                    val repository = VendorRepository(supabaseClient, sessionManager)
                    repository.registerFcmToken(vendorId, vendorEmail, token)
                    Log.d("VendorFCMService", "FCM token synced to Supabase for vendor $vendorId")
                } catch (e: Exception) {
                    Log.e("VendorFCMService", "Failed to sync refreshed FCM token", e)
                }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("VendorFCMService", "FCM Message received from: ${message.from}, data: ${message.data}")

        val orderId = message.data["orderId"] ?: message.data["order_id"] ?: message.data["id"] ?: "order_${System.currentTimeMillis()}"
        val title = message.data["title"] ?: message.notification?.title ?: "New Order"
        val body = message.data["message"] ?: message.data["body"] ?: message.notification?.body ?: "You have a new order"

        // Notify active screens (like OrdersScreen) to immediately reload data
        OrderEventBus.notifyNewOrder(orderId)

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("order_id", orderId)
            putExtra("NAVIGATE_TO", "orders")
            putExtra("SELECTED_ORDER_ID", orderId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, orderId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "new_order_alerts_v2"
        val channel = NotificationChannel(
            channelId, "New Order Alerts", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500, 200, 1000)
            setSound(null, null) // Silent channel so custom 10s MediaPlayer sound plays without overlap
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSound(null)
            .build()

        notificationManager.notify(orderId.hashCode(), notification)

        // Play 10-second continuous urgent alert sound using MediaPlayer
        playLongAlertSound(this)
    }

    private fun playLongAlertSound(context: Context) {
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
            Log.e("VendorFcm", "Failed to play alert sound", e)
        }
    }
}

