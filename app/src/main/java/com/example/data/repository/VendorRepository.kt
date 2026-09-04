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

sealed class Resource<out T> {
    data class Success<out T>(val data: T) : Resource<T>()
    data class Error(val message: String, val code: Int? = null) : Resource<Nothing>()
    data object Loading : Resource<Nothing>()
}

class VendorRepository(
    private val supabaseClient: SupabaseClient,
    private val sessionManager: SessionManager
) {
    private val api = supabaseClient.api

    private fun getCurrentIsoDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    suspend fun login(email: String, pass: String): Resource<Vendor> = withContext(Dispatchers.IO) {
        try {
            val authResponse = api.signInWithPassword(AuthRequest(email.trim(), pass))
            if (!authResponse.isSuccessful) {
                val errorBody = authResponse.errorBody()?.string()
                val message = when {
                    authResponse.code() == 400 -> "Invalid email or password. Please verify your credentials."
                    authResponse.code() == 401 -> "Unauthorized. Please check your login details."
                    !errorBody.isNullOrEmpty() -> "Login failed: $errorBody"
                    else -> "Login failed (Error ${authResponse.code()})"
                }
                return@withContext Resource.Error(message, authResponse.code())
            }

            val body = authResponse.body()
            if (body?.accessToken == null || body.user?.id == null) {
                return@withContext Resource.Error("Authentication succeeded but no access token received.")
            }

            sessionManager.accessToken = body.accessToken
            sessionManager.refreshToken = body.refreshToken
            sessionManager.userId = body.user.id
            sessionManager.userEmail = body.user.email ?: email

            // Fetch vendor row
            return@withContext fetchVendorProfile()
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Network error during login")
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
            val response = api.updateVendor("eq.$vendorId", mapOf("is_open" to isOpen))
            if (response.isSuccessful) {
                val updated = response.body()?.firstOrNull()
                if (updated != null) {
                    sessionManager.cachedVendor = updated
                    Resource.Success(updated)
                } else {
                    // Update cache manually if representation is not returned
                    val cached = sessionManager.cachedVendor?.copy(isOpen = isOpen)
                    if (cached != null) sessionManager.cachedVendor = cached
                    Resource.Success(cached ?: Vendor(id = vendorId, isOpen = isOpen))
                }
            } else {
                Resource.Error("Failed to update store status: ${response.message()}", response.code())
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
        bannerUrl: String?
    ): Resource<Vendor> = withContext(Dispatchers.IO) {
        try {
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
                        bannerUrl = bannerUrl
                    )
                    sessionManager.cachedVendor = newVendor
                    Resource.Success(newVendor)
                }
            } else {
                Resource.Error("Failed to update profile: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error updating store profile")
        }
    }

    // Categories
    suspend fun getCategories(vendorId: String): Resource<List<Category>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getCategories("eq.$vendorId")
            if (response.isSuccessful) {
                Resource.Success(response.body() ?: emptyList())
            } else {
                Resource.Error("Failed to load categories: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error loading categories")
        }
    }

    suspend fun createCategory(vendorId: String, name: String, imageUrl: String): Resource<Category> = withContext(Dispatchers.IO) {
        try {
            val body = mapOf(
                "vendor_id" to vendorId,
                "name" to name.trim(),
                "image_url" to imageUrl.trim()
            )
            val response = api.createCategory(body)
            if (response.isSuccessful) {
                val item = response.body()?.firstOrNull()
                if (item != null) Resource.Success(item)
                else Resource.Error("Category created but no record returned")
            } else {
                Resource.Error("Failed to add category: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error adding category")
        }
    }

    suspend fun updateCategory(categoryId: String, name: String, imageUrl: String): Resource<Category> = withContext(Dispatchers.IO) {
        try {
            val updates = mapOf(
                "name" to name.trim(),
                "image_url" to imageUrl.trim()
            )
            val response = api.updateCategory("eq.$categoryId", updates)
            if (response.isSuccessful) {
                val item = response.body()?.firstOrNull()
                if (item != null) Resource.Success(item)
                else Resource.Success(Category(id = categoryId, name = name, imageUrl = imageUrl))
            } else {
                Resource.Error("Failed to update category: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error updating category")
        }
    }

    suspend fun deleteCategory(categoryId: String): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.deleteCategory("eq.$categoryId")
            if (response.isSuccessful) {
                Resource.Success(Unit)
            } else {
                Resource.Error("Failed to delete category: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error deleting category")
        }
    }

    // Products
    suspend fun getProducts(vendorId: String): Resource<List<Product>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getProducts("eq.$vendorId")
            if (response.isSuccessful) {
                Resource.Success(response.body() ?: emptyList())
            } else {
                Resource.Error("Failed to load products: ${response.message()}", response.code())
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
        availableUntil: String?
    ): Resource<Product> = withContext(Dispatchers.IO) {
        try {
            val body = mutableMapOf<String, Any?>(
                "vendor_id" to vendorId,
                "name" to name.trim(),
                "price" to price,
                "is_active" to isActive
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
                Resource.Error("Failed to create product: ${response.message()}", response.code())
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
            val response = api.updateProduct("eq.$productId", updates)
            if (response.isSuccessful) {
                val item = response.body()?.firstOrNull()
                if (item != null) Resource.Success(item)
                else Resource.Success(Product(id = productId, name = updates["name"] as? String ?: ""))
            } else {
                Resource.Error("Failed to update product: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error updating product")
        }
    }

    suspend fun deleteProduct(productId: String): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.deleteProduct("eq.$productId")
            if (response.isSuccessful) {
                Resource.Success(Unit)
            } else {
                Resource.Error("Failed to delete product: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error deleting product")
        }
    }

    // Orders
    suspend fun getOrders(vendorId: String): Resource<List<Order>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getOrders("eq.$vendorId")
            if (response.isSuccessful) {
                Resource.Success(response.body() ?: emptyList())
            } else {
                Resource.Error("Failed to load orders: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error loading orders")
        }
    }

    suspend fun getOrderById(orderId: String): Resource<Order> = withContext(Dispatchers.IO) {
        try {
            val response = api.getOrderById("eq.$orderId")
            if (response.isSuccessful) {
                val order = response.body()?.firstOrNull()
                if (order != null) Resource.Success(order)
                else Resource.Error("Order not found")
            } else {
                Resource.Error("Failed to fetch order: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error fetching order")
        }
    }

    suspend fun updateOrderStatus(
        orderId: String,
        newStatus: String,
        notes: String? = null,
        cancellationReason: String? = null
    ): Resource<Order> = withContext(Dispatchers.IO) {
        try {
            val orderUpdates = mutableMapOf<String, Any?>("status" to newStatus)
            if (!cancellationReason.isNullOrEmpty()) {
                orderUpdates["cancellation_reason"] = cancellationReason
            }

            val response = api.updateOrderStatus("eq.$orderId", orderUpdates)
            if (response.isSuccessful) {
                // Also insert into order_status_history
                try {
                    api.insertOrderStatusHistory(
                        mapOf(
                            "order_id" to orderId,
                            "status" to newStatus,
                            "notes" to (notes ?: cancellationReason ?: "Status changed to $newStatus"),
                            "created_at" to getCurrentIsoDate()
                        )
                    )
                } catch (_: Exception) {
                    // non-fatal
                }

                val updatedOrder = response.body()?.firstOrNull()
                if (updatedOrder != null) Resource.Success(updatedOrder)
                else Resource.Success(Order(id = orderId, status = newStatus))
            } else {
                Resource.Error("Failed to update order status: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error updating order status")
        }
    }

    // Earnings & Wallet
    suspend fun getWalletTransactions(vendorId: String): Resource<List<WalletTransaction>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getWalletTransactions("eq.$vendorId")
            if (response.isSuccessful) {
                Resource.Success(response.body() ?: emptyList())
            } else {
                Resource.Error("Failed to load wallet transactions: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error loading wallet transactions")
        }
    }

    suspend fun getPayouts(vendorId: String): Resource<List<Payout>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getPayouts("eq.$vendorId")
            if (response.isSuccessful) {
                Resource.Success(response.body() ?: emptyList())
            } else {
                Resource.Error("Failed to load payouts: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error loading payouts")
        }
    }

    suspend fun getWithdrawals(vendorId: String): Resource<List<Withdrawal>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getWithdrawals("eq.$vendorId")
            if (response.isSuccessful) {
                Resource.Success(response.body() ?: emptyList())
            } else {
                Resource.Error("Failed to load withdrawals: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error loading withdrawals")
        }
    }

    suspend fun requestWithdrawal(
        vendorId: String,
        cityId: String?,
        amount: Double,
        note: String?
    ): Resource<Withdrawal> = withContext(Dispatchers.IO) {
        try {
            val body = mutableMapOf<String, Any?>(
                "vendor_id" to vendorId,
                "amount" to amount,
                "status" to "pending"
            )
            if (!cityId.isNullOrEmpty()) body["city_id"] = cityId
            if (!note.isNullOrEmpty()) body["note"] = note.trim()

            val response = api.requestWithdrawal(body)
            if (response.isSuccessful) {
                val created = response.body()?.firstOrNull()
                if (created != null) Resource.Success(created)
                else Resource.Success(Withdrawal(vendorId = vendorId, amount = amount, status = "pending", note = note))
            } else {
                Resource.Error("Failed to submit withdrawal request: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error submitting withdrawal request")
        }
    }

    // Reviews
    suspend fun getReviews(vendorId: String): Resource<List<Review>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getReviews("eq.$vendorId")
            if (response.isSuccessful) {
                Resource.Success(response.body() ?: emptyList())
            } else {
                Resource.Error("Failed to load reviews: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error loading reviews")
        }
    }

    // Operating Hours
    suspend fun getOperatingHours(vendorId: String): Resource<List<OperatingHour>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getOperatingHours("eq.$vendorId")
            if (response.isSuccessful) {
                Resource.Success(response.body() ?: emptyList())
            } else {
                Resource.Error("Failed to load operating hours: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error loading operating hours")
        }
    }

    suspend fun addOperatingHour(
        vendorId: String,
        dayOfWeek: String?,
        startTime: String,
        endTime: String,
        isActive: Boolean = true,
        isClosed: Boolean = false
    ): Resource<OperatingHour> = withContext(Dispatchers.IO) {
        try {
            val body = mutableMapOf<String, Any?>(
                "vendor_id" to vendorId,
                "start_time" to startTime,
                "end_time" to endTime,
                "is_active" to isActive,
                "is_closed" to isClosed
            )
            if (dayOfWeek != null) body["day_of_week"] = dayOfWeek

            val response = api.createOperatingHour(body)
            if (response.isSuccessful) {
                val item = response.body()?.firstOrNull()
                if (item != null) Resource.Success(item)
                else Resource.Success(OperatingHour(vendorId = vendorId, dayOfWeek = dayOfWeek, startTime = startTime, endTime = endTime, isActive = isActive, isClosed = isClosed))
            } else {
                Resource.Error("Failed to add operating hour: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error adding operating hour")
        }
    }

    suspend fun updateOperatingHour(
        id: String,
        startTime: String,
        endTime: String,
        isActive: Boolean = true,
        isClosed: Boolean = false,
        dayOfWeek: String? = null
    ): Resource<OperatingHour> = withContext(Dispatchers.IO) {
        try {
            val updates = mutableMapOf<String, Any?>(
                "start_time" to startTime,
                "end_time" to endTime,
                "is_active" to isActive,
                "is_closed" to isClosed
            )
            if (dayOfWeek != null) updates["day_of_week"] = dayOfWeek

            val response = api.updateOperatingHour("eq.$id", updates)
            if (response.isSuccessful) {
                val item = response.body()?.firstOrNull()
                if (item != null) Resource.Success(item)
                else Resource.Success(OperatingHour(id = id, startTime = startTime, endTime = endTime, isActive = isActive, isClosed = isClosed, dayOfWeek = dayOfWeek))
            } else {
                Resource.Error("Failed to update operating hour: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
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
            else Resource.Error("Failed to toggle operating hour: ${response.message()}", response.code())
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error toggling operating hour")
        }
    }

    suspend fun deleteOperatingHour(id: String): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.deleteOperatingHour("eq.$id")
            if (response.isSuccessful) Resource.Success(Unit)
            else Resource.Error("Failed to remove operating hour: ${response.message()}", response.code())
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
            val body = mapOf(
                "vendor_id" to vendorId,
                "vendor_type" to "hotel",
                "vendor_email" to email,
                "fcm_token" to token,
                "platform" to "android",
                "is_active" to true
            )
            val response = api.registerFcmToken(onConflict = "vendor_id", token = body)
            if (response.isSuccessful) {
                android.util.Log.d("VendorRepository", "FCM token successfully upserted for vendor_id=$vendorId")
                Resource.Success(Unit)
            } else {
                val errBody = response.errorBody()?.string()
                android.util.Log.e("VendorRepository", "FCM registration error: ${errBody ?: response.message()}")
                Resource.Error("FCM registration error: ${errBody ?: response.message()}", response.code())
            }
        } catch (e: Exception) {
            android.util.Log.e("VendorRepository", "Error upserting FCM token", e)
            Resource.Error(e.localizedMessage ?: "Error registering FCM token")
        }
    }

    suspend fun deactivateFcmToken(vendorId: String, token: String): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            api.updateFcmToken("eq.$vendorId", "eq.$token", mapOf("is_active" to false))
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error deactivating FCM token")
        }
    }

    // 13. Cities for Self-Registration
    suspend fun getActiveCities(): Resource<List<City>> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("VendorRepository", "Fetching active cities: select=id,name, status=eq.active, order=name.asc")
            val response = api.getCities(select = "id,name", statusFilter = "eq.active", order = "name.asc")
            if (response.isSuccessful) {
                val cities = response.body() ?: emptyList()
                android.util.Log.d("VendorRepository", "Successfully fetched ${cities.size} active cities: $cities")
                println("cities fetch success: fetched ${cities.size} cities: $cities")
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
                System.err.println("cities fetch error: $errorMsg")
                Resource.Error(errorMsg, code)
            }
        } catch (e: Exception) {
            val errorMsg = "cities fetch error (Exception): ${e.localizedMessage ?: e.javaClass.simpleName}"
            android.util.Log.e("VendorRepository", "Cities fetch exception", e)
            System.err.println("cities fetch error: $errorMsg")
            e.printStackTrace()
            Resource.Error(errorMsg)
        }
    }

    // 14. Self-Registration Flow
    suspend fun signUp(email: String, pass: String): Resource<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.signUp(SignUpRequest(email = email.trim(), password = pass))
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Resource.Success(body)
                } else {
                    Resource.Error("Sign up response was empty")
                }
            } else {
                val err = response.errorBody()?.string()
                Resource.Error(err ?: "Sign up failed with error code ${response.code()}", response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Network error during sign up")
        }
    }

    suspend fun verifyOtp(email: String, token: String): Resource<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            // First try type = 'email'
            var response = api.verifyOtp(VerifyOtpRequest(email = email.trim(), token = token.trim(), type = "email"))
            if (!response.isSuccessful) {
                // Fallback attempt with type = 'signup'
                val fallbackResponse = api.verifyOtp(VerifyOtpRequest(email = email.trim(), token = token.trim(), type = "signup"))
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
                        sessionManager.userEmail = body.user.email ?: email
                    }
                    Resource.Success(body)
                } else {
                    Resource.Error("OTP verification succeeded but no user data returned")
                }
            } else {
                val err = response.errorBody()?.string()
                Resource.Error(err ?: "Invalid or expired OTP code", response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Network error during OTP verification")
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
                val err = response.errorBody()?.string()
                Resource.Error("Upload failed: ${err ?: response.message()}", response.code())
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
            val relativePath = "$userId/$fileName"
            val requestBody = fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val response = api.uploadKycDocument(
                path = relativePath,
                fileBytes = requestBody,
                contentType = mimeType
            )
            if (response.isSuccessful) {
                // Storage path in bucket (e.g. userId/food-license-12345.jpg)
                val fullPath = response.body()?.key ?: relativePath
                Resource.Success(fullPath)
            } else {
                val err = response.errorBody()?.string()
                // If it already exists or returns path
                Resource.Error("Upload failed: ${err ?: response.message()}", response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error uploading KYC document")
        }
    }

    suspend fun createSignedUrl(path: String, expiresIn: Int = 3600): Resource<String> = withContext(Dispatchers.IO) {
        try {
            val response = api.createSignedUrl(path, SignedUrlRequest(expiresIn = expiresIn))
            if (response.isSuccessful) {
                val url = response.body()?.signedURL
                if (!url.isNullOrEmpty()) {
                    Resource.Success(url)
                } else {
                    Resource.Error("Signed URL not returned")
                }
            } else {
                Resource.Error("Failed to generate signed URL: ${response.message()}", response.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error getting document link")
        }
    }

    suspend fun submitVendorSelfRegistration(
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
        foodLicenseBytes: ByteArray?,
        foodLicenseExt: String = "jpg",
        foodLicenseMime: String = "image/jpeg",
        aadharBytes: ByteArray?,
        aadharExt: String = "jpg",
        aadharMime: String = "image/jpeg"
    ): Resource<Vendor> = withContext(Dispatchers.IO) {
        try {
            var foodLicensePath: String? = null
            var aadharPath: String? = null

            // 1. Upload Food License if provided
            if (foodLicenseBytes != null && foodLicenseBytes.isNotEmpty()) {
                val foodFileName = "food-license-${System.currentTimeMillis()}.$foodLicenseExt"
                val foodUploadRes = uploadKycDocument(userId, foodFileName, foodLicenseBytes, foodLicenseMime)
                if (foodUploadRes is Resource.Success) {
                    foodLicensePath = foodUploadRes.data
                } else if (foodUploadRes is Resource.Error) {
                    // Provide helpful error
                    return@withContext Resource.Error("Food License upload failed: ${foodUploadRes.message}")
                }
            }

            // 2. Upload Aadhar Card if provided
            if (aadharBytes != null && aadharBytes.isNotEmpty()) {
                val aadharFileName = "aadhar-${System.currentTimeMillis()}.$aadharExt"
                val aadharUploadRes = uploadKycDocument(userId, aadharFileName, aadharBytes, aadharMime)
                if (aadharUploadRes is Resource.Success) {
                    aadharPath = aadharUploadRes.data
                } else if (aadharUploadRes is Resource.Error) {
                    return@withContext Resource.Error("Aadhar Card upload failed: ${aadharUploadRes.message}")
                }
            }

            // 3. Upsert into profiles table
            val profileBody = mutableMapOf<String, Any?>(
                "id" to userId,
                "role" to "vendor",
                "full_name" to ownerName.trim(),
                "city_id" to cityId
            )
            try {
                api.insertProfile(profileBody)
            } catch (_: Exception) {
                // Non-fatal if profile trigger already created it or RLS requires minimal fields
            }

            // 4. Insert into vendors table with approval_status = 'pending'
            val vendorBody = mutableMapOf<String, Any?>(
                "user_id" to userId,
                "name" to hotelName.trim(),
                "owner_name" to ownerName.trim(),
                "phone" to phone.trim(),
                "email" to email.trim(),
                "vendor_type" to "hotel",
                "city_id" to cityId,
                "address" to address.trim(),
                "opening_time" to openingTime,
                "closing_time" to closingTime,
                "approval_status" to "pending",
                "is_active" to true
            )
            if (latitude != null) vendorBody["latitude"] = latitude
            if (longitude != null) vendorBody["longitude"] = longitude
            if (!foodLicensePath.isNullOrEmpty()) vendorBody["food_license_url"] = foodLicensePath
            if (!aadharPath.isNullOrEmpty()) vendorBody["aadhar_document_url"] = aadharPath

            val vendorResponse = api.insertVendor(vendorBody)
            if (vendorResponse.isSuccessful) {
                val created = vendorResponse.body()?.firstOrNull() ?: Vendor(
                    userId = userId,
                    name = hotelName,
                    ownerName = ownerName,
                    phone = phone,
                    email = email,
                    cityId = cityId,
                    address = address,
                    approvalStatus = "pending"
                )
                // Clear active session credentials so user remains in unauthenticated state
                // and cannot enter main dashboard until approved by City Admin
                sessionManager.clearSession()

                Resource.Success(created)
            } else {
                val err = vendorResponse.errorBody()?.string()
                Resource.Error("Registration failed: ${err ?: vendorResponse.message()}", vendorResponse.code())
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error submitting vendor registration")
        }
    }

    fun logout() {
        sessionManager.clearSession()
    }
}
