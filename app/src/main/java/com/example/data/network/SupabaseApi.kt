package com.example.data.network

import com.example.data.model.*
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface SupabaseApi {

    // 1. Auth Login & Registration (GoTrue)
    @POST("auth/v1/token?grant_type=password")
    suspend fun signInWithPassword(
        @Body request: AuthRequest
    ): Response<AuthResponse>

    @POST("auth/v1/signup")
    suspend fun signUp(
        @Body request: SignUpRequest
    ): Response<AuthResponse>

    @POST("auth/v1/verify")
    suspend fun verifyOtp(
        @Body request: VerifyOtpRequest
    ): Response<AuthResponse>

    @POST("auth/v1/token?grant_type=refresh_token")
    suspend fun refreshToken(
        @Body request: Map<String, String>
    ): Response<AuthResponse>

    @GET("auth/v1/user")
    suspend fun getAuthUser(): Response<AuthUser>

    // 1.1 Cities table - matching exact query: .from('cities').select('id, name').eq('status', 'active').order('name')
    @GET("rest/v1/cities")
    suspend fun getCities(
        @Query("select") select: String = "id,name",
        @Query("status") statusFilter: String? = "eq.active",
        @Query("order") order: String = "name.asc"
    ): Response<List<City>>

    // 1.2 Profiles table
    @GET("rest/v1/profiles")
    suspend fun getProfiles(
        @Query("id") idFilter: String,
        @Query("select") select: String = "id,full_name,phone"
    ): Response<List<Customer>>

    @POST("rest/v1/profiles")
    @Headers("Prefer: return=representation,resolution=merge-duplicates")
    suspend fun insertProfile(
        @Query("on_conflict") onConflict: String = "id",
        @Body profile: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<Map<String, Any>>>

    // 2. Vendors table
    @GET("rest/v1/vendors")
    suspend fun getVendorByUserId(
        @Query("user_id") userIdFilter: String = "eq.",
        @Query("select") select: String = "*"
    ): Response<List<Vendor>>

    @POST("rest/v1/vendors")
    @Headers("Prefer: return=representation")
    suspend fun insertVendor(
        @Body vendor: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<Vendor>>

    @PATCH("rest/v1/vendors")
    @Headers("Prefer: return=representation")
    suspend fun updateVendor(
        @Query("id") vendorIdFilter: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<Vendor>>

    // 3. Categories table
    @GET("rest/v1/categories")
    suspend fun getCategories(
        @Query("vendor_id") vendorIdFilter: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "name.asc"
    ): Response<List<Category>>

    @POST("rest/v1/categories")
    @Headers("Prefer: return=representation")
    suspend fun createCategory(
        @Body category: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<Category>>

    @PATCH("rest/v1/categories")
    @Headers("Prefer: return=representation")
    suspend fun updateCategory(
        @Query("id") categoryIdFilter: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<Category>>

    @DELETE("rest/v1/categories")
    suspend fun deleteCategory(
        @Query("id") categoryIdFilter: String
    ): Response<Unit>

    // 4. Products table
    @GET("rest/v1/products")
    suspend fun getProducts(
        @Query("vendor_id") vendorIdFilter: String,
        @Query("select") select: String = "id,vendor_id,category_id,name,description,price,image_url,is_active,stock_qty,is_available,created_at",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int? = 30,
        @Query("offset") offset: Int? = 0
    ): Response<List<Product>>

    @POST("rest/v1/products")
    @Headers("Prefer: return=representation")
    suspend fun createProduct(
        @Body product: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<Product>>

    @PATCH("rest/v1/products")
    @Headers("Prefer: return=representation")
    suspend fun updateProduct(
        @Query("id") productIdFilter: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<Product>>

    @DELETE("rest/v1/products")
    suspend fun deleteProduct(
        @Query("id") productIdFilter: String
    ): Response<Unit>

    // 5. Orders table (with nested order_items and customer address)
    @GET("rest/v1/orders")
    suspend fun getOrders(
        @Query("vendor_id") vendorIdFilter: String,
        @Query("status") statusFilter: String = "in.(confirmed,preparing,ready,out_for_delivery,delivered,cancelled,rejected)",
        @Query("select") select: String = "id,order_number,status,subtotal,discount_amount,delivery_fee,handling_fee,total_amount,commission_amount,vendor_amount,notes,payment_status,payment_method,created_at,customer_id,address_id,order_items(id,product_name,quantity,unit_price,total_price),address:customer_addresses!orders_address_id_fkey(address_line,landmark,recipient_name,phone)",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int? = 20,
        @Query("offset") offset: Int? = 0
    ): Response<List<Order>>

    @GET("rest/v1/orders")
    suspend fun getOrderById(
        @Query("id") idFilter: String,
        @Query("select") select: String = "*,order_items(*),address:customer_addresses!orders_address_id_fkey(address_line,landmark,recipient_name,phone)"
    ): Response<List<Order>>

    @PATCH("rest/v1/orders")
    @Headers("Prefer: return=representation")
    suspend fun updateOrderStatus(
        @Query("id") orderIdFilter: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<Order>>

    // 6. Order Status History
    @GET("rest/v1/order_status_history")
    suspend fun getOrderStatusHistory(
        @Query("order_id") orderIdFilter: String,
        @Query("select") select: String = "status,created_at",
        @Query("order") order: String = "created_at.asc"
    ): Response<List<OrderStatusHistory>>

    @POST("rest/v1/order_status_history")
    @Headers("Prefer: return=representation")
    suspend fun insertOrderStatusHistory(
        @Body history: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<OrderStatusHistory>>

    // 7. Wallet Transactions
    @GET("rest/v1/vendor_wallet_transactions")
    suspend fun getWalletTransactions(
        @Query("vendor_id") vendorIdFilter: String,
        @Query("select") select: String = "id,vendor_id,order_id,type,amount,description,created_at",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int? = 20,
        @Query("offset") offset: Int? = 0
    ): Response<List<WalletTransaction>>

    // 8. Payouts
    @GET("rest/v1/vendor_payouts")
    suspend fun getPayouts(
        @Query("vendor_id") vendorIdFilter: String,
        @Query("select") select: String = "id,vendor_id,order_id,gross_amount,commission_amount,net_amount,status,paid_at,created_at",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int? = 20,
        @Query("offset") offset: Int? = 0
    ): Response<List<Payout>>

    // 9. Withdrawals
    @GET("rest/v1/vendor_withdrawals")
    suspend fun getWithdrawals(
        @Query("vendor_id") vendorIdFilter: String,
        @Query("select") select: String = "id,vendor_id,city_id,amount,status,note,commission_amount,net_payable_amount,paid_at,created_at",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int? = 20,
        @Query("offset") offset: Int? = 0
    ): Response<List<Withdrawal>>

    @POST("rest/v1/vendor_withdrawals")
    @Headers("Prefer: return=representation")
    suspend fun requestWithdrawal(
        @Body withdrawal: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<Withdrawal>>

    // 10. Reviews
    @GET("rest/v1/vendor_reviews")
    suspend fun getReviews(
        @Query("vendor_id") vendorIdFilter: String,
        @Query("select") select: String = "id,vendor_id,order_id,customer_id,rating,comment,created_at,customer:profiles!vendor_reviews_customer_id_fkey(full_name)",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int? = 20,
        @Query("offset") offset: Int? = 0
    ): Response<List<Review>>

    // 11. Operating Hours
    @GET("rest/v1/vendor_operating_hours")
    suspend fun getOperatingHours(
        @Query("vendor_id") vendorIdFilter: String,
        @Query("select") select: String = "*"
    ): Response<List<OperatingHour>>

    @POST("rest/v1/vendor_operating_hours")
    @Headers("Prefer: return=representation")
    suspend fun createOperatingHour(
        @Body hour: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<OperatingHour>>

    @PATCH("rest/v1/vendor_operating_hours")
    @Headers("Prefer: return=representation")
    suspend fun updateOperatingHour(
        @Query("id") idFilter: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<OperatingHour>>

    @DELETE("rest/v1/vendor_operating_hours")
    suspend fun deleteOperatingHour(
        @Query("id") idFilter: String
    ): Response<Unit>

    // 12. FCM Tokens registration
    @GET("rest/v1/vendor_fcm_tokens")
    suspend fun getFcmTokens(
        @Query("vendor_id") vendorIdFilter: String,
        @Query("fcm_token") fcmTokenFilter: String,
        @Query("select") select: String = "*"
    ): Response<List<FcmTokenRegistration>>

    @POST("rest/v1/vendor_fcm_tokens")
    @Headers("Prefer: return=representation,resolution=merge-duplicates")
    suspend fun registerFcmToken(
        @Query("on_conflict") onConflict: String = "vendor_id",
        @Body token: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<FcmTokenRegistration>>

    @PATCH("rest/v1/vendor_fcm_tokens")
    @Headers("Prefer: return=representation")
    suspend fun updateFcmToken(
        @Query("vendor_id") vendorIdFilter: String,
        @Query("fcm_token") fcmTokenFilter: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any?>
    ): Response<List<FcmTokenRegistration>>

    // 13. Supabase Storage
    @POST("storage/v1/object/{bucket}/{path}")
    suspend fun uploadStorageObject(
        @Path(value = "bucket", encoded = true) bucket: String,
        @Path(value = "path", encoded = true) path: String,
        @Body fileBytes: RequestBody,
        @Header("Content-Type") contentType: String = "image/jpeg",
        @Header("x-upsert") upsert: String = "true"
    ): Response<StorageUploadResponse>

    @POST("storage/v1/object/vendor-kyc-documents/{path}")
    suspend fun uploadKycDocument(
        @Path(value = "path", encoded = true) path: String,
        @Body fileBytes: RequestBody,
        @Header("Content-Type") contentType: String = "image/jpeg",
        @Header("x-upsert") upsert: String = "true"
    ): Response<StorageUploadResponse>

    @POST("storage/v1/object/sign/vendor-kyc-documents/{path}")
    suspend fun createSignedUrl(
        @Path(value = "path", encoded = true) path: String,
        @Body request: SignedUrlRequest
    ): Response<SignedUrlResponse>

    // 14. App Versions & Maintenance Mode
    @GET("rest/v1/app_versions")
    suspend fun getAppVersions(
        @Query("platform") platformFilter: String = "eq.vendor_app",
        @Query("select") select: String = "*"
    ): Response<List<AppVersionInfo>>

    @GET("rest/v1/app_settings")
    suspend fun getAppSettings(
        @Query("key") keyFilter: String = "eq.maintenance_mode",
        @Query("select") select: String = "key,value"
    ): Response<List<AppSettingItem>>
}
