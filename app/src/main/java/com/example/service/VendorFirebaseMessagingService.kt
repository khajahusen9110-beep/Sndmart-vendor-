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
import com.example.util.NotificationHelper
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
        // Use ONLY cachedVendor?.id as vendor_id (remove sessionManager.userId fallback which is the login ID)
        val cachedVendor = sessionManager.cachedVendor
        val vendorId = cachedVendor?.id
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
        } else {
            Log.d("VendorFCMService", "FCM token saved locally; will sync to Supabase once vendor logs in")
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("VendorFCMService", "FCM Message received from: ${message.from}, data: ${message.data}")

        val orderId = message.data["orderId"] ?: message.data["order_id"] ?: message.data["id"] ?: "order_${System.currentTimeMillis()}"
        val title = message.data["title"] ?: message.notification?.title ?: "Sndmart Order Alert"
        val body = message.data["message"] ?: message.data["body"] ?: message.notification?.body ?: "You have a new update"

        val status = message.data["status"]?.lowercase()
        val type = message.data["type"]?.lowercase()
        val isNewOrder = type == "new_order" || type == "test_alert" || status in listOf("confirmed", "pending")

        if (isNewOrder) {
            // Notify active screens (like OrdersScreen) to immediately reload data
            OrderEventBus.notifyNewOrder(orderId)

            // Play 10-second continuous urgent alert sound using foreground service (survives app backgrounding & screen lock)
            OrderAlarmService.startAlarm(this, orderId, title, body)
        } else {
            // All other pushes (Order Rejected, Order Cancelled, Order Update, withdrawals, broadcasts) -> normal notification on general_updates channel with default short sound, no alarm
            NotificationHelper.showGeneralNotification(this, orderId.hashCode(), title, body)
        }
    }
}

