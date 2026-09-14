package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label) = when (status.lowercase()) {
        "pending" -> Triple(PolishRoseLight, PolishRose, "PENDING")
        "confirmed" -> Triple(Slate100, Slate700, "CONFIRMED")
        "preparing" -> Triple(SpruceContainerLight, SprucePrimary, "PREPARING")
        "ready" -> Triple(PolishPurpleLight, PolishPurple, "READY")
        "out_for_delivery" -> Triple(PolishAmberLight, PolishAmberDark, "OUT FOR DELIVERY")
        "delivered", "paid", "approved", "active" -> Triple(PolishEmeraldLight, PolishEmeraldDark, status.uppercase())
        "cancelled", "canceled", "rejected", "failed" -> Triple(PolishRoseLight, PolishRose, status.uppercase())
        "open" -> Triple(PolishEmeraldLight, PolishEmeraldDark, "OPEN")
        "closed" -> Triple(PolishRoseLight, PolishRose, "CLOSED")
        else -> Triple(Slate100, Slate700, status.uppercase())
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Text(
                text = label,
                color = textColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector? = null,
    iconColor: Color = SprucePrimary,
    modifier: Modifier = Modifier,
    subValue: String? = null,
    subValueColor: Color = Slate500,
    containerColor: Color = PolishSurfaceLight,
    valueColor: Color = Slate900,
    borderColor: Color = PolishBorderLight,
    onClick: (() -> Unit)? = null
) {
    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = if (containerColor == PolishRoseLight) PolishRoseDark else Slate500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(iconColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor
            )

            if (subValue != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subValue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = subValueColor
                )
            }
        }
    }
}

@Composable
fun InAppOrderAlertBanner(
    message: String,
    onDismiss: () -> Unit,
    onViewOrders: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = SprucePrimary,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = "Notification",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onViewOrders() }
            ) {
                Text(
                    text = "New Order Alert!",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = message,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onViewOrders,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SpruceContainerLight,
                    contentColor = SprucePrimary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("VIEW", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel",
    isDestructive: Boolean = false,
    isLoading: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Text(text = title, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(text = message)
        },
        confirmButton = {
            Button(
                onClick = { if (!isLoading) onConfirm() },
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = if (isDestructive) {
                    ButtonDefaults.buttonColors(containerColor = PolishRose)
                } else {
                    ButtonDefaults.buttonColors(containerColor = SprucePrimary)
                }
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Submitting...")
                } else {
                    Text(confirmText, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { if (!isLoading) onDismiss() },
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(dismissText)
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

// --- SKELETON LOADERS (Rule 7: Show something immediately) ---

fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_anim"
    )

    val shimmerColors = listOf(
        Slate200.copy(alpha = 0.6f),
        Slate100.copy(alpha = 0.9f),
        Slate200.copy(alpha = 0.6f)
    )

    background(
        brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(x = translateAnim - 500f, y = translateAnim - 500f),
            end = Offset(x = translateAnim, y = translateAnim)
        )
    )
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    Box(
        modifier = modifier
            .clip(shape)
            .shimmerEffect()
    )
}

@Composable
fun OrderCardSkeleton(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = PolishSurfaceLight,
        border = BorderStroke(1.dp, PolishBorderLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBox(modifier = Modifier.size(width = 110.dp, height = 20.dp), shape = RoundedCornerShape(6.dp))
                ShimmerBox(modifier = Modifier.size(width = 80.dp, height = 22.dp), shape = RoundedCornerShape(50))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ShimmerBox(modifier = Modifier.size(36.dp), shape = CircleShape)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShimmerBox(modifier = Modifier.size(width = 140.dp, height = 16.dp))
                    ShimmerBox(modifier = Modifier.size(width = 90.dp, height = 12.dp))
                }
            }
            Divider(color = PolishBorderLight.copy(alpha = 0.5f), thickness = 1.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBox(modifier = Modifier.size(width = 70.dp, height = 14.dp))
                ShimmerBox(modifier = Modifier.size(width = 60.dp, height = 18.dp))
            }
        }
    }
}

@Composable
fun ProductCardSkeleton(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = PolishSurfaceLight,
        border = BorderStroke(1.dp, PolishBorderLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ShimmerBox(
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(14.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.65f).height(18.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f).height(14.dp))
                ShimmerBox(modifier = Modifier.size(width = 64.dp, height = 20.dp), shape = RoundedCornerShape(6.dp))
            }
            ShimmerBox(modifier = Modifier.size(width = 44.dp, height = 24.dp), shape = RoundedCornerShape(50))
        }
    }
}

@Composable
fun DashboardStatCardSkeleton(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = PolishSurfaceLight,
        border = BorderStroke(1.dp, PolishBorderLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ShimmerBox(modifier = Modifier.size(width = 80.dp, height = 14.dp))
                ShimmerBox(modifier = Modifier.size(28.dp), shape = RoundedCornerShape(8.dp))
            }
            ShimmerBox(modifier = Modifier.size(width = 100.dp, height = 28.dp))
            ShimmerBox(modifier = Modifier.size(width = 60.dp, height = 12.dp))
        }
    }
}

@Composable
fun ReviewCardSkeleton(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = PolishSurfaceLight,
        border = BorderStroke(1.dp, PolishBorderLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ShimmerBox(modifier = Modifier.size(36.dp), shape = CircleShape)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ShimmerBox(modifier = Modifier.size(width = 120.dp, height = 14.dp))
                        ShimmerBox(modifier = Modifier.size(width = 70.dp, height = 11.dp))
                    }
                }
                ShimmerBox(modifier = Modifier.size(width = 50.dp, height = 16.dp), shape = RoundedCornerShape(6.dp))
            }
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(14.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp))
        }
    }
}

@Composable
fun TransactionCardSkeleton(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = PolishSurfaceLight,
        border = BorderStroke(1.dp, PolishBorderLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                ShimmerBox(modifier = Modifier.size(40.dp), shape = CircleShape)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShimmerBox(modifier = Modifier.size(width = 140.dp, height = 14.dp))
                    ShimmerBox(modifier = Modifier.size(width = 80.dp, height = 11.dp))
                }
            }
            ShimmerBox(modifier = Modifier.size(width = 60.dp, height = 18.dp))
        }
    }
}

