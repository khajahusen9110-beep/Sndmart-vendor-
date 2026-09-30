package com.example.service

import android.util.Log
import com.example.data.local.SessionManager
import com.example.data.network.SupabaseClient
import com.example.data.repository.VendorRepository
import com.example.util.NotificationHelper
import com.example.util.OrderAlertTracker
import com.example.util.OrderEventBus
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class VendorFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val sessionManager = SessionManager(applicationContext)
        sessionManager.fcmToken = token

        // Sync the refreshed token only when an approved vendor is logged in.
        val vendor = sessionManager.cachedVendor
        if (!sessionManager.isLoggedIn || vendor == null || vendor.id.isEmpty()) return
        val vendorEmail = sessionManager.userEmail ?: "${vendor.id}@sndmart.vendor"

        serviceScope.launch {
            try {
                val repository = VendorRepository(SupabaseClient(sessionManager), sessionManager)
                repository.registerFcmToken(vendor.id, vendorEmail, token)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync refreshed FCM token", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val sessionManager = SessionManager(applicationContext)
        // A logged-out phone must not ring for a vendor's orders.
        if (!sessionManager.isLoggedIn) return

        val data = message.data
        val orderId = data["orderId"] ?: data["order_id"] ?: data["id"] ?: "order_${System.currentTimeMillis()}"
        val title = data["title"] ?: message.notification?.title ?: "Sndmart Order Alert"
        val body = data["message"] ?: data["body"] ?: message.notification?.body ?: "You have a new update"

        if (isNewOrderAlert(data, title)) {
            val isTest = orderId == "test" || title.equals(TEST_ALERT_TITLE, ignoreCase = true)
            val isFirstAlert = isTest || OrderAlertTracker.markIfNew(orderId)
            // Let an open Orders screen reload right away.
            OrderEventBus.notifyNewOrder(orderId)
            if (!isFirstAlert) return

            if (sessionManager.isSoundAlertEnabled) {
                OrderAlarmService.startAlarm(this, orderId, title, body)
            } else {
                NotificationHelper.showNewOrderNotification(this, orderId, title, body, playSound = false)
            }
        } else {
            // Order cancelled/rejected/ready, withdrawals, broadcasts: normal notification.
            NotificationHelper.showGeneralNotification(this, orderId.hashCode(), title, body)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * The backend push (send-fcm-vendor-native) forwards only title/message/order_id, so a new
     * order is recognised by its title. status/type are also honoured if the backend sends them.
     */
    private fun isNewOrderAlert(data: Map<String, String>, title: String): Boolean {
        val type = data["type"]?.lowercase()
        val status = data["status"]?.lowercase()
        return type == "new_order" ||
            type == "test_alert" ||
            status == "confirmed" ||
            title.startsWith(NEW_ORDER_TITLE_PREFIX, ignoreCase = true) ||
            title.equals(TEST_ALERT_TITLE, ignoreCase = true)
    }

    companion object {
        private const val TAG = "VendorFCMService"
        // Titles used by the notify_order_status_change / vendor_send_test_alert database functions.
        private const val NEW_ORDER_TITLE_PREFIX = "New Order - Please Prepare"
        private const val TEST_ALERT_TITLE = "Test Order Alert"
    }
}
