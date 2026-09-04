package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.auth.DeactivatedScreen
import com.example.ui.auth.LoginScreen
import com.example.ui.auth.PendingApprovalScreen
import com.example.ui.auth.RegisterRestaurantScreen
import com.example.ui.auth.RejectedScreen
import com.example.ui.components.InAppOrderAlertBanner
import com.example.ui.components.VendorBottomBar
import com.example.ui.components.VendorNavTab
import com.example.ui.components.VendorTopBar
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.earnings.EarningsScreen
import com.example.ui.menu.MenuScreen
import com.example.ui.orders.OrdersScreen
import com.example.ui.profile.StoreProfileScreen
import com.example.ui.reviews.ReviewsScreen
import com.example.ui.theme.SndmartVendorTheme
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.VendorViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: VendorViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Permission result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkNotificationPermission()
        handleIntent(intent)

        setContent {
            SndmartVendorTheme {
                MainVendorApp(
                    viewModel = viewModel,
                    initialTargetTab = intent.getStringExtra("NAVIGATE_TO")
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        val selectedOrderId = intent?.getStringExtra("SELECTED_ORDER_ID")
        if (!selectedOrderId.isNullOrBlank()) {
            val order = viewModel.ordersState.value.orders.firstOrNull { it.id == selectedOrderId }
            if (order != null) {
                viewModel.selectOrder(order)
            }
        }
    }
}

@Composable
fun MainVendorApp(
    viewModel: VendorViewModel,
    initialTargetTab: String? = null,
    modifier: Modifier = Modifier
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val menuState by viewModel.menuState.collectAsStateWithLifecycle()
    val ordersState by viewModel.ordersState.collectAsStateWithLifecycle()
    val earningsState by viewModel.earningsState.collectAsStateWithLifecycle()
    val reviewsState by viewModel.reviewsState.collectAsStateWithLifecycle()
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val orderAlertNotification by viewModel.orderAlertNotification.collectAsStateWithLifecycle()

    var currentTab by remember {
        mutableStateOf(
            if (initialTargetTab.equals("orders", ignoreCase = true)) VendorNavTab.ORDERS else VendorNavTab.HOME
        )
    }
    var showingProfileScreen by remember { mutableStateOf(false) }
    var showingRegisterScreen by remember { mutableStateOf(false) }

    when (val state = authState) {
        is AuthUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator(color = com.example.ui.theme.SprucePrimary)
            }
        }

        is AuthUiState.Unauthenticated -> {
            if (showingRegisterScreen) {
                RegisterRestaurantScreen(
                    viewModel = viewModel,
                    onNavigateBackToLogin = { showingRegisterScreen = false },
                    modifier = modifier
                )
            } else {
                LoginScreen(
                    onLogin = { email, password, onComplete ->
                        viewModel.login(email, password, onComplete)
                    },
                    onNavigateToRegister = { showingRegisterScreen = true },
                    modifier = modifier
                )
            }
        }

        is AuthUiState.PendingApproval -> {
            PendingApprovalScreen(
                vendor = state.vendor,
                onRefresh = { viewModel.refreshStatus() },
                onLogout = { viewModel.logout() },
                modifier = modifier
            )
        }

        is AuthUiState.Rejected -> {
            RejectedScreen(
                vendor = state.vendor,
                reason = state.vendor.rejectionReason,
                onRefresh = { viewModel.refreshStatus() },
                onLogout = { viewModel.logout() },
                modifier = modifier
            )
        }

        is AuthUiState.Deactivated -> {
            DeactivatedScreen(
                vendor = state.vendor,
                onRefresh = { viewModel.refreshStatus() },
                onLogout = { viewModel.logout() },
                modifier = modifier
            )
        }

        is AuthUiState.Authenticated -> {
            val vendor = state.vendor

            if (showingProfileScreen) {
                StoreProfileScreen(
                    state = profileState,
                    onSaveProfile = { name, ownerName, phone, address, lat, lng, openTime, closeTime, bannerUrl, onComplete ->
                        viewModel.updateVendorProfile(name, ownerName, phone, address, lat, lng, openTime, closeTime, bannerUrl, onComplete)
                    },
                    onAddOperatingHour = { start, end, active, closed, day, onComplete ->
                        viewModel.addOperatingHour(start, end, active, closed, day, onComplete)
                    },
                    onUpdateOperatingHour = { id, start, end, active, closed, day, onComplete ->
                        viewModel.updateOperatingHour(id, start, end, active, closed, day, onComplete)
                    },
                    onToggleOperatingHourActive = { hour ->
                        viewModel.toggleOperatingHourActive(hour)
                    },
                    onDeleteOperatingHour = { id, onComplete ->
                        viewModel.deleteOperatingHour(id, onComplete)
                    },
                    onToggleSoundAlert = { enabled ->
                        viewModel.toggleSoundAlert(enabled)
                    },
                    onTestSoundAlert = {
                        viewModel.testSoundAlert()
                    },
                    onSimulateFullScreenAlert = {
                        viewModel.simulateIncomingFullScreenOrderAlert()
                    },
                    onRegisterCustomFcmToken = { token, onComplete ->
                        viewModel.registerCustomFcmToken(token, onComplete)
                    },
                    onLogout = {
                        showingProfileScreen = false
                        viewModel.logout()
                    },
                    onBack = { showingProfileScreen = false },
                    modifier = modifier
                )
            } else {
                Scaffold(
                    topBar = {
                        VendorTopBar(
                            vendor = vendor,
                            onProfileClick = { showingProfileScreen = true },
                            onSimulateNotificationClick = { viewModel.simulateNewOrderNotification() }
                        )
                    },
                    bottomBar = {
                        VendorBottomBar(
                            currentTab = currentTab,
                            onTabSelected = { currentTab = it },
                            pendingOrdersCount = dashboardState.pendingOrdersCount
                        )
                    },
                    modifier = modifier.fillMaxSize()
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // In-App Notification Alert Banner for New Orders
                        AnimatedVisibility(
                            visible = orderAlertNotification != null,
                            enter = slideInVertically() + fadeIn(),
                            exit = slideOutVertically() + fadeOut()
                        ) {
                            orderAlertNotification?.let { msg ->
                                InAppOrderAlertBanner(
                                    message = msg,
                                    onDismiss = { viewModel.clearOrderAlert() },
                                    onViewOrders = {
                                        viewModel.clearOrderAlert()
                                        currentTab = VendorNavTab.ORDERS
                                        viewModel.setOrdersFilter("PENDING")
                                    }
                                )
                            }
                        }

                        // Tab Destination Screens
                        Box(modifier = Modifier.weight(1f)) {
                            when (currentTab) {
                                VendorNavTab.HOME -> {
                                    DashboardScreen(
                                        vendor = vendor,
                                        state = dashboardState,
                                        onToggleStoreOpen = { viewModel.toggleStoreOpen(it) },
                                        onNavigateToEarnings = { currentTab = VendorNavTab.EARNINGS },
                                        onNavigateToOrders = { statusFilter ->
                                            currentTab = VendorNavTab.ORDERS
                                            if (statusFilter != null) {
                                                viewModel.setOrdersFilter(statusFilter)
                                            }
                                        },
                                        onOrderClick = { order ->
                                            currentTab = VendorNavTab.ORDERS
                                            viewModel.selectOrder(order)
                                        },
                                        onRefresh = { viewModel.refreshAll() }
                                    )
                                }

                                VendorNavTab.MENU -> {
                                    MenuScreen(
                                        state = menuState,
                                        onAddCategory = { name, imageUrl, onComplete ->
                                            viewModel.addCategory(name, imageUrl, onComplete)
                                        },
                                        onUpdateCategory = { id, name, imageUrl, onComplete ->
                                            viewModel.updateCategory(id, name, imageUrl, onComplete)
                                        },
                                        onDeleteCategory = { id, onComplete ->
                                            viewModel.deleteCategory(id, onComplete)
                                        },
                                        onAddProduct = { catId, name, desc, price, img, active, from, until, onComplete ->
                                            viewModel.addProduct(catId, name, desc, price, img, active, from, until, onComplete)
                                        },
                                        onUpdateProduct = { prodId, updates, onComplete ->
                                            viewModel.updateProduct(prodId, updates, onComplete)
                                        },
                                        onToggleProductActive = { product ->
                                            viewModel.toggleProductActive(product)
                                        },
                                        onDeleteProduct = { prodId, onComplete ->
                                            viewModel.deleteProduct(prodId, onComplete)
                                        },
                                        onUploadCategoryImage = { fileName, fileBytes, mimeType, onComplete ->
                                            viewModel.uploadCategoryImage(fileName, fileBytes, mimeType, onComplete)
                                        },
                                        onUploadProductImage = { fileName, fileBytes, mimeType, onComplete ->
                                            viewModel.uploadProductImage(fileName, fileBytes, mimeType, onComplete)
                                        },
                                        onRefresh = { viewModel.refreshAll() }
                                    )
                                }

                                VendorNavTab.ORDERS -> {
                                    OrdersScreen(
                                        state = ordersState,
                                        onFilterChange = { filter ->
                                            viewModel.setOrdersFilter(filter)
                                        },
                                        onSelectOrder = { order ->
                                            viewModel.selectOrder(order)
                                        },
                                        onUpdateOrderStatus = { orderId, newStatus, notes, cancellationReason, onComplete ->
                                            viewModel.updateOrderStatus(orderId, newStatus, notes, cancellationReason, onComplete)
                                        },
                                        onRefresh = { viewModel.refreshAll() }
                                    )
                                }

                                VendorNavTab.EARNINGS -> {
                                    EarningsScreen(
                                        state = earningsState,
                                        onRequestWithdrawal = { amount, note, onComplete ->
                                            viewModel.requestWithdrawal(amount, note, onComplete)
                                        },
                                        onRefresh = { viewModel.refreshAll() }
                                    )
                                }

                                VendorNavTab.REVIEWS -> {
                                    ReviewsScreen(
                                        state = reviewsState,
                                        onRefresh = { viewModel.refreshAll() }
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
