package com.example.ui.earnings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VendorBankDetails
import com.example.ui.theme.*
import com.example.util.PayoutValidation

/** Compact card on the Earnings screen showing where withdrawals are paid. */
@Composable
fun PayoutAccountCard(
    bankDetails: VendorBankDetails?,
    isLoaded: Boolean,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val missing = isLoaded && bankDetails == null
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (missing) PolishAmberLight else PolishSurfaceLight,
        border = BorderStroke(1.dp, if (missing) PolishAmberDark.copy(alpha = 0.35f) else PolishBorderLight)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalance,
                contentDescription = null,
                tint = if (missing) PolishAmberDark else SprucePrimary,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Payout Account",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Slate900
                )
                when {
                    !isLoaded -> Text("Loading...", fontSize = 12.sp, color = Slate500)
                    bankDetails == null -> Text(
                        text = "Add your bank account or UPI ID to receive withdrawals",
                        fontSize = 12.sp,
                        color = PolishAmberDark,
                        lineHeight = 16.sp
                    )
                    else -> {
                        Text(
                            text = bankDetails.summary,
                            fontSize = 12.sp,
                            color = Slate700,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        VerificationLabel(isVerified = bankDetails.isVerified)
                    }
                }
            }
            if (isLoaded) {
                TextButton(onClick = onEdit) {
                    Text(if (bankDetails == null) "Add" else "Edit", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun VerificationLabel(isVerified: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = if (isVerified) Icons.Default.Verified else Icons.Default.Schedule,
            contentDescription = null,
            tint = if (isVerified) PolishEmeraldDark else PolishAmberDark,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = if (isVerified) "Verified by City Admin" else "Waiting for City Admin verification",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isVerified) PolishEmeraldDark else PolishAmberDark
        )
    }
}

/** Add / edit form for the payout account. Server messages are shown exactly as returned. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankDetailsDialog(
    existing: VendorBankDetails?,
    isSaving: Boolean,
    onSave: (
        payoutMethod: String,
        accountHolderName: String,
        accountNumber: String?,
        ifscCode: String?,
        bankName: String?,
        upiId: String?,
        onComplete: (Boolean, String?) -> Unit
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var method by remember { mutableStateOf(existing?.payoutMethod?.lowercase()) }
    var holderName by remember { mutableStateOf(existing?.accountHolderName.orEmpty()) }
    // The saved account number is never pre-filled; switching or re-entering is explicit.
    var accountNumber by remember { mutableStateOf("") }
    var confirmAccountNumber by remember { mutableStateOf("") }
    var ifsc by remember { mutableStateOf(existing?.ifscCode.orEmpty()) }
    var bankName by remember { mutableStateOf(existing?.bankName.orEmpty()) }
    var upiId by remember { mutableStateOf(existing?.upiId.orEmpty()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text(
                text = if (existing == null) "Add Payout Account" else "Change Payout Account",
                fontWeight = FontWeight.Bold,
                color = Slate900
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Withdrawals are paid to this account. Any change must be verified again by your City Admin.",
                    fontSize = 12.sp,
                    color = Slate500,
                    lineHeight = 16.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = method == PayoutValidation.METHOD_BANK,
                        onClick = { method = PayoutValidation.METHOD_BANK; errorMsg = null },
                        label = { Text("Bank account") },
                        leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = method == PayoutValidation.METHOD_UPI,
                        onClick = { method = PayoutValidation.METHOD_UPI; errorMsg = null },
                        label = { Text("UPI") },
                        leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }

                if (method != null) {
                    OutlinedTextField(
                        value = holderName,
                        onValueChange = { holderName = it; errorMsg = null },
                        label = { Text("Account holder name *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (method == PayoutValidation.METHOD_BANK) {
                    if (existing != null && !existing.isUpi && !existing.accountNumber.isNullOrBlank()) {
                        Text(
                            text = "Current account: ${existing.summary}. Enter the account number again to save.",
                            fontSize = 11.5.sp,
                            color = Slate500
                        )
                    }
                    OutlinedTextField(
                        value = accountNumber,
                        onValueChange = { v -> accountNumber = v.filter { it.isDigit() }.take(18); errorMsg = null },
                        label = { Text("Account number *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmAccountNumber,
                        onValueChange = { v -> confirmAccountNumber = v.filter { it.isDigit() }.take(18); errorMsg = null },
                        label = { Text("Re-enter account number *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ifsc,
                        onValueChange = { v -> ifsc = v.uppercase().take(11); errorMsg = null },
                        label = { Text("IFSC code *") },
                        placeholder = { Text("SBIN0001234") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it; errorMsg = null },
                        label = { Text("Bank name") },
                        placeholder = { Text("e.g. State Bank of India") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (method == PayoutValidation.METHOD_UPI) {
                    OutlinedTextField(
                        value = upiId,
                        onValueChange = { upiId = it.trim(); errorMsg = null },
                        label = { Text("UPI ID *") },
                        placeholder = { Text("name@okaxis") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                errorMsg?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = PolishRoseLight,
                        border = BorderStroke(1.dp, PolishRose.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = PolishRose, modifier = Modifier.size(16.dp))
                            Text(msg, fontSize = 12.sp, color = PolishRoseDark, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val validationError = PayoutValidation.validate(method, holderName, accountNumber, ifsc, upiId)
                    if (validationError != null) {
                        errorMsg = validationError
                        return@Button
                    }
                    val isBank = method == PayoutValidation.METHOD_BANK
                    if (isBank && accountNumber != confirmAccountNumber) {
                        errorMsg = "Account numbers do not match"
                        return@Button
                    }
                    errorMsg = null
                    onSave(
                        method ?: return@Button,
                        holderName.trim(),
                        if (isBank) PayoutValidation.normalizeAccountNumber(accountNumber) else null,
                        if (isBank) PayoutValidation.normalizeIfsc(ifsc) else null,
                        if (isBank) bankName.trim().ifBlank { null } else null,
                        if (isBank) null else PayoutValidation.normalizeUpi(upiId)
                    ) { success, err ->
                        if (success) onDismiss() else errorMsg = err
                    }
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Saving...")
                } else {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
