package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AuthRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class SignUpRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class VerifyOtpRequest(
    val email: String,
    val token: String,
    val type: String = "email"
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "token_type") val tokenType: String? = null,
    @Json(name = "expires_in") val expiresIn: Long? = null,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    val user: AuthUser? = null,
    @Json(name = "error_description") val errorDescription: String? = null,
    val error: String? = null,
    val msg: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class AuthUser(
    val id: String,
    val email: String? = null,
    val phone: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class City(
    val id: String = "",
    val name: String = "",
    val state: String? = null,
    val status: String? = "active",
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SignedUrlRequest(
    val expiresIn: Int = 3600
)

@JsonClass(generateAdapter = true)
data class SignedUrlResponse(
    @Json(name = "signedURL") val signedURL: String? = null,
    val error: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class StorageUploadResponse(
    @Json(name = "Key") val key: String? = null,
    @Json(name = "Id") val id: String? = null,
    val message: String? = null,
    val error: String? = null,
    val statusCode: String? = null
)

@JsonClass(generateAdapter = true)
data class Vendor(
    val id: String = "",
    @Json(name = "user_id") val userId: String = "",
    val name: String = "",
    @Json(name = "owner_name") val ownerName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    @Json(name = "vendor_type") val vendorType: String? = "hotel",
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @Json(name = "opening_time") val openingTime: String? = null,
    @Json(name = "closing_time") val closingTime: String? = null,
    @Json(name = "banner_url") val bannerUrl: String? = null,
    @Json(name = "logo_url") val logoUrl: String? = null,
    @Json(name = "is_open") val isOpen: Boolean = true,
    @Json(name = "is_active") val isActive: Boolean = true,
    @Json(name = "approval_status") val approvalStatus: String = "pending", // 'pending', 'approved', 'rejected'
    @Json(name = "rejection_reason") val rejectionReason: String? = null,
    @Json(name = "commission_percent") val commissionPercent: Double? = null,
    @Json(name = "city_id") val cityId: String? = null,
    @Json(name = "food_license_url") val foodLicenseUrl: String? = null,
    @Json(name = "aadhar_document_url") val aadharDocumentUrl: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class Category(
    val id: String = "",
    @Json(name = "vendor_id") val vendorId: String = "",
    val name: String = "",
    @Json(name = "image_url") val imageUrl: String = "",
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class Product(
    val id: String = "",
    @Json(name = "vendor_id") val vendorId: String = "",
    @Json(name = "category_id") val categoryId: String? = null,
    val name: String = "",
    val description: String? = null,
    val price: Double = 0.0,
    @Json(name = "image_url") val imageUrl: String? = null,
    @Json(name = "is_active") val isActive: Boolean = true,
    @Json(name = "available_from") val availableFrom: String? = null,
    @Json(name = "available_until") val availableUntil: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class Order(
    val id: String = "",
    @Json(name = "order_number") val orderNumber: String? = null,
    @Json(name = "vendor_id") val vendorId: String = "",
    @Json(name = "customer_name") val customerName: String? = null,
    @Json(name = "customer_phone") val customerPhone: String? = null,
    @Json(name = "delivery_address") val deliveryAddress: String? = null,
    val status: String = "pending", // 'pending', 'confirmed', 'preparing', 'ready', 'delivered', 'cancelled'
    @Json(name = "total_amount") val totalAmount: Double = 0.0,
    @Json(name = "payment_status") val paymentStatus: String? = null,
    @Json(name = "payment_method") val paymentMethod: String? = null,
    @Json(name = "cancellation_reason") val cancellationReason: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "order_items") val orderItems: List<OrderItem>? = null
)

@JsonClass(generateAdapter = true)
data class OrderItem(
    val id: String = "",
    @Json(name = "order_id") val orderId: String = "",
    @Json(name = "product_id") val productId: String? = null,
    @Json(name = "product_name") val productName: String? = null,
    val name: String? = null,
    val quantity: Int = 1,
    @Json(name = "unit_price") val unitPrice: Double = 0.0,
    @Json(name = "total_price") val totalPrice: Double = 0.0
) {
    val displayName: String
        get() = productName ?: name ?: "Item"
}

@JsonClass(generateAdapter = true)
data class OrderStatusHistory(
    val id: String = "",
    @Json(name = "order_id") val orderId: String = "",
    val status: String = "",
    val notes: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class WalletTransaction(
    val id: String = "",
    @Json(name = "vendor_id") val vendorId: String = "",
    val type: String = "credit", // 'credit', 'debit', 'order_earning', 'withdrawal_payout', etc.
    val amount: Double = 0.0,
    val description: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class Payout(
    val id: String = "",
    @Json(name = "vendor_id") val vendorId: String = "",
    @Json(name = "gross_amount") val grossAmount: Double = 0.0,
    @Json(name = "commission_amount") val commissionAmount: Double = 0.0,
    @Json(name = "net_amount") val netAmount: Double = 0.0,
    val status: String = "pending", // 'pending', 'paid', 'failed'
    @Json(name = "paid_at") val paidAt: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class Withdrawal(
    val id: String = "",
    @Json(name = "vendor_id") val vendorId: String = "",
    @Json(name = "city_id") val cityId: String? = null,
    val amount: Double = 0.0,
    val status: String = "pending", // 'pending', 'approved', 'paid', 'rejected'
    val note: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class Review(
    val id: String = "",
    @Json(name = "vendor_id") val vendorId: String = "",
    @Json(name = "customer_name") val customerName: String? = null,
    val rating: Double = 5.0,
    val comment: String? = null,
    @Json(name = "order_id") val orderId: String? = null,
    @Json(name = "order_number") val orderNumber: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class OperatingHour(
    val id: String = "",
    @Json(name = "vendor_id") val vendorId: String = "",
    @Json(name = "day_of_week") val dayOfWeek: String? = null,
    @Json(name = "start_time") val startTime: String = "08:00",
    @Json(name = "end_time") val endTime: String = "22:00",
    @Json(name = "is_active") val isActive: Boolean = true,
    @Json(name = "is_closed") val isClosed: Boolean = false
) {
    val isSlotActive: Boolean
        get() = isActive && !isClosed
}

@JsonClass(generateAdapter = true)
data class FcmTokenRegistration(
    val id: String? = null,
    @Json(name = "vendor_id") val vendorId: String,
    @Json(name = "vendor_type") val vendorType: String = "hotel",
    @Json(name = "vendor_email") val vendorEmail: String,
    @Json(name = "fcm_token") val fcmToken: String,
    val platform: String = "android",
    @Json(name = "is_active") val isActive: Boolean = true
)
