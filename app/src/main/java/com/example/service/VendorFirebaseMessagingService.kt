package com.example.service

import android.util.Log
import com.example.data.local.SessionManager
import com.example.data.network.SupabaseClient
import com.example.data.repository.VendorRepository
import com.example.util.NotificationHelper
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

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("VendorFCMService", "FCM Message received from: ${remoteMessage.from}, data: ${remoteMessage.data}")

        val data = remoteMessage.data

        // Extract parameters from payload data map or notification fallback
        val orderId = data["order_id"] ?: data["orderId"] ?: data["id"] ?: "order_${System.currentTimeMillis().toString().takeLast(6)}"
        val orderNumber = data["order_number"] ?: data["orderNumber"] ?: orderId.takeLast(6).uppercase()
        val customerName = data["customer_name"] ?: data["customerName"] ?: data["user_name"] ?: remoteMessage.notification?.title ?: "Customer"
        val customerPhone = data["customer_phone"] ?: data["customerPhone"] ?: data["phone"]
        val itemsCount = data["items_count"]?.toIntOrNull() ?: data["itemsCount"]?.toIntOrNull() ?: 1
        val totalAmount = data["total_amount"]?.toDoubleOrNull() ?: data["totalAmount"]?.toDoubleOrNull() ?: data["amount"]?.toDoubleOrNull() ?: 0.0
        val deliveryAddress = data["delivery_address"] ?: data["deliveryAddress"] ?: data["address"]
        val itemsSummary = data["items_summary"] ?: data["itemsSummary"] ?: data["items"] ?: remoteMessage.notification?.body

        // Trigger High-Priority Full Screen Intent & Heads-up notification
        NotificationHelper.showIncomingOrderAlert(
            context = applicationContext,
            orderId = orderId,
            orderNumber = orderNumber,
            customerName = customerName,
            customerPhone = customerPhone,
            itemsCount = itemsCount,
            totalAmount = totalAmount,
            deliveryAddress = deliveryAddress,
            itemsSummary = itemsSummary
        )
    }
}
