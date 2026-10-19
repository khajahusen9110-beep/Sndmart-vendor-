package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/**
 * Shown instead of the app until the vendor allows full-screen order alerts (Android 14+),
 * so new orders can open over the lock screen like an incoming call.
 */
@Composable
fun FullScreenPermissionScreen(
    onAllow: () -> Unit,
    modifier: Modifier = Modifier,
    onSkip: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PolishBackgroundLight)
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(SpruceContainerLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = SprucePrimary,
                modifier = Modifier.size(46.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Allow full-screen order alerts",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Slate900,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "New orders must open on your screen like an incoming call, even when the phone is locked, so no order is missed. Please allow this to continue.",
            fontSize = 14.sp,
            color = Slate600,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(18.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = PolishSurfaceLight,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("1. Tap \"Allow\" below", fontSize = 13.sp, color = Slate700)
                Text("2. Turn ON \"Allow full screen notifications\" for Sndmart Vendor", fontSize = 13.sp, color = Slate700)
                Text("3. Come back to the app", fontSize = 13.sp, color = Slate700)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onAllow,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary, contentColor = Color.White),
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Allow", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        if (onSkip != null) {
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Skip for now", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Slate600)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "You can turn this on anytime from Profile → Full-Screen Alert.",
                fontSize = 11.sp,
                color = Slate600,
                textAlign = TextAlign.Center
            )
        }
    }
}
