package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/**
 * Blocking full-screen overlay presented when the client app version is below
 * the minimum supported version or when a force-update flag is enabled.
 * Back press closes the application without bypassing the update requirement.
 */
@Composable
fun BlockingUpdateScreen(
    updateMessage: String?,
    currentVersion: String,
    minimumVersion: String?,
    updateUrl: String?,
    onCheckAgain: () -> Unit,
    onUpdateNow: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    BackHandler(enabled = true) {
        activity?.finishAffinity() ?: activity?.finish()
    }

    val message = updateMessage?.ifBlank { null }
        ?: "A critical update is required to continue using Sndmart Vendor. Please update to the latest version to ensure order security and compatibility."

    Surface(
        modifier = modifier.fillMaxSize(),
        color = PolishBackgroundLight
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(SprucePrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = "Update Required",
                    tint = SprucePrimary,
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Surface(
                color = PolishRoseLight,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, PolishRose.copy(alpha = 0.3f))
            ) {
                Text(
                    text = "ACTION REQUIRED",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PolishRoseDark,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Update Required",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Slate900,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Slate600,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Version details card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = PolishSurfaceLight,
                border = BorderStroke(1.dp, PolishBorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Installed Version",
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate500
                        )
                        Text(
                            text = "v$currentVersion",
                            fontWeight = FontWeight.Bold,
                            color = Slate800,
                            fontSize = 14.sp
                        )
                    }

                    if (!minimumVersion.isNullOrBlank()) {
                        Divider(
                            modifier = Modifier
                                .height(28.dp)
                                .width(1.dp),
                            color = Slate300
                        )

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Required Version",
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate500
                            )
                            Text(
                                text = "v$minimumVersion+",
                                fontWeight = FontWeight.Bold,
                                color = SprucePrimary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (onUpdateNow != null) {
                        onUpdateNow()
                    } else {
                        openUpdateDestination(context, updateUrl)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Update Now",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = onCheckAgain,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Slate300)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = Slate700,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Check Again",
                    fontWeight = FontWeight.SemiBold,
                    color = Slate700
                )
            }
        }
    }
}

fun openUpdateDestination(context: Context, updateUrl: String?) {
    try {
        if (!updateUrl.isNullOrBlank()) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        }
    } catch (_: Exception) {}

    // Fallback: Google Play store URL
    try {
        val appPackageName = context.packageName
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(marketIntent)
    } catch (_: Exception) {
        val appPackageName = context.packageName
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(webIntent)
    }
}
