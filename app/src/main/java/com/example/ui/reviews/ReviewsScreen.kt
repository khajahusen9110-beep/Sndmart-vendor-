package com.example.ui.reviews

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ReviewCardSkeleton
import com.example.ui.theme.*
import com.example.ui.viewmodel.ReviewsUiState

@Composable
fun ReviewsScreen(
    state: ReviewsUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PolishBackgroundLight)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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

        // Average Rating Summary Header
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = PolishSurfaceLight,
                border = BorderStroke(1.dp, PolishBorderLight),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Customer Rating",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate500
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "%.1f".format(state.averageRating),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Slate900
                            )
                            Column {
                                Row {
                                    repeat(5) { index ->
                                        Icon(
                                            imageVector = if (index < state.averageRating.toInt()) Icons.Default.Star else Icons.Default.StarHalf,
                                            contentDescription = null,
                                            tint = PolishAmber,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${state.totalCount} verified reviews",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate500
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(PolishAmberLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RateReview,
                            contentDescription = null,
                            tint = PolishAmber,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        // Reviews List Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Customer Feedback",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
                Text(
                    text = "Read-only",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate500
                )
            }
        }

        // Reviews items
        if (state.isLoading && state.reviews.isEmpty()) {
            items(4) {
                ReviewCardSkeleton()
            }
        } else if (state.reviews.isEmpty()) {
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
                            .padding(36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (!state.errorMessage.isNullOrBlank()) Icons.Default.Warning else Icons.Outlined.RateReview,
                            contentDescription = null,
                            tint = if (!state.errorMessage.isNullOrBlank()) PolishRose else Slate400,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (!state.errorMessage.isNullOrBlank()) "Unable to load reviews" else "No reviews yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (!state.errorMessage.isNullOrBlank()) state.errorMessage
                            else "Customer ratings and comments after order completion will appear here.",
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
                                Text("Retry Loading Reviews", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            items(state.reviews, key = { it.id }) { review ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = PolishSurfaceLight,
                    border = BorderStroke(1.dp, PolishBorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(SpruceContainerLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = SprucePrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = review.customerName?.ifBlank { "Customer" } ?: "Customer",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Slate900
                                    )
                                    if (!review.orderNumber.isNullOrBlank() || !review.orderId.isNullOrBlank()) {
                                        Text(
                                            text = "Order #${review.orderNumber ?: review.orderId?.take(6)?.uppercase()}",
                                            fontSize = 11.sp,
                                            color = Slate500
                                        )
                                    }
                                }
                            }

                            Row {
                                repeat(5) { starIndex ->
                                    Icon(
                                        imageVector = if (starIndex < review.rating.toInt()) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = null,
                                        tint = PolishAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (!review.comment.isNullOrBlank()) {
                            Text(
                                text = review.comment,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Slate700
                            )
                        }

                        if (!review.createdAt.isNullOrBlank()) {
                            Text(
                                text = review.createdAt.take(10),
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate400
                            )
                        }
                    }
                }
            }

            // Rule 1: Pagination footer (20 reviews at a time)
            if (state.isLoadingMore) {
                item {
                    ReviewCardSkeleton()
                }
            } else if (state.canLoadMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
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
                            Text("Load More Reviews (20 more)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

