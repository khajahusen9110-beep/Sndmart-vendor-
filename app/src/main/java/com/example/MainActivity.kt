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
import com.example.ui.components.BlockingUpdateScreen
import com.example.ui.components.InAppOrderAlertBanner
import com.example.ui.components.MaintenanceBanner
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
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        val selectedOrderId = intent?.getStringExtra("SELECTED_ORDER_ID")
        if (!selectedOrderId.isNullOrBlank()) {
            viewModel.navigateToOrders(selectedOrderId)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppForegrounded()
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
    val sessionNotice by viewModel.sessionExpiredNotice.collectAsStateWithLifecycle()
    val targetNav by viewModel.targetNavigation.collectAsStateWithLifecycle()
    val blockingUpdate by viewModel.blockingUpdateState.collectAsStateWithLifecycle()
    val maintenanceBanner by viewModel.maintenanceBannerState.collectAsStateWithLifecycle()

    // 1. Blocking Screen when App Version is below minimum supported version or force update is active
    if (blockingUpdate.isBlocked) {
        BlockingUpdateScreen(
            updateMessage = blockingUpdate.updateMessage,
            currentVersion = blockingUpdate.currentAppVersion,
            minimumVersion = blockingUpdate.minimumSupportedVersion,
            updateUrl = blockingUpdate.updateUrl,
            onCheckAgain = { viewModel.runStartupChecks() },
            modifier = modifier
        )
        return
    }

    var currentTab by remember {
        mutableStateOf(
            if (initialTargetTab.equals("orders", ignoreCase = true)) VendorNavTab.ORDERS else VendorNavTab.HOME
        )
    }

    LaunchedEffect(targetNav) {
        if (targetNav != null) {
            currentTab = VendorNavTab.ORDERS
            viewModel.clearTargetNavigation()
        }
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
            Column(modifier = modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = maintenanceBanner.isEnabled && !maintenanceBanner.isDismissedForSession,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    MaintenanceBanner(
                        message = maintenanceBanner.message,
                        onDismiss = { viewModel.dismissMaintenanceBanner() },
                        modifier = Modifier.statusBarsPadding()
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (showingRegisterScreen) {
                        RegisterRestaurantScreen(
                            viewModel = viewModel,
                            onNavigateBackToLogin = { showingRegisterScreen = false },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LoginScreen(
                            onLogin = { email, password, onComplete ->
                                viewModel.login(email, password, onComplete)
                            },
                            onNavigateToRegister = { showingRegisterScreen = true },
                            sessionNotice = sessionNotice,
                            onClearSessionNotice = { viewModel.clearSessionExpiredNotice() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
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
                    onSaveProfile = { name, ownerName, phone, address, lat, lng, openTime, closeTime, bannerUrl, cityId, onComplete ->
                        viewModel.updateVendorProfile(name, ownerName, phone, address, lat, lng, openTime, closeTime, bannerUrl, cityId, onComplete)
                    },
                    onRefreshProfile = {
                        viewModel.loadProfile()
                    },
                    onUploadBanner = { fileName, fileBytes, mimeType, onComplete ->
                        viewModel.uploadStoreBanner(fileName, fileBytes, mimeType, onComplete)
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
                            confirmedOrdersCount = dashboardState.confirmedOrdersCount
                        )
                    },
                    modifier = modifier.fillMaxSize()
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Platform Maintenance Mode Top Banner (dismissible for this session, reappears on next launch)
                        AnimatedVisibility(
                            visible = maintenanceBanner.isEnabled && !maintenanceBanner.isDismissedForSession,
                            enter = slideInVertically() + fadeIn(),
                            exit = slideOutVertically() + fadeOut()
                        ) {
                            MaintenanceBanner(
                                message = maintenanceBanner.message,
                                onDismiss = { viewModel.dismissMaintenanceBanner() }
                            )
                        }

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
                                        viewModel.setOrdersFilter("CONFIRMED")
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
                                        onAddProduct = { catId, name, desc, price, img, active, from, until, stock, isAvail, onComplete ->
                                            viewModel.addProduct(catId, name, desc, price, img, active, from, until, stock, isAvail, onComplete)
                                        },
                                        onUpdateProduct = { prodId, updates, onComplete ->
                                            viewModel.updateProduct(prodId, updates, onComplete)
                                        },
                                        onQuickRestockItem = { prodId, stock ->
                                            viewModel.quickRestockProduct(prodId, stock)
                                        },
                                        onRestockAllZeroStock = { targetStock ->
                                            viewModel.restockZeroStockProducts(targetStock)
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
                                        onRefresh = { viewModel.refreshAll() },
                                        onLoadMore = { viewModel.loadMoreProducts() },
                                        onSearchQueryChange = { viewModel.setMenuSearchQuery(it) }
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
                                        onRefresh = { viewModel.refreshAll() },
                                        onLoadMore = { viewModel.loadMoreOrders() },
                                        onSearchQueryChange = { viewModel.setOrdersSearchQuery(it) }
                                    )
                                }

                                VendorNavTab.EARNINGS -> {
                                    EarningsScreen(
                                        state = earningsState,
                                        onRequestWithdrawal = { amount, note, onComplete ->
                                            viewModel.requestWithdrawal(amount, note, onComplete)
                                        },
                                        onRefresh = { viewModel.refreshAll() },
                                        onLoadMoreTransactions = { viewModel.loadMoreTransactions() }
                                    )
                                }

                                VendorNavTab.REVIEWS -> {
                                    ReviewsScreen(
                                        state = reviewsState,
                                        onRefresh = { viewModel.refreshAll() },
                                        onLoadMore = { viewModel.loadMoreReviews() }
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
