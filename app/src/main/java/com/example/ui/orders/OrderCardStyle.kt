package com.example.ui.orders

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

/**
 * How an order card looks for each kind of order, so a vendor tells them apart at a glance:
 * NEW (glowing amber), preparing (green), ready (purple), out for delivery (blue), delivered (emerald, calm),
 * cancelled (rose, dashed border, no shadow). Each has its own tint, border, side stripe and shadow.
 */
data class OrderCardTheme(
    val kind: String,
    val tintTop: Color,
    val tintBottom: Color,
    val border: Color,
    val borderWidth: Dp,
    val stripe: Color,
    val shadow: Dp,
    val dashedBorder: Boolean = false,
    val glow: Boolean = false
)

fun orderCardTheme(status: String?): OrderCardTheme = when (status?.trim()?.lowercase()) {
    "confirmed" -> OrderCardTheme("new", PolishAmberLight, Color(0xFFFFFBEB), PolishAmber, 2.dp, PolishAmber, 6.dp, glow = true)
    "pending" -> OrderCardTheme("pending", Slate100, Color.White, Slate400, 1.5.dp, Slate500, 1.dp)
    "preparing" -> OrderCardTheme("preparing", SpruceContainerLight, Color.White, SprucePrimaryLight, 1.5.dp, SprucePrimary, 3.dp)
    "ready" -> OrderCardTheme("ready", PolishPurpleLight, Color.White, PolishPurple, 1.5.dp, PolishPurple, 3.dp)
    "out_for_delivery" -> OrderCardTheme("out_for_delivery", PolishBlueLight, Color.White, PolishBlue, 1.5.dp, PolishBlue, 3.dp)
    "delivered" -> OrderCardTheme("delivered", PolishEmeraldLight, Color(0xFFF3FCF8), PolishEmerald, 1.5.dp, PolishEmerald, 2.dp)
    "cancelled", "canceled", "rejected" -> OrderCardTheme("cancelled", PolishRoseLight, Color(0xFFFFF7F7), PolishRose, 1.5.dp, PolishRose, 0.dp, dashedBorder = true)
    else -> OrderCardTheme("other", Color.White, Color.White, PolishBorderLight, 1.dp, PolishBorderLight, 0.dp)
}

/** The card of one order: tinted background, border, a stripe down the left side, and a glow for a NEW order. */
@Composable
fun OrderCardSurface(
    status: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val t = orderCardTheme(status)
    val shape = RoundedCornerShape(20.dp)
    val glow = if (t.glow) {
        val a by rememberInfiniteTransition(label = "order-glow").animateFloat(
            initialValue = 0.35f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "order-glow-alpha"
        )
        a
    } else 1f
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = Color.Transparent,
        border = if (t.dashedBorder) null else BorderStroke(t.borderWidth, t.border.copy(alpha = glow)),
        shadowElevation = t.shadow
    ) {
        Box(
            Modifier
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(t.tintTop, t.tintBottom)))
                    drawRect(t.stripe, topLeft = Offset.Zero, size = Size(5.dp.toPx(), size.height))
                    if (t.dashedBorder) {
                        val w = t.borderWidth.toPx()
                        drawRoundRect(
                            color = t.border, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
                            cornerRadius = CornerRadius(20.dp.toPx()),
                            style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)))
                        )
                    }
                }
        ) { content() }
    }
}
