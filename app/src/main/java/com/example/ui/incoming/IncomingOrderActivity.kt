package com.example.ui.incoming

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.MainActivity
import com.example.data.local.SessionManager
import com.example.data.network.SupabaseClient
import com.example.data.repository.Resource
import com.example.data.repository.VendorRepository
import com.example.service.OrderAlarmService
import com.example.ui.theme.SndmartVendorTheme
import com.example.ui.theme.SprucePrimary
import com.example.util.OrderEventBus
import kotlinx.coroutines.launch

/**
 * Incoming-call style screen for a new order, opened by the alarm notification's full-screen
 * intent. It only shows the order: the sound comes from [OrderAlarmService], which every button
 * here stops.
 */
class IncomingOrderActivity : ComponentActivity() {

    private var orderId by mutableStateOf("")
    private var orderTitle by mutableStateOf("")
    private var orderBody by mutableStateOf("")

    // While "Start Preparing" is being saved, stopping the sound must not close this screen.
    private var isAccepting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        if (!readOrder(intent)) return

        // Close when the alert is handled elsewhere (notification button, app, other order action).
        lifecycleScope.launch {
            OrderAlarmService.alarmHandled.collect { if (!isAccepting) finish() }
        }

        setContent {
            SndmartVendorTheme {
                IncomingOrderScreen(
                    title = orderTitle,
                    body = orderBody,
                    canStartPreparing = isRealOrderId(orderId),
                    onStartPreparing = { onComplete -> startPreparing(orderId, onComplete) },
                    onOpenOrder = { openOrder(orderId) },
                    onDismiss = {
                        OrderAlarmService.stopAlarm(this)
                        finish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // A newer order arrived while this screen was open: show the newest one.
        readOrder(intent)
    }

    /** Returns false (and closes) when there is nothing to show or nobody is logged in. */
    private fun readOrder(intent: Intent?): Boolean {
        val id = intent?.getStringExtra(EXTRA_ORDER_ID)
        if (id.isNullOrBlank() || !SessionManager(applicationContext).isLoggedIn) {
            finish()
            return false
        }
        orderId = id
        orderTitle = intent.getStringExtra(EXTRA_TITLE) ?: "New Order"
        orderBody = intent.getStringExtra(EXTRA_BODY) ?: "Tap Start Preparing to accept it."
        return true
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startPreparing(id: String, onComplete: (Boolean) -> Unit) {
        isAccepting = true
        OrderAlarmService.stopAlarm(this)
        lifecycleScope.launch {
            val sessionManager = SessionManager(applicationContext)
            val repository = VendorRepository(SupabaseClient(sessionManager), sessionManager)
            when (val res = repository.updateOrderStatus(id, "preparing", "confirmed")) {
                is Resource.Success -> {
                    OrderEventBus.notifyNewOrder(id) // refresh the Orders screen if it is open
                    Toast.makeText(this@IncomingOrderActivity, "Order accepted - start preparing", Toast.LENGTH_SHORT).show()
                    onComplete(true)
                    finish()
                }
                is Resource.Error -> {
                    Toast.makeText(this@IncomingOrderActivity, res.message, Toast.LENGTH_LONG).show()
                    onComplete(false)
                }
                Resource.Loading -> {}
            }
        }
    }

    private fun openOrder(id: String) {
        OrderAlarmService.stopAlarm(this)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra("NAVIGATE_TO", "orders")
                if (isRealOrderId(id)) putExtra("SELECTED_ORDER_ID", id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
        finish()
    }

    companion object {
        private const val EXTRA_ORDER_ID = "incoming_order_id"
        private const val EXTRA_TITLE = "incoming_order_title"
        private const val EXTRA_BODY = "incoming_order_body"

        fun newIntent(context: Context, orderId: String, title: String, body: String): Intent =
            Intent(context, IncomingOrderActivity::class.java).apply {
                putExtra(EXTRA_ORDER_ID, orderId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_BODY, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

        /** Test alerts and fallback ids are not real orders and cannot be accepted. */
        private fun isRealOrderId(id: String): Boolean =
            id.isNotBlank() && id != "test" && !id.startsWith("order_")
    }
}

@Composable
private fun IncomingOrderScreen(
    title: String,
    body: String,
    canStartPreparing: Boolean,
    onStartPreparing: (onComplete: (Boolean) -> Unit) -> Unit,
    onOpenOrder: () -> Unit,
    onDismiss: () -> Unit
) {
    var isAccepting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SprucePrimary)
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 48.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(52.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = body,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (canStartPreparing) {
                Button(
                    onClick = {
                        isAccepting = true
                        onStartPreparing { success -> if (!success) isAccepting = false }
                    },
                    enabled = !isAccepting,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = SprucePrimary),
                    contentPadding = PaddingValues(vertical = 18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isAccepting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SprucePrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Kitchen, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Preparing", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            OutlinedButton(
                onClick = onOpenOrder,
                enabled = !isAccepting,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Order", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(
                onClick = onDismiss,
                enabled = !isAccepting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop Sound & Close", color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}
