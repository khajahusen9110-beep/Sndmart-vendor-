package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Forgot password: 1) mobile number -> OTP on WhatsApp  2) OTP + new password -> signed in.
 * The OTP is sent by the same WhatsApp login-code service the app already uses (SMS only as a backup).
 */
@Composable
fun ForgotPasswordScreen(
    onRequestOtp: (phone: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onReset: (phone: String, otp: String, newPassword: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }
    var otpSent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var resendIn by remember { mutableIntStateOf(0) }

    LaunchedEffect(resendIn) {
        if (resendIn > 0) { delay(1000); resendIn -= 1 }
    }

    fun sendOtp() {
        val digits = phone.filter { it.isDigit() }
        if (digits.length < 10) { error = "Please enter your 10-digit mobile number."; return }
        busy = true; error = null; info = null
        onRequestOtp(digits) { ok, msg ->
            busy = false
            if (ok) { otpSent = true; resendIn = 45; info = "We sent a 6-digit OTP to your WhatsApp number." }
            else error = msg ?: "Could not send the OTP. Please try again."
        }
    }

    Box(modifier = modifier.fillMaxSize().background(PolishBackgroundLight)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Forgot password", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = Slate900)
            Text(
                if (otpSent) "Enter the OTP we sent on WhatsApp and choose a new password."
                else "Enter your vendor mobile number. We will send an OTP on WhatsApp.",
                color = Slate600, fontSize = 14.sp
            )

            Surface(shape = RoundedCornerShape(24.dp), color = PolishSurfaceLight, border = BorderStroke(1.dp, PolishBorderLight), shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    error?.let {
                        Surface(shape = RoundedCornerShape(12.dp), color = PolishRoseLight, border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFFFD5D5)), modifier = Modifier.fillMaxWidth()) {
                            Text(it, color = PolishRose, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(12.dp))
                        }
                    }
                    info?.let { Text(it, color = SprucePrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium) }

                    OutlinedTextField(
                        value = phone, onValueChange = { phone = it.filter { c -> c.isDigit() }.take(10); error = null },
                        label = { Text("Vendor Mobile Number") }, placeholder = { Text("9876543210", color = Slate400) },
                        leadingIcon = { Row(Modifier.padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = Slate400); Spacer(Modifier.width(4.dp)); Text("+91", fontWeight = FontWeight.SemiBold, color = Slate700, fontSize = 14.sp)
                        } },
                        enabled = !otpSent && !busy, singleLine = true, colors = polishTextFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                    )

                    if (!otpSent) {
                        Button(
                            onClick = { sendOtp() }, enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary)
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                            else Text("Send OTP on WhatsApp", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedTextField(
                            value = otp, onValueChange = { otp = it.filter { c -> c.isDigit() }.take(6); error = null },
                            label = { Text("OTP") }, placeholder = { Text("6-digit code", color = Slate400) },
                            leadingIcon = { Icon(Icons.Default.Sms, contentDescription = null, tint = Slate400) },
                            singleLine = true, colors = polishTextFieldColors(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                        )
                        OutlinedTextField(
                            value = pass, onValueChange = { pass = it; error = null },
                            label = { Text("New password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Slate400) },
                            trailingIcon = { IconButton(onClick = { passVisible = !passVisible }) {
                                Icon(if (passVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = if (passVisible) "Hide password" else "Show password", tint = Slate400)
                            } },
                            visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true, colors = polishTextFieldColors(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                        )
                        OutlinedTextField(
                            value = pass2, onValueChange = { pass2 = it; error = null },
                            label = { Text("Confirm new password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Slate400) },
                            visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true, colors = polishTextFieldColors(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                        )
                        Button(
                            onClick = {
                                when {
                                    otp.length != 6 -> error = "Please enter the 6-digit OTP."
                                    pass.length < 6 -> error = "Password must be at least 6 characters."
                                    pass != pass2 -> error = "The two passwords do not match."
                                    else -> {
                                        busy = true; error = null
                                        onReset(phone.filter { it.isDigit() }, otp, pass) { ok, msg ->
                                            busy = false
                                            if (!ok) error = msg ?: "Could not reset the password."
                                        }
                                    }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary)
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                            else Text("Verify & set new password", fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { otpSent = false; otp = ""; pass = ""; pass2 = ""; info = null; error = null }, enabled = !busy) { Text("Change number", fontSize = 13.sp) }
                            TextButton(onClick = { sendOtp() }, enabled = !busy && resendIn == 0) {
                                Text(if (resendIn > 0) "Resend in ${resendIn}s" else "Resend OTP", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
