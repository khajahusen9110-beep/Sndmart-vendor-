package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Vendor
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorTopBar(
    vendor: Vendor?,
    onProfileClick: () -> Unit,
    onSimulateNotificationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SpruceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint = SprucePrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = vendor?.name?.ifBlank { "The Spice Kitchen" } ?: "The Spice Kitchen",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SpruceOnContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (vendor?.isOpen == true) PolishEmerald else PolishRose)
                        )
                        Text(
                            text = if (vendor?.isOpen == true) "STORE OPEN" else "STORE CLOSED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp,
                            color = if (vendor?.isOpen == true) PolishEmeraldDark else Slate500
                        )
                    }
                }
            }
        },
        actions = {
            // Notification test trigger
            IconButton(onClick = onSimulateNotificationClick) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Test Order Alert Notification",
                    tint = Slate700
                )
            }

            // Initials Avatar Circle Button
            val initials = remember(vendor?.ownerName, vendor?.name) {
                val name = vendor?.ownerName?.ifBlank { vendor.name } ?: "Vendor"
                val parts = name.trim().split(" ").filter { it.isNotBlank() }
                if (parts.size >= 2) {
                    "${parts[0].first().uppercase()}${parts[1].first().uppercase()}"
                } else if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                    parts[0].take(2).uppercase()
                } else "VN"
            }

            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SpruceContainer)
                    .clickable { onProfileClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = SpruceOnContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = PolishSurfaceLight,
            titleContentColor = SpruceOnContainer
        ),
        modifier = modifier
    )
}

