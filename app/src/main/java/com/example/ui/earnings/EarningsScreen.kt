package com.example.ui.earnings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.example.data.model.Payout
import com.example.data.model.WalletTransaction
import com.example.data.model.WalletTransactionKind
import com.example.data.model.Withdrawal
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.StatusBadge
import com.example.ui.components.TransactionCardSkeleton
import com.example.ui.theme.*
import com.example.ui.viewmodel.EarningsUiState
import com.example.ui.viewmodel.NO_PAYOUT_ACCOUNT_MESSAGE
import com.example.util.CurrencyFormatter
import java.math.BigDecimal

enum class EarningsSubTab(val title: String) {
    WITHDRAWALS("Withdrawals"),
    TRANSACTIONS("Wallet Log"),
    PAYOUTS("Payouts")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EarningsScreen(
    state: EarningsUiState,
    onRequestWithdrawal: (amount: Double, onComplete: (Boolean, String?) -> Unit) -> Unit,
    onSaveBankDetails: (
        payoutMethod: String,
        accountHolderName: String,
        accountNumber: String?,
        ifscCode: String?,
        bankName: String?,
        upiId: String?,
        onComplete: (Boolean, String?) -> Unit
    ) -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreTransactions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeSubTab by remember { mutableStateOf(EarningsSubTab.WITHDRAWALS) }
    var showWithdrawalDialog by remember { mutableStateOf(false) }
    var showBankDetailsDialog by remember { mutableStateOf(false) }
    var isHowEarningsWorkExpanded by remember { mutableStateOf(false) }

    // One scrolling list for the whole page, so the history below is always reachable
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PolishBackgroundLight),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            // Global Error / Info Banner if present
            if (!state.errorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PolishRoseLight,
                    border = BorderStroke(1.dp, PolishRose.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = PolishRose,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = state.errorMessage ?: "",
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
            } else if (!state.successMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PolishEmeraldLight,
                    border = BorderStroke(1.dp, PolishEmeraldDark.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = PolishEmeraldDark,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = state.successMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = PolishEmeraldDark,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            // Balance & Overview Section (Spruce Polish)
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
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                text = "Total Wallet Balance",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.82f)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (state.hasValidWalletBalance || !state.isWalletBalanceError) {
                                    CurrencyFormatter.format(state.runningBalance)
                                } else {
                                    "--"
                                },
                                fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            if (state.isWalletBalanceError) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Surface(
                                    color = Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onRefresh() }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Retry",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = if (state.hasValidWalletBalance) "Cached balance (update failed) • Tap to retry" else "Balance unavailable • Tap to retry",
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "Food subtotal earned from delivered orders",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(46.dp)
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

                    // Metric Cards: In Progress & Available to Withdraw
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // In Progress Card
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = PolishAmberLight,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "In Progress",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = CurrencyFormatter.format(state.inProgressWithdrawalsSum),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (state.activeWithdrawalInProgress != null) "Under admin review" else "None pending",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Available to Withdraw Card
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = PolishEmeraldLight,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Available to Withdraw",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = CurrencyFormatter.format(state.availableToWithdraw),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PolishEmeraldLight
                                )
                                Text(
                                    text = "Commission: ${state.commissionPercent.toInt()}% on payout",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Request Withdrawal Button or In-Progress Indicator
                    if (state.activeWithdrawalInProgress != null) {
                        Button(
                            onClick = { },
                            enabled = false,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = PolishAmberLight,
                                disabledContentColor = PolishAmberDark
                            ),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "A request is already in progress",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    } else {
                        Button(
                            onClick = { showWithdrawalDialog = true },
                            shape = RoundedCornerShape(14.dp),
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
            }
        }

        item {
            // Where withdrawals are paid (bank account or UPI)
            PayoutAccountCard(
                bankDetails = state.bankDetails,
                isLoaded = state.isBankDetailsLoaded,
                onEdit = { showBankDetailsDialog = true },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            )
        }

        item {
            // Expandable In-App Info Card: How your earnings work (Rule 3)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                shape = RoundedCornerShape(16.dp),
                color = SpruceContainerLight.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, SpruceBorderLight.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = SprucePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "How your earnings work",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = SpruceOnContainer
                            )
                        }
                        IconButton(
                            onClick = { isHowEarningsWorkExpanded = !isHowEarningsWorkExpanded },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isHowEarningsWorkExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isHowEarningsWorkExpanded) "Collapse" else "Expand",
                                tint = SprucePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (isHowEarningsWorkExpanded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = SpruceBorderLight.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "• When an order is delivered, the food subtotal is added to your wallet balance.",
                                fontSize = 12.sp,
                                color = Slate700,
                                lineHeight = 16.sp
                            )
                            Text(
                                text = "• You can request a withdrawal at any time up to your available balance.",
                                fontSize = 12.sp,
                                color = Slate700,
                                lineHeight = 16.sp
                            )
                            Text(
                                text = "• Commission (${state.commissionPercent.toInt()}%) is deducted when your withdrawal is paid out to your payout account (bank or UPI).",
                                fontSize = 12.sp,
                                color = Slate700,
                                lineHeight = 16.sp
                            )
                            Text(
                                text = "• For questions or faster payouts, contact your City Admin.",
                                fontSize = 12.sp,
                                color = Slate700,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // Tabs stay pinned to the top while the list scrolls
        stickyHeader {
            TabRow(
                selectedTabIndex = activeSubTab.ordinal,
                containerColor = PolishSurfaceLight,
                contentColor = SprucePrimary
            ) {
                EarningsSubTab.values().forEach { tab ->
                    val count = when (tab) {
                        EarningsSubTab.WITHDRAWALS -> state.withdrawals.size
                        EarningsSubTab.TRANSACTIONS -> state.walletTransactions.size
                        EarningsSubTab.PAYOUTS -> state.payouts.size
                    }
                    Tab(
                        selected = activeSubTab == tab,
                        onClick = { activeSubTab = tab },
                        text = {
                            Text(
                                text = if (count > 0) "${tab.title} ($count)" else tab.title,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (activeSubTab == tab) FontWeight.Bold else FontWeight.Medium,
                                color = if (activeSubTab == tab) SprucePrimary else Slate500
                            )
                        }
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(10.dp)) }

        // Subtab content
        when (activeSubTab) {
            EarningsSubTab.TRANSACTIONS -> {
                if (state.isLoading && state.walletTransactions.isEmpty()) {
                    items(4) {
                        TabListItem {
                            TransactionCardSkeleton()
                        }
                    }
                } else if (state.walletTransactions.isEmpty()) {
                    item {
                        TabListItem {
                            EmptyStateCard(
                                icon = Icons.Outlined.Receipt,
                                title = "No wallet transactions yet",
                                description = "Earnings from completed orders and settlement logs will appear here."
                            )
                        }
                    }
                } else {
                    items(state.walletTransactions, key = { it.id }) { tx ->
                        TabListItem {
                            val kind = tx.kind
                            val amountBd = CurrencyFormatter.toBigDecimal(tx.amount) ?: BigDecimal.ZERO
                            val isNegative = tx.amount < 0.0

                            val badgeBg = when (kind) {
                                WalletTransactionKind.CREDIT -> SpruceContainerLight
                                WalletTransactionKind.DEBIT -> PolishRoseLight
                                WalletTransactionKind.NEUTRAL -> if (isNegative) PolishRoseLight else PolishSurfaceLight
                            }
                            val iconTint = when (kind) {
                                WalletTransactionKind.CREDIT -> SprucePrimary
                                WalletTransactionKind.DEBIT -> PolishRose
                                WalletTransactionKind.NEUTRAL -> if (isNegative) PolishRose else Slate600
                            }
                            val iconVec = when (kind) {
                                WalletTransactionKind.CREDIT -> Icons.Default.ArrowDownward
                                WalletTransactionKind.DEBIT -> Icons.Default.ArrowUpward
                                WalletTransactionKind.NEUTRAL -> if (isNegative) Icons.Default.ArrowUpward else Icons.Default.SwapHoriz
                            }
                            val amountStr = when (kind) {
                                WalletTransactionKind.CREDIT -> "+${CurrencyFormatter.format(amountBd)}"
                                WalletTransactionKind.DEBIT -> "-${CurrencyFormatter.format(amountBd.abs())}"
                                WalletTransactionKind.NEUTRAL -> if (isNegative) "-${CurrencyFormatter.format(amountBd.abs())}" else CurrencyFormatter.format(amountBd)
                            }
                            val amountColor = when (kind) {
                                WalletTransactionKind.CREDIT -> PolishEmeraldDark
                                WalletTransactionKind.DEBIT -> PolishRose
                                WalletTransactionKind.NEUTRAL -> if (isNegative) PolishRose else Slate800
                            }
                            val defaultDesc = when (kind) {
                                WalletTransactionKind.CREDIT -> "Order Earning"
                                WalletTransactionKind.DEBIT -> if (tx.type.equals("withdrawal", ignoreCase = true)) "Withdrawal Payout" else if (tx.type.equals("penalty", ignoreCase = true)) "Penalty Deduction" else "Debit Transaction"
                                WalletTransactionKind.NEUTRAL -> tx.type.replace('_', ' ').replaceFirstChar { it.uppercase() }
                            }

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
                                                .background(badgeBg),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = iconVec,
                                                contentDescription = null,
                                                tint = iconTint,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = tx.description ?: defaultDesc,
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
                                        text = amountStr,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = amountColor
                                    )
                                }
                            }
                        }
                    }

                    // Rule 1: Pagination footer (20 transactions at a time)
                    if (state.isLoadingMoreTransactions) {
                        item {
                            TabListItem {
                                TransactionCardSkeleton()
                            }
                        }
                    } else if (state.canLoadMoreTransactions) {
                        item {
                            TabListItem {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    OutlinedButton(
                                        onClick = onLoadMoreTransactions,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, SprucePrimary.copy(alpha = 0.5f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SprucePrimary)
                                    ) {
                                        Icon(imageVector = Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Load More Transactions (20 more)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            
            }

            EarningsSubTab.WITHDRAWALS -> {
                if (state.withdrawals.isEmpty()) {
                    item {
                        TabListItem {
                            EmptyStateCard(
                                icon = Icons.Outlined.Payments,
                                title = "No withdrawal requests",
                                description = "Request payouts anytime to transfer available balance to your bank."
                            )
                        }
                    }
                } else {
                    items(state.withdrawals, key = { it.id }) { withdrawal ->
                        TabListItem {
                            val commRate = state.commissionPercent
                            val commAmount = withdrawal.commissionAmount ?: (withdrawal.amount * commRate / 100.0)
                            val netPayable = withdrawal.netPayableAmount ?: (withdrawal.amount - commAmount)

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
                                            text = CurrencyFormatter.format(withdrawal.amount),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = Slate900
                                        )
                                        WithdrawalStatusBadge(status = withdrawal.status)
                                    }

                                    when (withdrawal.status.lowercase()) {
                                        "pending", "approved" -> {
                                            Surface(
                                                color = PolishAmberLight.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, PolishAmberDark.copy(alpha = 0.2f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Info,
                                                        contentDescription = null,
                                                        tint = PolishAmberDark,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Text(
                                                        text = "Under review by City Admin",
                                                        fontSize = 12.sp,
                                                        color = PolishAmberDark,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Est. bank payout (${commRate.toInt()}% comm.):",
                                                    fontSize = 12.sp,
                                                    color = Slate500
                                                )
                                                Text(
                                                    text = CurrencyFormatter.format(netPayable),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Slate800
                                                )
                                            }
                                        }

                                        "paid" -> {
                                            Surface(
                                                color = PolishEmeraldLight.copy(alpha = 0.4f),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text("Requested:", fontSize = 11.5.sp, color = Slate600)
                                                        Text(CurrencyFormatter.format(withdrawal.amount), fontSize = 11.5.sp, color = Slate700)
                                                    }
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text("Commission (${commRate.toInt()}%):", fontSize = 11.5.sp, color = PolishRoseDark)
                                                        Text("-${CurrencyFormatter.format(commAmount)}", fontSize = 11.5.sp, color = PolishRoseDark, fontWeight = FontWeight.SemiBold)
                                                    }
                                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = PolishEmeraldDark.copy(alpha = 0.2f))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text("Credited to Bank:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PolishEmeraldDark)
                                                        Text(CurrencyFormatter.format(netPayable), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = PolishEmeraldDark)
                                                    }
                                                }
                                            }

                                            if (!withdrawal.paidAt.isNullOrBlank()) {
                                                Text(
                                                    text = "Paid date: ${withdrawal.paidAt.take(10)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = PolishEmeraldDark,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }

                                        "rejected" -> {
                                            val reason = withdrawal.rejectionReason?.takeIf { it.isNotBlank() }
                                                ?: withdrawal.note?.takeIf { it.isNotBlank() }
                                            Surface(
                                                color = PolishRoseLight,
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, PolishRose.copy(alpha = 0.3f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ErrorOutline,
                                                        contentDescription = null,
                                                        tint = PolishRose,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Text(
                                                        text = if (reason != null) "Rejected: $reason" else "Request was rejected by City Admin",
                                                        fontSize = 12.sp,
                                                        color = PolishRoseDark,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    withdrawal.payoutDetails?.let { payout ->
                                        Text(
                                            text = "To: ${payout.summary}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Slate600
                                        )
                                    }

                                    if (!withdrawal.note.isNullOrBlank() && withdrawal.status.lowercase() != "rejected") {
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
                if (state.payouts.isEmpty()) {
                    item {
                        TabListItem {
                            EmptyStateCard(
                                icon = Icons.Outlined.AccountBalance,
                                title = "No settled payouts yet",
                                description = "Admin processed payout batches will be recorded here."
                            )
                        }
                    }
                } else {
                    items(state.payouts, key = { it.id }) { payout ->
                        TabListItem {
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
                                            text = "Net Payout: ${CurrencyFormatter.format(payout.netAmount)}",
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
                                            text = "Gross: ${CurrencyFormatter.format(payout.grossAmount)}",
                                            fontSize = 12.sp,
                                            color = Slate500
                                        )
                                        Text(
                                            text = "Commission: -${CurrencyFormatter.format(payout.commissionAmount)}",
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
        val context = LocalContext.current
        var amountStr by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        var showConfirmSubmit by remember { mutableStateOf(false) }
        var isSubmittingLocally by remember { mutableStateOf(false) }

        val hasActiveRequest = state.activeWithdrawalInProgress != null
        val payoutAccount = state.bankDetails
        val needsPayoutAccount = state.isBankDetailsLoaded && payoutAccount == null
        val available = state.availableToWithdraw
        val commPercent = state.commissionPercent
        val parsedAmount = amountStr.toDoubleOrNull()
        val exceedsAvailable = parsedAmount != null && parsedAmount > available
        val isInvalidAmount = parsedAmount != null && parsedAmount <= 0.0

        if (showConfirmSubmit) {
            val amount = parsedAmount ?: 0.0
            val estCommission = amount * (commPercent / 100.0)
            val estNet = amount - estCommission

            ConfirmationDialog(
                title = "Confirm Withdrawal",
                message = "Are you sure you want to request a payout of ${CurrencyFormatter.format(amount)} to your payout account?\n\n" +
                        "• Requested: ${CurrencyFormatter.format(amount)}\n" +
                        "• Commission (${commPercent.toInt()}%): -${CurrencyFormatter.format(estCommission)}\n" +
                        "• You will receive: ${CurrencyFormatter.format(estNet)}" +
                        (payoutAccount?.let { "\n• Paid to: ${it.summary}" } ?: ""),
                confirmText = "Submit Request",
                isLoading = state.isSubmittingWithdrawal || isSubmittingLocally,
                onConfirm = {
                    isSubmittingLocally = true
                    onRequestWithdrawal(amount) { success, err ->
                        isSubmittingLocally = false
                        if (success) {
                            showConfirmSubmit = false
                            showWithdrawalDialog = false
                            Toast.makeText(context, "Withdrawal request submitted successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            showConfirmSubmit = false
                            errorMsg = err
                            Toast.makeText(context, err ?: "Withdrawal request failed", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onDismiss = {
                    if (!state.isSubmittingWithdrawal && !isSubmittingLocally) showConfirmSubmit = false
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = {
                    if (!state.isSubmittingWithdrawal && !isSubmittingLocally) showWithdrawalDialog = false
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = SprucePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text("Request Withdrawal", fontWeight = FontWeight.Bold, color = Slate900)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Available Wallet Balance Card (Clearly shown above amount input)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = PolishEmeraldLight.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, PolishEmeraldDark.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(PolishEmeraldDark.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccountBalanceWallet,
                                                contentDescription = null,
                                                tint = PolishEmeraldDark,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "Available to Withdraw",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Slate600
                                            )
                                            Text(
                                                text = CurrencyFormatter.format(available),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = PolishEmeraldDark
                                            )
                                        }
                                    }

                                    if (available > 0 && !hasActiveRequest) {
                                        FilledTonalButton(
                                            onClick = {
                                                amountStr = if (available % 1.0 == 0.0) {
                                                    available.toLong().toString()
                                                } else {
                                                    CurrencyFormatter.toBigDecimal(available)?.toPlainString() ?: available.toString()
                                                }
                                                errorMsg = null
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = PolishEmeraldDark.copy(alpha = 0.15f),
                                                contentColor = PolishEmeraldDark
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Withdraw all", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Total balance: ${CurrencyFormatter.format(state.runningBalance)}",
                                        fontSize = 11.sp,
                                        color = Slate500
                                    )
                                    Text(
                                        text = "Commission: ${commPercent.toInt()}%",
                                        fontSize = 11.sp,
                                        color = Slate600,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Check if an existing withdrawal is in progress (Rule 2)
                        if (hasActiveRequest) {
                            val activeReq = state.activeWithdrawalInProgress!!
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = PolishAmberLight,
                                border = BorderStroke(1.dp, PolishAmberDark.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = PolishAmberDark,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Request already in progress",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = PolishAmberDark
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "You already have a withdrawal request of ${CurrencyFormatter.format(activeReq.amount)} in progress. Please wait for City Admin to process it before requesting another.",
                                            fontSize = 12.sp,
                                            color = PolishAmberDark,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Payout destination (required by the server for every withdrawal)
                        if (!hasActiveRequest) {
                            if (needsPayoutAccount) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = PolishAmberLight,
                                    border = BorderStroke(1.dp, PolishAmberDark.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = NO_PAYOUT_ACCOUNT_MESSAGE,
                                            fontSize = 12.sp,
                                            color = PolishAmberDark,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Button(
                                            onClick = {
                                                showWithdrawalDialog = false
                                                showBankDetailsDialog = true
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Add Bank / UPI", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else if (payoutAccount != null) {
                                Text(
                                    text = "Paid to: ${payoutAccount.summary}" +
                                        if (payoutAccount.isVerified) "" else " (waiting for City Admin verification)",
                                    fontSize = 12.sp,
                                    color = Slate600
                                )
                            }
                        }

                        // 2. Server Error Message Banner (Shows exact Postgres error directly)
                        if (!errorMsg.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = PolishRoseLight,
                                border = BorderStroke(1.dp, PolishRose.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = PolishRose,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Withdrawal Notice",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = PolishRoseDark
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = errorMsg ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PolishRoseDark,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Amount Input (only if no active request)
                        if (!hasActiveRequest) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = amountStr,
                                    onValueChange = {
                                        amountStr = it
                                        errorMsg = null
                                    },
                                    label = { Text("Amount (₹) *") },
                                    placeholder = { Text("0.00") },
                                    leadingIcon = {
                                        Text("₹", fontWeight = FontWeight.Bold, color = Slate600, fontSize = 16.sp)
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    isError = exceedsAvailable || isInvalidAmount,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                // Real-time calculation card (Rule 2)
                                if (parsedAmount != null && parsedAmount > 0) {
                                    val estCommission = parsedAmount * (commPercent / 100.0)
                                    val estNet = (parsedAmount - estCommission).coerceAtLeast(0.0)

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = PolishBackgroundLight,
                                        border = BorderStroke(1.dp, PolishBorderLight),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Requested Amount:", fontSize = 12.sp, color = Slate600)
                                                Text(CurrencyFormatter.format(parsedAmount), fontSize = 12.sp, color = Slate800, fontWeight = FontWeight.SemiBold)
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Commission (${commPercent.toInt()}%):", fontSize = 12.sp, color = PolishRoseDark)
                                                Text("-${CurrencyFormatter.format(estCommission)}", fontSize = 12.sp, color = PolishRoseDark, fontWeight = FontWeight.SemiBold)
                                            }
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = PolishBorderLight)
                                            Text(
                                                text = "You will receive approx ${CurrencyFormatter.format(estNet)} after ${commPercent.toInt()}% commission (final amount is decided when paid).",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PolishEmeraldDark,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }

                                // Client-side inline warning when entered amount exceeds available balance
                                if (exceedsAvailable) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = PolishRoseLight,
                                        border = BorderStroke(1.dp, PolishRose.copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.WarningAmber,
                                                contentDescription = null,
                                                tint = PolishRoseDark,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Withdrawal amount ${CurrencyFormatter.format(parsedAmount ?: 0.0)} exceeds available balance ${CurrencyFormatter.format(available)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 11.5.sp,
                                                color = PolishRoseDark,
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (!hasActiveRequest) {
                        Button(
                            onClick = {
                                val amount = amountStr.toDoubleOrNull()
                                if (amount == null || amount <= 0) {
                                    errorMsg = "Please enter a valid amount greater than 0"
                                    return@Button
                                }
                                if (amount > available) {
                                    errorMsg = "Withdrawal amount ${CurrencyFormatter.format(amount)} exceeds available balance ${CurrencyFormatter.format(available)}"
                                    return@Button
                                }
                                showConfirmSubmit = true
                            },
                            enabled = !state.isSubmittingWithdrawal && !isSubmittingLocally && !needsPayoutAccount &&
                                parsedAmount != null && parsedAmount > 0 && parsedAmount <= available,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary)
                        ) {
                            if (state.isSubmittingWithdrawal || isSubmittingLocally) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Submitting...")
                            } else {
                                Text("Proceed", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showWithdrawalDialog = false },
                        enabled = !state.isSubmittingWithdrawal && !isSubmittingLocally,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (hasActiveRequest) "Close" else "Cancel")
                    }
                },
                shape = RoundedCornerShape(24.dp)
            )
        }
    }

    if (showBankDetailsDialog) {
        BankDetailsDialog(
            existing = state.bankDetails,
            isSaving = state.isSavingBankDetails,
            onSave = onSaveBankDetails,
            onDismiss = { showBankDetailsDialog = false }
        )
    }
}

@Composable
fun WithdrawalStatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, icon, label) = when (status.lowercase()) {
        "pending" -> listOf(PolishAmberLight, PolishAmberDark, Icons.Default.Schedule, "Pending Admin Approval")
        "approved" -> listOf(PolishBlueLight, PolishBlue, Icons.Default.CheckCircle, "Approved - Processing Payment")
        "paid" -> listOf(PolishEmeraldLight, PolishEmeraldDark, Icons.Default.CheckCircle, "Paid to Bank")
        "rejected" -> listOf(PolishRoseLight, PolishRose, Icons.Default.Cancel, "Rejected")
        else -> listOf(Slate100, Slate700, Icons.Default.Info, status.replaceFirstChar { it.uppercase() })
    }

    Surface(
        color = bgColor as Color,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon as androidx.compose.ui.graphics.vector.ImageVector,
                contentDescription = null,
                tint = textColor as Color,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label as String,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
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


/** Padding and spacing for the cards inside the Wallet Log / Withdrawals / Payouts tabs. */
@Composable
private fun TabListItem(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
    ) {
        content()
    }
}
