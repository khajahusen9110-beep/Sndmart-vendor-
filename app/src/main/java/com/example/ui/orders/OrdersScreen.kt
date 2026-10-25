package com.example.ui.orders

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Order
import com.example.util.CurrencyFormatter
import com.example.util.OrderAge
import com.example.ui.components.OrderCardSkeleton
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*
import com.example.ui.viewmodel.OrdersUiState
import kotlinx.coroutines.delay

val ORDER_STATUS_FILTERS = listOf(
    "ALL" to "All",
    "CONFIRMED" to "Confirmed",
    "PREPARING" to "Preparing",
    "READY" to "Ready",
    "OUT_FOR_DELIVERY" to "Out for Delivery",
    "DELIVERED" to "Delivered",
    "CANCELLED" to "Cancelled",
    "REJECTED" to "Rejected"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    state: OrdersUiState,
    onFilterChange: (String) -> Unit,
    onSelectOrder: (Order?) -> Unit,
    onUpdateOrderStatus: (orderId: String, fromStatus: String, newStatus: String, onComplete: (Boolean, String?) -> Unit) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var orderToCancel by remember { mutableStateOf<Order?>(null) }
    var localSearchText by remember { mutableStateOf(state.searchQuery) }

    // Rule 5: Debounce search inputs by 400ms
    LaunchedEffect(localSearchText) {
        if (localSearchText != state.searchQuery) {
            delay(400)
            onSearchQueryChange(localSearchText)
        }
    }

    val filteredOrders = remember(state.orders, state.activeFilter, state.searchQuery) {
        val visibleOrders = state.orders.filter { it.status?.lowercase() != "pending" }
        val byFilter = if (state.activeFilter == "ALL") visibleOrders
        else visibleOrders.filter { it.status.equals(state.activeFilter, ignoreCase = true) }

        if (state.searchQuery.isBlank()) {
            byFilter
        } else {
            val q = state.searchQuery.trim().lowercase()
            byFilter.filter { order ->
                (order.orderNumber?.lowercase()?.contains(q) == true) ||
                order.id.lowercase().contains(q) ||
                (order.customer?.full_name?.lowercase()?.contains(q) == true) ||
                (order.customerName?.lowercase()?.contains(q) == true) ||
                (order.customer?.phone?.contains(q) == true) ||
                (order.customerPhone?.contains(q) == true) ||
                (order.address?.address_line?.lowercase()?.contains(q) == true) ||
                (order.deliveryAddress?.lowercase()?.contains(q) == true)
            }
        }
    }

    // "New" = confirmed orders still waiting for the kitchen; shown first under their own header
    val newOrders = remember(filteredOrders) { filteredOrders.filter { it.status?.lowercase() == "confirmed" } }
    val otherOrders = remember(filteredOrders) { filteredOrders.filterNot { it.status?.lowercase() == "confirmed" } }

    // Refreshes the "x min ago" label on new orders
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMillis = System.currentTimeMillis()
        }
    }

    val listState = rememberLazyListState()
    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 2
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && state.canLoadMore && !state.isLoadingMore && !state.isLoading) {
            onLoadMore()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PolishBackgroundLight)
    ) {
        // Search Input (Rule 5: Debounced 400ms)
        Surface(
            color = PolishSurfaceLight,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = localSearchText,
                onValueChange = { localSearchText = it },
                placeholder = { Text("Search by order #, customer, phone...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (localSearchText.isNotEmpty()) {
                        IconButton(onClick = { localSearchText = "" }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = Slate400, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SprucePrimary,
                    unfocusedBorderColor = PolishBorderLight,
                    focusedContainerColor = PolishBackgroundLight,
                    unfocusedContainerColor = PolishBackgroundLight
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Filter Chips Row
        Surface(
            color = PolishSurfaceLight,
            border = BorderStroke(1.dp, PolishBorderLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(ORDER_STATUS_FILTERS) { (filterKey, label) ->
                    val isSelected = state.activeFilter.equals(filterKey, ignoreCase = true)
                    val visibleOrders = state.orders.filter { it.status?.lowercase() != "pending" }
                    val count = if (filterKey == "ALL") visibleOrders.size
                    else visibleOrders.count { it.status.equals(filterKey, ignoreCase = true) }

                    FilterChip(
                        selected = isSelected,
                        onClick = { onFilterChange(filterKey) },
                        label = {
                            Text(
                                text = "$label ($count)",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (filterKey == "CONFIRMED") SprucePrimary else SpruceContainer,
                            selectedLabelColor = if (filterKey == "CONFIRMED") Color.White else SpruceOnContainer,
                            containerColor = PolishBackgroundLight,
                            labelColor = Slate700
                        ),
                        shape = RoundedCornerShape(50)
                    )
                }
            }
        }

        // Orders List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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

            // Rule 7: Show skeleton loaders while initial data is fetching
            if (state.isLoading && state.orders.isEmpty()) {
                items(4) {
                    OrderCardSkeleton()
                }
            } else if (filteredOrders.isEmpty()) {
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
                                .padding(40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (!state.errorMessage.isNullOrBlank()) Icons.Default.Warning else Icons.Outlined.ReceiptLong,
                                contentDescription = null,
                                tint = if (!state.errorMessage.isNullOrBlank()) PolishRose else Slate400,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (!state.errorMessage.isNullOrBlank()) "Unable to load orders"
                                else "No ${if (state.activeFilter != "ALL") state.activeFilter.lowercase() else ""} orders found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                            Text(
                                text = if (!state.errorMessage.isNullOrBlank()) state.errorMessage
                                else "Orders placed by customers will appear here in real time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate500,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            if (!state.errorMessage.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = onRefresh,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PolishRose)
                                ) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry Loading Orders", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                val renderOrder: @Composable (Order) -> Unit = { order ->
                    val isConfirmed = order.status?.lowercase() == "confirmed"
                    val isPreparing = order.status?.lowercase() == "preparing"

                    Surface(
                        onClick = { onSelectOrder(order) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = if (isConfirmed) PolishAmberLight else PolishSurfaceLight,
                        border = if (isConfirmed) BorderStroke(2.dp, PolishAmber) else BorderStroke(1.dp, PolishBorderLight),
                        shadowElevation = if (isConfirmed) 4.dp else 0.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Top Row: Order # + Status
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "#${order.orderNumber ?: order.id.take(6).uppercase()}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate900
                                    )
                                    if (isConfirmed) {
                                        Surface(
                                            color = PolishAmberDark,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "NEW",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        val age = OrderAge.label(OrderAge.minutesSince(order.createdAt, nowMillis))
                                        if (age.isNotEmpty()) {
                                            Text(
                                                text = age,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = PolishAmberDark
                                            )
                                        }
                                    }
                                }

                                StatusBadge(status = order.status ?: "pending")
                            }

                            val foodSubtotal = order.subtotal
                                ?: order.orderItems?.sumOf { it.totalPrice.takeIf { p -> p > 0 } ?: (it.unitPrice * (it.quantity ?: 1.0)) }
                                ?: order.totalAmount
                            val hasFees = (order.deliveryFee ?: 0.0) > 0 || (order.handlingFee ?: 0.0) > 0 || foodSubtotal < order.totalAmount

                            // Customer & Price
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    val customerDisplayName = order.customer?.full_name?.takeIf { it.isNotBlank() }
                                        ?: order.customer?.fullName?.takeIf { it.isNotBlank() } 
                                        ?: order.customerName 
                                        ?: "Guest Customer"
                                    Text(
                                        text = customerDisplayName,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Slate900
                                    )
                                    Text(
                                        text = "${order.orderItems?.size ?: 0} items ordered",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Slate500
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = CurrencyFormatter.format(foodSubtotal),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PolishEmeraldDark
                                    )
                                    Text(
                                        text = if (hasFees) "Food (Total ${CurrencyFormatter.format(order.totalAmount)})" else "Food Subtotal",
                                        fontSize = 10.5.sp,
                                        color = Slate500
                                    )
                                }
                            }

                            // If order is delivered, show credit banner
                            if (order.status?.lowercase() == "delivered") {
                                Surface(
                                    color = PolishEmeraldLight.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, PolishEmeraldDark.copy(alpha = 0.2f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = PolishEmeraldDark,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "Credited to wallet: ${CurrencyFormatter.format(foodSubtotal)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = PolishEmeraldDark
                                        )
                                    }
                                }
                            }

                            // Order items summary preview
                            val summaryItems = order.orderItems
                            if (!summaryItems.isNullOrEmpty()) {
                                Surface(
                                    color = PolishBackgroundLight,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        summaryItems.take(3).forEach { item ->
                                            Text(
                                                text = "• ${item.intQuantity}x ${item.displayName}",
                                                fontSize = 12.sp,
                                                color = Slate700
                                            )
                                        }
                                        if (summaryItems.size > 3) {
                                            Text(
                                                text = "+ ${summaryItems.size - 3} more items...",
                                                fontSize = 11.sp,
                                                color = SprucePrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            // Quick Action Buttons based on Vendor Workflow:
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when (order.status?.lowercase()) {
                                    "confirmed" -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = { orderToCancel = order },
                                                enabled = !state.isUpdating,
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PolishRose),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.weight(0.7f)
                                            ) {
                                                Text("Cancel")
                                            }
                                            Button(
                                                onClick = {
                                                    onUpdateOrderStatus(order.id, "confirmed", "preparing") { success, err ->
                                                        if (!success) {
                                                            Toast.makeText(context, err ?: "Could not start preparing. Please try again.", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                },
                                                enabled = !state.isUpdating,
                                                colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.weight(1.3f)
                                            ) {
                                                Icon(imageVector = Icons.Default.Kitchen, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Start Preparing", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    "preparing" -> {
                                        Surface(
                                            color = SpruceContainer,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeliveryDining,
                                                    contentDescription = null,
                                                    tint = SprucePrimary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Assigning delivery partner...",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = SpruceOnContainer
                                                )
                                            }
                                        }
                                    }
                                    "ready", "out_for_delivery" -> {
                                        Surface(
                                            color = PolishPurpleLight,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeliveryDining,
                                                    contentDescription = null,
                                                    tint = PolishPurple,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (order.status?.lowercase() == "out_for_delivery") "Out for delivery" else "Delivery partner assigned",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = PolishPurple
                                                )
                                            }
                                        }
                                    }
                                    "delivered" -> {
                                        Text(
                                            text = "✓ Order completed & delivered",
                                            fontSize = 12.sp,
                                            color = PolishEmeraldDark,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    "cancelled" -> {
                                        Text(
                                            text = "✕ Order cancelled",
                                            fontSize = 12.sp,
                                            color = PolishRose
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (newOrders.isNotEmpty() && otherOrders.isNotEmpty()) {
                    item(key = "header_new") { OrderSectionHeader("New orders", newOrders.size, isNew = true) }
                    items(newOrders, key = { it.id }) { renderOrder(it) }
                    item(key = "header_earlier") { OrderSectionHeader("Earlier orders", otherOrders.size, isNew = false) }
                    items(otherOrders, key = { it.id }) { renderOrder(it) }
                } else {
                    if (newOrders.isNotEmpty() && state.activeFilter == "ALL") {
                        item(key = "header_new") { OrderSectionHeader("New orders", newOrders.size, isNew = true) }
                    }
                    items(filteredOrders, key = { it.id }) { renderOrder(it) }
                }
            }

            // Pagination footer (Rule 1)
            if (state.isLoadingMore) {
                item {
                    OrderCardSkeleton()
                }
            } else if (state.canLoadMore && filteredOrders.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        OutlinedButton(
                            onClick = onLoadMore,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SprucePrimary.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SprucePrimary)
                        ) {
                            Icon(imageVector = Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Load More Orders (20 more)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // --- Order Detail Sheet / Dialog ---
    if (state.selectedOrder != null) {
        val order = state.selectedOrder
        AlertDialog(
            onDismissRequest = { onSelectOrder(null) },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Order #${order.orderNumber ?: order.id.take(6).uppercase()}",
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                    StatusBadge(status = order.status ?: "pending")
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Customer info card
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = PolishBackgroundLight,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, PolishBorderLight)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Customer Details", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate900)
                                val detailCustName = order.customer?.full_name?.takeIf { it.isNotBlank() }
                                    ?: order.customer?.fullName?.takeIf { it.isNotBlank() }
                                    ?: order.customerName
                                    ?: "Guest Customer"
                                Text("Name: $detailCustName", color = Slate700)
                                val detailCustPhone = order.customer?.phone?.takeIf { it.isNotBlank() }
                                    ?: order.customerPhone
                                if (!detailCustPhone.isNullOrBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Phone: $detailCustPhone", color = Slate700)
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    try {
                                                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$detailCustPhone")))
                                                    } catch (_: Exception) {
                                                        Toast.makeText(context, "No phone app found", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.Phone, contentDescription = "Call", tint = PolishEmeraldDark)
                                            }
                                            IconButton(
                                                onClick = {
                                                    try {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${detailCustPhone.replace("+", "")}")))
                                                    } catch (_: Exception) {
                                                        Toast.makeText(context, "WhatsApp is not available", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.Chat, contentDescription = "WhatsApp", tint = SprucePrimary)
                                            }
                                        }
                                    }
                                }
                                val displayAddress = order.address?.address_line?.let { line ->
                                    val lmk = order.address.landmark?.takeIf { it.isNotBlank() }
                                    if (lmk != null) "$line (Near $lmk)" else line
                                } ?: order.address?.addressLine?.let { line ->
                                    val lmk = order.address.landmark?.takeIf { it.isNotBlank() }
                                    if (lmk != null) "$line (Near $lmk)" else line
                                } ?: order.deliveryAddress
                                if (!displayAddress.isNullOrBlank()) {
                                    Text("Delivery Address: $displayAddress", fontSize = 12.sp, color = Slate500)
                                }
                                val payStatus = order.paymentStatus
                                if (!payStatus.isNullOrBlank()) {
                                    Text("Payment: ${payStatus.uppercase()} (${order.paymentMethod ?: "Online"})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
                                }
                            }
                        }
                    }

                    // Cooking instructions from the customer (for the kitchen)
                    order.cookingInstructions?.let { ci ->
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = PolishAmberLight,
                                border = BorderStroke(1.dp, PolishAmber)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("🍳 Cooking Instructions", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PolishAmberDark)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(ci, fontSize = 13.sp, color = Slate700)
                                }
                            }
                        }
                    }

                    // Order Items
                    item {
                        Text("Ordered Items", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate900)
                    }

                    val detailItems = order.orderItems
                    if (detailItems.isNullOrEmpty()) {
                        item {
                            Text("No item details available", color = Slate500, fontSize = 12.sp)
                        }
                    } else {
                        items(detailItems) { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${item.intQuantity}x ${item.displayName}",
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.Medium,
                                    color = Slate900
                                )
                                val itemTotal = item.totalPrice.takeIf { it > 0 } ?: (item.unitPrice * (item.quantity ?: 1.0))
                                Text(
                                    text = CurrencyFormatter.format(itemTotal),
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                            }
                        }
                    }

                    // Financial Breakdown
                    val detailFoodSubtotal = order.subtotal
                        ?: detailItems?.sumOf { it.totalPrice.takeIf { p -> p > 0 } ?: (it.unitPrice * (it.quantity ?: 1.0)) }
                        ?: order.totalAmount
                    val deliveryFee = order.deliveryFee ?: 0.0
                    val handlingFee = order.handlingFee ?: 0.0

                    item {
                        HorizontalDivider(color = PolishBorderLight)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Food Subtotal (Hotel):", fontSize = 13.sp, color = Slate700, fontWeight = FontWeight.SemiBold)
                                Text(CurrencyFormatter.format(detailFoodSubtotal), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = PolishEmeraldDark)
                            }
                            if (deliveryFee > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Delivery Fee (Partner):", fontSize = 12.sp, color = Slate500)
                                    Text(CurrencyFormatter.format(deliveryFee), fontSize = 12.sp, color = Slate600)
                                }
                            }
                            if (handlingFee > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Handling / Platform Fee:", fontSize = 12.sp, color = Slate500)
                                    Text(CurrencyFormatter.format(handlingFee), fontSize = 12.sp, color = Slate600)
                                }
                            }
                            HorizontalDivider(color = PolishBorderLight.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Paid by Customer:", fontWeight = FontWeight.Bold, color = Slate900)
                                Text(CurrencyFormatter.format(order.totalAmount), fontWeight = FontWeight.Bold, color = Slate900, fontSize = 15.sp)
                            }

                            // Notice for Hotel earnings
                            if (order.status?.lowercase() == "delivered") {
                                Surface(
                                    color = PolishEmeraldLight.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, PolishEmeraldDark.copy(alpha = 0.25f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = PolishEmeraldDark,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Credited to wallet: ${CurrencyFormatter.format(detailFoodSubtotal)}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp,
                                                color = PolishEmeraldDark
                                            )
                                            Text(
                                                text = "Food amount credited to hotel wallet upon delivery.",
                                                fontSize = 11.sp,
                                                color = PolishEmeraldDark.copy(alpha = 0.85f)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    color = SpruceContainerLight.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = SprucePrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Hotel Earning: ${CurrencyFormatter.format(detailFoodSubtotal)} will be credited to wallet when delivered.",
                                            fontSize = 11.5.sp,
                                            color = SpruceOnContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Order Status Timeline
                    if (state.isLoadingHistory) {
                        item {
                            HorizontalDivider(color = PolishBorderLight)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = SprucePrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Loading timeline...", fontSize = 12.sp, color = Slate500)
                            }
                        }
                    } else if (state.selectedOrderHistory.isNotEmpty()) {
                        item {
                            HorizontalDivider(color = PolishBorderLight)
                            Text("Status Timeline", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate900)
                        }
                        items(state.selectedOrderHistory) { historyItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(SprucePrimary, CircleShape)
                                    )
                                    Text(
                                        text = (historyItem.status ?: "Updated").replace("_", " ").replaceFirstChar { it.uppercase() },
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = Slate700
                                    )
                                }
                                val formattedTime = historyItem.createdAt?.let { formatStatusTime(it) } ?: ""
                                if (formattedTime.isNotBlank()) {
                                    Text(text = formattedTime, fontSize = 11.sp, color = Slate500)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onSelectOrder(null) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // --- Cancel Order Confirmation ---
    orderToCancel?.let { targetOrder ->
        AlertDialog(
            onDismissRequest = { if (!state.isUpdating) orderToCancel = null },
            title = {
                Text("Cancel Order #${targetOrder.orderNumber ?: targetOrder.id.take(6).uppercase()}?", fontWeight = FontWeight.Bold, color = Slate900)
            },
            text = {
                Text(
                    text = "The customer will be informed that this order was cancelled. This cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateOrderStatus(targetOrder.id, "confirmed", "cancelled") { success, err ->
                            orderToCancel = null
                            if (!success) {
                                Toast.makeText(context, err ?: "Could not cancel the order. Please try again.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !state.isUpdating,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PolishRose)
                ) {
                    Text("Yes, Cancel Order", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { orderToCancel = null },
                    enabled = !state.isUpdating,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Keep Order")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun OrderSectionHeader(title: String, count: Int, isNew: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isNew) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(PolishAmber)
            )
        }
        Text(
            text = title.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isNew) PolishAmberDark else Slate500,
            letterSpacing = 0.8.sp
        )
        Surface(
            color = if (isNew) PolishAmber else Slate200,
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = count.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isNew) Color.White else Slate700,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
            )
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = if (isNew) PolishAmber.copy(alpha = 0.4f) else PolishBorderLight
        )
    }
}

private fun formatStatusTime(isoString: String): String {
    return try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val cleanIso = isoString.take(19)
        val date = parser.parse(cleanIso) ?: return ""
        val formatter = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
        formatter.format(date)
    } catch (_: Exception) {
        ""
    }
}


