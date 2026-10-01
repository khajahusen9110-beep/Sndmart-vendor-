package com.example.data.repository

import com.example.data.local.SessionManager
import com.example.data.model.*
import com.example.data.network.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import org.json.JSONObject
import retrofit2.Response

sealed class Resource<out T> {
    data class Success<out T>(val data: T) : Resource<T>()
    data class Error(val message: String, override val code: Int? = null) : Resource<Nothing>()
    data object Loading : Resource<Nothing>()

    open val code: Int?
        get() = null
}

class VendorRepository(
    private val supabaseClient: SupabaseClient,
    val sessionManager: SessionManager
) {
    private val api = supabaseClient.api

    suspend fun getAuthenticatedUser(): Resource<AuthUser> = withContext(Dispatchers.IO) {
        try {
            val response = api.getAuthUser()
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!
                sessionManager.userId = user.id
                Resource.Success(user)
            } else {
                val err = extractErrorMessage(response, "Failed to get authenticated user")
                Resource.Error(err, response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Network error fetching user")
        }
    }

    private fun getCurrentIsoDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun generateSlug(name: String): String {
        val slug = name.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return if (slug.isNotBlank()) slug else "cat-${System.currentTimeMillis()}"
    }

    private fun <T> extractErrorMessage(response: Response<T>, defaultPrefix: String = ""): String {
        val errorBody = try {
            response.errorBody()?.string()
        } catch (_: Exception) {
            null
        }

        val statusCode = response.code()
        val isTransientOrAdvisory = statusCode in 500..599 || statusCode == 404 ||
                defaultPrefix.contains("maintenance", ignoreCase = true) ||
                defaultPrefix.contains("version", ignoreCase = true)

        if (!errorBody.isNullOrBlank()) {
            try {
                val json = JSONObject(errorBody)
                val msg = json.optString("message").takeIf { it.isNotBlank() && it != "null" }
                    ?: json.optString("error_description").takeIf { it.isNotBlank() && it != "null" }
                    ?: json.optString("error").takeIf { it.isNotBlank() && it != "null" }
                    ?: json.optString("msg").takeIf { it.isNotBlank() && it != "null" }

                val details = json.optString("details").takeIf { it.isNotBlank() && it != "null" }
                val hint = json.optString("hint").takeIf { it.isNotBlank() && it != "null" }

                val fullDetail = buildString {
                    if (msg != null) append(msg)
                    if (details != null && details != msg) {
                        if (isNotEmpty()) append(" (").append(details).append(")")
                        else append(details)
                    }
                    if (hint != null) {
                        if (isNotEmpty()) append(" [Hint: ").append(hint).append("]")
                        else append(hint)
                    }
                }

                if (fullDetail.isNotBlank()) {
                    if (isTransientOrAdvisory) {
                        android.util.Log.w("VendorRepository", "Supabase advisory response (code=$statusCode, prefix='$defaultPrefix'): $fullDetail")
                    } else {
                        android.util.Log.e("VendorRepository", "Supabase error (prefix='$defaultPrefix'): $fullDetail | raw: $errorBody")
                    }
                    return if (defaultPrefix.isBlank() || fullDetail.startsWith(defaultPrefix, ignoreCase = true)) {
                        fullDetail
                    } else {
                        "$defaultPrefix: $fullDetail"
                    }
                }
            } catch (_: Exception) {
                val trimmed = errorBody.trim()
                if (trimmed.isNotBlank()) {
                    if (isTransientOrAdvisory) {
                        android.util.Log.w("VendorRepository", "Supabase advisory raw (code=$statusCode, prefix='$defaultPrefix'): $trimmed")
                    } else {
                        android.util.Log.e("VendorRepository", "Raw error (prefix='$defaultPrefix'): $trimmed")
                    }
                    return if (defaultPrefix.isBlank() || trimmed.startsWith(defaultPrefix, ignoreCase = true)) {
                        trimmed
                    } else {
                        "$defaultPrefix: $trimmed"
                    }
                }
            }
        }

        val httpMsg = response.message()
        return if (!httpMsg.isNullOrBlank()) {
            if (defaultPrefix.isBlank()) httpMsg else "$defaultPrefix: $httpMsg"
        } else {
            if (defaultPrefix.isBlank()) "Request failed (HTTP ${response.code()})" else "$defaultPrefix (HTTP ${response.code()})"
        }
    }

    private fun formatPhoneOrEmail(input: String): Pair<String?, String?> {
        val trimmed = input.trim()
        val digitsOnly = trimmed.filter { it.isDigit() }
        return if (trimmed.contains("@")) {
            Pair(trimmed, null)
        } else if (digitsOnly.length in 10..13) {
            val phoneFormatted = if (digitsOnly.length == 10) "+91$digitsOnly" else if (digitsOnly.startsWith("91") && digitsOnly.length == 12) "+$digitsOnly" else "+$digitsOnly"
            Pair(null, phoneFormatted)
        } else {
            // Default to email if contains non-digits, else format as +91 phone
            if (digitsOnly.isNotEmpty()) Pair(null, if (digitsOnly.startsWith("91")) "+$digitsOnly" else "+91$digitsOnly")
            else Pair(trimmed, null)
        }
    }

    suspend fun login(identifier: String, pass: String): Resource<Vendor> = withContext(Dispatchers.IO) {
        try {
            val (emailVal, phoneVal) = formatPhoneOrEmail(identifier)
            val authRequest = if (phoneVal != null) {
                AuthRequest(phone = phoneVal, password = pass)
            } else {
                AuthRequest(email = emailVal, password = pass)
            }
            val authResponse = api.signInWithPassword(authRequest)
            if (!authResponse.isSuccessful) {
                val errorBody = authResponse.errorBody()?.string()
                val message = when {
                    authResponse.code() == 400 -> "Invalid mobile number or password. Please verify your credentials."
                    authResponse.code() == 401 -> "Unauthorized. Please check your login details."
                    !errorBody.isNullOrEmpty() -> extractErrorMessage(authResponse, "Login failed")
                    else -> "Login failed (Error ${authResponse.code()})"
                }
                return@withContext Resource.Error(message, authResponse.code())
            }

            val body = authResponse.body()
            if (body?.accessToken == null || body.user?.id == null) {
                return@withContext Resource.Error("Authentication succeeded but no access token received.")
            }

            sessionManager.updateTokens(
                newAccessToken = body.accessToken,
                newRefreshToken = body.refreshToken,
                expiresInSeconds = body.expiresIn
            )
            sessionManager.userId = body.user.id
            sessionManager.userEmail = body.user.phone ?: body.user.email ?: identifier
            sessionManager.sessionExpiredMessage = null

            // Single-device login: sign out this account everywhere else.
            try {
                api.logoutOtherSessions()
            } catch (e: Exception) {
                android.util.Log.w("VendorRepository", "Could not revoke other sessions: ${e.message}")
            }

            // Fetch vendor row
            return@withContext fetchVendorProfile()
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Network error during login")
        }
    }

    suspend fun validateOrRefreshSession(): Resource<Boolean> = withContext(Dispatchers.IO) {
        val refreshToken = sessionManager.refreshToken
        if (refreshToken.isNullOrBlank() || sessionManager.userId.isNullOrBlank()) {
            sessionManager.clearSession()
            return@withContext Resource.Error("No active session found", 401)
        }

        try {
            val response = api.refreshToken(mapOf("refresh_token" to refreshToken))
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.accessToken != null) {
                    sessionManager.updateTokens(
                        newAccessToken = body.accessToken,
                        newRefreshToken = body.refreshToken,
                        expiresInSeconds = body.expiresIn
                    )
                    if (body.user?.id != null) {
                        sessionManager.userId = body.user.id
                    }
                    sessionManager.sessionExpiredMessage = null
                    Resource.Success(true)
                } else {
                    Resource.Error("Token refresh succeeded without access token", 500)
                }
            } else {
                val code = response.code()
                val errBody = response.errorBody()?.string() ?: ""
                if (code == 400 || code == 401 || code == 403) {
                    sessionManager.clearSession()
                    sessionManager.notifySessionExpired("Your session expired, please log in again")
                    Resource.Error("Your session expired, please log in again", 401)
                } else {
                    Resource.Error("Session check failed: $errBody", code)
                }
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Network error checking session")
        }
    }

    suspend fun fetchVendorProfile(): Resource<Vendor> = withContext(Dispatchers.IO) {
        val userId = sessionManager.userId
            ?: return@withContext Resource.Error("No active user session")

        try {
            val response = api.getVendorByUserId(userIdFilter = "eq.$userId")
            if (response.isSuccessful) {
                val vendors = response.body() ?: emptyList()
                if (vendors.isNotEmpty()) {
                    val vendor = vendors.first()
                    sessionManager.cachedVendor = vendor
                    Resource.Success(vendor)
                } else {
                    Resource.Error("No vendor record found associated with this user ID ($userId).")
                }
            } else {
                val err = response.errorBody()?.string()
                Resource.Error("Failed to fetch vendor: ${err ?: response.message()}", response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to load vendor profile")
        }
    }

    suspend fun updateStoreOpenStatus(vendorId: String, isOpen: Boolean): Resource<Vendor> = withContext(Dispatchers.IO) {
        try {
            val updates = mutableMapOf<String, Any?>("is_open" to isOpen)
            val cityId = sessionManager.cachedVendor?.cityId
            if (!cityId.isNullOrBlank()) {
                updates["city_id"] = cityId
            }
            val response = api.updateVendor("eq.$vendorId", updates)
            if (response.isSuccessful) {
                val updated = response.body()?.firstOrNull()
                if (updated != null) {
                    sessionManager.cachedVendor = updated
                    Resource.Success(updated)
                } else {
                    // Update cache manually if representation is not returned
                    val cached = sessionManager.cachedVendor?.copy(isOpen = isOpen)
                    if (cached != null) sessionManager.cachedVendor = cached
                    Resource.Success(cached ?: Vendor(id = vendorId, isOpen = isOpen, cityId = cityId))
                }
            } else {
                Resource.Error(extractErrorMessage(response, "Failed to update store status"), response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error updating store open status")
        }
    }

    suspend fun updateStoreProfile(
        vendorId: String,
        name: String,
        ownerName: String?,
        phone: String?,
        address: String?,
        latitude: Double?,
        longitude: Double?,
        openingTime: String?,
        closingTime: String?,
        bannerUrl: String?,
        cityId: String? = null
    ): Resource<Vendor> = withContext(Dispatchers.IO) {
        try {
            // Find existing city_id if not explicitly provided
            var targetCityId = cityId?.ifBlank { null }
                ?: sessionManager.cachedVendor?.cityId

            // If still missing, attempt to fetch vendor profile to recover city_id
            if (targetCityId.isNullOrBlank()) {
                val fetched = fetchVendorProfile()
                if (fetched is Resource.Success && !fetched.data.cityId.isNullOrBlank()) {
                    targetCityId = fetched.data.cityId
                }
            }

            val updates = mutableMapOf<String, Any?>()
            updates["name"] = name
            if (ownerName != null) updates["owner_name"] = ownerName
            if (phone != null) updates["phone"] = phone
            if (address != null) updates["address"] = address
            if (latitude != null) updates["latitude"] = latitude
            if (longitude != null) updates["longitude"] = longitude
            if (openingTime != null) updates["opening_time"] = openingTime
            if (closingTime != null) updates["closing_time"] = closingTime
            if (bannerUrl != null) updates["banner_url"] = bannerUrl

            // Critical: Postgres database requires non-null city_id on every update to vendors table
            if (!targetCityId.isNullOrBlank()) {
                updates["city_id"] = targetCityId
            }

            val response = api.updateVendor("eq.$vendorId", updates)
            if (response.isSuccessful) {
                val updated = response.body()?.firstOrNull()
                if (updated != null) {
                    sessionManager.cachedVendor = updated
                    Resource.Success(updated)
                } else {
                    val current = sessionManager.cachedVendor ?: Vendor(id = vendorId)
                    val newVendor = current.copy(
                        name = name,
                        ownerName = ownerName,
                        phone = phone,
                        address = address,
                        latitude = latitude,
                        longitude = longitude,
                        openingTime = openingTime,
                        closingTime = closingTime,
                        bannerUrl = bannerUrl,
                        cityId = targetCityId ?: current.cityId
                    )
                    sessionManager.cachedVendor = newVendor
                    Resource.Success(newVendor)
                }
            } else {
                Resource.Error(extractErrorMessage(response, "Failed to update profile"), response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error updating store profile")
        }
    }

    // In-memory caches for slow-changing data (Rule 6)
    private var cachedCities: List<City>? = null
    private val cachedCategories: MutableMap<String, List<Category>> = java.util.concurrent.ConcurrentHashMap()

    fun clearCaches() {
        cachedCities = null
        cachedCategories.clear()
    }

    // Categories
    suspend fun getCategories(vendorId: String, forceRefresh: Boolean = false): Resource<List<Category>> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedCategories.containsKey(vendorId)) {
            val cached = cachedCategories[vendorId]
            if (cached != null) return@withContext Resource.Success(cached)
        }
        try {
            val response = api.getCategories("eq.$vendorId")
            if (response.isSuccessful) {
                val cats = response.body() ?: emptyList()
                cachedCategories[vendorId] = cats
                Resource.Success(cats)
            } else {
                Resource.Error(extractErrorMessage(response, "Failed to load categories"), response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error loading categories")
        }
    }

    suspend fun createCategory(
        vendorId: String,
        name: String,
        imageUrl: String,
        cityId: String? = null
    ): Resource<Category> = withContext(Dispatchers.IO) {
        try {
            val cleanName = name.trim()
            val cleanImageUrl = imageUrl.trim()
            val slug = generateSlug(cleanName)
            val resolvedCityId = cityId?.takeIf { it.isNotBlank() }
                ?: sessionManager.cachedVendor?.cityId?.takeIf { it.isNotBlank() }

            val body = mutableMapOf<String, Any?>(
                "name" to cleanName,
                "slug" to slug,
                "image_url" to cleanImageUrl,
                "vendor_id" to vendorId,
                "vendor_type" to "hotel",
                "sort_order" to 0,
                "is_active" to true
            )
            if (!resolvedCityId.isNullOrBlank()) {
                body["city_id"] = resolvedCityId
            }

            val response = api.createCategory(body)
            if (response.isSuccessful) {
                cachedCategories.remove(vendorId)
                val item = response.body()?.firstOrNull()
                if (item != null) Resource.Success(item)
                else Resource.Error("Category created but no record returned")
            } else if (response.code() == 409) {
                Resource.Error("A category named \"$cleanName\" already exists in your menu.", 409)
            } else {
                Resource.Error(extractErrorMessage(response, "Failed to add category"), response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error adding category")
        }
    }

    suspend fun updateCategory(categoryId: String, name: String, imageUrl: String): Resource<Category> = withContext(Dispatchers.IO) {
        try {
            val cleanName = name.trim()
            val cleanImageUrl = imageUrl.trim()
            val slug = generateSlug(cleanName)
            val updates = mapOf(
                "name" to cleanName,
                "slug" to slug,
                "image_url" to cleanImageUrl,
                "vendor_type" to "hotel"
            )
            val response = api.updateCategory("eq.$categoryId", updates)
            if (response.isSuccessful) {
                sessionManager.cachedVendor?.id?.let { cachedCategories.remove(it) }
                val item = response.body()?.firstOrNull()
                if (item != null) Resource.Success(item)
                else Resource.Error("Category was not updated. Please refresh and try again.")
            } else if (response.code() == 409) {
                Resource.Error("A category named \"$cleanName\" already exists in your menu.", 409)
            } else {
                Resource.Error(extractErrorMessage(response, "Failed to update category"), response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error updating category")
        }
    }

    suspend fun deleteCategory(categoryId: String): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.deleteCategory("eq.$categoryId")
            if (response.isSuccessful) {
                sessionManager.cachedVendor?.id?.let { cachedCategories.remove(it) }
                if (response.body().isNullOrEmpty()) {
                    Resource.Error("Category was not deleted. Please refresh and try again.")
                } else {
                    Resource.Success(Unit)
                }
            } else {
                Resource.Error(extractErrorMessage(response, "Failed to delete category"), response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error deleting category")
        }
    }

    // Products (Rule 1: Paginated 30 at a time)
    suspend fun getProducts(vendorId: String, limit: Int = 30, offset: Int = 0): Resource<List<Product>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getProducts(vendorIdFilter = "eq.$vendorId", limit = limit, offset = offset)
            if (response.isSuccessful) {
                Resource.Success(response.body() ?: emptyList())
            } else {
                Resource.Error(extractErrorMessage(response, "Failed to load products"), response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error loading products")
        }
    }

    suspend fun createProduct(
        vendorId: String,
        categoryId: String?,
        name: String,
        description: String?,
        price: Double,
        imageUrl: String?,
        isActive: Boolean,
        availableFrom: String?,
        availableUntil: String?,
        stockQty: Int = 100,
        isAvailable: Boolean = true
    ): Resource<Product> = withContext(Dispatchers.IO) {
        try {
            val body = mutableMapOf<String, Any?>(
                "vendor_id" to vendorId,
                "name" to name.trim(),
                "price" to price,
                "is_active" to isActive,
                "stock_qty" to stockQty,
                "is_available" to (isAvailable && stockQty > 0)
            )
            if (!categoryId.isNullOrEmpty()) body["category_id"] = categoryId
            if (!description.isNullOrEmpty()) body["description"] = description.trim()
            if (!imageUrl.isNullOrEmpty()) body["image_url"] = imageUrl.trim()
            if (!availableFrom.isNullOrEmpty()) body["available_from"] = availableFrom
            if (!availableUntil.isNullOrEmpty()) body["available_until"] = availableUntil

            val response = api.createProduct(body)
            if (response.isSuccessful) {
                val item = response.body()?.firstOrNull()
                if (item != null) Resource.Success(item)
                else Resource.Error("Product created but no representation returned")
            } else {
                Resource.Error(extractErrorMessage(response, "Failed to create product"), response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error adding product")
        }
    }

    suspend fun updateProduct(
        productId: String,
        updates: Map<String, Any?>
    ): Resource<Product> = withContext(Dispatchers.IO) {
        try {
            val adjustedUpdates = updates.toMutableMap()
            if (adjustedUpdates.containsKey("stock_qty") && !adjustedUpdates.containsKey("is_available")) {
                val stock = (adjustedUpdates["stock_qty"] as? Number)?.toInt() ?: 0
                adjustedUpdates["is_available"] = stock > 0
            }
            val response = api.updateProduct("eq.$productId", adjustedUpdates)
            if (response.isSuccessful) {
                val item = response.body()?.firstOrNull()
                if (item != null) Resource.Success(item)
                else Resource.Error("Item was not updated. Please refresh and try again.")
            } else {
                Resource.Error(extractErrorMessage(response, "Failed to update product"), response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error updating product")
        }
    }

    suspend fun deleteProduct(productId: String): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.deleteProduct("eq.$productId")
            if (response.isSuccessful) {
                if (response.body().isNullOrEmpty()) {
                    Resource.Error("Item was not deleted. Please refresh and try again.")
                } else {
                    Resource.Success(Unit)
                }
            } else {
                Resource.Error(extractErrorMessage(response, "Failed to delete product"), response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error deleting product")
        }
    }

    // Orders (Rule 1: Paginated 20 at a time)
    suspend fun getOrders(vendorId: String, limit: Int = 20, offset: Int = 0): Resource<List<Order>> = withContext(Dispatchers.IO) {
        try {
            var response = api.getOrders(
                vendorIdFilter = "eq.$vendorId",
                statusFilter = "in.(confirmed,preparing,ready,out_for_delivery,delivered,cancelled,rejected)",
                limit = limit,
                offset = offset
            )

            // Defensive fallback: If address relationship fails to resolve
            if (!response.isSuccessful && response.code() == 400) {
                val errorBodyStr = try { response.errorBody()?.string() } catch (_: Exception) { null }
                if (errorBodyStr?.contains("orders_address_id_fkey") == true || errorBodyStr?.contains("customer_addresses") == true || errorBodyStr?.contains("relationship") == true || errorBodyStr?.contains("PGRST200") == true) {
                    android.util.Log.w("VendorRepository", "Address join not resolved, retrying without constraint hint: $errorBodyStr")
                    val fallbackSelect = "id,order_number,status,subtotal,discount_amount,delivery_fee,handling_fee,total_amount,commission_amount,vendor_amount,notes,payment_status,payment_method,created_at,customer_id,address_id,order_items(id,product_name,quantity,unit_price,total_price),address:customer_addresses(address_line,landmark,recipient_name,phone)"
                    response = api.getOrders(
                        vendorIdFilter = "eq.$vendorId",
                        statusFilter = "in.(confirmed,preparing,ready,out_for_delivery,delivered,cancelled,rejected)",
                        select = fallbackSelect,
                        limit = limit,
                        offset = offset
                    )

                    if (!response.isSuccessful && response.code() == 400) {
                        android.util.Log.w("VendorRepository", "Customer addresses join not resolved, retrying base query")
                        val baseSelect = "id,order_number,status,subtotal,discount_amount,delivery_fee,handling_fee,total_amount,commission_amount,vendor_amount,notes,payment_status,payment_method,created_at,customer_id,address_id,order_items(id,product_name,quantity,unit_price,total_price)"
                        response = api.getOrders(
                            vendorIdFilter = "eq.$vendorId",
                            statusFilter = "in.(confirmed,preparing,ready,out_for_delivery,delivered,cancelled,rejected)",
                            select = baseSelect,
                            limit = limit,
                            offset = offset
                        )
                    }
                }
            }

            if (response.isSuccessful) {
                // Defensive filter: Vendor never sees pending orders until City Admin accepts
                val confirmedAndActiveOrders = (response.body() ?: emptyList())
                    .filter { it.status?.lowercase() != "pending" }

                // Enrich orders with customer profiles if missing
                val enrichedOrders = confirmedAndActiveOrders.toMutableList()
                val missingCustomerIds = enrichedOrders
                    .filter { it.customer == null && !it.customer_id.isNullOrBlank() }
                    .mapNotNull { it.customer_id }
                    .distinct()

                if (missingCustomerIds.isNotEmpty()) {
                    try {
                        val filter = "in.(${missingCustomerIds.joinToString(",")})"
                        val profRes = api.getProfiles(idFilter = filter)
                        if (profRes.isSuccessful && !profRes.body().isNullOrEmpty()) {
                            val profMap = profRes.body()!!.filter { !it.id.isNullOrBlank() }.associateBy { it.id!! }
                            for (i in enrichedOrders.indices) {
                                val ord = enrichedOrders[i]
                                val cid = ord.customer_id
                                if (ord.customer == null && cid != null && profMap.containsKey(cid)) {
                                    val p = profMap[cid]!!
                                    enrichedOrders[i] = ord.copy(customer = Customer(id = p.id, full_name = p.full_name, phone = p.phone))
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Non-blocking enrichment
                    }
                }

                Resource.Success(enrichedOrders)
            } else {
                Resource.Error(extractErrorMessage(response, "Failed to load orders"), response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error loading orders")
        }
    }

    /**
     * Moves an order to [newStatus] only if it is still in [expectedCurrentStatus].
     * Order status history is written by a database trigger.
     */
    suspend fun updateOrderStatus(
        orderId: String,
        newStatus: String,
        expectedCurrentStatus: String
    ): Resource<Order> = withContext(Dispatchers.IO) {
        try {
            val response = api.updateOrderStatus(
                orderIdFilter = "eq.$orderId",
                currentStatusFilter = "eq.$expectedCurrentStatus",
                updates = mapOf("status" to newStatus)
            )
            if (response.isSuccessful) {
                val updatedOrder = response.body()?.firstOrNull()
                if (updatedOrder != null) Resource.Success(updatedOrder)
                else Resource.Error("This order was already updated. Refreshing orders...", 409)
            } else {
                Resource.Error(extractErrorMessage(response, "Failed to update order status"), response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error updating order status")
        }
    }

    suspend fun getOrderStatusHistory(orderId: String): Resource<List<OrderStatusHistory>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getOrderStatusHistory(orderIdFilter = "eq.$orderId")
            if (response.isSuccessful) {
                Resource.Success(response.body() ?: emptyList())
            } else {
                Resource.Error(extractErrorMessage(response, "Failed to load order status history"), response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error loading order status history")
        }
    }

    // Earnings & Wallet
    suspend fun getVendorWallet(): Resource<VendorWalletResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getMyVendorWallet()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Resource.Success(body)
                } else {
                    Resource.Error("Empty wallet response", response.code())
                }
            } else {
                Resource.Error(extractErrorMessage(response, "Failed to load wallet balance"), response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error loading wallet balance")
        }
    }

    // Wallet Transactions (Rule 1: Paginated 20 at a time)
    suspend fun getWalletTransactions(vendorId: String, limit: Int = 20, offset: Int = 0): Resource<List<WalletTransaction>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getWalletTransactions(vendorIdFilter = "eq.$vendorId", limit = limit, offset = offset)
            if (response.isSuccessful) {
                Resource.Success(response.body() ?: emptyList())
            } else {
                Resource.Error(extractErrorMessage(response, "Failed to load wallet transactions"), response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error loading wallet transactions")
        }
    }

    suspend fun getPayouts(vendorId: String, limit: Int = 20, offset: Int = 0): Resource<List<Payout>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getPayouts(vendorIdFilter = "eq.$vendorId", limit = limit, offset = offset)
            if (response.isSuccessful) {
                Resource.Success(response.body() ?: emptyList())
            } else {
                Resource.Error(extractErrorMessage(response, "Failed to load payouts"), response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error loading payouts")
        }
    }

    suspend fun getWithdrawals(vendorId: String, limit: Int = 20, offset: Int = 0): Resource<List<Withdrawal>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getWithdrawals(vendorIdFilter = "eq.$vendorId", limit = limit, offset = offset)
            if (response.isSuccessful) {
                Resource.Success(response.body() ?: emptyList())
            } else {
                Resource.Error(extractErrorMessage(response, "Failed to load withdrawals"), response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error loading withdrawals")
        }
    }

    // Orders placed today filtered on server using IST range converted to UTC
    suspend fun getTodayOrders(
        vendorId: String,
        startUtcIso: String,
        endUtcIso: String
    ): Resource<List<Order>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getTodayOrders(
                vendorIdFilter = "eq.$vendorId",
                createdAtGte = "gte.$startUtcIso",
                createdAtLt = "lt.$endUtcIso"
            )
            if (response.isSuccessful) {
                Resource.Success(response.body() ?: emptyList())
            } else {
                Resource.Error(extractErrorMessage(response, "Failed to load today's orders"), response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error loading today's orders")
        }
    }

    suspend fun requestWithdrawal(amount: Double): Resource<Withdrawal> = withContext(Dispatchers.IO) {
        try {
            val response = api.vendorRequestWithdrawal(mapOf("p_amount" to amount))
            if (response.isSuccessful) {
                val created = response.body()
                if (created != null) Resource.Success(created)
                else Resource.Error("Withdrawal request response was empty")
            } else {
                val errMsg = extractErrorMessage(response, "")
                Resource.Error(errMsg.ifBlank { "Failed to submit withdrawal request" }, response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error submitting withdrawal request")
        }
    }

    // Reviews (Rule 1: Paginated 20 at a time)
    suspend fun getReviews(vendorId: String, limit: Int = 20, offset: Int = 0): Resource<List<Review>> = withContext(Dispatchers.IO) {
        try {
            var response = api.getReviews(vendorIdFilter = "eq.$vendorId", limit = limit, offset = offset)
            if (!response.isSuccessful && response.code() == 400) {
                val errorBodyStr = try { response.errorBody()?.string() } catch (_: Exception) { null }
                if (errorBodyStr?.contains("vendor_reviews_customer_id_fkey") == true || errorBodyStr?.contains("relationship") == true || errorBodyStr?.contains("PGRST200") == true) {
                    val fallbackSelect = "id,vendor_id,order_id,customer_id,rating,comment,created_at,customer:profiles(full_name)"
                    response = api.getReviews(vendorIdFilter = "eq.$vendorId", select = fallbackSelect, limit = limit, offset = offset)
                    if (!response.isSuccessful && response.code() == 400) {
                        val baseSelect = "id,vendor_id,order_id,customer_id,rating,comment,created_at"
                        response = api.getReviews(vendorIdFilter = "eq.$vendorId", select = baseSelect, limit = limit, offset = offset)
                    }
                }
            }
            if (response.isSuccessful) {
                Resource.Success(response.body() ?: emptyList())
            } else {
                Resource.Error(extractErrorMessage(response, "Failed to load reviews"), response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error loading reviews")
        }
    }

    // Operating Hours
    private fun normalizeTimeFormat(time: String): String {
        val trimmed = time.trim()
        val parts = trimmed.split(":")
        return when (parts.size) {
            1 -> {
                val h = parts[0].toIntOrNull() ?: 0
                String.format(java.util.Locale.US, "%02d:00:00", h)
            }
            2 -> {
                val h = parts[0].toIntOrNull() ?: 0
                val m = parts[1].toIntOrNull() ?: 0
                String.format(java.util.Locale.US, "%02d:%02d:00", h, m)
            }
            3 -> {
                val h = parts[0].toIntOrNull() ?: 0
                val m = parts[1].toIntOrNull() ?: 0
                val s = parts[2].toIntOrNull() ?: 0
                String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
            }
            else -> trimmed
        }
    }

    suspend fun getOperatingHours(vendorId: String): Resource<List<OperatingHour>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getOperatingHours("eq.$vendorId")
            if (response.isSuccessful) {
                Resource.Success(response.body() ?: emptyList())
            } else {
                val errorMsg = extractErrorMessage(response, "Failed to load operating hours")
                Resource.Error(errorMsg, response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error loading operating hours")
        }
    }

    suspend fun addOperatingHour(
        vendorId: String,
        startTime: String,
        endTime: String,
        isActive: Boolean = true
    ): Resource<OperatingHour> = withContext(Dispatchers.IO) {
        try {
            // Verify vendor ID is fresh and valid
            var effectiveVendorId = vendorId
            if (effectiveVendorId.isBlank()) {
                effectiveVendorId = sessionManager.cachedVendor?.id ?: ""
            }
            if (effectiveVendorId.isBlank()) {
                val profileRes = fetchVendorProfile()
                if (profileRes is Resource.Success) {
                    effectiveVendorId = profileRes.data.id
                }
            }
            if (effectiveVendorId.isBlank()) {
                return@withContext Resource.Error("Could not determine current vendor ID. Please ensure you are logged in.")
            }

            val formattedStart = normalizeTimeFormat(startTime)
            val formattedEnd = normalizeTimeFormat(endTime)

            // Step 2: The vendor_operating_hours table has ONLY: id, vendor_id, start_time, end_time, is_active, created_at.
            // Extra fields like 'day_of_week' or 'is_closed' will cause Supabase to reject the insert.
            val body = mapOf<String, Any?>(
                "vendor_id" to effectiveVendorId,
                "start_time" to formattedStart,
                "end_time" to formattedEnd,
                "is_active" to isActive
            )

            android.util.Log.d("VendorRepository", "Inserting operating hour slot: $body")
            val response = api.createOperatingHour(body)
            if (response.isSuccessful) {
                val item = response.body()?.firstOrNull()
                if (item != null) Resource.Success(item)
                else Resource.Success(OperatingHour(vendorId = effectiveVendorId, startTime = formattedStart, endTime = formattedEnd, isActive = isActive))
            } else {
                val errorMsg = extractErrorMessage(response, "Failed to add operating hour")
                android.util.Log.e("VendorRepository", "Add operating hour error: $errorMsg (code=${response.code()})")
                Resource.Error(errorMsg, response.code())
            }
        } catch (e: Exception) {
            android.util.Log.e("VendorRepository", "Exception adding operating hour", e)
            Resource.Error(e.localizedMessage ?: "Error adding operating hour")
        }
    }

    suspend fun updateOperatingHour(
        id: String,
        startTime: String,
        endTime: String,
        isActive: Boolean = true
    ): Resource<OperatingHour> = withContext(Dispatchers.IO) {
        try {
            val formattedStart = normalizeTimeFormat(startTime)
            val formattedEnd = normalizeTimeFormat(endTime)

            // Step 2: Only columns existing on vendor_operating_hours: start_time, end_time, is_active
            val updates = mapOf<String, Any?>(
                "start_time" to formattedStart,
                "end_time" to formattedEnd,
                "is_active" to isActive
            )

            android.util.Log.d("VendorRepository", "Updating operating hour slot $id: $updates")
            val response = api.updateOperatingHour("eq.$id", updates)
            if (response.isSuccessful) {
                val item = response.body()?.firstOrNull()
                if (item != null) Resource.Success(item)
                else Resource.Success(OperatingHour(id = id, startTime = formattedStart, endTime = formattedEnd, isActive = isActive))
            } else {
                val errorMsg = extractErrorMessage(response, "Failed to update operating hour")
                android.util.Log.e("VendorRepository", "Update operating hour error: $errorMsg (code=${response.code()})")
                Resource.Error(errorMsg, response.code())
            }
        } catch (e: Exception) {
            android.util.Log.e("VendorRepository", "Exception updating operating hour", e)
            Resource.Error(e.localizedMessage ?: "Error updating operating hour")
        }
    }

    suspend fun toggleOperatingHourActive(
        id: String,
        isActive: Boolean
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val updates = mapOf("is_active" to isActive)
            val response = api.updateOperatingHour("eq.$id", updates)
            if (response.isSuccessful) Resource.Success(Unit)
            else {
                val errorMsg = extractErrorMessage(response, "Failed to toggle operating hour")
                android.util.Log.e("VendorRepository", "Toggle operating hour error: $errorMsg (code=${response.code()})")
                Resource.Error(errorMsg, response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error toggling operating hour")
        }
    }

    suspend fun deleteOperatingHour(id: String): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.deleteOperatingHour("eq.$id")
            if (response.isSuccessful) Resource.Success(Unit)
            else {
                val errorMsg = extractErrorMessage(response, "Failed to delete operating hour")
                android.util.Log.e("VendorRepository", "Delete operating hour error: $errorMsg (code=${response.code()})")
                Resource.Error(errorMsg, response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error deleting operating hour")
        }
    }

    // Push FCM Tokens
    suspend fun registerFcmToken(
        vendorId: String,
        email: String,
        token: String
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            sessionManager.fcmToken = token
            val body = mapOf("p_token" to token, "p_email" to email, "p_device_id" to sessionManager.deviceId)
            val response = api.registerMyVendorDeviceToken(body)
            if (response.isSuccessful) {
                android.util.Log.d("VendorRepository", "FCM token registered for vendor_id=$vendorId")
                Resource.Success(Unit)
            } else {
                val err = extractErrorMessage(response, "FCM registration error")
                Resource.Error(err, response.code())
            }
        } catch (e: Exception) {
            android.util.Log.e("VendorRepository", "Exception registering FCM token", e)
            Resource.Error(e.localizedMessage ?: "Error registering FCM token")
        }
    }

    /** Deactivates the push token only if it still belongs to this phone. */
    suspend fun deactivateFcmToken(vendorId: String): Resource<Unit> = withContext(Dispatchers.IO) {
        val token = sessionManager.fcmToken
        if (token.isNullOrBlank()) return@withContext Resource.Success(Unit)
        try {
            val response = api.deactivateVendorFcmTokens("eq.$vendorId", "eq.$token", mapOf("is_active" to false))
            if (response.isSuccessful) Resource.Success(Unit)
            else Resource.Error(extractErrorMessage(response, "Failed to deactivate FCM token"), response.code())
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error deactivating FCM token")
        }
    }

    /** Success(false) means the vendor account is now active on another phone. */
    suspend fun isThisDeviceActive(): Resource<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = api.isMyVendorDeviceActive(mapOf("p_device_id" to sessionManager.deviceId))
            if (response.isSuccessful) Resource.Success(response.body() ?: true)
            else Resource.Error(extractErrorMessage(response, "Device check failed"), response.code())
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Device check failed")
        }
    }

    suspend fun sendTestAlert(): Resource<TestAlertResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.vendorSendTestAlert()
            if (response.isSuccessful) {
                val body = response.body() ?: TestAlertResponse(sent = false, reason = "Empty response")
                Resource.Success(body)
            } else {
                val err = extractErrorMessage(response, "Failed to send test alert")
                Resource.Error(err, response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Failed to send test alert")
        }
    }

    // 13. Cities for Self-Registration (Rule 6: Cached)
    suspend fun getActiveCities(forceRefresh: Boolean = false): Resource<List<City>> = withContext(Dispatchers.IO) {
        if (!forceRefresh && !cachedCities.isNullOrEmpty()) {
            return@withContext Resource.Success(cachedCities!!)
        }
        try {
            android.util.Log.d("VendorRepository", "Fetching active cities: select=id,name, status=eq.active, order=name.asc")
            var response = api.getCities(select = "id,name", statusFilter = "eq.active", order = "name.asc")
            if (response.isSuccessful && (response.body().isNullOrEmpty())) {
                android.util.Log.w("VendorRepository", "No active cities returned with status=eq.active; trying without status filter fallback")
                val fallbackResponse = api.getCities(select = "id,name", statusFilter = null, order = "name.asc")
                if (fallbackResponse.isSuccessful && !fallbackResponse.body().isNullOrEmpty()) {
                    response = fallbackResponse
                }
            }
            if (response.isSuccessful) {
                val cities = response.body() ?: emptyList()
                if (cities.isNotEmpty()) {
                    cachedCities = cities
                }
                android.util.Log.d("VendorRepository", "Successfully fetched ${cities.size} cities: $cities")
                Resource.Success(cities)
            } else {
                val errorBody = response.errorBody()?.string()
                val code = response.code()
                val errorMsg = if (!errorBody.isNullOrBlank()) {
                    "cities fetch error (HTTP $code): $errorBody"
                } else {
                    "cities fetch error (HTTP $code): ${response.message()}"
                }
                android.util.Log.e("VendorRepository", errorMsg)
                Resource.Error(errorMsg, code)
            }
        } catch (e: Exception) {
            val errorMsg = "cities fetch error (Exception): ${e.localizedMessage ?: e.javaClass.simpleName}"
            android.util.Log.e("VendorRepository", "Cities fetch exception", e)
            Resource.Error(errorMsg)
        }
    }

    // 14. Self-Registration Flow (Mobile SMS OTP)
    suspend fun signUpWithPhone(phone: String, pass: String): Resource<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val formattedPhone = formatPhoneWithCountryCode(phone)
            val response = api.signUp(SignUpRequest(phone = formattedPhone, password = pass))
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    if (body.accessToken != null && body.user != null) {
                        sessionManager.accessToken = body.accessToken
                        sessionManager.refreshToken = body.refreshToken
                        sessionManager.userId = body.user.id
                        sessionManager.userEmail = body.user.phone ?: formattedPhone
                    }
                    Resource.Success(body)
                } else {
                    Resource.Error("Sign up response was empty")
                }
            } else {
                val err = extractErrorMessage(response, "Sign up failed")
                Resource.Error(err, response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Network error during sign up")
        }
    }

    suspend fun verifyPhoneOtp(phone: String, token: String): Resource<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val formattedPhone = formatPhoneWithCountryCode(phone)
            // Call verifyOtp with type = 'sms'
            val response = api.verifyOtp(
                VerifyOtpRequest(
                    phone = formattedPhone,
                    token = token.trim(),
                    type = "sms"
                )
            )

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.user != null) {
                    if (body.accessToken != null) {
                        sessionManager.accessToken = body.accessToken
                        sessionManager.refreshToken = body.refreshToken
                        sessionManager.userId = body.user.id
                        sessionManager.userEmail = body.user.phone ?: formattedPhone
                    }
                    Resource.Success(body)
                } else {
                    Resource.Error("OTP verification succeeded but no user data returned")
                }
            } else {
                val err = extractErrorMessage(response, "OTP verification failed")
                Resource.Error(err, response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Network error during OTP verification")
        }
    }

    private fun formatPhoneWithCountryCode(phone: String): String {
        val digitsOnly = phone.trim().filter { it.isDigit() }
        return when {
            digitsOnly.length == 10 -> "+91$digitsOnly"
            digitsOnly.startsWith("91") && digitsOnly.length == 12 -> "+$digitsOnly"
            phone.trim().startsWith("+") -> phone.trim()
            else -> "+91$digitsOnly"
        }
    }

    suspend fun signUp(email: String, pass: String): Resource<AuthResponse> = withContext(Dispatchers.IO) {
        if (email.contains("@")) {
            try {
                val response = api.signUp(SignUpRequest(email = email.trim(), password = pass))
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        if (body.accessToken != null && body.user != null) {
                            sessionManager.accessToken = body.accessToken
                            sessionManager.refreshToken = body.refreshToken
                            sessionManager.userId = body.user.id
                            sessionManager.userEmail = body.user.email ?: email.trim()
                        }
                        Resource.Success(body)
                    } else {
                        Resource.Error("Sign up response was empty")
                    }
                } else {
                    val err = extractErrorMessage(response, "Sign up failed")
                    Resource.Error(err, response.code())
                }
            } catch (e: Exception) {
                Resource.Error(e.localizedMessage ?: "Network error during sign up")
            }
        } else {
            signUpWithPhone(email, pass)
        }
    }

    suspend fun verifyOtp(identifier: String, token: String): Resource<AuthResponse> = withContext(Dispatchers.IO) {
        if (!identifier.contains("@")) {
            verifyPhoneOtp(identifier, token)
        } else {
            try {
                var response = api.verifyOtp(VerifyOtpRequest(email = identifier.trim(), token = token.trim(), type = "email"))
                if (!response.isSuccessful) {
                    val fallbackResponse = api.verifyOtp(VerifyOtpRequest(email = identifier.trim(), token = token.trim(), type = "signup"))
                    if (fallbackResponse.isSuccessful) {
                        response = fallbackResponse
                    }
                }

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.user != null) {
                        if (body.accessToken != null) {
                            sessionManager.accessToken = body.accessToken
                            sessionManager.refreshToken = body.refreshToken
                            sessionManager.userId = body.user.id
                            sessionManager.userEmail = body.user.email ?: identifier.trim()
                        }
                        Resource.Success(body)
                    } else {
                        Resource.Error("OTP verification succeeded but no user data returned")
                    }
                } else {
                    val err = extractErrorMessage(response, "OTP verification failed")
                    Resource.Error(err, response.code())
                }
            } catch (e: Exception) {
                Resource.Error(e.localizedMessage ?: "Network error during OTP verification")
            }
        }
    }

    suspend fun uploadCategoryImage(
        vendorId: String,
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String
    ): Resource<String> = withContext(Dispatchers.IO) {
        uploadPublicImage("restaurant-images", vendorId, fileName, fileBytes, mimeType)
    }

    suspend fun uploadProductImage(
        vendorId: String,
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String
    ): Resource<String> = withContext(Dispatchers.IO) {
        uploadPublicImage("product-images", vendorId, fileName, fileBytes, mimeType)
    }

    suspend fun uploadStoreBanner(
        vendorId: String,
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String
    ): Resource<String> = withContext(Dispatchers.IO) {
        uploadPublicImage("restaurant-images", vendorId, fileName, fileBytes, mimeType)
    }

    suspend fun uploadPublicImage(
        bucketName: String,
        vendorId: String,
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String
    ): Resource<String> = withContext(Dispatchers.IO) {
        try {
            val relativePath = "$vendorId/$fileName"
            val requestBody = fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val response = api.uploadStorageObject(
                bucket = bucketName,
                path = relativePath,
                fileBytes = requestBody,
                contentType = mimeType
            )
            if (response.isSuccessful) {
                val baseUrl = SupabaseClient.resolveBaseUrl()
                val cleanBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
                val publicUrl = "${cleanBase}storage/v1/object/public/$bucketName/$relativePath"
                Resource.Success(publicUrl)
            } else {
                val err = extractErrorMessage(response, "Upload failed")
                Resource.Error(err, response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error uploading image to $bucketName")
        }
    }

    suspend fun uploadKycDocument(
        userId: String,
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String
    ): Resource<String> = withContext(Dispatchers.IO) {
        try {
            val effectiveUserId = if (userId.isNotBlank() && userId != "user") {
                userId.trim()
            } else {
                sessionManager.userId ?: run {
                    val authRes = api.getAuthUser()
                    if (authRes.isSuccessful && authRes.body() != null) authRes.body()!!.id else ""
                }
            }

            if (effectiveUserId.isBlank()) {
                val err = "Cannot upload KYC document: Authenticated user ID is missing. Please verify OTP first."
                android.util.Log.e("VendorRepository", err)
                return@withContext Resource.Error(err)
            }

            val relativePath = "$effectiveUserId/$fileName"
            android.util.Log.d("VendorRepository", "Uploading KYC document to storage: path=$relativePath, size=${fileBytes.size}, mime=$mimeType")

            val requestBody = fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val response = api.uploadKycDocument(
                path = relativePath,
                fileBytes = requestBody,
                contentType = mimeType,
                upsert = "true"
            )
            if (response.isSuccessful) {
                // Return clean storage path within bucket (e.g. userId/food-license-12345.jpg)
                val rawKey = response.body()?.key ?: relativePath
                val cleanPath = if (rawKey.startsWith("vendor-kyc-documents/")) {
                    rawKey.removePrefix("vendor-kyc-documents/")
                } else {
                    rawKey
                }
                android.util.Log.d("VendorRepository", "KYC doc uploaded successfully: $cleanPath")
                Resource.Success(cleanPath)
            } else {
                val err = extractErrorMessage(response, "KYC Storage upload failed")
                android.util.Log.e("VendorRepository", "Supabase storage upload error: $err (code: ${response.code()})")
                Resource.Error(err, response.code())
            }
        } catch (e: Exception) {
            val errorMsg = "KYC upload exception: ${e.localizedMessage ?: e.javaClass.simpleName}"
            android.util.Log.e("VendorRepository", errorMsg, e)
            Resource.Error(errorMsg)
        }
    }

    suspend fun submitVendorRegistrationWithPaths(
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
        aadharPath: String?
    ): Resource<Vendor> = withContext(Dispatchers.IO) {
        try {
            // 1. Resolve authentic user.id
            val effectiveUserId = if (userId.isNotBlank() && userId != "user") {
                userId.trim()
            } else {
                sessionManager.userId ?: run {
                    val authRes = api.getAuthUser()
                    if (authRes.isSuccessful && authRes.body() != null) authRes.body()!!.id else ""
                }
            }

            if (effectiveUserId.isBlank()) {
                val err = "Active authenticated session not found. Please verify your mobile number with OTP first."
                android.util.Log.e("VendorRepository", err)
                return@withContext Resource.Error(err)
            }

            // 2. Validate all required fields
            if (hotelName.trim().isBlank()) {
                return@withContext Resource.Error("Hotel / Restaurant Name is required.")
            }
            if (ownerName.trim().isBlank()) {
                return@withContext Resource.Error("Owner Name is required.")
            }
            if (phone.trim().isBlank()) {
                return@withContext Resource.Error("Mobile Number is required.")
            }
            if (cityId.trim().isBlank() || cityId == "city_default") {
                return@withContext Resource.Error("A valid City must be selected.")
            }
            val isValidCityUuid = try {
                UUID.fromString(cityId.trim())
                true
            } catch (_: Exception) {
                false
            }
            if (!isValidCityUuid) {
                val err = "Selected City ID is not a valid UUID: '$cityId'. Please select a city from the dropdown."
                android.util.Log.e("VendorRepository", err)
                return@withContext Resource.Error(err)
            }
            if (address.trim().isBlank()) {
                return@withContext Resource.Error("Complete Physical Address is required.")
            }
            if (foodLicensePath.isNullOrBlank()) {
                val err = "Food License document URL is missing. Please ensure your Food License is uploaded."
                android.util.Log.e("VendorRepository", err)
                return@withContext Resource.Error(err)
            }
            if (aadharPath.isNullOrBlank()) {
                val err = "Aadhar Card document URL is missing. Please ensure your Aadhar Card is uploaded."
                android.util.Log.e("VendorRepository", err)
                return@withContext Resource.Error(err)
            }

            // 3. Insert into vendors table FIRST (strictly adhering to user prompt)
            val vendorBody = mutableMapOf<String, Any?>(
                "user_id" to effectiveUserId,
                "name" to hotelName.trim(),
                "owner_name" to ownerName.trim(),
                "phone" to phone.trim(),
                "city_id" to cityId.trim(),
                "address" to address.trim(),
                "opening_time" to openingTime.trim().ifBlank { "09:00" },
                "closing_time" to closingTime.trim().ifBlank { "22:00" },
                "vendor_type" to "hotel",
                "food_license_url" to foodLicensePath.trim(),
                "aadhar_document_url" to aadharPath.trim(),
                "approval_status" to "pending"
            )
            if (latitude != null) vendorBody["latitude"] = latitude
            if (longitude != null) vendorBody["longitude"] = longitude
            if (email.trim().isNotBlank()) vendorBody["email"] = email.trim()

            android.util.Log.d("VendorRepository", "Executing supabase.from('vendors').insert: $vendorBody")
            val vendorResponse = api.insertVendor(vendorBody)

            if (!vendorResponse.isSuccessful) {
                val errorMsg = extractErrorMessage(vendorResponse, "Vendor submit error")
                android.util.Log.e("VendorRepository", "Vendor submit error: $errorMsg (code: ${vendorResponse.code()})")
                return@withContext Resource.Error(errorMsg, vendorResponse.code())
            }

            val createdVendor = vendorResponse.body()?.firstOrNull() ?: Vendor(
                userId = effectiveUserId,
                name = hotelName.trim(),
                ownerName = ownerName.trim(),
                phone = phone.trim(),
                cityId = cityId.trim(),
                address = address.trim(),
                approvalStatus = "pending"
            )
            android.util.Log.d("VendorRepository", "Vendor row inserted successfully: ${createdVendor.id}")

            // 4. Also create/update the profiles row (only reachable after vendor insert succeeds)
            val profileBody = mutableMapOf<String, Any?>(
                "id" to effectiveUserId,
                "role" to "vendor",
                "full_name" to ownerName.trim(),
                "phone" to phone.trim(),
                "city_id" to cityId.trim()
            )
            try {
                android.util.Log.d("VendorRepository", "Executing supabase.from('profiles').upsert: $profileBody")
                val profileResponse = api.insertProfile(onConflict = "id", profile = profileBody)
                if (!profileResponse.isSuccessful) {
                    val pErr = extractErrorMessage(profileResponse, "Profile upsert error")
                    android.util.Log.w("VendorRepository", "Profile upsert non-critical warning: $pErr")
                } else {
                    android.util.Log.d("VendorRepository", "Vendor profile upserted successfully")
                }
            } catch (e: Exception) {
                android.util.Log.w("VendorRepository", "Profile upsert exception: ${e.message}")
            }

            // 5. Clear session credentials so user remains in unauthenticated state
            sessionManager.clearSession()

            Resource.Success(createdVendor)
        } catch (e: Exception) {
            val err = "Vendor submit exception: ${e.localizedMessage ?: e.javaClass.simpleName}"
            android.util.Log.e("VendorRepository", err, e)
            Resource.Error(err)
        }
    }

    fun logout() {
        sessionManager.clearSession()
        clearCaches()
    }

    // 14. App Version and Maintenance Mode Checks
    suspend fun getAppVersionInfo(platform: String = "vendor_app"): Resource<AppVersionInfo?> = withContext(Dispatchers.IO) {
        try {
            val res = api.getAppVersions(platformFilter = "eq.$platform")
            if (res.isSuccessful) {
                Resource.Success(res.body()?.firstOrNull())
            } else {
                Resource.Error(extractErrorMessage(res, "App version check notice"), res.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "App version check notice")
        }
    }

    suspend fun getMaintenanceModeSetting(): Resource<AppSettingItem?> = withContext(Dispatchers.IO) {
        try {
            val res = api.getAppSettings(keyFilter = "eq.maintenance_mode")
            if (res.isSuccessful) {
                Resource.Success(res.body()?.firstOrNull())
            } else {
                Resource.Error(extractErrorMessage(res, "Maintenance mode check notice"), res.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Maintenance check notice")
        }
    }
}
