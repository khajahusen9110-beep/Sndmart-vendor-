package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.SessionManager
import com.example.data.model.*
import com.example.data.network.SupabaseClient
import com.example.data.repository.Resource
import com.example.data.repository.VendorRepository
import com.example.util.NotificationHelper
import com.example.util.SoundAlertManager
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
    val registeredVendor: Vendor? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

data class DashboardUiState(
    val ordersTodayCount: Int = 0,
    val earningsTodaySum: Double = 0.0,
    val pendingOrdersCount: Int = 0,
    val averageRating: Double = 0.0,
    val walletBalance: Double = 0.0,
    val recentOrders: List<Order> = emptyList(),
    val isStoreOpen: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class MenuUiState(
    val categories: List<Category> = emptyList(),
    val products: List<Product> = emptyList(),
    val selectedCategoryId: String? = null,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

data class OrdersUiState(
    val orders: List<Order> = emptyList(),
    val activeFilter: String = "ALL", // ALL, PENDING, CONFIRMED, PREPARING, READY, DELIVERED, CANCELLED
    val selectedOrder: Order? = null,
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
    val isLoading: Boolean = false,
    val isSubmittingWithdrawal: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

data class ReviewsUiState(
    val reviews: List<Review> = emptyList(),
    val averageRating: Double = 0.0,
    val totalCount: Int = 0,
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

class VendorViewModel(application: Application) : AndroidViewModel(application) {

    val sessionManager = SessionManager(application.applicationContext)
    private val supabaseClient = SupabaseClient(sessionManager)
    val repository = VendorRepository(supabaseClient, sessionManager)
    val soundAlertManager = SoundAlertManager(application.applicationContext)

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

    private var livePollingJob: Job? = null
    private var lastKnownPendingCount = 0

    init {
        checkSession()
    }

    fun checkSession() {
        viewModelScope.launch {
            if (!sessionManager.isLoggedIn) {
                _authUiState.value = AuthUiState.Unauthenticated
                return@launch
            }

            _authUiState.value = AuthUiState.Loading
            when (val result = repository.fetchVendorProfile()) {
                is Resource.Success -> {
                    processVendorStatus(result.data)
                }
                is Resource.Error -> {
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
        val token = sessionManager.fcmToken
        if (currentVendor != null && !token.isNullOrEmpty()) {
            viewModelScope.launch {
                repository.deactivateFcmToken(currentVendor.id, token)
            }
        }
        livePollingJob?.cancel()
        repository.logout()
        _authUiState.value = AuthUiState.Unauthenticated
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
            ?: sessionManager.cachedVendor?.id
    }

    fun loadAllVendorData(vendorId: String) {
        loadDashboard(vendorId)
        loadMenu(vendorId)
        loadOrders(vendorId)
        loadEarnings(vendorId)
        loadReviews(vendorId)
        loadOperatingHours(vendorId)
    }

    fun refreshAll() {
        val vendorId = currentVendorId() ?: return
        loadAllVendorData(vendorId)
    }

    // --- Dashboard & Store Toggle ---
    fun loadDashboard(vendorId: String? = null) {
        viewModelScope.launch {
            val vId = vendorId ?: currentVendorId() ?: return@launch
            _dashboardState.update { it.copy(isLoading = true) }
            
            // Orders
            val ordersRes = repository.getOrders(vId)
            val orders = if (ordersRes is Resource.Success) ordersRes.data else emptyList()

            // Calculate Today's stats
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val todayOrders = orders.filter { it.createdAt?.startsWith(todayStr) == true }
            val ordersTodayCount = todayOrders.size
            val earningsTodaySum = todayOrders
                .filter { it.status.lowercase() !in listOf("cancelled", "canceled") }
                .sumOf { it.totalAmount }
            val pendingOrdersCount = orders.count { it.status.lowercase() == "pending" }

            // Reviews for average
            val reviewsRes = repository.getReviews(vId)
            val reviews = if (reviewsRes is Resource.Success) reviewsRes.data else emptyList()
            val avgRating = if (reviews.isNotEmpty()) reviews.map { it.rating }.average() else 0.0

            // Wallet transactions for balance
            val walletRes = repository.getWalletTransactions(vId)
            val transactions = if (walletRes is Resource.Success) walletRes.data else emptyList()
            val balance = transactions.sumOf {
                if (it.type.lowercase() in listOf("debit", "withdrawal")) -it.amount else it.amount
            }

            _dashboardState.update {
                it.copy(
                    ordersTodayCount = ordersTodayCount,
                    earningsTodaySum = earningsTodaySum,
                    pendingOrdersCount = pendingOrdersCount,
                    averageRating = if (avgRating.isNaN()) 0.0 else avgRating,
                    walletBalance = balance,
                    recentOrders = orders.take(10),
                    isLoading = false
                )
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

    // --- Menu ---
    fun loadMenu(vendorId: String? = null) {
        viewModelScope.launch {
            val vId = vendorId ?: currentVendorId() ?: return@launch
            _menuState.update { it.copy(isLoading = true, errorMessage = null) }
            val catRes = repository.getCategories(vId)
            val prodRes = repository.getProducts(vId)

            val cats = if (catRes is Resource.Success) catRes.data else emptyList()
            val prods = if (prodRes is Resource.Success) prodRes.data else emptyList()

            _menuState.update {
                it.copy(
                    categories = cats,
                    products = prods,
                    isLoading = false
                )
            }
        }
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

    fun addCategory(name: String, imageUrl: String, onComplete: (Boolean, String?) -> Unit) {
        val vendorId = currentVendorId() ?: return
        viewModelScope.launch {
            _menuState.update { it.copy(isSubmitting = true) }
            when (val res = repository.createCategory(vendorId, name, imageUrl)) {
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
        onComplete: (Boolean, String?) -> Unit
    ) {
        val vendorId = currentVendorId() ?: return
        viewModelScope.launch {
            _menuState.update { it.copy(isSubmitting = true) }
            when (val res = repository.createProduct(vendorId, categoryId, name, description, price, imageUrl, isActive, availableFrom, availableUntil)) {
                is Resource.Success -> {
                    _menuState.update { it.copy(products = listOf(res.data) + it.products, isSubmitting = false, successMessage = "Product added successfully") }
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

    // --- Orders ---
    fun loadOrders(vendorId: String? = null) {
        viewModelScope.launch {
            val vId = vendorId ?: currentVendorId() ?: return@launch
            _ordersState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val res = repository.getOrders(vId)) {
                is Resource.Success -> {
                    val pendingOrders = res.data.filter { it.status.lowercase() == "pending" }
                    val pendingCount = pendingOrders.size
                    if (pendingCount > 0 && (pendingCount > lastKnownPendingCount || lastKnownPendingCount == 0)) {
                        _inAppOrderAlert.value = "🛎️ New incoming order! Pending confirmation."
                        soundAlertManager.startRepeatingOrderAlert(
                            scope = viewModelScope,
                            isSoundEnabled = { sessionManager.isSoundAlertEnabled },
                            hasPendingOrders = { _ordersState.value.orders.any { it.status.lowercase() == "pending" } }
                        )
                    } else if (pendingCount == 0) {
                        soundAlertManager.stopRepeatingAlert()
                    }
                    lastKnownPendingCount = pendingCount

                    _ordersState.update {
                        it.copy(orders = res.data, isLoading = false)
                    }
                }
                is Resource.Error -> {
                    _ordersState.update { it.copy(isLoading = false, errorMessage = res.message) }
                }
                Resource.Loading -> {}
            }
        }
    }

    fun selectOrder(order: Order?) {
        _ordersState.update { it.copy(selectedOrder = order) }
        if (order?.status?.lowercase() == "pending") {
            soundAlertManager.stopRepeatingAlert()
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
                            if (o.id == orderId) o.copy(status = newStatus, cancellationReason = cancellationReason ?: o.cancellationReason)
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

    // --- Earnings & Withdrawals ---
    fun loadEarnings(vendorId: String? = null) {
        viewModelScope.launch {
            val vId = vendorId ?: currentVendorId() ?: return@launch
            _earningsState.update { it.copy(isLoading = true, errorMessage = null) }
            val txRes = repository.getWalletTransactions(vId)
            val payRes = repository.getPayouts(vId)
            val withRes = repository.getWithdrawals(vId)

            val txs = if (txRes is Resource.Success) txRes.data else emptyList()
            val pays = if (payRes is Resource.Success) payRes.data else emptyList()
            val withs = if (withRes is Resource.Success) withRes.data else emptyList()

            val balance = txs.sumOf {
                if (it.type.lowercase() in listOf("debit", "withdrawal")) -it.amount else it.amount
            }

            _earningsState.update {
                it.copy(
                    walletTransactions = txs,
                    payouts = pays,
                    withdrawals = withs,
                    runningBalance = balance,
                    isLoading = false
                )
            }
        }
    }

    fun requestWithdrawal(amount: Double, note: String?, onComplete: (Boolean, String?) -> Unit) {
        val vendor = (_authUiState.value as? AuthUiState.Authenticated)?.vendor
            ?: sessionManager.cachedVendor ?: return
        viewModelScope.launch {
            _earningsState.update { it.copy(isSubmittingWithdrawal = true) }
            when (val res = repository.requestWithdrawal(vendor.id, vendor.cityId, amount, note)) {
                is Resource.Success -> {
                    _earningsState.update {
                        it.copy(
                            withdrawals = listOf(res.data) + it.withdrawals,
                            isSubmittingWithdrawal = false,
                            successMessage = "Withdrawal request submitted for approval"
                        )
                    }
                    onComplete(true, null)
                }
                is Resource.Error -> {
                    _earningsState.update { it.copy(isSubmittingWithdrawal = false, errorMessage = res.message) }
                    onComplete(false, res.message)
                }
                Resource.Loading -> {}
            }
        }
    }

    // --- Reviews ---
    fun loadReviews(vendorId: String? = null) {
        viewModelScope.launch {
            val vId = vendorId ?: currentVendorId() ?: return@launch
            _reviewsState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val res = repository.getReviews(vId)) {
                is Resource.Success -> {
                    val reviews = res.data
                    val avg = if (reviews.isNotEmpty()) reviews.map { it.rating }.average() else 0.0
                    _reviewsState.update {
                        it.copy(
                            reviews = reviews,
                            averageRating = if (avg.isNaN()) 0.0 else avg,
                            totalCount = reviews.size,
                            isLoading = false
                        )
                    }
                }
                is Resource.Error -> {
                    _reviewsState.update { it.copy(isLoading = false, errorMessage = res.message) }
                }
                Resource.Loading -> {}
            }
        }
    }

    // --- Store Profile & Operating Hours ---
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
        onComplete: (Boolean, String?) -> Unit
    ) {
        updateStoreProfile(name, ownerName, phone, address, latitude, longitude, openingTime, closingTime, bannerUrl, onComplete)
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
        onComplete: (Boolean, String?) -> Unit
    ) {
        val vendorId = currentVendorId() ?: return
        viewModelScope.launch {
            _profileState.update { it.copy(isSaving = true) }
            when (val res = repository.updateStoreProfile(vendorId, name, ownerName, phone, address, latitude, longitude, openingTime, closingTime, bannerUrl)) {
                is Resource.Success -> {
                    _profileState.update { it.copy(vendor = res.data, isSaving = false, successMessage = "Store profile updated successfully") }
                    _authUiState.value = AuthUiState.Authenticated(res.data)
                    onComplete(true, null)
                }
                is Resource.Error -> {
                    _profileState.update { it.copy(isSaving = false, errorMessage = res.message) }
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
        val vendorId = currentVendorId() ?: return
        viewModelScope.launch {
            when (val res = repository.addOperatingHour(vendorId, dayOfWeek, startTime, endTime, isActive, isClosed)) {
                is Resource.Success -> {
                    _profileState.update { it.copy(operatingHours = it.operatingHours + res.data, successMessage = "Operating hour slot added") }
                    onComplete(true, null)
                }
                is Resource.Error -> {
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
            when (val res = repository.updateOperatingHour(id, startTime, endTime, isActive, isClosed, dayOfWeek)) {
                is Resource.Success -> {
                    _profileState.update {
                        it.copy(
                            operatingHours = it.operatingHours.map { h -> if (h.id == id) res.data else h },
                            successMessage = "Operating hour slot updated"
                        )
                    }
                    onComplete(true, null)
                }
                is Resource.Error -> {
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
    private fun registerClientFcmToken(vendor: Vendor) {
        val email = sessionManager.userEmail ?: "${vendor.id}@sndmart.vendor"

        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                val token = try {
                    if (task.isSuccessful && !task.result.isNullOrBlank()) {
                        task.result
                    } else {
                        android.util.Log.w("VendorViewModel", "FCM token retrieval not available, using device client token: ${task.exception?.message}")
                        sessionManager.fcmToken ?: "android_fcm_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                    }
                } catch (t: Throwable) {
                    android.util.Log.w("VendorViewModel", "FCM token retrieval exception: ${t.message}")
                    sessionManager.fcmToken ?: "android_fcm_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                }

                sessionManager.fcmToken = token
                _profileState.update { it.copy(fcmToken = token) }

                viewModelScope.launch {
                    when (repository.registerFcmToken(vendor.id, email, token)) {
                        is Resource.Success -> _profileState.update { it.copy(isFcmRegistered = true) }
                        is Resource.Error -> _profileState.update { it.copy(isFcmRegistered = false) }
                        Resource.Loading -> {}
                    }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.w("VendorViewModel", "FirebaseMessaging unavailable: ${e.message}")
            val fallbackToken = sessionManager.fcmToken ?: "android_fcm_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            sessionManager.fcmToken = fallbackToken
            _profileState.update { it.copy(fcmToken = fallbackToken) }

            viewModelScope.launch {
                when (repository.registerFcmToken(vendor.id, email, fallbackToken)) {
                    is Resource.Success -> _profileState.update { it.copy(isFcmRegistered = true) }
                    is Resource.Error -> _profileState.update { it.copy(isFcmRegistered = false) }
                    Resource.Loading -> {}
                }
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

    fun simulateIncomingFullScreenOrderAlert() {
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

        NotificationHelper.showIncomingOrderAlert(
            context = getApplication(),
            orderId = sampleOrderId,
            orderNumber = sampleOrderNumber,
            customerName = sampleCustomer,
            customerPhone = "+91 98765 43210",
            itemsCount = 3,
            totalAmount = sampleAmount,
            deliveryAddress = "Flat 402, Green Valley Apts, MG Road",
            itemsSummary = sampleItems
        )
    }

    fun clearInAppAlert() {
        _inAppOrderAlert.value = null
        soundAlertManager.stopRepeatingAlert()
    }

    fun clearOrderAlert() = clearInAppAlert()

    fun simulateNewOrderAlert() {
        simulateIncomingFullScreenOrderAlert()
        _inAppOrderAlert.value = "🛎️ New incoming order #ORD-${(1000..9999).random()}! Please accept/prepare."
        if (sessionManager.isSoundAlertEnabled) {
            soundAlertManager.playNewOrderSound(vibrate = true)
        }
        val vId = currentVendorId()
        if (vId != null) loadOrders(vId)
    }

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
        email: String,
        pass: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            _registrationState.update { it.copy(isRequestingOtp = true, errorMessage = null) }
            when (val res = repository.signUp(email, pass)) {
                is Resource.Success -> {
                    _registrationState.update {
                        it.copy(
                            isRequestingOtp = false,
                            isOtpSent = true,
                            successMessage = "Verification code sent to $email"
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
