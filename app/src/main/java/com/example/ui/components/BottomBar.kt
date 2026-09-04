package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

enum class VendorNavTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    MENU("Menu", Icons.Filled.RestaurantMenu, Icons.Outlined.RestaurantMenu),
    ORDERS("Orders", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong),
    EARNINGS("Earnings", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    REVIEWS("Reviews", Icons.Filled.Star, Icons.Outlined.StarOutline)
}

@Composable
fun VendorBottomBar(
    currentTab: VendorNavTab,
    onTabSelected: (VendorNavTab) -> Unit,
    pendingOrdersCount: Int = 0,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        VendorNavTab.values().forEach { tab ->
            val selected = currentTab == tab

            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = {
                    if (tab == VendorNavTab.ORDERS && pendingOrdersCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = PolishRose,
                                    contentColor = Color.White
                                ) {
                                    Text(text = pendingOrdersCount.toString(), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        }
                    } else {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.title
                        )
                    }
                },
                label = {
                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SpruceOnContainer,
                    selectedTextColor = SprucePrimary,
                    indicatorColor = SpruceContainer,
                    unselectedIconColor = Slate400,
                    unselectedTextColor = Slate400
                )
            )
        }
    }
}
