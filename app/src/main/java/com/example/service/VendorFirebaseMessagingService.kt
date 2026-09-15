package com.example.service

import android.content.Intent
import android.util.Log
import com.example.data.local.SessionManager
import com.example.data.network.SupabaseClient
import com.example.data.repository.VendorRepository
import com.example.ui.orders.IncomingOrderActivity
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
        val title = data["title"] ?: data["customer_name"] ?: data["customerName"] ?: data["user_name"] ?: remoteMessage.notification?.title ?: "New Order"
        val body = data["body"] ?: data["message"] ?: data["items_summary"] ?: data["itemsSummary"] ?: remoteMessage.notification?.body ?: "You have a new order"
        val customerName = data["customer_name"] ?: data["customerName"] ?: data["user_name"] ?: title
        val customerPhone = data["customer_phone"] ?: data["customerPhone"] ?: data["phone"]
        val itemsCount = data["items_count"]?.toIntOrNull() ?: data["itemsCount"]?.toIntOrNull() ?: 1
        val totalAmount = data["total_amount"]?.toDoubleOrNull() ?: data["totalAmount"]?.toDoubleOrNull() ?: data["amount"]?.toDoubleOrNull() ?: 0.0
        val deliveryAddress = data["delivery_address"] ?: data["deliveryAddress"] ?: data["address"]
        val itemsSummary = data["items_summary"] ?: data["itemsSummary"] ?: data["items"] ?: body

        // Always launch the full-screen alert Activity directly - do not depend on
        // the system notification tray to do this, since that behavior differs
        // between foreground and background app states.
        val fullScreenIntent = Intent(this, IncomingOrderActivity::class.java).apply {
            putExtra("order_id", orderId)
            putExtra("orderId", orderId)
            putExtra("title", title)
            putExtra("body", body)
            putExtra(IncomingOrderActivity.EXTRA_ORDER_ID, orderId)
            putExtra(IncomingOrderActivity.EXTRA_ORDER_NUMBER, orderNumber)
            putExtra(IncomingOrderActivity.EXTRA_CUSTOMER_NAME, customerName)
            putExtra(IncomingOrderActivity.EXTRA_CUSTOMER_PHONE, customerPhone)
            putExtra(IncomingOrderActivity.EXTRA_ITEMS_COUNT, itemsCount)
            putExtra(IncomingOrderActivity.EXTRA_TOTAL_AMOUNT, totalAmount)
            putExtra(IncomingOrderActivity.EXTRA_DELIVERY_ADDRESS, deliveryAddress)
            putExtra(IncomingOrderActivity.EXTRA_ITEMS_SUMMARY, itemsSummary)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        try {
            startActivity(fullScreenIntent)
            Log.d("VendorFCMService", "Directly launched full-screen alert activity for order $orderId")
        } catch (e: Exception) {
            Log.e("VendorFCMService", "Could not directly start full-screen alert activity", e)
        }

        // Notify active screens (like OrdersScreen) to immediately reload data
        OrderEventBus.notifyNewOrder(orderId)

        // Still also post the system notification (for when the app is killed/backgrounded,
        // and as a fallback record in the notification tray) - keep the existing
        // NotificationCompat.Builder + setFullScreenIntent code from before, in addition
        // to the direct startActivity call above.
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

