package com.example.ui.orders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Order
import com.example.data.model.parseIsoMillis
import kotlinx.coroutines.delay

/** A shop that does not answer a new order within this time loses it (the server cancels it). */
const val ORDER_RESPONSE_MINUTES = 15

/** Time left to answer a new order: the clock starts when it was placed (for a scheduled order, when it is due). */
internal fun responseSecondsLeft(order: Order, nowMs: Long): Long? {
    if (order.status?.lowercase() != "pending") return null
    val start = maxOf(parseIsoMillis(order.createdAt) ?: 0L, order.prepareFromMillis ?: order.scheduledForMillis ?: 0L)
    if (start <= 0L) return null
    return (start + ORDER_RESPONSE_MINUTES * 60_000L - nowMs) / 1000L
}

internal fun formatMinSec(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

/** "Answer within 12:34, or the order is cancelled": calm until 5 minutes are left, then red. Nothing for an order that is not waiting for an answer. */
@Composable
fun ResponseTimer(order: Order, modifier: Modifier = Modifier) {
    if (order.status?.lowercase() != "pending") return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(order.id) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val left = responseSecondsLeft(order, now) ?: return
    val urgent = left < 5 * 60
    val bg = if (left <= 0) Color(0xFFFEE2E2) else if (urgent) Color(0xFFFFEDD5) else Color(0xFFEFF6FF)
    val fg = if (left <= 0) Color(0xFF991B1B) else if (urgent) Color(0xFFC2410C) else Color(0xFF1D4ED8)
    Surface(shape = RoundedCornerShape(10.dp), color = bg, modifier = modifier.fillMaxWidth().testTag("response_timer")) {
        Text(
            text = if (left <= 0) "Time is up: this order is being cancelled" else "Accept or reject within ${formatMinSec(left)}, or the order is cancelled",
            color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
