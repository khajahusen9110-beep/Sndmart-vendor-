package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/**
 * Maintenance banner shown at the top of the Vendor app when platform maintenance mode is enabled.
 *
 * Notice: Unlike the customer app, vendors are not blocked during maintenance — they can still
 * view and manage existing orders, update their menu, and review earnings. This banner clearly
 * informs them that new customer order creation is paused platform-wide.
 *
 * It is dismissible for the current session, and reappears on the next app launch.
 */
@Composable
fun MaintenanceBanner(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayMessage = message?.ifBlank { null }
        ?: "Sndmart is temporarily paused for maintenance — new customer orders are not coming in right now."

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PolishAmberLight,
        border = BorderStroke(1.5.dp, PolishAmber.copy(alpha = 0.6f)),
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PolishAmber.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Engineering,
                    contentDescription = "Maintenance Mode",
                    tint = PolishAmberDark,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "System Maintenance",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = PolishAmberDark
                    )
                    Surface(
                        color = PolishAmber.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PolishAmberDark,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = displayMessage,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = Slate800
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "You can still manage existing orders, store menu & earnings normally.",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = Slate600
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss notice for this session",
                    tint = Slate600,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
