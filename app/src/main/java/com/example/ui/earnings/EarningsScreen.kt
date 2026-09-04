package com.example.ui.earnings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Payout
import com.example.data.model.WalletTransaction
import com.example.data.model.Withdrawal
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.StatusBadge
import com.example.ui.theme.*
import com.example.ui.viewmodel.EarningsUiState

enum class EarningsSubTab(val title: String) {
    TRANSACTIONS("Wallet Log"),
    WITHDRAWALS("Withdrawals"),
    PAYOUTS("Payouts")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarningsScreen(
    state: EarningsUiState,
    onRequestWithdrawal: (amount: Double, note: String?, onComplete: (Boolean, String?) -> Unit) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSubTab by remember { mutableStateOf(EarningsSubTab.TRANSACTIONS) }
    var showWithdrawalDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PolishBackgroundLight)
    ) {
        // Balance Header Card (Deep Spruce Polish)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = SprucePrimary,
            shadowElevation = 4.dp
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
                            text = "Total Running Balance",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "₹${"%.2f".format(state.runningBalance)}",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Button(
                    onClick = { showWithdrawalDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpruceContainerLight,
                        contentColor = SprucePrimary
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Payments,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "REQUEST WITHDRAWAL",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Sub Tabs
        TabRow(
            selectedTabIndex = activeSubTab.ordinal,
            containerColor = PolishSurfaceLight,
            contentColor = SprucePrimary
        ) {
            EarningsSubTab.values().forEach { tab ->
                Tab(
                    selected = activeSubTab == tab,
                    onClick = { activeSubTab = tab },
                    text = {
                        Text(
                            text = tab.title,
                            fontWeight = if (activeSubTab == tab) FontWeight.Bold else FontWeight.Medium,
                            color = if (activeSubTab == tab) SprucePrimary else Slate500
                        )
                    }
                )
            }
        }

        // Subtab content
        when (activeSubTab) {
            EarningsSubTab.TRANSACTIONS -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.walletTransactions.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Outlined.Receipt,
                                title = "No wallet transactions yet",
                                description = "Earnings from completed orders and settlement logs will appear here."
                            )
                        }
                    } else {
                        items(state.walletTransactions, key = { it.id }) { tx ->
                            val isDebit = tx.type.lowercase() in listOf("debit", "withdrawal")
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
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
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(if (isDebit) PolishRoseLight else SpruceContainerLight),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isDebit) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                                contentDescription = null,
                                                tint = if (isDebit) PolishRose else SprucePrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = tx.description ?: if (isDebit) "Debit Transaction" else "Order Earning",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = Slate900
                                            )
                                            Text(
                                                text = tx.createdAt?.take(10) ?: "Recent",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Slate500
                                            )
                                        }
                                    }

                                    Text(
                                        text = "${if (isDebit) "-" else "+"}₹${"%.2f".format(tx.amount)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (isDebit) PolishRose else PolishEmeraldDark
                                    )
                                }
                            }
                        }
                    }
                }
            }

            EarningsSubTab.WITHDRAWALS -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.withdrawals.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Outlined.Payments,
                                title = "No withdrawal requests",
                                description = "Request payouts anytime to transfer wallet balance to your bank."
                            )
                        }
                    } else {
                        items(state.withdrawals, key = { it.id }) { withdrawal ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                color = PolishSurfaceLight,
                                border = BorderStroke(1.dp, PolishBorderLight)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "₹${"%.2f".format(withdrawal.amount)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = Slate900
                                        )
                                        StatusBadge(status = withdrawal.status)
                                    }

                                    if (!withdrawal.note.isNullOrBlank()) {
                                        Text(
                                            text = "Note: ${withdrawal.note}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Slate500
                                        )
                                    }

                                    Text(
                                        text = "Requested: ${withdrawal.createdAt?.take(10) ?: "Recently"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Slate400
                                    )
                                }
                            }
                        }
                    }
                }
            }

            EarningsSubTab.PAYOUTS -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.payouts.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Outlined.AccountBalance,
                                title = "No settled payouts yet",
                                description = "Admin processed payout batches will be recorded here."
                            )
                        }
                    } else {
                        items(state.payouts, key = { it.id }) { payout ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                color = PolishSurfaceLight,
                                border = BorderStroke(1.dp, PolishBorderLight)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Net Payout: ₹${"%.2f".format(payout.netAmount)}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = PolishEmeraldDark
                                        )
                                        StatusBadge(status = payout.status)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Gross: ₹${"%.2f".format(payout.grossAmount)}",
                                            fontSize = 12.sp,
                                            color = Slate500
                                        )
                                        Text(
                                            text = "Commission: -₹${"%.2f".format(payout.commissionAmount)}",
                                            fontSize = 12.sp,
                                            color = PolishRose
                                        )
                                    }

                                    if (!payout.paidAt.isNullOrBlank()) {
                                        Text(
                                            text = "Paid at: ${payout.paidAt.take(10)}",
                                            fontSize = 11.sp,
                                            color = Slate400
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Request Withdrawal Form Modal with Confirmation ---
    if (showWithdrawalDialog) {
        var amountStr by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        var showConfirmSubmit by remember { mutableStateOf(false) }

        if (showConfirmSubmit) {
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            ConfirmationDialog(
                title = "Confirm Withdrawal",
                message = "Are you sure you want to request a payout of ₹${"%.2f".format(amount)} to your bank account?",
                confirmText = "Submit Request",
                onConfirm = {
                    onRequestWithdrawal(amount, note.ifBlank { null }) { success, err ->
                        if (success) {
                            showConfirmSubmit = false
                            showWithdrawalDialog = false
                        } else {
                            showConfirmSubmit = false
                            errorMsg = err
                        }
                    }
                },
                onDismiss = { showConfirmSubmit = false }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showWithdrawalDialog = false },
                title = {
                    Text("Request Withdrawal", fontWeight = FontWeight.Bold, color = Slate900)
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Available Balance: ₹${"%.2f".format(state.runningBalance)}",
                            fontWeight = FontWeight.SemiBold,
                            color = PolishEmeraldDark
                        )

                        if (errorMsg != null) {
                            Text(text = errorMsg ?: "", color = PolishRose, fontSize = 12.sp)
                        }

                        OutlinedTextField(
                            value = amountStr,
                            onValueChange = { amountStr = it; errorMsg = null },
                            label = { Text("Amount (₹) *") },
                            placeholder = { Text("e.g. 1000") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("Note / Remarks (Optional)") },
                            placeholder = { Text("e.g. Bank transfer request") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            maxLines = 2
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val amount = amountStr.toDoubleOrNull()
                            if (amount == null || amount <= 0) {
                                errorMsg = "Please enter a valid amount greater than 0"
                                return@Button
                            }
                            if (amount > state.runningBalance && state.runningBalance > 0) {
                                errorMsg = "Amount exceeds your available balance (₹${"%.2f".format(state.runningBalance)})"
                                return@Button
                            }
                            showConfirmSubmit = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary)
                    ) {
                        Text("Proceed", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showWithdrawalDialog = false },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                },
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = PolishSurfaceLight,
        border = BorderStroke(1.dp, PolishBorderLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Slate400,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Slate900
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Slate500
            )
        }
    }
}

