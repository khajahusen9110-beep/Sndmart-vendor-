package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun OptionalUpdateDialog(
    message: String?,
    latestVersion: String,
    currentVersion: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val displayMessage = message?.ifBlank { null }
        ?: "A new update for Sndmart Vendor is available with improvements, enhanced stability, and new features."

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = SprucePrimary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Update Available",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Slate900
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = displayMessage,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    color = Slate700
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PolishSurfaceLight,
                    border = BorderStroke(1.dp, PolishBorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Installed",
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate500
                            )
                            Text(
                                text = "v$currentVersion",
                                fontWeight = FontWeight.SemiBold,
                                color = Slate800,
                                fontSize = 13.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Latest",
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate500
                            )
                            Text(
                                text = "v$latestVersion",
                                fontWeight = FontWeight.Bold,
                                color = SprucePrimary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Update Now",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Later",
                    color = Slate600,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp
                )
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = PolishBackgroundLight
    )
}
