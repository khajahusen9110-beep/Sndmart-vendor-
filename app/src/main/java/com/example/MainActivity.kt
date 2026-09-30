package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Vendor
import com.example.service.OrderAlarmService
import com.example.ui.auth.DeactivatedScreen
import com.example.ui.auth.LoginScreen
import com.example.ui.auth.PendingApprovalScreen
import com.example.ui.auth.RegisterRestaurantScreen
import com.example.ui.auth.RejectedScreen
import com.example.ui.components.BlockingUpdateScreen
import com.example.ui.components.InAppOrderAlertBanner
import com.example.ui.components.MaintenanceBanner
import com.example.ui.components.OptionalUpdateDialog
import com.example.ui.components.openUpdateDestination
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
import com.example.util.PlayUpdateManager

class MainActivity : ComponentActivity() {
    private val viewModel: VendorViewModel by viewModels()
    private lateinit var playUpdateManager: PlayUpdateManager
    private var isFlexibleUpdateDownloaded by mutableStateOf(false)

    private val playUpdateResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            if (BuildConfig.DEBUG) {
                Log.w("MainActivity", "Play update flow canceled or failed: ${result.resultCode}")
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.updateNotificationPermissionStatus(isGranted)
        if (!isGranted) {
            Toast.makeText(this, "Notification permission is required for new order alerts", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        playUpdateManager = PlayUpdateManager(this)
        checkNotificationPermission()
        handleIntent(intent)

        setContent {
            SndmartVendorTheme {
                val blockingUpdate by viewModel.blockingUpdateState.collectAsStateWithLifecycle()
                val optionalUpdate by viewModel.optionalUpdateState.collectAsStateWithLifecycle()

                MainVendorApp(
                    viewModel = viewModel,
                    initialTargetTab = intent.getStringExtra("NAVIGATE_TO"),
                    isUpdateDownloaded = isFlexibleUpdateDownloaded,
                    onUpdateNow = {
                        playUpdateManager.startImmediateUpdate(
                            activity = this@MainActivity,
                            launcher = playUpdateResultLauncher,
                            onFallback = {
                                openUpdateDestination(this@MainActivity, blockingUpdate.updateUrl)
                            }
                        )
                    },
                    onStartFlexibleUpdate = {
                        val fallbackUrl = optionalUpdate?.updateUrl
                        playUpdateManager.startFlexibleUpdate(
                            activity = this@MainActivity,
                            launcher = playUpdateResultLauncher,
                            onDownloaded = {
                                isFlexibleUpdateDownloaded = true
                            },
                            onFallback = {
                                openUpdateDestination(this@MainActivity, fallbackUrl)
                            }
                        )
                    },
                    onCompleteFlexibleUpdate = {
                        playUpdateManager.completeUpdate()
                    },
                    onSendTestAlert = {
                        viewModel.sendTestAlert { success, msg ->
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    onFixAlerts = { vendor ->
                        val notifGranted = androidx.core.app.NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()
                        if (!notifGranted) {
                            requestNotificationPermissionOrOpenSettings()
                        } else {
                            viewModel.registerClientFcmToken(vendor)
                            Toast.makeText(this@MainActivity, "Retrying alert registration...", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRequestIgnoreBatteryOptimization = { requestIgnoreBatteryOptimization() },
                    onDismissAutostartTip = { viewModel.dismissAutostartTip() },
                    onOpenAppInfo = { openAppInfo() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppStarted()
    }

    override fun onStop() {
        viewModel.onAppStopped()
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        playUpdateManager.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun requestNotificationPermissionOrOpenSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        val notifGranted = androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()
        if (!notifGranted) {
            try {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                startActivity(intent)
            } catch (e: Exception) {
                openAppInfo()
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        try {
            // Stop 10-second alarm whenever user opens the app from a notification
            OrderAlarmService.stopAlarm(this)
            val selectedOrderId = intent?.getStringExtra("SELECTED_ORDER_ID")
            if (!selectedOrderId.isNullOrBlank()) {
                viewModel.navigateToOrders(selectedOrderId)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error handling notification intent", e)
        }
    }

    override fun onResume() {
        super.onResume()
        val notifGranted = androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()
        viewModel.updateNotificationPermissionStatus(notifGranted)
        checkBatteryOptimizationStatus()
        viewModel.onAppForegrounded()
        viewModel.checkAppVersion(bypassThrottle = false)
        playUpdateManager.resumeImmediateUpdate(this)
    }

    private fun checkBatteryOptimizationStatus() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isIgnoring = powerManager?.isIgnoringBatteryOptimizations(packageName) ?: true
            viewModel.updateBatteryOptimizationWarning(isIgnoring)
            viewModel.checkAndShowAutostartTip()
        } catch (e: Exception) {
            Log.w("MainActivity", "Notice checking battery optimization: ${e.message}")
        }
    }

    fun requestIgnoreBatteryOptimization() {
        // Opens the system battery optimization list; the vendor selects Sndmart Vendor → "Don't optimize".
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            Toast.makeText(this, "Select Sndmart Vendor and choose \"Don't optimize\"", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            openAppInfo()
        }
    }

    fun openAppInfo() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open app settings", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun MainVendorApp(
    viewModel: VendorViewModel,
    initialTargetTab: String? = null,
    isUpdateDownloaded: Boolean = false,
    onUpdateNow: () -> Unit = {},
    onStartFlexibleUpdate: () -> Unit = {},
    onCompleteFlexibleUpdate: () -> Unit = {},
    onSendTestAlert: () -> Unit = {},
    onFixAlerts: (Vendor) -> Unit = {},
    onRequestIgnoreBatteryOptimization: () -> Unit = {},
    onDismissAutostartTip: () -> Unit = {},
    onOpenAppInfo: () -> Unit = {},
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
    val optionalUpdate by viewModel.optionalUpdateState.collectAsStateWithLifecycle()
    val maintenanceBanner by viewModel.maintenanceBannerState.collectAsStateWithLifecycle()

    // 1. Blocking Screen when App Version is below minimum supported version or force update is active
    if (blockingUpdate.isBlocked) {
        BlockingUpdateScreen(
            updateMessage = blockingUpdate.updateMessage,
            currentVersion = blockingUpdate.currentAppVersion,
            minimumVersion = blockingUpdate.minimumSupportedVersion,
            updateUrl = blockingUpdate.updateUrl,
            onCheckAgain = { viewModel.runStartupChecks() },
            onUpdateNow = onUpdateNow,
            modifier = modifier
        )
        return
    }

    // 2. Optional Update Dialog (if current < latest but >= min, not dismissed)
    optionalUpdate?.let { optUpdate ->
        if (optUpdate.isAvailable) {
            OptionalUpdateDialog(
                message = optUpdate.updateMessage,
                latestVersion = optUpdate.latestVersion,
                currentVersion = optUpdate.currentVersion,
                onUpdate = onStartFlexibleUpdate,
                onDismiss = {
                    viewModel.dismissOptionalUpdate(
                        latestCode = optUpdate.latestVersionCode,
                        latestName = optUpdate.latestVersion
                    )
                }
            )
        }
    }

    var currentTab by remember {
        mutableStateOf(
            if (initialTargetTab.equals("orders", ignoreCase = true)) VendorNavTab.ORDERS else VendorNavTab.HOME
        )
    }

    // Requirement C17: Guard navigation from notification taps with isNavGraphReady + try/catch
    var isNavGraphReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isNavGraphReady = true
    }

    LaunchedEffect(targetNav, isNavGraphReady) {
        if (isNavGraphReady && targetNav != null) {
            try {
                currentTab = VendorNavTab.ORDERS
                viewModel.clearTargetNavigation()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error navigating to target order destination", e)
            }
        }
    }
    var showingProfileScreen by remember { mutableStateOf(false) }
    var showingRegisterScreen by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = authState) {
            is AuthUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                            onProfileClick = { showingProfileScreen = true }
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
                                        onRefresh = { viewModel.refreshAll() },
                                        onSendTestAlert = onSendTestAlert,
                                        onFixAlerts = { onFixAlerts(vendor) },
                                        onRequestIgnoreBatteryOptimization = onRequestIgnoreBatteryOptimization,
                                        onDismissAutostartTip = onDismissAutostartTip,
                                        onOpenAppInfo = onOpenAppInfo
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
                                        onUpdateOrderStatus = { orderId, fromStatus, newStatus, onComplete ->
                                            viewModel.updateOrderStatus(orderId, fromStatus, newStatus, onComplete)
                                        },
                                        onRefresh = { viewModel.refreshAll() },
                                        onLoadMore = { viewModel.loadMoreOrders() },
                                        onSearchQueryChange = { viewModel.setOrdersSearchQuery(it) }
                                    )
                                }

                                VendorNavTab.EARNINGS -> {
                                    EarningsScreen(
                                        state = earningsState,
                                        onRequestWithdrawal = { amount, onComplete ->
                                            viewModel.requestWithdrawal(amount, onComplete)
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

        // Play In-App Updates: Flexible update downloaded, prompt to restart
        if (isUpdateDownloaded) {
            Snackbar(
                action = {
                    Button(
                        onClick = onCompleteFlexibleUpdate,
                        colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.SprucePrimary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("RESTART", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                Text("Update downloaded. Restart app to finish installing.")
            }
        }
    }
}
