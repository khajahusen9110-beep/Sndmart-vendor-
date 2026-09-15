package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Order
import com.example.data.model.Vendor
import com.example.ui.components.DashboardStatCardSkeleton
import com.example.ui.components.OrderCardSkeleton
import com.example.ui.components.StatCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*
import com.example.ui.viewmodel.DashboardUiState
import java.text.NumberFormat
import java.util.Locale

@Composable
fun DashboardScreen(
    vendor: Vendor,
    state: DashboardUiState,
    onToggleStoreOpen: (Boolean) -> Unit,
    onNavigateToEarnings: () -> Unit,
    onNavigateToOrders: (statusFilter: String?) -> Unit,
    onOrderClick: (Order) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 2
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PolishBackgroundLight),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!state.errorMessage.isNullOrBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PolishRoseLight,
                    border = BorderStroke(1.dp, PolishRose.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = PolishRose,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = PolishRoseDark,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = onRefresh,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Retry", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 1. Store Online / Offline Status Banner
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = PolishSurfaceLight,
                border = BorderStroke(1.dp, PolishBorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (state.isStoreOpen) PolishEmerald else PolishRose)
                        )
                        Column {
                            Text(
                                text = if (state.isStoreOpen) "Store is accepting orders" else "Store is currently closed",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                            Text(
                                text = if (state.isStoreOpen) "Visible to online customers" else "Customers cannot order right now",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate500
                            )
                        }
                    }

                    Switch(
                        checked = state.isStoreOpen,
                        onCheckedChange = { onToggleStoreOpen(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SprucePrimary,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Slate400
                        )
                    )
                }
            }
        }

        // 2. Hero Wallet Card (Deep Spruce #006B5E)
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = SprucePrimary,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                text = "Wallet Balance",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "₹${"%.2f".format(state.walletBalance)}",
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Button(
                        onClick = onNavigateToEarnings,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpruceContainerLight,
                            contentColor = SprucePrimary
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "REQUEST WITHDRAWAL",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        // 3. Stats Grid (2x2)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Orders Today",
                        value = state.ordersTodayCount.toString(),
                        subValue = if (state.ordersTodayCount > 0) "${state.ordersTodayCount} orders placed" else "No orders yet",
                        subValueColor = if (state.ordersTodayCount > 0) PolishEmeraldDark else Slate400,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToOrders(null) }
                    )
                    StatCard(
                        title = "Earnings",
                        value = "₹${"%.0f".format(state.earningsTodaySum)}",
                        subValue = "Settled today",
                        subValueColor = Slate400,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToEarnings() }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "To Prepare",
                        value = if (state.confirmedOrdersCount < 10) "0${state.confirmedOrdersCount}" else state.confirmedOrdersCount.toString(),
                        subValue = if (state.confirmedOrdersCount > 0) "Action needed" else "All cleared",
                        subValueColor = if (state.confirmedOrdersCount > 0) SprucePrimary else Slate400,
                        containerColor = if (state.confirmedOrdersCount > 0) SpruceContainerLight else PolishSurfaceLight,
                        valueColor = if (state.confirmedOrdersCount > 0) SprucePrimary else Slate900,
                        borderColor = if (state.confirmedOrdersCount > 0) SprucePrimary.copy(alpha = 0.35f) else PolishBorderLight,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToOrders("CONFIRMED") }
                    )
                    StatCard(
                        title = "Avg Rating",
                        value = if (state.averageRating > 0.0) "${"%.1f".format(state.averageRating)} ★" else "N/A",
                        valueColor = if (state.averageRating > 0.0) PolishAmber else Slate500,
                        subValue = if (state.averageRating > 0.0) "Verified reviews" else "No reviews yet",
                        subValueColor = Slate400,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 4. Recent Orders Section Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Orders",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
                TextButton(onClick = { onNavigateToOrders(null) }) {
                    Text(
                        text = "VIEW ALL",
                        color = SprucePrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Recent Orders List
        if (state.isLoading && state.recentOrders.isEmpty()) {
            items(2) {
                OrderCardSkeleton()
            }
        } else if (state.recentOrders.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = PolishSurfaceLight,
                    border = BorderStroke(1.dp, PolishBorderLight)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ReceiptLong,
                            contentDescription = null,
                            tint = Slate400,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No recent orders",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate500
                        )
                    }
                }
            }
        } else {
            items(state.recentOrders.take(6), key = { it.id }) { order ->
                Surface(
                    onClick = { onOrderClick(order) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = PolishSurfaceLight,
                    border = BorderStroke(1.dp, PolishBorderLight)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(PolishBackgroundLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "#${order.orderNumber?.takeLast(3) ?: order.id.take(3).uppercase()}",
                                    color = SprucePrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Column {
                                Text(
                                    text = order.customer?.full_name?.takeIf { it.isNotBlank() } ?: order.customer?.fullName?.takeIf { it.isNotBlank() } ?: order.customerName ?: "Guest Customer",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Slate900
                                )
                                Text(
                                    text = "${order.orderItems?.size ?: 1} Items • ₹${"%.2f".format(order.totalAmount)}",
                                    fontSize = 12.sp,
                                    color = Slate500
                                )
                            }
                        }

                        StatusBadge(status = order.status ?: "pending")
                    }
                }
            }
        }
    }
}

