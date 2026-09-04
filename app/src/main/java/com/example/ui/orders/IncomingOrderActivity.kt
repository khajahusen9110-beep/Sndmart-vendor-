package com.example.ui.orders

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainActivity
import com.example.data.local.SessionManager
import com.example.data.model.Order
import com.example.data.network.SupabaseClient
import com.example.data.repository.Resource
import com.example.data.repository.VendorRepository
import com.example.ui.theme.*
import com.example.util.NotificationHelper
import kotlinx.coroutines.*

class IncomingOrderActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ORDER_ID = "extra_order_id"
        const val EXTRA_ORDER_NUMBER = "extra_order_number"
        const val EXTRA_CUSTOMER_NAME = "extra_customer_name"
        const val EXTRA_CUSTOMER_PHONE = "extra_customer_phone"
        const val EXTRA_ITEMS_COUNT = "extra_items_count"
        const val EXTRA_TOTAL_AMOUNT = "extra_total_amount"
        const val EXTRA_DELIVERY_ADDRESS = "extra_delivery_address"
        const val EXTRA_ITEMS_SUMMARY = "extra_items_summary"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var vibrationJob: Job? = null
    private val activityScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(applicationContext)

        setupLockscreenFlags()
        initVibrator()

        val orderId = intent.getStringExtra(EXTRA_ORDER_ID) ?: "ord_${System.currentTimeMillis().toString().takeLast(6)}"
        val orderNumber = intent.getStringExtra(EXTRA_ORDER_NUMBER) ?: orderId.takeLast(6).uppercase()
        val customerName = intent.getStringExtra(EXTRA_CUSTOMER_NAME) ?: "Customer"
        val customerPhone = intent.getStringExtra(EXTRA_CUSTOMER_PHONE)
        val itemsCount = intent.getIntExtra(EXTRA_ITEMS_COUNT, 1)
        val totalAmount = intent.getDoubleExtra(EXTRA_TOTAL_AMOUNT, 0.0)
        val deliveryAddress = intent.getStringExtra(EXTRA_DELIVERY_ADDRESS)
        val itemsSummary = intent.getStringExtra(EXTRA_ITEMS_SUMMARY)

        if (sessionManager.isSoundAlertEnabled) {
            startAudioAlert()
            startVibrationAlert()
        }

        setContent {
            MyApplicationTheme(darkTheme = true) {
                IncomingOrderScreen(
                    orderId = orderId,
                    orderNumber = orderNumber,
                    initialCustomerName = customerName,
                    customerPhone = customerPhone,
                    initialItemsCount = itemsCount,
                    initialTotalAmount = totalAmount,
                    initialDeliveryAddress = deliveryAddress,
                    initialItemsSummary = itemsSummary,
                    isMuted = !sessionManager.isSoundAlertEnabled,
                    onToggleMute = { muted ->
                        sessionManager.isSoundAlertEnabled = !muted
                        if (muted) {
                            stopAlerts()
                        } else {
                            startAudioAlert()
                            startVibrationAlert()
                        }
                    },
                    onAccept = { targetOrderId ->
                        handleAcceptOrder(targetOrderId)
                    },
                    onReject = { targetOrderId, reason ->
                        handleRejectOrder(targetOrderId, reason)
                    },
                    onDismiss = {
                        stopAlerts()
                        NotificationHelper.dismissIncomingOrderAlert(applicationContext)
                        finish()
                    }
                )
            }
        }
    }

    private fun setupLockscreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun startAudioAlert() {
        try {
            stopAudioAlert()
            val alertUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAudioAlert() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun startVibrationAlert() {
        vibrationJob?.cancel()
        vibrationJob = activityScope.launch(Dispatchers.Default) {
            val vib = vibrator ?: return@launch
            val pattern = longArrayOf(0, 400, 200, 400, 600)
            while (isActive) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vib.vibrate(VibrationEffect.createWaveform(pattern, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vib.vibrate(pattern, -1)
                    }
                } catch (e: Exception) {
                    break
                }
                delay(1600)
            }
        }
    }

    private fun stopVibrationAlert() {
        vibrationJob?.cancel()
        vibrationJob = null
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun stopAlerts() {
        stopAudioAlert()
        stopVibrationAlert()
    }

    private fun handleAcceptOrder(orderId: String) {
        stopAlerts()
        NotificationHelper.dismissIncomingOrderAlert(applicationContext)

        activityScope.launch(Dispatchers.IO) {
            try {
                val supabaseClient = SupabaseClient(sessionManager)
                val repository = VendorRepository(supabaseClient, sessionManager)
                val res = repository.updateOrderStatus(orderId, "confirmed", notes = "Accepted via Full-Screen Alert")
                withContext(Dispatchers.Main) {
                    if (res is Resource.Success) {
                        Toast.makeText(applicationContext, "Order #$orderId accepted! Moving to kitchen.", Toast.LENGTH_SHORT).show()
                    }
                    val mainIntent = Intent(applicationContext, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("NAVIGATE_TO", "orders")
                        putExtra("SELECTED_ORDER_ID", orderId)
                    }
                    startActivity(mainIntent)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val mainIntent = Intent(applicationContext, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("NAVIGATE_TO", "orders")
                    }
                    startActivity(mainIntent)
                    finish()
                }
            }
        }
    }

    private fun handleRejectOrder(orderId: String, reason: String) {
        stopAlerts()
        NotificationHelper.dismissIncomingOrderAlert(applicationContext)

        activityScope.launch(Dispatchers.IO) {
            try {
                val supabaseClient = SupabaseClient(sessionManager)
                val repository = VendorRepository(supabaseClient, sessionManager)
                repository.updateOrderStatus(orderId, "cancelled", cancellationReason = reason)
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Order declined", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    finish()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlerts()
        activityScope.cancel()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomingOrderScreen(
    orderId: String,
    orderNumber: String,
    initialCustomerName: String,
    customerPhone: String?,
    initialItemsCount: Int,
    initialTotalAmount: Double,
    initialDeliveryAddress: String?,
    initialItemsSummary: String?,
    isMuted: Boolean,
    onToggleMute: (Boolean) -> Unit,
    onAccept: (orderId: String) -> Unit,
    onReject: (orderId: String, reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isMutedState by remember { mutableStateOf(isMuted) }
    var isAccepting by remember { mutableStateOf(false) }
    var isRejecting by remember { mutableStateOf(false) }
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectionReason by remember { mutableStateOf("Kitchen too busy") }

    // Remote order details if available
    var orderData by remember { mutableStateOf<Order?>(null) }
    val sessionManager = remember { SessionManager(context) }
    val repository = remember { VendorRepository(SupabaseClient(sessionManager), sessionManager) }

    LaunchedEffect(orderId) {
        try {
            when (val res = repository.getOrderById(orderId)) {
                is Resource.Success -> orderData = res.data
                else -> {}
            }
        } catch (_: Exception) {}
    }

    val currentOrder = orderData
    val customerName = currentOrder?.customerName ?: initialCustomerName
    val totalAmount = currentOrder?.totalAmount ?: initialTotalAmount
    val deliveryAddress = currentOrder?.deliveryAddress ?: initialDeliveryAddress
    val itemsCount = currentOrder?.orderItems?.size ?: initialItemsCount
    val itemsList = currentOrder?.orderItems
    val itemsSummary = if (!itemsList.isNullOrEmpty()) {
        itemsList.joinToString(", ") { "${it.productName ?: "Item"} x${it.quantity}" }
    } else {
        initialItemsSummary ?: "$itemsCount items"
    }

    // Radar pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    // Countdown Timer (90 seconds)
    var timeLeft by remember { mutableIntStateOf(90) }
    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            delay(1000)
            timeLeft--
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A), // Dark slate
                        Color(0xFF1E293B),
                        Color(0xFF064E3B)  // Forest glow
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- TOP BAR: Header & Mute Action ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = SndmartOrange.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SndmartOrange.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SndmartOrange)
                        )
                        Text(
                            text = "LIVE INCOMING ORDER",
                            color = SndmartOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = {
                            val newMute = !isMutedState
                            isMutedState = newMute
                            onToggleMute(newMute)
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.1f)
                        )
                    ) {
                        Icon(
                            imageVector = if (isMutedState) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Toggle Mute",
                            tint = if (isMutedState) PolishRose else Color.White
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.1f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Slate300
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- CENTER: Radar Alert & Order Details ---
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Pulsating Bell Icon
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(SprucePrimary.copy(alpha = 0.15f))
                    )
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(SprucePrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "New Order #$orderNumber",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

                Text(
                    text = "Auto-dismisses in ${timeLeft}s if not accepted",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate400
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Order Summary Card
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Total Price & Type
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "TOTAL AMOUNT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate400
                                )
                                Text(
                                    text = "₹${String.format(java.util.Locale.US, "%.2f", totalAmount)}",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SndmartOrange
                                )
                            }

                            Surface(
                                color = SprucePrimary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, SprucePrimary.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "$itemsCount ITEM${if (itemsCount > 1) "S" else ""}",
                                    color = SprucePrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                        // Customer info
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Column {
                                Text(
                                    text = customerName,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                                if (!customerPhone.isNullOrBlank()) {
                                    Text(
                                        text = customerPhone,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Slate400
                                    )
                                }
                            }
                        }

                        // Delivery Address
                        if (!deliveryAddress.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = SndmartOrange, modifier = Modifier.size(18.dp))
                                }
                                Column {
                                    Text(
                                        text = "Delivery Location",
                                        fontWeight = FontWeight.SemiBold,
                                        color = Slate300,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = deliveryAddress,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Slate400,
                                        maxLines = 2
                                    )
                                }
                            }
                        }

                        // Items Breakdown
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0F172A).copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Order Items:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate400
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = itemsSummary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- BOTTOM ACTIONS: Accept & Reject Buttons ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Big Glowing Accept Button
                Button(
                    onClick = {
                        isAccepting = true
                        onAccept(orderId)
                    },
                    enabled = !isAccepting && !isRejecting,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SprucePrimary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    if (isAccepting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("ACCEPTING ORDER...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(26.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "ACCEPT ORDER",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Reject Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showRejectDialog = true },
                        enabled = !isAccepting && !isRejecting,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = PolishRose
                        ),
                        border = BorderStroke(1.dp, PolishRose.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Decline Order", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Slate300
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("View In App", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    // Rejection Reason Dialog
    if (showRejectDialog) {
        val reasons = listOf(
            "Kitchen too busy",
            "Item(s) out of stock",
            "Store closing soon",
            "Delivery distance too far",
            "Other"
        )
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text("Decline Order #$orderNumber", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select reason for declining this order:", fontSize = 13.sp, color = Slate400)
                    reasons.forEach { r ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = rejectionReason == r,
                                onClick = { rejectionReason = r }
                            )
                            Text(text = r, fontSize = 14.sp, color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRejectDialog = false
                        isRejecting = true
                        onReject(orderId, rejectionReason)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PolishRose)
                ) {
                    Text("Confirm Decline")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRejectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
