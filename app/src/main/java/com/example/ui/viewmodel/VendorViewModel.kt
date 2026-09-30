package com.example.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.SessionManager
import com.example.data.model.*
import com.example.data.network.SupabaseClient
import com.example.data.repository.Resource
import com.example.data.repository.VendorRepository
import com.example.util.AppUpdateState
import com.example.util.NotificationHelper
import com.example.util.SoundAlertManager
import com.example.util.compareVersionStrings
import com.example.util.evaluateUpdateState
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.math.BigDecimal
import java.math.RoundingMode
import com.example.util.CurrencyFormatter
import com.example.util.IstDateUtils

sealed interface AuthUiState {
    data object Loading : AuthUiState
    data object Unauthenticated : AuthUiState
    data class Authenticated(val vendor: Vendor) : AuthUiState
    data class PendingApproval(val vendor: Vendor) : AuthUiState
    data class Rejected(val vendor: Vendor, val reason: String?) : AuthUiState
    data class Deactivated(val vendor: Vendor) : AuthUiState
}

data class RegistrationUiState(
    val cities: List<City> = emptyList(),
    val isLoadingCities: Boolean = false,
    val isRequestingOtp: Boolean = false,
    val isSubmittingRegistration: Boolean = false,
    val isOtpSent: Boolean = false,
    val isOtpVerified: Boolean = false,
    val authenticatedUserId: String? = null,
    val isUploadingFoodLicense: Boolean = false,
    val foodLicenseStoragePath: String? = null,
    val foodLicenseUploadError: String? = null,
    val isUploadingAadhar: Boolean = false,
    val aadharStoragePath: String? = null,
    val aadharUploadError: String? = null,
    val registeredVendor: Vendor? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

data class DashboardUiState(
    val ordersTodayCount: Int = 0,
    val deliveredOrdersTodayCount: Int = 0,
    val earningsTodaySum: Double = 0.0,
    val todayEarningsVendorAmount: BigDecimal? = null,
    val foodSalesTodayDelivered: Double = 0.0,
    val commissionPercent: Double = 25.0,
    val confirmedOrdersCount: Int = 0, // Orders accepted by City Admin waiting for vendor preparation
    val pendingOrdersCount: Int = 0, // Kept at 0: vendors never see pending orders
    val averageRating: Double = 0.0,
    val walletBalance: Double = 0.0,
    val hasValidWalletBalance: Boolean = false,
    val isWalletBalanceError: Boolean = false,
    val walletErrorMessage: String? = null,
    val recentOrders: List<Order> = emptyList(),
    val isStoreOpen: Boolean = true,
    val hasNotificationPermission: Boolean = true,
    val isOrderAlertsOn: Boolean = false,
    val orderAlertsOffReason: String? = null,
    val isSendingTestAlert: Boolean = false,
    val showBatteryOptimizationWarning: Boolean = false,
    val showAutostartTipDialog: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class MenuUiState(
    val categories: List<Category> = emptyList(),
    val products: List<Product> = emptyList(),
    val selectedCategoryId: String? = null,
    val searchQuery: String = "",
    val page: Int = 0,
    val canLoadMore: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

data class OrdersUiState(
    val orders: List<Order> = emptyList(),
    val activeFilter: String = "ALL", // ALL, PENDING, CONFIRMED, PREPARING, READY, DELIVERED, CANCELLED
    val selectedOrder: Order? = null,
    val selectedOrderHistory: List<OrderStatusHistory> = emptyList(),
    val isLoadingHistory: Boolean = false,
    val searchQuery: String = "",
    val page: Int = 0,
    val canLoadMore: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isLoading: Boolean = false,
    val isUpdating: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val newPendingAlert: String? = null
)

data class EarningsUiState(
    val walletTransactions: List<WalletTransaction> = emptyList(),
    val payouts: List<Payout> = emptyList(),
    val withdrawals: List<Withdrawal> = emptyList(),
    val runningBalance: Double = 0.0,
    val hasValidWalletBalance: Boolean = false,
    val isWalletBalanceError: Boolean = false,
    val walletErrorMessage: String? = null,
    val inProgressWithdrawalsSum: Double = 0.0,
    val availableToWithdraw: Double = 0.0,
    val commissionPercent: Double = 25.0,
    val activeWithdrawalInProgress: Withdrawal? = null,
    val page: Int = 0,
    val canLoadMoreTransactions: Boolean = true,
    val isLoadingMoreTransactions: Boolean = false,
    val isLoading: Boolean = false,
    val isSubmittingWithdrawal: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

data class ReviewsUiState(
    val reviews: List<Review> = emptyList(),
    val averageRating: Double = 0.0,
    val totalCount: Int = 0,
    val page: Int = 0,
    val canLoadMore: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class ProfileUiState(
    val vendor: Vendor? = null,
    val operatingHours: List<OperatingHour> = emptyList(),
    val fcmToken: String = "",
    val isFcmRegistered: Boolean = false,
    val isSoundAlertEnabled: Boolean = true,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

val CURRENT_APP_VERSION: String get() = BuildConfig.VERSION_NAME

data class BlockingUpdateState(
    val isBlocked: Boolean = false,
    val updateMessage: String? = null,
    val minimumSupportedVersion: String? = null,
    val currentAppVersion: String = BuildConfig.VERSION_NAME,
    val updateUrl: String? = null
)

data class OptionalUpdateState(
    val isAvailable: Boolean = false,
    val updateMessage: String? = null,
    val latestVersion: String = "",
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val updateUrl: String? = null,
    val latestVersionCode: Int? = null
)

data class MaintenanceBannerState(
    val isEnabled: Boolean = false,
    val message: String? = null,
    val isDismissedForSession: Boolean = false
)

fun compareVersions(a: String, b: String): Int = compareVersionStrings(a, b)

class VendorViewModel(application: Application) : AndroidViewModel(application) {

    val sessionManager = SessionManager(application.applicationContext)
    private val supabaseClient = SupabaseClient(sessionManager)
    val repository = VendorRepository(supabaseClient, sessionManager)
    val soundAlertManager = SoundAlertManager(application.applicationContext)

    private val _blockingUpdateState = MutableStateFlow(BlockingUpdateState())
    val blockingUpdateState: StateFlow<BlockingUpdateState> = _blockingUpdateState.asStateFlow()

    private val _optionalUpdateState = MutableStateFlow<OptionalUpdateState?>(null)
    val optionalUpdateState: StateFlow<OptionalUpdateState?> = _optionalUpdateState.asStateFlow()

    private val _maintenanceBannerState = MutableStateFlow(MaintenanceBannerState())
    val maintenanceBannerState: StateFlow<MaintenanceBannerState> = _maintenanceBannerState.asStateFlow()

    fun dismissMaintenanceBanner() {
        _maintenanceBannerState.update { it.copy(isDismissedForSession = true) }
    }

    private val _authUiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()
    val authState: StateFlow<AuthUiState> get() = _authUiState.asStateFlow()

    private val _dashboardState = MutableStateFlow(DashboardUiState())
    val dashboardState: StateFlow<DashboardUiState> = _dashboardState.asStateFlow()

    private val _menuState = MutableStateFlow(MenuUiState())
    val menuState: StateFlow<MenuUiState> = _menuState.asStateFlow()

    private val _ordersState = MutableStateFlow(OrdersUiState())
    val ordersState: StateFlow<OrdersUiState> = _ordersState.asStateFlow()

    private val _earningsState = MutableStateFlow(EarningsUiState())
    val earningsState: StateFlow<EarningsUiState> = _earningsState.asStateFlow()

    private val _reviewsState = MutableStateFlow(ReviewsUiState())
    val reviewsState: StateFlow<ReviewsUiState> = _reviewsState.asStateFlow()

    private val _profileState = MutableStateFlow(ProfileUiState(isSoundAlertEnabled = sessionManager.isSoundAlertEnabled))
    val profileState: StateFlow<ProfileUiState> = _profileState.asStateFlow()

    private val _registrationState = MutableStateFlow(RegistrationUiState())
    val registrationState: StateFlow<RegistrationUiState> = _registrationState.asStateFlow()

    private val _inAppOrderAlert = MutableStateFlow<String?>(null)
    val inAppOrderAlert: StateFlow<String?> = _inAppOrderAlert.asStateFlow()
    val orderAlertNotification: StateFlow<String?> get() = _inAppOrderAlert.asStateFlow()

    private val _targetNavigation = MutableStateFlow<String?>(null)
    val targetNavigation: StateFlow<String?> = _targetNavigation.asStateFlow()

    private val _sessionExpiredNotice = MutableStateFlow<String?>(sessionManager.sessionExpiredMessage)
    val sessionExpiredNotice: StateFlow<String?> = _sessionExpiredNotice.asStateFlow()

    private var livePollingJob: Job? = null
    private var lastKnownConfirmedCount = 0

    fun navigateToOrders(orderId: String? = null) {
        setOrdersFilter("CONFIRMED")
        _targetNavigation.value = orderId ?: "orders"
        if (!orderId.isNullOrBlank()) {
            val target = _ordersState.value.orders.firstOrNull { it.id == orderId }
            if (target != null) {
                selectOrder(target)
            }
        }
    }

    fun clearTargetNavigation() {
        _targetNavigation.value = null
    }

    init {
        viewModelScope.launch {
            sessionManager.sessionExpiredFlow.collect { reason ->
                handleSessionExpired(reason)
            }
        }
        viewModelScope.launch {
            com.example.util.OrderEventBus.incomingOrderEvents.collect { _ ->
                val vId = currentVendorId()
                if (vId != null) {
                    loadOrders(vId)
                    loadDashboard(vId)
                }
            }
        }
        runStartupChecks()
        checkSession()

        // Low-frequency periodic version check (every 30 minutes)
        viewModelScope.launch {
            while (isActive) {
                delay(30 * 60 * 1000L)
                checkAppVersion(bypassThrottle = false)
            }
        }
    }

    fun runStartupChecks() {
        checkAppVersion(bypassThrottle = true)
        checkMaintenanceMode()
    }

    fun checkAppVersion(bypassThrottle: Boolean = false) {
        val now = System.currentTimeMillis()
        val lastCheck = sessionManager.lastVersionCheckTimestamp
        if (!bypassThrottle && (now - lastCheck < 15 * 60 * 1000L)) {
            if (BuildConfig.DEBUG) {
                android.util.Log.d("VendorViewModel", "Throttling version check (last check was ${(now - lastCheck) / 1000}s ago)")
            }
            return
        }

        viewModelScope.launch {
            try {
                val currentCode = BuildConfig.VERSION_CODE
                val currentName = BuildConfig.VERSION_NAME
                val lastDismissedCode = sessionManager.lastDismissedOptionalVersionCode.takeIf { it >= 0 }
                val lastDismissedName = sessionManager.lastDismissedOptionalVersionName
                val cachedForce = sessionManager.cachedForceUpdateInfo

                when (val versionRes = repository.getAppVersionInfo("vendor_app")) {
                    is Resource.Success -> {
                        sessionManager.lastVersionCheckTimestamp = System.currentTimeMillis()
                        val info = versionRes.data
                        val updateState = evaluateUpdateState(
                            currentCode = currentCode,
                            currentName = currentName,
                            info = info,
                            lastDismissedCode = lastDismissedCode,
                            lastDismissedName = lastDismissedName,
                            cachedForceInfo = cachedForce
                        )

                        when (updateState) {
                            is AppUpdateState.ForceUpdate -> {
                                if (info != null) {
                                    sessionManager.cachedForceUpdateInfo = info
                                }
                                _blockingUpdateState.value = BlockingUpdateState(
                                    isBlocked = true,
                                    updateMessage = updateState.message,
                                    minimumSupportedVersion = updateState.requiredVersion,
                                    currentAppVersion = updateState.currentVersion,
                                    updateUrl = updateState.updateUrl
                                )
                                _optionalUpdateState.value = null
                            }
                            is AppUpdateState.OptionalUpdate -> {
                                sessionManager.clearCachedForceUpdate()
                                _blockingUpdateState.value = BlockingUpdateState(isBlocked = false)
                                _optionalUpdateState.value = OptionalUpdateState(
                                    isAvailable = true,
                                    updateMessage = updateState.message,
                                    latestVersion = updateState.latestVersion,
                                    currentVersion = updateState.currentVersion,
                                    updateUrl = updateState.updateUrl,
                                    latestVersionCode = updateState.latestVersionCode
                                )
                            }
                            is AppUpdateState.UpToDate -> {
                                sessionManager.clearCachedForceUpdate()
                                _blockingUpdateState.value = BlockingUpdateState(isBlocked = false)
                                _optionalUpdateState.value = null
                            }
                        }
                    }
                    is Resource.Error -> {
                        if (BuildConfig.DEBUG) {
                            android.util.Log.w("VendorViewModel", "Version check error: ${versionRes.message}")
                        }
                        // Fail-open policy: do not block unless a previous FORCE result remains unsatisfied
                        val fallbackState = evaluateUpdateState(
                            currentCode = currentCode,
                            currentName = currentName,
                            info = null,
                            lastDismissedCode = lastDismissedCode,
                            lastDismissedName = lastDismissedName,
                            cachedForceInfo = cachedForce
                        )

                        if (fallbackState is AppUpdateState.ForceUpdate) {
                            _blockingUpdateState.value = BlockingUpdateState(
                                isBlocked = true,
                                updateMessage = fallbackState.message,
                                minimumSupportedVersion = fallbackState.requiredVersion,
                                currentAppVersion = fallbackState.currentVersion,
                                updateUrl = fallbackState.updateUrl
                            )
                        } else {
                            _blockingUpdateState.value = BlockingUpdateState(isBlocked = false)
                        }
                    }
                    Resource.Loading -> {}
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.w("VendorViewModel", "Exception checking app version: ${e.message}")
                }
            }
        }
    }

    fun dismissOptionalUpdate(latestCode: Int?, latestName: String?) {
        sessionManager.setOptionalUpdateDismissed(latestCode, latestName)
        _optionalUpdateState.value = null
    }

    fun checkMaintenanceMode() {
        viewModelScope.launch {
            try {
                // 2. Platform Maintenance Mode Check (Key: maintenance_mode)
                when (val settingsRes = repository.getMaintenanceModeSetting()) {
                    is Resource.Success -> {
                        val setting = settingsRes.data
                        if (setting?.isMaintenanceEnabled == true) {
                            val msg = setting.maintenanceMessage?.ifBlank { null }
                                ?: "Sndmart is temporarily paused for maintenance — new customer orders are not coming in right now."
                            _maintenanceBannerState.update {
                                it.copy(
                                    isEnabled = true,
                                    message = msg
                                )
                            }
                        } else {
                            _maintenanceBannerState.update {
                                it.copy(isEnabled = false)
                            }
                        }
                    }
                    is Resource.Error -> {
                        if (BuildConfig.DEBUG) {
                            android.util.Log.w("VendorViewModel", "Maintenance check notice: ${settingsRes.message}")
                        }
                    }
                    Resource.Loading -> {}
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.w("VendorViewModel", "Maintenance mode error: ${e.message}")
                }
            }
        }
    }

    fun clearSessionExpiredNotice() {
        sessionManager.sessionExpiredMessage = null
        _sessionExpiredNotice.value = null
    }

    fun handleSessionExpired(reason: String = "Your session expired, please log in again") {
        livePollingJob?.cancel()
        sessionManager.clearSession()
        sessionManager.sessionExpiredMessage = reason
        _sessionExpiredNotice.value = reason
        _authUiState.value = AuthUiState.Unauthenticated
    }

    fun onAppForegrounded() {
        runStartupChecks()
        if (!sessionManager.isLoggedIn) return

        // Register/verify FCM token on every app resume for approved vendor
        val cachedVendor = sessionManager.cachedVendor
        if (cachedVendor != null && cachedVendor.approvalStatus.equals("approved", ignoreCase = true)) {
            registerClientFcmToken(cachedVendor)
        }

        viewModelScope.launch {
            // When returning to the app after being idle for hours,
            // proactively validate or refresh the Supabase session before executing queries
            when (val refreshRes = repository.validateOrRefreshSession()) {
                is Resource.Success -> {
                    val vendorId = currentVendorId()
                    if (vendorId != null) {
                        refreshActiveVendorProfile()
                        refreshAll()
                    }
                }
                is Resource.Error -> {
                    if (refreshRes.code == 401) {
                        handleSessionExpired("Your session expired, please log in again")
                    }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun checkSession() {
        viewModelScope.launch {
            if (!sessionManager.isLoggedIn) {
                _authUiState.value = AuthUiState.Unauthenticated
                return@launch
            }

            _authUiState.value = AuthUiState.Loading

            // Proactively refresh session if access token is expired or close to expiration
            if (sessionManager.isTokenExpiredOrExpiringSoon() || sessionManager.accessToken.isNullOrBlank()) {
                when (val refreshRes = repository.validateOrRefreshSession()) {
                    is Resource.Error -> {
                        if (refreshRes.code == 401) {
                            handleSessionExpired("Your session expired, please log in again")
                            return@launch
                        }
                    }
                    is Resource.Success -> {}
                    Resource.Loading -> {}
                }
            }

            when (val result = repository.fetchVendorProfile()) {
                is Resource.Success -> {
                    processVendorStatus(result.data)
                }
                is Resource.Error -> {
                    if (result.code == 401) {
                        // Attempt one refresh attempt before failing
                        val refreshRes = repository.validateOrRefreshSession()
                        if (refreshRes is Resource.Success) {
                            val retryProfile = repository.fetchVendorProfile()
                            if (retryProfile is Resource.Success) {
                                processVendorStatus(retryProfile.data)
                                return@launch
                            }
                        }
                        handleSessionExpired("Your session expired, please log in again")
                        return@launch
                    }
                    val cached = sessionManager.cachedVendor
                    if (cached != null) {
                        processVendorStatus(cached)
                    } else {
                        _authUiState.value = AuthUiState.Unauthenticated
                    }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun refreshStatus() = checkSession()

    private fun processVendorStatus(vendor: Vendor) {
        if (!vendor.isActive) {
            _authUiState.value = AuthUiState.Deactivated(vendor)
            return
        }

        when (vendor.approvalStatus.lowercase(Locale.ROOT)) {
            "approved" -> {
                _authUiState.value = AuthUiState.Authenticated(vendor)
                _profileState.update { it.copy(vendor = vendor) }
                _dashboardState.update { it.copy(isStoreOpen = vendor.isOpen) }
                loadAllVendorData(vendor.id)
                startAutoPolling(vendor.id)
                registerClientFcmToken(vendor)
            }
            "rejected" -> {
                _authUiState.value = AuthUiState.Rejected(vendor, vendor.rejectionReason)
            }
            else -> { // 'pending' or others
                _authUiState.value = AuthUiState.PendingApproval(vendor)
            }
        }
    }

    fun login(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _authUiState.value = AuthUiState.Loading
            when (val result = repository.login(email, pass)) {
                is Resource.Success -> {
                    processVendorStatus(result.data)
                    onResult(true, null)
                }
                is Resource.Error -> {
                    _authUiState.value = AuthUiState.Unauthenticated
                    onResult(false, result.message)
                }
                Resource.Loading -> {}
            }
        }
    }

    fun logout() {
        val currentVendor = (_authUiState.value as? AuthUiState.Authenticated)?.vendor
            ?: sessionManager.cachedVendor
        if (currentVendor != null) {
            viewModelScope.launch {
                repository.deactivateFcmToken(currentVendor.id)
            }
        }
        livePollingJob?.cancel()
        repository.logout()
        _authUiState.value = AuthUiState.Unauthenticated
        _dashboardState.update { it.copy(isOrderAlertsOn = false) }
        _profileState.update { it.copy(isFcmRegistered = false) }
    }

    fun refreshActiveVendorProfile() {
        viewModelScope.launch {
            when (val result = repository.fetchVendorProfile()) {
                is Resource.Success -> processVendorStatus(result.data)
                is Resource.Error -> {}
                Resource.Loading -> {}
            }
        }
    }

    private fun currentVendorId(): String? {
        return (_authUiState.value as? AuthUiState.Authenticated)?.vendor?.id
            ?: _profileState.value.vendor?.id
            ?: sessionManager.cachedVendor?.id
    }

    fun loadAllVendorData(vendorId: String) {
        // Only load essential initial data on launch; other tabs load lazily
        loadDashboard(vendorId)
        loadOrders(vendorId)
    }

    fun refreshAll() {
        val vendorId = currentVendorId() ?: return
        loadDashboard(vendorId)
        loadOrders(vendorId)
        loadMenu(vendorId, forceRefresh = true)
        loadEarnings(vendorId)
        loadReviews(vendorId)
    }

    // --- Dashboard & Store Toggle ---
    fun loadDashboard(vendorId: String? = null) {
        viewModelScope.launch {
            val vId = vendorId ?: currentVendorId() ?: return@launch
            _dashboardState.update { it.copy(isLoading = true, errorMessage = null) }
            
            // Rule 4: Run independent requests in parallel, not one after another
            try {
                coroutineScope {
                    val (startUtcIso, endUtcIso) = IstDateUtils.getTodayIstRangeUtcStrings()
                    val ordersDeferred = async { repository.getOrders(vId, limit = 20, offset = 0) }
                    val todayOrdersDeferred = async { repository.getTodayOrders(vId, startUtcIso, endUtcIso) }
                    val reviewsDeferred = async { repository.getReviews(vId, limit = 20, offset = 0) }
                    val walletDeferred = async { repository.getVendorWallet() }

                    val ordersRes = ordersDeferred.await()
                    val todayOrdersRes = todayOrdersDeferred.await()
                    val reviewsRes = reviewsDeferred.await()
                    val walletRes = walletDeferred.await()

                    if (ordersRes.code == 401 || reviewsRes.code == 401 || walletRes.code == 401 || todayOrdersRes.code == 401) {
                        handleSessionExpired("Your session expired, please log in again")
                        return@coroutineScope
                    }

                    if (ordersRes is Resource.Error) {
                        _dashboardState.update { it.copy(isLoading = false, errorMessage = ordersRes.message) }
                        return@coroutineScope
                    }

                    val orders = (ordersRes as Resource.Success).data
                    val todayOrders = if (todayOrdersRes is Resource.Success) todayOrdersRes.data else emptyList()
                    val reviews = if (reviewsRes is Resource.Success) reviewsRes.data else emptyList()
                    val avgRating = if (reviews.isNotEmpty()) reviews.map { it.rating }.average() else 0.0

                    // 3.1: loadDashboard fallback: purana balance dikhao agar hai, aur error/retry dikhao, 0.0 nahi
                    val prevBalance = _dashboardState.value.walletBalance
                    val prevHasValid = _dashboardState.value.hasValidWalletBalance
                    val (balance, isWalletError, hasValidWallet) = if (walletRes is Resource.Success) {
                        Triple(walletRes.data.balance, false, true)
                    } else {
                        Triple(prevBalance, true, prevHasValid)
                    }

                    // 3.2: Calculate Today's stats based on server-side filtered IST orders (without 20-order limit)
                    val ordersTodayCount = todayOrders.size
                    val deliveredTodayOrders = todayOrders.filter { it.status?.lowercase() == "delivered" }
                    val deliveredOrdersTodayCount = deliveredTodayOrders.size

                    // Kamai ke liye order ka vendor_amount use karo (subtotal x commission estimate aur hardcoded 25% fallback hatao; vendor_amount na ho to "--" dikhao)
                    val todayEarningsVendorAmount: BigDecimal? = if (deliveredTodayOrders.isEmpty()) {
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    } else {
                        val allHaveVendorAmount = deliveredTodayOrders.all { it.vendor_amount != null }
                        if (allHaveVendorAmount) {
                            deliveredTodayOrders.fold(BigDecimal.ZERO) { acc, o ->
                                acc + (CurrencyFormatter.toBigDecimal(o.vendor_amount) ?: BigDecimal.ZERO)
                            }.setScale(2, RoundingMode.HALF_UP)
                        } else {
                            null
                        }
                    }

                    val foodSalesTodayDelivered = deliveredTodayOrders.sumOf { it.subtotal ?: it.totalAmount ?: 0.0 }
                    val confirmedOrdersCount = orders.count { it.status?.lowercase() == "confirmed" }
                    val vendorCommission = (sessionManager.cachedVendor?.commissionPercent ?: 25.0)

                    _dashboardState.update {
                        it.copy(
                            ordersTodayCount = ordersTodayCount,
                            deliveredOrdersTodayCount = deliveredOrdersTodayCount,
                            earningsTodaySum = todayEarningsVendorAmount?.toDouble() ?: 0.0,
                            todayEarningsVendorAmount = todayEarningsVendorAmount,
                            foodSalesTodayDelivered = foodSalesTodayDelivered,
                            commissionPercent = vendorCommission,
                            confirmedOrdersCount = confirmedOrdersCount,
                            pendingOrdersCount = 0,
                            averageRating = if (avgRating.isNaN()) 0.0 else avgRating,
                            walletBalance = balance,
                            hasValidWalletBalance = hasValidWallet,
                            isWalletBalanceError = isWalletError,
                            walletErrorMessage = if (isWalletError) "Wallet balance unavailable. Tap to retry." else null,
                            recentOrders = orders.take(10),
                            isLoading = false,
                            errorMessage = if (isWalletError && !hasValidWallet) "Failed to load wallet balance" else null
                        )
                    }
                }
            } catch (e: Exception) {
                _dashboardState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Failed to load dashboard") }
            }
        }
    }

    fun toggleStoreOpen(isOpen: Boolean) {
        val vendorId = currentVendorId() ?: return
        viewModelScope.launch {
            _dashboardState.update { it.copy(isStoreOpen = isOpen) }
            when (val res = repository.updateStoreOpenStatus(vendorId, isOpen)) {
                is Resource.Success -> {
                    _profileState.update { it.copy(vendor = res.data) }
                }
                is Resource.Error -> {
                    // revert
                    _dashboardState.update { it.copy(isStoreOpen = !isOpen, errorMessage = res.message) }
                }
                Resource.Loading -> {}
            }
        }
    }

    // --- Menu (Rule 1: Paginated 30 at a time, Rule 4: Parallel Fetching) ---
    fun loadMenu(vendorId: String? = null, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val vId = vendorId ?: currentVendorId() ?: return@launch
            _menuState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                coroutineScope {
                    val catDeferred = async { repository.getCategories(vId, forceRefresh = forceRefresh) }
                    val prodDeferred = async { repository.getProducts(vId, limit = 30, offset = 0) }

                    val catRes = catDeferred.await()
                    val prodRes = prodDeferred.await()

                    if (catRes.code == 401 || prodRes.code == 401) {
                        handleSessionExpired("Your session expired, please log in again")
                        return@coroutineScope
                    }

                    if (catRes is Resource.Error) {
                        _menuState.update { it.copy(isLoading = false, errorMessage = catRes.message) }
                        return@coroutineScope
                    }

                    if (prodRes is Resource.Error) {
                        _menuState.update { it.copy(isLoading = false, errorMessage = prodRes.message) }
                        return@coroutineScope
                    }

                    val cats = (catRes as Resource.Success).data
                    val prods = (prodRes as Resource.Success).data

                    _menuState.update {
                        it.copy(
                            categories = cats,
                            products = prods,
                            page = 0,
                            canLoadMore = prods.size >= 30,
                            isLoadingMore = false,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
            } catch (e: Exception) {
                _menuState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Failed to load menu") }
            }
        }
    }

    fun loadMoreProducts() {
        val vId = currentVendorId() ?: return
        val currentState = _menuState.value
        if (!currentState.canLoadMore || currentState.isLoadingMore || currentState.isLoading) return

        viewModelScope.launch {
            _menuState.update { it.copy(isLoadingMore = true) }
            val nextPage = currentState.page + 1
            val offset = nextPage * 30
            when (val res = repository.getProducts(vId, limit = 30, offset = offset)) {
                is Resource.Success -> {
                    val newItems = res.data
                    val combined = (currentState.products + newItems).distinctBy { it.id }
                    _menuState.update {
                        it.copy(
                            products = combined,
                            page = nextPage,
                            canLoadMore = newItems.size >= 30,
                            isLoadingMore = false
                        )
                    }
                }
                is Resource.Error -> {
                    _menuState.update { it.copy(isLoadingMore = false) }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun setMenuSearchQuery(query: String) {
        _menuState.update { it.copy(searchQuery = query) }
    }

    fun uploadCategoryImage(
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String,
        onComplete: (Boolean, String?, String?) -> Unit
    ) {
        val vendorId = currentVendorId()
        if (vendorId == null) {
            onComplete(false, null, "Vendor session missing. Please log in.")
            return
        }
        viewModelScope.launch {
            when (val res = repository.uploadCategoryImage(vendorId, fileName, fileBytes, mimeType)) {
                is Resource.Success -> onComplete(true, res.data, null)
                is Resource.Error -> onComplete(false, null, res.message)
                Resource.Loading -> {}
            }
        }
    }

    fun uploadProductImage(
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String,
        onComplete: (Boolean, String?, String?) -> Unit
    ) {
        val vendorId = currentVendorId()
        if (vendorId == null) {
            onComplete(false, null, "Vendor session missing. Please log in.")
            return
        }
        viewModelScope.launch {
            when (val res = repository.uploadProductImage(vendorId, fileName, fileBytes, mimeType)) {
                is Resource.Success -> onComplete(true, res.data, null)
                is Resource.Error -> onComplete(false, null, res.message)
                Resource.Loading -> {}
            }
        }
    }

    fun uploadStoreBanner(
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String,
        onComplete: (Boolean, String?, String?) -> Unit
    ) {
        val vendorId = currentVendorId()
        if (vendorId == null) {
            onComplete(false, null, "Vendor session missing. Please log in.")
            return
        }
        viewModelScope.launch {
            when (val res = repository.uploadStoreBanner(vendorId, fileName, fileBytes, mimeType)) {
                is Resource.Success -> onComplete(true, res.data, null)
                is Resource.Error -> onComplete(false, null, res.message)
                Resource.Loading -> {}
            }
        }
    }

    fun addCategory(name: String, imageUrl: String, onComplete: (Boolean, String?) -> Unit) {
        val vendorId = currentVendorId()
        if (vendorId == null) {
            val err = "No active vendor profile found. Please re-login."
            _menuState.update { it.copy(isSubmitting = false, errorMessage = err) }
            onComplete(false, err)
            return
        }
        val cityId = _profileState.value.vendor?.cityId ?: sessionManager.cachedVendor?.cityId
        viewModelScope.launch {
            _menuState.update { it.copy(isSubmitting = true) }
            when (val res = repository.createCategory(vendorId, name, imageUrl, cityId)) {
                is Resource.Success -> {
                    _menuState.update { it.copy(categories = it.categories + res.data, isSubmitting = false, successMessage = "Category added successfully") }
                    onComplete(true, null)
                }
                is Resource.Error -> {
                    _menuState.update { it.copy(isSubmitting = false, errorMessage = res.message) }
                    onComplete(false, res.message)
                }
                Resource.Loading -> {}
            }
        }
    }

    fun updateCategory(id: String, name: String, imageUrl: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _menuState.update { it.copy(isSubmitting = true) }
            when (val res = repository.updateCategory(id, name, imageUrl)) {
                is Resource.Success -> {
                    _menuState.update {
                        it.copy(
                            categories = it.categories.map { c -> if (c.id == id) res.data else c },
                            isSubmitting = false,
                            successMessage = "Category updated"
                        )
                    }
                    onComplete(true, null)
                }
                is Resource.Error -> {
                    _menuState.update { it.copy(isSubmitting = false, errorMessage = res.message) }
                    onComplete(false, res.message)
                }
                Resource.Loading -> {}
            }
        }
    }

    fun deleteCategory(id: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _menuState.update { it.copy(isSubmitting = true) }
            when (val res = repository.deleteCategory(id)) {
                is Resource.Success -> {
                    _menuState.update {
                        it.copy(
                            categories = it.categories.filter { c -> c.id != id },
                            isSubmitting = false,
                            successMessage = "Category deleted"
                        )
                    }
                    onComplete(true, null)
                }
                is Resource.Error -> {
                    _menuState.update { it.copy(isSubmitting = false, errorMessage = res.message) }
                    onComplete(false, res.message)
                }
                Resource.Loading -> {}
            }
        }
    }

    fun addProduct(
        categoryId: String?,
        name: String,
        description: String?,
        price: Double,
        imageUrl: String?,
        isActive: Boolean,
        availableFrom: String?,
        availableUntil: String?,
        stockQty: Int = 100,
        isAvailable: Boolean = true,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val vendorId = currentVendorId() ?: return
        viewModelScope.launch {
            _menuState.update { it.copy(isSubmitting = true) }
            when (val res = repository.createProduct(vendorId, categoryId, name, description, price, imageUrl, isActive, availableFrom, availableUntil, stockQty, isAvailable)) {
                is Resource.Success -> {
                    _menuState.update { it.copy(products = listOf(res.data) + it.products, isSubmitting = false, successMessage = "Product added successfully with $stockQty stock") }
                    onComplete(true, null)
                }
                is Resource.Error -> {
                    _menuState.update { it.copy(isSubmitting = false, errorMessage = res.message) }
                    onComplete(false, res.message)
                }
                Resource.Loading -> {}
            }
        }
    }

    fun quickRestockProduct(productId: String, targetStock: Int = 100, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        val updates = mapOf(
            "stock_qty" to targetStock,
            "is_available" to true
        )
        updateProduct(productId, updates) { success, err ->
            if (success) {
                _menuState.update { it.copy(successMessage = "Restocked item to $targetStock portions") }
            }
            onComplete(success, err)
        }
    }

    fun restockZeroStockProducts(targetStock: Int = 100, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        val zeroItems = _menuState.value.products.filter { it.isSoldOut }
        if (zeroItems.isEmpty()) {
            _menuState.update { it.copy(successMessage = "All items already have stock!") }
            onComplete(true, null)
            return
        }
        viewModelScope.launch {
            _menuState.update { it.copy(isSubmitting = true) }
            val updates = mapOf(
                "stock_qty" to targetStock,
                "is_available" to true
            )
            val results: List<Pair<String, Resource<Product>>> = coroutineScope {
                zeroItems.map { item ->
                    async { item.id to repository.updateProduct(item.id, updates) }
                }.awaitAll()
            }
            var successCount = 0
            var lastError: String? = null
            val updatedMap = mutableMapOf<String, Product>()
            for (entry in results) {
                val id = entry.first
                when (val res = entry.second) {
                    is Resource.Success<Product> -> {
                        successCount++
                        updatedMap[id] = res.data
                    }
                    is Resource.Error -> {
                        lastError = res.message
                    }
                    Resource.Loading -> {}
                }
            }
            if (updatedMap.isNotEmpty()) {
                _menuState.update { s ->
                    s.copy(products = s.products.map { p -> updatedMap[p.id] ?: p })
                }
            }
            _menuState.update {
                it.copy(
                    isSubmitting = false,
                    successMessage = if (successCount > 0) "Successfully restocked $successCount item(s) to $targetStock portions!" else null,
                    errorMessage = if (successCount == 0) lastError else null
                )
            }
            onComplete(successCount > 0, lastError)
        }
    }

    fun updateProduct(productId: String, updates: Map<String, Any?>, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _menuState.update { it.copy(isSubmitting = true) }
            when (val res = repository.updateProduct(productId, updates)) {
                is Resource.Success -> {
                    _menuState.update {
                        it.copy(
                            products = it.products.map { p -> if (p.id == productId) res.data else p },
                            isSubmitting = false,
                            successMessage = "Product updated"
                        )
                    }
                    onComplete(true, null)
                }
                is Resource.Error -> {
                    _menuState.update { it.copy(isSubmitting = false, errorMessage = res.message) }
                    onComplete(false, res.message)
                }
                Resource.Loading -> {}
            }
        }
    }

    fun toggleProductActive(product: Product) {
        val newStatus = !product.isActive
        viewModelScope.launch {
            // Optimistic update
            _menuState.update {
                it.copy(products = it.products.map { p -> if (p.id == product.id) p.copy(isActive = newStatus) else p })
            }
            when (val res = repository.updateProduct(product.id, mapOf("is_active" to newStatus))) {
                is Resource.Success -> {}
                is Resource.Error -> {
                    // rollback
                    _menuState.update {
                        it.copy(products = it.products.map { p -> if (p.id == product.id) product else p }, errorMessage = res.message)
                    }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun deleteProduct(productId: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _menuState.update { it.copy(isSubmitting = true) }
            when (val res = repository.deleteProduct(productId)) {
                is Resource.Success -> {
                    _menuState.update {
                        it.copy(
                            products = it.products.filter { p -> p.id != productId },
                            isSubmitting = false,
                            successMessage = "Product deleted"
                        )
                    }
                    onComplete(true, null)
                }
                is Resource.Error -> {
                    _menuState.update { it.copy(isSubmitting = false, errorMessage = res.message) }
                    onComplete(false, res.message)
                }
                Resource.Loading -> {}
            }
        }
    }

    // --- Orders (Rule 1: Paginated 20 at a time) ---
    fun loadOrders(vendorId: String? = null) {
        viewModelScope.launch {
            val vId = vendorId ?: currentVendorId() ?: return@launch
            _ordersState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val res = repository.getOrders(vId, limit = 20, offset = 0)) {
                is Resource.Success -> {
                    val confirmedOrders = res.data.filter { it.status?.lowercase() == "confirmed" }
                    val confirmedCount = confirmedOrders.size
                    if (confirmedCount > 0 && (confirmedCount > lastKnownConfirmedCount || lastKnownConfirmedCount == 0)) {
                        _inAppOrderAlert.value = "🛎️ New Order - Please Prepare!"
                        soundAlertManager.startRepeatingOrderAlert(
                            scope = viewModelScope,
                            isSoundEnabled = { sessionManager.isSoundAlertEnabled },
                            hasPendingOrders = { _ordersState.value.orders.any { it.status?.lowercase() == "confirmed" } }
                        )
                    } else if (confirmedCount == 0) {
                        soundAlertManager.stopRepeatingAlert()
                    }
                    lastKnownConfirmedCount = confirmedCount

                    _ordersState.update {
                        it.copy(
                            orders = res.data,
                            page = 0,
                            canLoadMore = res.data.size >= 20,
                            isLoadingMore = false,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
                is Resource.Error -> {
                    if (res.code == 401) {
                        handleSessionExpired("Your session expired, please log in again")
                    } else {
                        _ordersState.update { it.copy(isLoading = false, errorMessage = res.message) }
                    }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun loadMoreOrders() {
        val vId = currentVendorId() ?: return
        val currentState = _ordersState.value
        if (!currentState.canLoadMore || currentState.isLoadingMore || currentState.isLoading) return

        viewModelScope.launch {
            _ordersState.update { it.copy(isLoadingMore = true) }
            val nextPage = currentState.page + 1
            val offset = nextPage * 20
            when (val res = repository.getOrders(vId, limit = 20, offset = offset)) {
                is Resource.Success -> {
                    val newItems = res.data
                    val combined = (currentState.orders + newItems).distinctBy { it.id }
                    _ordersState.update {
                        it.copy(
                            orders = combined,
                            page = nextPage,
                            canLoadMore = newItems.size >= 20,
                            isLoadingMore = false
                        )
                    }
                }
                is Resource.Error -> {
                    _ordersState.update { it.copy(isLoadingMore = false) }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun setOrdersSearchQuery(query: String) {
        _ordersState.update { it.copy(searchQuery = query) }
    }

    fun selectOrder(order: Order?) {
        _ordersState.update { it.copy(selectedOrder = order, selectedOrderHistory = emptyList(), isLoadingHistory = order != null) }
        if (order?.status?.lowercase() in listOf("confirmed", "preparing")) {
            soundAlertManager.stopRepeatingAlert()
        }
        if (order != null) {
            viewModelScope.launch {
                when (val historyRes = repository.getOrderStatusHistory(order.id)) {
                    is Resource.Success -> {
                        _ordersState.update { it.copy(selectedOrderHistory = historyRes.data, isLoadingHistory = false) }
                    }
                    is Resource.Error -> {
                        _ordersState.update { it.copy(isLoadingHistory = false) }
                    }
                    Resource.Loading -> {}
                }
            }
        }
    }

    fun selectOrderForDetails(order: Order?) = selectOrder(order)

    fun setOrdersFilter(filter: String) {
        _ordersState.update { it.copy(activeFilter = filter) }
    }

    fun updateOrderStatus(
        orderId: String,
        newStatus: String,
        notes: String? = null,
        cancellationReason: String? = null,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            _ordersState.update { it.copy(isUpdating = true) }
            when (val res = repository.updateOrderStatus(orderId, newStatus, notes, cancellationReason)) {
                is Resource.Success -> {
                    _ordersState.update {
                        val updatedList = it.orders.map { o ->
                            if (o.id == orderId) o.copy(status = newStatus, cancellation_reason = cancellationReason ?: o.cancellationReason)
                            else o
                        }
                        it.copy(
                            orders = updatedList,
                            selectedOrder = if (it.selectedOrder?.id == orderId) it.selectedOrder.copy(status = newStatus) else it.selectedOrder,
                            isUpdating = false,
                            successMessage = "Order marked as $newStatus"
                        )
                    }
                    val vId = currentVendorId()
                    if (vId != null) loadDashboard(vId)
                    if (_ordersState.value.selectedOrder?.id == orderId) {
                        viewModelScope.launch {
                            val historyRes = repository.getOrderStatusHistory(orderId)
                            if (historyRes is Resource.Success) {
                                _ordersState.update { it.copy(selectedOrderHistory = historyRes.data) }
                            }
                        }
                    }
                    onComplete(true, null)
                }
                is Resource.Error -> {
                    _ordersState.update { it.copy(isUpdating = false, errorMessage = res.message) }
                    onComplete(false, res.message)
                }
                Resource.Loading -> {}
            }
        }
    }

    // --- Earnings & Withdrawals (Rule 1: Paginated 20 at a time, Rule 4: Parallel Fetching) ---
    fun loadEarnings(vendorId: String? = null) {
        viewModelScope.launch {
            val vId = vendorId ?: currentVendorId() ?: return@launch
            _earningsState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                coroutineScope {
                    val walletDeferred = async { repository.getVendorWallet() }
                    val txDeferred = async { repository.getWalletTransactions(vId, limit = 20, offset = 0) }
                    val payDeferred = async { repository.getPayouts(vId, limit = 20, offset = 0) }
                    val withDeferred = async { repository.getWithdrawals(vId, limit = 50, offset = 0) }
                    val vendorDeferred = async { repository.fetchVendorProfile() }

                    val walletRes = walletDeferred.await()
                    val txRes = txDeferred.await()
                    val payRes = payDeferred.await()
                    val withRes = withDeferred.await()
                    val vendorRes = vendorDeferred.await()

                    if (walletRes.code == 401 || txRes.code == 401 || payRes.code == 401 || withRes.code == 401) {
                        handleSessionExpired("Your session expired, please log in again")
                        return@coroutineScope
                    }

                    if (txRes is Resource.Error) {
                        _earningsState.update { it.copy(isLoading = false, errorMessage = txRes.message) }
                        return@coroutineScope
                    }
                    if (payRes is Resource.Error) {
                        _earningsState.update { it.copy(isLoading = false, errorMessage = payRes.message) }
                        return@coroutineScope
                    }
                    if (withRes is Resource.Error) {
                        _earningsState.update { it.copy(isLoading = false, errorMessage = withRes.message) }
                        return@coroutineScope
                    }

                    val txs = if (txRes is Resource.Success) txRes.data else _earningsState.value.walletTransactions
                    val pays = if (payRes is Resource.Success) payRes.data else _earningsState.value.payouts
                    val withs = if (withRes is Resource.Success) withRes.data else _earningsState.value.withdrawals

                    val inProgressList = withs.filter { it.status.lowercase() in listOf("pending", "approved") }
                    val inProgressSum = inProgressList.sumOf { it.amount }

                    // 3.1: Fallback 20 transactions sum hatao. Fail hone par purana balance dikhao (agar hai) aur error/retry dikhao
                    val prevBalance = _earningsState.value.runningBalance
                    val prevHasValid = _earningsState.value.hasValidWalletBalance
                    val (balance, isWalletError, hasValidBalance) = if (walletRes is Resource.Success) {
                        Triple(walletRes.data.balance, false, true)
                    } else {
                        Triple(prevBalance, true, prevHasValid)
                    }

                    val available = (balance - inProgressSum).coerceAtLeast(0.0)

                    val vendor = if (vendorRes is Resource.Success) vendorRes.data else (_authUiState.value as? AuthUiState.Authenticated)?.vendor ?: sessionManager.cachedVendor
                    val commPercent = vendor?.commissionPercent?.takeIf { it > 0.0 } ?: 25.0

                    _earningsState.update {
                        it.copy(
                            walletTransactions = txs,
                            payouts = pays,
                            withdrawals = withs,
                            runningBalance = balance,
                            hasValidWalletBalance = hasValidBalance,
                            isWalletBalanceError = isWalletError,
                            walletErrorMessage = if (isWalletError) "Could not update wallet balance from server" else null,
                            inProgressWithdrawalsSum = inProgressSum,
                            availableToWithdraw = available,
                            commissionPercent = commPercent,
                            activeWithdrawalInProgress = inProgressList.firstOrNull(),
                            page = 0,
                            canLoadMoreTransactions = (txRes as? Resource.Success)?.data?.size?.let { s -> s >= 20 } ?: false,
                            isLoadingMoreTransactions = false,
                            isLoading = false,
                            errorMessage = when {
                                isWalletError && !hasValidBalance -> "Wallet balance unavailable. Tap to retry."
                                txRes is Resource.Error -> txRes.message
                                payRes is Resource.Error -> payRes.message
                                withRes is Resource.Error -> withRes.message
                                else -> null
                            }
                        )
                    }

                    if (!isWalletError) {
                        // Keep dashboard balance in sync with total server balance
                        _dashboardState.update { it.copy(walletBalance = balance, hasValidWalletBalance = true, isWalletBalanceError = false) }
                    }
                }
            } catch (e: Exception) {
                _earningsState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Failed to load earnings") }
            }
        }
    }

    fun loadMoreTransactions() {
        val vId = currentVendorId() ?: return
        val currentState = _earningsState.value
        if (!currentState.canLoadMoreTransactions || currentState.isLoadingMoreTransactions || currentState.isLoading) return

        viewModelScope.launch {
            _earningsState.update { it.copy(isLoadingMoreTransactions = true) }
            val nextPage = currentState.page + 1
            val offset = nextPage * 20
            when (val res = repository.getWalletTransactions(vId, limit = 20, offset = offset)) {
                is Resource.Success -> {
                    val newItems = res.data
                    val combined = (currentState.walletTransactions + newItems).distinctBy { it.id }
                    _earningsState.update {
                        it.copy(
                            walletTransactions = combined,
                            page = nextPage,
                            canLoadMoreTransactions = newItems.size >= 20,
                            isLoadingMoreTransactions = false
                        )
                    }
                }
                is Resource.Error -> {
                    _earningsState.update { it.copy(isLoadingMoreTransactions = false) }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun requestWithdrawal(amount: Double, note: String?, onComplete: (Boolean, String?) -> Unit) {
        val vendor = (_authUiState.value as? AuthUiState.Authenticated)?.vendor
            ?: sessionManager.cachedVendor ?: return
        val currentState = _earningsState.value

        if (currentState.isSubmittingWithdrawal) return

        val amountBigDecimal = CurrencyFormatter.toBigDecimal(amount) ?: BigDecimal.ZERO
        val availableBigDecimal = CurrencyFormatter.toBigDecimal(currentState.availableToWithdraw) ?: BigDecimal.ZERO

        // Only one open request in progress allowed
        if (currentState.activeWithdrawalInProgress != null) {
            val msg = "You already have a withdrawal request of ${CurrencyFormatter.format(currentState.activeWithdrawalInProgress.amount)} in progress. Please wait for City Admin to process it before requesting another."
            _earningsState.update { it.copy(errorMessage = msg) }
            onComplete(false, msg)
            return
        }

        if (amountBigDecimal <= BigDecimal.ZERO) {
            val msg = "Withdrawal amount must be greater than 0"
            _earningsState.update { it.copy(errorMessage = msg) }
            onComplete(false, msg)
            return
        }

        if (amountBigDecimal > availableBigDecimal) {
            val msg = "Withdrawal amount ${CurrencyFormatter.format(amountBigDecimal)} exceeds available balance ${CurrencyFormatter.format(availableBigDecimal)}"
            _earningsState.update { it.copy(errorMessage = msg) }
            onComplete(false, msg)
            return
        }

        // 3.5: Client UUID request_id for idempotent withdrawal
        val clientRequestId = UUID.randomUUID().toString()

        // Button submit hote hi synchronously disable ho
        _earningsState.update { it.copy(isSubmittingWithdrawal = true, errorMessage = null) }

        viewModelScope.launch {
            when (val res = repository.requestWithdrawal(
                vendorId = vendor.id,
                cityId = vendor.cityId,
                amount = amountBigDecimal.toDouble(),
                note = note,
                requestId = clientRequestId
            )) {
                is Resource.Success -> {
                    _earningsState.update {
                        it.copy(
                            withdrawals = listOf(res.data) + it.withdrawals.filter { w -> w.id != res.data.id },
                            isSubmittingWithdrawal = false,
                            successMessage = "Withdrawal request submitted for approval"
                        )
                    }
                    loadEarnings(vendor.id)
                    onComplete(true, null)
                }
                is Resource.Error -> {
                    // Timeout/network error recovery: pehle withdrawals dobara fetch karke check karo ki request ban chuki hai ya nahi
                    val checkRes = repository.getWithdrawals(vendor.id, limit = 5, offset = 0)
                    if (checkRes is Resource.Success) {
                        val matchingWithdrawal = checkRes.data.firstOrNull { w ->
                            (w.requestId != null && w.requestId == clientRequestId) ||
                            (w.status.lowercase() == "pending" && Math.abs(w.amount - amountBigDecimal.toDouble()) < 0.01)
                        }
                        if (matchingWithdrawal != null) {
                            _earningsState.update {
                                it.copy(
                                    withdrawals = checkRes.data,
                                    isSubmittingWithdrawal = false,
                                    successMessage = "Withdrawal request submitted for approval"
                                )
                            }
                            loadEarnings(vendor.id)
                            onComplete(true, null)
                            return@launch
                        }
                    }

                    _earningsState.update { it.copy(isSubmittingWithdrawal = false, errorMessage = res.message) }
                    onComplete(false, res.message)
                }
                Resource.Loading -> {}
            }
        }
    }

    // --- Reviews (Rule 1: Paginated 20 at a time) ---
    fun loadReviews(vendorId: String? = null) {
        viewModelScope.launch {
            val vId = vendorId ?: currentVendorId() ?: return@launch
            _reviewsState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val res = repository.getReviews(vId, limit = 20, offset = 0)) {
                is Resource.Success -> {
                    val reviews = res.data
                    val avg = if (reviews.isNotEmpty()) reviews.map { it.rating }.average() else 0.0
                    _reviewsState.update {
                        it.copy(
                            reviews = reviews,
                            averageRating = if (avg.isNaN()) 0.0 else avg,
                            totalCount = reviews.size,
                            page = 0,
                            canLoadMore = reviews.size >= 20,
                            isLoadingMore = false,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
                is Resource.Error -> {
                    if (res.code == 401) {
                        handleSessionExpired("Your session expired, please log in again")
                    } else {
                        _reviewsState.update { it.copy(isLoading = false, errorMessage = res.message) }
                    }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun loadMoreReviews() {
        val vId = currentVendorId() ?: return
        val currentState = _reviewsState.value
        if (!currentState.canLoadMore || currentState.isLoadingMore || currentState.isLoading) return

        viewModelScope.launch {
            _reviewsState.update { it.copy(isLoadingMore = true) }
            val nextPage = currentState.page + 1
            val offset = nextPage * 20
            when (val res = repository.getReviews(vId, limit = 20, offset = offset)) {
                is Resource.Success -> {
                    val newItems = res.data
                    val combined = (currentState.reviews + newItems).distinctBy { it.id }
                    val avg = if (combined.isNotEmpty()) combined.map { it.rating }.average() else 0.0
                    _reviewsState.update {
                        it.copy(
                            reviews = combined,
                            averageRating = if (avg.isNaN()) 0.0 else avg,
                            page = nextPage,
                            canLoadMore = newItems.size >= 20,
                            isLoadingMore = false
                        )
                    }
                }
                is Resource.Error -> {
                    _reviewsState.update { it.copy(isLoadingMore = false) }
                }
                Resource.Loading -> {}
            }
        }
    }

    // --- Store Profile & Operating Hours ---
    fun loadProfile() {
        viewModelScope.launch {
            _profileState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val res = repository.fetchVendorProfile()) {
                is Resource.Success -> {
                    _profileState.update { it.copy(vendor = res.data, isLoading = false, errorMessage = null) }
                    _authUiState.value = AuthUiState.Authenticated(res.data)
                }
                is Resource.Error -> {
                    if (res.code == 401) {
                        handleSessionExpired("Your session expired, please log in again")
                    } else {
                        _profileState.update { it.copy(isLoading = false, errorMessage = res.message) }
                    }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun updateVendorProfile(
        name: String,
        ownerName: String?,
        phone: String?,
        address: String?,
        latitude: Double?,
        longitude: Double?,
        openingTime: String?,
        closingTime: String?,
        bannerUrl: String?,
        cityId: String? = null,
        onComplete: (Boolean, String?) -> Unit
    ) {
        updateStoreProfile(name, ownerName, phone, address, latitude, longitude, openingTime, closingTime, bannerUrl, cityId, onComplete)
    }

    fun updateStoreProfile(
        name: String,
        ownerName: String?,
        phone: String?,
        address: String?,
        latitude: Double?,
        longitude: Double?,
        openingTime: String?,
        closingTime: String?,
        bannerUrl: String?,
        cityId: String? = null,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val vendorId = currentVendorId() ?: return
        viewModelScope.launch {
            _profileState.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }

            // Ensure vendor's current city_id is carried over, never null
            val resolvedCityId = cityId?.ifBlank { null }
                ?: _profileState.value.vendor?.cityId
                ?: sessionManager.cachedVendor?.cityId

            when (val res = repository.updateStoreProfile(
                vendorId = vendorId,
                name = name,
                ownerName = ownerName,
                phone = phone,
                address = address,
                latitude = latitude,
                longitude = longitude,
                openingTime = openingTime,
                closingTime = closingTime,
                bannerUrl = bannerUrl,
                cityId = resolvedCityId
            )) {
                is Resource.Success -> {
                    _profileState.update { it.copy(vendor = res.data, isSaving = false, successMessage = "Store profile updated successfully", errorMessage = null) }
                    _authUiState.value = AuthUiState.Authenticated(res.data)
                    onComplete(true, null)
                }
                is Resource.Error -> {
                    _profileState.update { it.copy(isSaving = false, errorMessage = res.message, successMessage = null) }
                    onComplete(false, res.message)
                }
                Resource.Loading -> {}
            }
        }
    }

    fun loadOperatingHours(vendorId: String? = null) {
        viewModelScope.launch {
            val vId = vendorId ?: currentVendorId() ?: return@launch
            when (val res = repository.getOperatingHours(vId)) {
                is Resource.Success -> _profileState.update { it.copy(operatingHours = res.data) }
                is Resource.Error -> {}
                Resource.Loading -> {}
            }
        }
    }

    fun addOperatingHour(
        startTime: String,
        endTime: String,
        isActive: Boolean = true,
        isClosed: Boolean = false,
        dayOfWeek: String? = null,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val vendorId = currentVendorId()
        if (vendorId.isNullOrBlank()) {
            val errMsg = "Active vendor profile not found. Please ensure you are logged in."
            _profileState.update { it.copy(errorMessage = errMsg) }
            onComplete(false, errMsg)
            return
        }
        viewModelScope.launch {
            _profileState.update { it.copy(errorMessage = null) }
            when (val res = repository.addOperatingHour(vendorId, startTime, endTime, isActive)) {
                is Resource.Success -> {
                    val saved = if (dayOfWeek != null) res.data.copy(dayOfWeek = dayOfWeek) else res.data
                    _profileState.update { it.copy(operatingHours = it.operatingHours + saved, successMessage = "Operating hour slot added") }
                    onComplete(true, null)
                }
                is Resource.Error -> {
                    android.util.Log.e("VendorViewModel", "Add operating hour error: ${res.message}")
                    _profileState.update { it.copy(errorMessage = res.message) }
                    onComplete(false, res.message)
                }
                Resource.Loading -> {}
            }
        }
    }

    fun updateOperatingHour(
        id: String,
        startTime: String,
        endTime: String,
        isActive: Boolean = true,
        isClosed: Boolean = false,
        dayOfWeek: String? = null,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _profileState.update { it.copy(errorMessage = null) }
            when (val res = repository.updateOperatingHour(id, startTime, endTime, isActive)) {
                is Resource.Success -> {
                    val saved = if (dayOfWeek != null) res.data.copy(dayOfWeek = dayOfWeek) else res.data
                    _profileState.update {
                        it.copy(
                            operatingHours = it.operatingHours.map { h -> if (h.id == id) saved else h },
                            successMessage = "Operating hour slot updated"
                        )
                    }
                    onComplete(true, null)
                }
                is Resource.Error -> {
                    android.util.Log.e("VendorViewModel", "Update operating hour error: ${res.message}")
                    _profileState.update { it.copy(errorMessage = res.message) }
                    onComplete(false, res.message)
                }
                Resource.Loading -> {}
            }
        }
    }

    fun toggleOperatingHourActive(hour: OperatingHour) {
        val newActive = !hour.isActive
        viewModelScope.launch {
            // Optimistic update
            _profileState.update {
                it.copy(operatingHours = it.operatingHours.map { h -> if (h.id == hour.id) h.copy(isActive = newActive) else h })
            }
            when (val res = repository.toggleOperatingHourActive(hour.id, newActive)) {
                is Resource.Success -> {}
                is Resource.Error -> {
                    // Revert
                    _profileState.update {
                        it.copy(operatingHours = it.operatingHours.map { h -> if (h.id == hour.id) hour else h }, errorMessage = res.message)
                    }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun deleteOperatingHour(id: String, onComplete: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            when (val res = repository.deleteOperatingHour(id)) {
                is Resource.Success -> {
                    _profileState.update { it.copy(operatingHours = it.operatingHours.filter { h -> h.id != id }, successMessage = "Operating hour slot deleted") }
                    onComplete?.invoke(true, null)
                }
                is Resource.Error -> {
                    _profileState.update { it.copy(errorMessage = res.message) }
                    onComplete?.invoke(false, res.message)
                }
                Resource.Loading -> {}
            }
        }
    }

    // --- FCM Push Notification Registration ---
    private fun isEmulatorOrPlayServicesUnavailable(): Boolean {
        val isEmulator = (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.FINGERPRINT.contains("test-keys")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MODEL.contains("Cuttlefish")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || (Build.BRAND.startsWith("google") && Build.DEVICE.contains("generic"))
                || "google_sdk" == Build.PRODUCT
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("emulator")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.HARDWARE.contains("cutf"))

        if (isEmulator) return true

        return try {
            val code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getApplication())
            code != ConnectionResult.SUCCESS
        } catch (_: Throwable) {
            true
        }
    }

    private var fcmRegistrationRetryCount = 0
    private var isFcmRegistering = false

    fun registerClientFcmToken(vendor: Vendor) {
        val email = sessionManager.userEmail ?: "${vendor.id}@sndmart.vendor"

        if (isFcmRegistering) {
            android.util.Log.d("VendorViewModel", "FCM token registration already in progress")
            return
        }

        viewModelScope.launch {
            isFcmRegistering = true
            try {
                // Fetch real Firebase token without fake fallback
                val token = kotlinx.coroutines.suspendCancellableCoroutine<String?> { cont ->
                    try {
                        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                            if (task.isSuccessful && !task.result.isNullOrBlank()) {
                                if (cont.isActive) cont.resume(task.result, null)
                            } else {
                                android.util.Log.w("VendorViewModel", "FCM token retrieval failed: ${task.exception?.message}")
                                if (cont.isActive) cont.resume(null, null)
                            }
                        }
                    } catch (t: Throwable) {
                        android.util.Log.e("VendorViewModel", "FirebaseMessaging token exception", t)
                        if (cont.isActive) cont.resume(null, null)
                    }
                }

                if (!token.isNullOrBlank()) {
                    sessionManager.fcmToken = token
                    _profileState.update { it.copy(fcmToken = token) }

                    when (val res = repository.registerFcmToken(vendor.id, email, token)) {
                        is Resource.Success -> {
                            android.util.Log.i("VendorViewModel", "FCM token registered successfully for vendor ${vendor.id}")
                            fcmRegistrationRetryCount = 0
                            _profileState.update { it.copy(isFcmRegistered = true) }
                            val hasPerm = _dashboardState.value.hasNotificationPermission
                            _dashboardState.update {
                                it.copy(
                                    isOrderAlertsOn = hasPerm,
                                    orderAlertsOffReason = if (hasPerm) null else "Notification permission denied"
                                )
                            }
                        }
                        is Resource.Error -> {
                            android.util.Log.e("VendorViewModel", "Failed to register FCM token with backend: ${res.message}")
                            _profileState.update { it.copy(isFcmRegistered = false) }
                            val hasPerm = _dashboardState.value.hasNotificationPermission
                            val reason = if (!hasPerm) "Notification permission denied" else "Push token not registered on server"
                            _dashboardState.update {
                                it.copy(
                                    isOrderAlertsOn = false,
                                    orderAlertsOffReason = reason
                                )
                            }
                            retryFcmRegistrationIfNeeded(vendor)
                        }
                        Resource.Loading -> {}
                    }
                } else {
                    android.util.Log.w("VendorViewModel", "No valid FCM token obtained. Order alerts: OFF")
                    _profileState.update { it.copy(isFcmRegistered = false) }
                    val hasPerm = _dashboardState.value.hasNotificationPermission
                    val reason = if (!hasPerm) "Notification permission denied" else "Push token not registered on server"
                    _dashboardState.update {
                        it.copy(
                            isOrderAlertsOn = false,
                            orderAlertsOffReason = reason
                        )
                    }
                    retryFcmRegistrationIfNeeded(vendor)
                }
            } catch (e: Exception) {
                android.util.Log.e("VendorViewModel", "Exception during FCM registration", e)
                _profileState.update { it.copy(isFcmRegistered = false) }
                val hasPerm = _dashboardState.value.hasNotificationPermission
                val reason = if (!hasPerm) "Notification permission denied" else "Push token not registered on server"
                _dashboardState.update {
                    it.copy(
                        isOrderAlertsOn = false,
                        orderAlertsOffReason = reason
                    )
                }
                retryFcmRegistrationIfNeeded(vendor)
            } finally {
                isFcmRegistering = false
            }
        }
    }

    private fun retryFcmRegistrationIfNeeded(vendor: Vendor) {
        if (fcmRegistrationRetryCount < 3) {
            fcmRegistrationRetryCount++
            android.util.Log.i("VendorViewModel", "Scheduling FCM registration retry #$fcmRegistrationRetryCount in 30 seconds")
            viewModelScope.launch {
                delay(30_000L)
                registerClientFcmToken(vendor)
            }
        } else {
            android.util.Log.w("VendorViewModel", "Max FCM registration retries (3) reached. Order alerts remain OFF.")
        }
    }

    fun sendTestAlert(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _dashboardState.update { it.copy(isSendingTestAlert = true) }
            when (val res = repository.sendTestAlert()) {
                is Resource.Success -> {
                    val data = res.data
                    _dashboardState.update { it.copy(isSendingTestAlert = false) }
                    if (data.sent) {
                        onResult(true, "Test sent - you should hear the alarm in a few seconds")
                    } else {
                        if (data.reason == "no_device_registered") {
                            val cachedVendor = sessionManager.cachedVendor
                            if (cachedVendor != null) {
                                registerClientFcmToken(cachedVendor)
                            }
                            onResult(false, "This phone is not registered for alerts")
                        } else {
                            onResult(false, "Test alert not sent: ${data.reason ?: "Unknown reason"}")
                        }
                    }
                }
                is Resource.Error -> {
                    _dashboardState.update { it.copy(isSendingTestAlert = false) }
                    onResult(false, res.message)
                }
                Resource.Loading -> {}
            }
        }
    }

    fun updateNotificationPermissionStatus(granted: Boolean) {
        val isRegistered = _profileState.value.isFcmRegistered
        val alertsOn = granted && isRegistered
        val reason = when {
            !granted -> "Notification permission denied"
            !isRegistered -> "Push token not registered on server"
            else -> null
        }
        _dashboardState.update {
            it.copy(
                hasNotificationPermission = granted,
                isOrderAlertsOn = alertsOn,
                orderAlertsOffReason = reason
            )
        }
    }

    fun updateBatteryOptimizationWarning(isIgnoring: Boolean) {
        _dashboardState.update { it.copy(showBatteryOptimizationWarning = !isIgnoring) }
    }

    fun dismissAutostartTip() {
        sessionManager.hasSeenAutostartTip = true
        _dashboardState.update { it.copy(showAutostartTipDialog = false) }
    }

    fun checkAndShowAutostartTip() {
        if (!sessionManager.hasSeenAutostartTip) {
            val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
            val isOemNeedingAutostart = manufacturer.contains("xiaomi") ||
                    manufacturer.contains("redmi") ||
                    manufacturer.contains("poco") ||
                    manufacturer.contains("vivo") ||
                    manufacturer.contains("oppo") ||
                    manufacturer.contains("realme") ||
                    manufacturer.contains("huawei") ||
                    manufacturer.contains("samsung")
            if (isOemNeedingAutostart) {
                _dashboardState.update { it.copy(showAutostartTipDialog = true) }
            }
        }
    }

    fun registerCustomFcmToken(customToken: String, onComplete: (Boolean, String?) -> Unit) {
        val vendorId = currentVendorId() ?: return
        val email = sessionManager.userEmail ?: "${vendorId}@sndmart.vendor"
        viewModelScope.launch {
            _profileState.update { it.copy(fcmToken = customToken) }
            when (val res = repository.registerFcmToken(vendorId, email, customToken)) {
                is Resource.Success -> {
                    _profileState.update { it.copy(isFcmRegistered = true, successMessage = "FCM token registered successfully") }
                    onComplete(true, null)
                }
                is Resource.Error -> {
                    _profileState.update { it.copy(isFcmRegistered = false, errorMessage = res.message) }
                    onComplete(false, res.message)
                }
                Resource.Loading -> {}
            }
        }
    }

    // --- Sound Alert Preference & Simulation ---
    fun toggleSoundAlert(enabled: Boolean) {
        sessionManager.isSoundAlertEnabled = enabled
        _profileState.update { it.copy(isSoundAlertEnabled = enabled) }
        if (!enabled) {
            soundAlertManager.stopRepeatingAlert()
        }
    }

    fun testSoundAlert() {
        soundAlertManager.playNewOrderSound(vibrate = true)
    }

    fun simulateNewOrderAlert() {
        val sampleOrderId = "ord_${System.currentTimeMillis().toString().takeLast(6)}"
        val sampleOrderNumber = (1000..9999).random().toString()
        val customerNames = listOf("Rahul Sharma", "Priya Patel", "Amit Verma", "Sneha Reddy", "Vikram Singh")
        val sampleCustomer = customerNames.random()
        val sampleAmount = listOf(280.0, 450.0, 620.0, 890.0, 340.0).random()
        val sampleItems = listOf(
            "Butter Chicken x1, Garlic Naan x2",
            "Paneer Tikka Biryani x2, Gulab Jamun x2",
            "Masala Dosa x2, Filter Coffee x2, Idli Vada x1",
            "Veg Fried Rice x1, Chilli Paneer x1, Spring Rolls x1"
        ).random()

        NotificationHelper.showNewOrderNotification(
            context = getApplication(),
            orderId = sampleOrderId,
            title = "🛎️ New Order #$sampleOrderNumber (${CurrencyFormatter.format(sampleAmount)})",
            body = "$sampleCustomer • $sampleItems"
        )
        _inAppOrderAlert.value = "🛎️ New Order - Please Prepare! #$sampleOrderNumber"
        if (sessionManager.isSoundAlertEnabled) {
            soundAlertManager.playNewOrderSound(vibrate = true)
        }
        val vId = currentVendorId()
        if (vId != null) loadOrders(vId)
    }

    fun simulateIncomingFullScreenOrderAlert() = simulateNewOrderAlert()

    fun clearInAppAlert() {
        _inAppOrderAlert.value = null
        soundAlertManager.stopRepeatingAlert()
    }

    fun clearOrderAlert() = clearInAppAlert()

    fun simulateNewOrderNotification() = simulateNewOrderAlert()

    private fun startAutoPolling(vendorId: String) {
        livePollingJob?.cancel()
        livePollingJob = viewModelScope.launch {
            while (isActive) {
                delay(15000) // Poll every 15 seconds for live orders and sound alerts
                loadOrders(vendorId)
                loadDashboard(vendorId)
            }
        }
    }

    // --- Vendor Self-Registration ---
    fun loadCities() {
        viewModelScope.launch {
            _registrationState.update { it.copy(isLoadingCities = true, errorMessage = null) }
            when (val res = repository.getActiveCities()) {
                is Resource.Success -> {
                    _registrationState.update { it.copy(cities = res.data, isLoadingCities = false) }
                }
                is Resource.Error -> {
                    _registrationState.update { it.copy(isLoadingCities = false, errorMessage = res.message) }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun requestRegistrationOtp(
        identifier: String,
        pass: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            _registrationState.update { it.copy(isRequestingOtp = true, errorMessage = null) }
            val cleanId = identifier.trim()
            val isPhone = !cleanId.contains("@")
            val formattedPhone = if (isPhone) {
                val digits = cleanId.filter { it.isDigit() }
                if (digits.length == 10) "+91$digits" else if (digits.startsWith("91")) "+$digits" else "+91$digits"
            } else cleanId

            val res = if (isPhone) {
                repository.signUpWithPhone(formattedPhone, pass)
            } else {
                repository.signUp(cleanId, pass)
            }

            when (res) {
                is Resource.Success -> {
                    _registrationState.update {
                        it.copy(
                            isRequestingOtp = false,
                            isOtpSent = true,
                            successMessage = if (isPhone) "SMS verification OTP sent to $formattedPhone" else "Verification code sent to $cleanId"
                        )
                    }
                    onResult(true, null)
                }
                is Resource.Error -> {
                    _registrationState.update {
                        it.copy(
                            isRequestingOtp = false,
                            errorMessage = res.message
                        )
                    }
                    onResult(false, res.message)
                }
                Resource.Loading -> {}
            }
        }
    }

    fun verifyRegistrationOtp(
        identifier: String,
        token: String,
        onResult: (Boolean, String?, String?) -> Unit = { _, _, _ -> }
    ) {
        viewModelScope.launch {
            _registrationState.update { it.copy(isSubmittingRegistration = true, errorMessage = null) }
            val cleanId = identifier.trim()
            val isPhone = !cleanId.contains("@")
            val formattedPhone = if (isPhone) {
                val digits = cleanId.filter { it.isDigit() }
                if (digits.length == 10) "+91$digits" else if (digits.startsWith("91")) "+$digits" else "+91$digits"
            } else cleanId

            val verifyRes = if (isPhone) {
                repository.verifyPhoneOtp(formattedPhone, token)
            } else {
                repository.verifyOtp(cleanId, token)
            }

            when (verifyRes) {
                is Resource.Success -> {
                    val userId = verifyRes.data.user?.id
                    if (!userId.isNullOrEmpty()) {
                        _registrationState.update {
                            it.copy(
                                isSubmittingRegistration = false,
                                isOtpVerified = true,
                                authenticatedUserId = userId,
                                successMessage = "Mobile number verified successfully! Please upload KYC documents."
                            )
                        }
                        onResult(true, null, userId)
                    } else {
                        val err = "Verification succeeded but user ID is missing"
                        _registrationState.update { it.copy(isSubmittingRegistration = false, errorMessage = err) }
                        onResult(false, err, null)
                    }
                }
                is Resource.Error -> {
                    _registrationState.update {
                        it.copy(
                            isSubmittingRegistration = false,
                            errorMessage = verifyRes.message
                        )
                    }
                    onResult(false, verifyRes.message, null)
                }
                Resource.Loading -> {}
            }
        }
    }

    fun uploadKycDocument(
        userId: String,
        isFoodLicense: Boolean,
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            if (isFoodLicense) {
                _registrationState.update { it.copy(isUploadingFoodLicense = true, foodLicenseUploadError = null) }
            } else {
                _registrationState.update { it.copy(isUploadingAadhar = true, aadharUploadError = null) }
            }

            when (val res = repository.uploadKycDocument(userId, fileName, bytes, mimeType)) {
                is Resource.Success -> {
                    val path = res.data
                    if (isFoodLicense) {
                        _registrationState.update {
                            it.copy(isUploadingFoodLicense = false, foodLicenseStoragePath = path, foodLicenseUploadError = null)
                        }
                    } else {
                        _registrationState.update {
                            it.copy(isUploadingAadhar = false, aadharStoragePath = path, aadharUploadError = null)
                        }
                    }
                    onResult(true, path)
                }
                is Resource.Error -> {
                    val err = res.message ?: "Document upload failed"
                    if (isFoodLicense) {
                        _registrationState.update {
                            it.copy(isUploadingFoodLicense = false, foodLicenseUploadError = err)
                        }
                    } else {
                        _registrationState.update {
                            it.copy(isUploadingAadhar = false, aadharUploadError = err)
                        }
                    }
                    onResult(false, err)
                }
                Resource.Loading -> {}
            }
        }
    }

    fun clearFoodLicenseUpload() {
        _registrationState.update { it.copy(foodLicenseStoragePath = null, foodLicenseUploadError = null) }
    }

    fun clearAadharUpload() {
        _registrationState.update { it.copy(aadharStoragePath = null, aadharUploadError = null) }
    }

    fun resetOtpSent() {
        _registrationState.update { it.copy(isOtpSent = false, isOtpVerified = false, errorMessage = null) }
    }

    fun getEffectiveUserId(): String? {
        return _registrationState.value.authenticatedUserId?.takeIf { it.isNotBlank() && it != "user" }
            ?: repository.sessionManager.userId?.takeIf { it.isNotBlank() && it != "user" }
    }

    fun submitFinalRegistration(
        ownerName: String,
        hotelName: String,
        phone: String,
        cityId: String,
        address: String,
        latitude: Double?,
        longitude: Double?,
        openingTime: String,
        closingTime: String,
        email: String,
        foodLicenseDoc: SelectedDocument?,
        aadharDoc: SelectedDocument?,
        onComplete: (Boolean, String?, Vendor?) -> Unit = { _, _, _ -> }
    ) {
        viewModelScope.launch {
            _registrationState.update { it.copy(isSubmittingRegistration = true, errorMessage = null) }

            // 1. Resolve authentic user ID
            val effectiveUserId = getEffectiveUserId() ?: run {
                when (val u = repository.getAuthenticatedUser()) {
                    is Resource.Success -> u.data.id
                    else -> null
                }
            }

            if (effectiveUserId.isNullOrBlank()) {
                val err = "Active session expired. Please verify your mobile number OTP again."
                android.util.Log.e("VendorViewModel", err)
                _registrationState.update { it.copy(isSubmittingRegistration = false, errorMessage = err) }
                onComplete(false, err, null)
                return@launch
            }

            _registrationState.update { it.copy(authenticatedUserId = effectiveUserId) }

            // 2. Validate City
            if (cityId.isBlank() || cityId == "city_default") {
                val err = "Please select a valid City from the dropdown before submitting."
                android.util.Log.e("VendorViewModel", err)
                _registrationState.update { it.copy(isSubmittingRegistration = false, errorMessage = err) }
                onComplete(false, err, null)
                return@launch
            }

            // 3. Upload Food License if not already uploaded
            var foodPath = _registrationState.value.foodLicenseStoragePath
            if (foodPath.isNullOrBlank()) {
                if (foodLicenseDoc == null) {
                    val err = "Please select and upload your Food License document."
                    android.util.Log.e("VendorViewModel", err)
                    _registrationState.update { it.copy(isSubmittingRegistration = false, errorMessage = err) }
                    onComplete(false, err, null)
                    return@launch
                }
                _registrationState.update { it.copy(isUploadingFoodLicense = true, foodLicenseUploadError = null) }
                val safeFileName = "food-license-${System.currentTimeMillis()}.${foodLicenseDoc.extension}"
                when (val uploadRes = repository.uploadKycDocument(
                    userId = effectiveUserId,
                    fileName = safeFileName,
                    fileBytes = foodLicenseDoc.bytes,
                    mimeType = foodLicenseDoc.mimeType
                )) {
                    is Resource.Success -> {
                        foodPath = uploadRes.data
                        _registrationState.update {
                            it.copy(isUploadingFoodLicense = false, foodLicenseStoragePath = foodPath, foodLicenseUploadError = null)
                        }
                    }
                    is Resource.Error -> {
                        val err = "Food License upload failed: ${uploadRes.message}"
                        android.util.Log.e("VendorViewModel", err)
                        _registrationState.update {
                            it.copy(isUploadingFoodLicense = false, foodLicenseUploadError = uploadRes.message, errorMessage = err, isSubmittingRegistration = false)
                        }
                        onComplete(false, err, null)
                        return@launch
                    }
                    Resource.Loading -> {}
                }
            }

            // 4. Upload Aadhar Card if not already uploaded
            var aadharPath = _registrationState.value.aadharStoragePath
            if (aadharPath.isNullOrBlank()) {
                if (aadharDoc == null) {
                    val err = "Please select and upload your Aadhar Card document."
                    android.util.Log.e("VendorViewModel", err)
                    _registrationState.update { it.copy(isSubmittingRegistration = false, errorMessage = err) }
                    onComplete(false, err, null)
                    return@launch
                }
                _registrationState.update { it.copy(isUploadingAadhar = true, aadharUploadError = null) }
                val safeFileName = "aadhar-${System.currentTimeMillis()}.${aadharDoc.extension}"
                when (val uploadRes = repository.uploadKycDocument(
                    userId = effectiveUserId,
                    fileName = safeFileName,
                    fileBytes = aadharDoc.bytes,
                    mimeType = aadharDoc.mimeType
                )) {
                    is Resource.Success -> {
                        aadharPath = uploadRes.data
                        _registrationState.update {
                            it.copy(isUploadingAadhar = false, aadharStoragePath = aadharPath, aadharUploadError = null)
                        }
                    }
                    is Resource.Error -> {
                        val err = "Aadhar Card upload failed: ${uploadRes.message}"
                        android.util.Log.e("VendorViewModel", err)
                        _registrationState.update {
                            it.copy(isUploadingAadhar = false, aadharUploadError = uploadRes.message, errorMessage = err, isSubmittingRegistration = false)
                        }
                        onComplete(false, err, null)
                        return@launch
                    }
                    Resource.Loading -> {}
                }
            }

            // 5. Submit to database
            when (val res = repository.submitVendorRegistrationWithPaths(
                userId = effectiveUserId,
                ownerName = ownerName,
                hotelName = hotelName,
                phone = phone,
                cityId = cityId,
                address = address,
                latitude = latitude,
                longitude = longitude,
                openingTime = openingTime,
                closingTime = closingTime,
                email = email,
                foodLicensePath = foodPath,
                aadharPath = aadharPath
            )) {
                is Resource.Success -> {
                    _registrationState.update {
                        it.copy(
                            isSubmittingRegistration = false,
                            registeredVendor = res.data,
                            successMessage = "Application submitted successfully",
                            errorMessage = null
                        )
                    }
                    onComplete(true, null, res.data)
                }
                is Resource.Error -> {
                    android.util.Log.e("VendorViewModel", "Vendor registration error: ${res.message}")
                    _registrationState.update {
                        it.copy(isSubmittingRegistration = false, errorMessage = res.message)
                    }
                    onComplete(false, res.message, null)
                }
                Resource.Loading -> {}
            }
        }
    }

    fun submitFinalRegistration(
        userId: String,
        ownerName: String,
        hotelName: String,
        phone: String,
        cityId: String,
        address: String,
        latitude: Double?,
        longitude: Double?,
        openingTime: String,
        closingTime: String,
        email: String,
        foodLicensePath: String?,
        aadharPath: String?,
        onComplete: (Boolean, String?, Vendor?) -> Unit = { _, _, _ -> }
    ) {
        viewModelScope.launch {
            _registrationState.update { it.copy(isSubmittingRegistration = true, errorMessage = null) }
            when (val res = repository.submitVendorRegistrationWithPaths(
                userId = userId,
                ownerName = ownerName,
                hotelName = hotelName,
                phone = phone,
                cityId = cityId,
                address = address,
                latitude = latitude,
                longitude = longitude,
                openingTime = openingTime,
                closingTime = closingTime,
                email = email,
                foodLicensePath = foodLicensePath,
                aadharPath = aadharPath
            )) {
                is Resource.Success -> {
                    _registrationState.update {
                        it.copy(
                            isSubmittingRegistration = false,
                            registeredVendor = res.data,
                            successMessage = "Application submitted successfully"
                        )
                    }
                    onComplete(true, null, res.data)
                }
                is Resource.Error -> {
                    android.util.Log.e("VendorViewModel", "Vendor submit error: ${res.message}")
                    _registrationState.update {
                        it.copy(isSubmittingRegistration = false, errorMessage = res.message)
                    }
                    onComplete(false, res.message, null)
                }
                Resource.Loading -> {}
            }
        }
    }

    fun submitRegistrationWithOtp(
        ownerName: String,
        hotelName: String,
        phone: String,
        cityId: String,
        address: String,
        latitude: Double?,
        longitude: Double?,
        openingTime: String,
        closingTime: String,
        email: String,
        token: String,
        foodLicenseBytes: ByteArray?,
        foodLicenseExt: String,
        foodLicenseMime: String,
        aadharBytes: ByteArray?,
        aadharExt: String,
        aadharMime: String,
        onComplete: (Boolean, String?, Vendor?) -> Unit
    ) {
        viewModelScope.launch {
            _registrationState.update { it.copy(isSubmittingRegistration = true, errorMessage = null) }
            // 1. Verify OTP
            when (val verifyRes = repository.verifyOtp(email, token)) {
                is Resource.Success -> {
                    val userId = verifyRes.data.user?.id
                    if (userId.isNullOrEmpty()) {
                        _registrationState.update {
                            it.copy(isSubmittingRegistration = false, errorMessage = "User verification succeeded but no user ID found.")
                        }
                        onComplete(false, "User verification succeeded but no user ID found.", null)
                        return@launch
                    }

                    // 2. Submit documents, profile & vendor record
                    when (val regRes = repository.submitVendorSelfRegistration(
                        userId = userId,
                        ownerName = ownerName,
                        hotelName = hotelName,
                        phone = phone,
                        cityId = cityId,
                        address = address,
                        latitude = latitude,
                        longitude = longitude,
                        openingTime = openingTime,
                        closingTime = closingTime,
                        email = email,
                        foodLicenseBytes = foodLicenseBytes,
                        foodLicenseExt = foodLicenseExt,
                        foodLicenseMime = foodLicenseMime,
                        aadharBytes = aadharBytes,
                        aadharExt = aadharExt,
                        aadharMime = aadharMime
                    )) {
                        is Resource.Success -> {
                            _registrationState.update {
                                it.copy(
                                    isSubmittingRegistration = false,
                                    registeredVendor = regRes.data,
                                    successMessage = "Application submitted successfully"
                                )
                            }
                            onComplete(true, null, regRes.data)
                        }
                        is Resource.Error -> {
                            _registrationState.update {
                                it.copy(isSubmittingRegistration = false, errorMessage = regRes.message)
                            }
                            onComplete(false, regRes.message, null)
                        }
                        Resource.Loading -> {}
                    }
                }
                is Resource.Error -> {
                    _registrationState.update {
                        it.copy(isSubmittingRegistration = false, errorMessage = verifyRes.message)
                    }
                    onComplete(false, verifyRes.message, null)
                }
                Resource.Loading -> {}
            }
        }
    }

    fun resetRegistrationState() {
        _registrationState.value = RegistrationUiState()
    }

    fun clearMessages() {
        _menuState.update { it.copy(errorMessage = null, successMessage = null) }
        _ordersState.update { it.copy(errorMessage = null, successMessage = null) }
        _earningsState.update { it.copy(errorMessage = null, successMessage = null) }
        _profileState.update { it.copy(errorMessage = null, successMessage = null) }
        _registrationState.update { it.copy(errorMessage = null, successMessage = null) }
        _dashboardState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        soundAlertManager.release()
    }
}
