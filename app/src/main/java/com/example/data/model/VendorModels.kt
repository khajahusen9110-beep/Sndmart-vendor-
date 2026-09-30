package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AuthRequest(
    val email: String? = null,
    val phone: String? = null,
    val password: String
)

@JsonClass(generateAdapter = true)
data class SignUpRequest(
    val email: String? = null,
    val phone: String? = null,
    val password: String
)

@JsonClass(generateAdapter = true)
data class VerifyOtpRequest(
    val phone: String? = null,
    val email: String? = null,
    val token: String,
    val type: String = "sms"
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
data class StorageUploadResponse(
    @Json(name = "Key") val key: String? = null,
    @Json(name = "Id") val id: String? = null,
    val message: String? = null,
    val error: String? = null,
    val statusCode: String? = null
)

data class SelectedDocument(
    val uriString: String? = null,
    val fileName: String,
    val fileSizeFormatted: String,
    val bytes: ByteArray,
    val extension: String,
    val mimeType: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SelectedDocument
        if (fileName != other.fileName) return false
        if (!bytes.contentEquals(other.bytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

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
    @Json(name = "is_featured") val isFeatured: Boolean? = false,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class Category(
    val id: String = "",
    @Json(name = "vendor_id") val vendorId: String = "",
    val name: String = "",
    @Json(name = "name_kn") val nameKn: String? = null,
    val slug: String? = null,
    @Json(name = "image_url") val imageUrl: String? = null,
    @Json(name = "sort_order") val sortOrder: Int? = 0,
    @Json(name = "is_active") val isActive: Boolean = true,
    @Json(name = "vendor_type") val vendorType: String? = "hotel",
    @Json(name = "city_id") val cityId: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class Product(
    val id: String = "",
    @Json(name = "vendor_id") val vendorId: String = "",
    @Json(name = "category_id") val categoryId: String? = null,
    val name: String = "",
    @Json(name = "name_kn") val nameKn: String? = null,
    val description: String? = null,
    val price: Double = 0.0,
    val mrp: Double? = null,
    val unit: String? = "piece",
    @Json(name = "stock_qty") val stockQty: Int? = null,
    @Json(name = "stock_quantity") val stockQuantity: Int? = null,
    @Json(name = "low_stock_threshold") val lowStockThreshold: Int? = null,
    @Json(name = "is_available") val isAvailable: Boolean = true,
    @Json(name = "is_featured") val isFeatured: Boolean = false,
    @Json(name = "image_url") val imageUrl: String? = null,
    @Json(name = "is_active") val isActive: Boolean = true,
    @Json(name = "available_from") val availableFrom: String? = null,
    @Json(name = "available_until") val availableUntil: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
) {
    val effectiveStock: Int get() = stockQty ?: stockQuantity ?: 0
    val isSoldOut: Boolean get() = effectiveStock <= 0
}

@JsonClass(generateAdapter = true)
data class Customer(
    val id: String? = null,
    val full_name: String? = null,
    val phone: String? = null
) {
    val fullName: String? get() = full_name
}

@JsonClass(generateAdapter = true)
data class Address(
    val address_line: String? = null,
    val landmark: String? = null,
    val recipient_name: String? = null,
    val phone: String? = null
) {
    val addressLine: String? get() = address_line
    val recipientName: String? get() = recipient_name
}

@JsonClass(generateAdapter = true)
data class OrderItem(
    val id: String = "",
    val product_name: String? = null,
    val quantity: Double? = 1.0,
    val unit_price: Double? = 0.0,
    val total_price: Double? = 0.0,
    val order_id: String? = null,
    val product_id: String? = null,
    val vendor_id: String? = null,
    val variant_label: String? = null,
    val name: String? = null
) {
    val productName: String? get() = product_name ?: name
    val unitPrice: Double get() = unit_price ?: 0.0
    val totalPrice: Double get() = total_price ?: 0.0
    val displayName: String get() = product_name ?: name ?: "Item"
    val intQuantity: Int get() = quantity?.toInt() ?: 1
}

@JsonClass(generateAdapter = true)
data class Order(
    val id: String = "",
    val order_number: String? = null,
    val status: String? = "pending", // 'pending', 'confirmed', 'preparing', 'ready', 'out_for_delivery', 'delivered', 'cancelled', 'rejected'
    val subtotal: Double? = null,
    val discount_amount: Double? = null,
    val delivery_fee: Double? = null,
    val handling_fee: Double? = null,
    val total_amount: Double? = 0.0,
    val commission_amount: Double? = null,
    val vendor_amount: Double? = null,
    val notes: String? = null,
    val payment_status: String? = null,
    val payment_method: String? = null,
    val created_at: String? = null,
    val order_items: List<OrderItem>? = null,
    val customer: Customer? = null,
    val address: Address? = null,
    val vendor_id: String? = null,
    val customer_id: String? = null,
    val delivery_partner_id: String? = null,
    val address_id: String? = null,
    val slot_id: String? = null,
    val placed_at: String? = null,
    val city_id: String? = null
) {
    val orderNumber: String? get() = order_number
    val discountAmount: Double? get() = discount_amount
    val deliveryFee: Double? get() = delivery_fee
    val handlingFee: Double? get() = handling_fee
    val totalAmount: Double get() = total_amount ?: 0.0
    val commissionAmount: Double? get() = commission_amount
    val vendorAmount: Double? get() = vendor_amount
    val paymentStatus: String? get() = payment_status
    val paymentMethod: String? get() = payment_method
    val createdAt: String? get() = created_at
    val orderItems: List<OrderItem>? get() = order_items

    val customerName: String?
        get() = customer?.full_name?.takeIf { it.isNotBlank() } ?: "Customer"

    val customerPhone: String?
        get() = customer?.phone?.takeIf { it.isNotBlank() }

    val deliveryAddress: String?
        get() {
            val line = address?.address_line?.takeIf { it.isNotBlank() }
            val landmark = address?.landmark?.takeIf { it.isNotBlank() }
            return when {
                line != null && landmark != null -> "$line, Landmark: $landmark"
                line != null -> line
                landmark != null -> landmark
                else -> null
            }
        }
}

@JsonClass(generateAdapter = true)
data class OrderStatusHistory(
    val id: String? = null,
    @Json(name = "order_id") val orderId: String? = null,
    val status: String? = null,
    val notes: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

enum class WalletTransactionKind {
    CREDIT,   // Credit, order_credit: green "+₹...", positive inflow
    DEBIT,    // Debit, withdrawal, penalty: red "-₹...", deduction
    NEUTRAL   // Unknown, refund, adjustment: neutral styling, not silently credit
}

@JsonClass(generateAdapter = true)
data class WalletTransaction(
    val id: String = "",
    @Json(name = "vendor_id") val vendorId: String = "",
    @Json(name = "order_id") val orderId: String? = null,
    val type: String = "credit", // 'credit', 'debit', 'order_credit', 'withdrawal', 'penalty', 'adjustment', 'refund'
    val amount: Double = 0.0,
    val description: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
) {
    val kind: WalletTransactionKind
        get() {
            val normType = type.trim().lowercase()
            return when {
                normType in listOf("credit", "order_credit") -> WalletTransactionKind.CREDIT
                normType in listOf("debit", "withdrawal") -> WalletTransactionKind.DEBIT
                amount < 0.0 -> WalletTransactionKind.DEBIT
                normType in listOf("penalty") -> WalletTransactionKind.DEBIT
                normType in listOf("refund", "adjustment") -> {
                    if (amount < 0.0) WalletTransactionKind.DEBIT else WalletTransactionKind.NEUTRAL
                }
                normType.contains("debit") -> WalletTransactionKind.DEBIT
                else -> WalletTransactionKind.NEUTRAL
            }
        }

    val isCredit: Boolean
        get() = kind == WalletTransactionKind.CREDIT

    val isDebit: Boolean
        get() = kind == WalletTransactionKind.DEBIT

    val isNeutral: Boolean
        get() = kind == WalletTransactionKind.NEUTRAL
}

@JsonClass(generateAdapter = true)
data class VendorWalletResponse(
    @Json(name = "vendor_id") val vendorId: String? = null,
    val balance: Double = 0.0,
    val transactions: List<WalletTransaction>? = null
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
    @Json(name = "rejection_reason") val rejectionReason: String? = null,
    @Json(name = "commission_amount") val commissionAmount: Double? = null,
    @Json(name = "net_payable_amount") val netPayableAmount: Double? = null,
    @Json(name = "paid_at") val paidAt: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ReviewCustomer(
    @Json(name = "full_name") val fullName: String? = null
)

@JsonClass(generateAdapter = true)
data class Review(
    val id: String = "",
    @Json(name = "vendor_id") val vendorId: String = "",
    @Json(name = "order_id") val orderId: String? = null,
    @Json(name = "customer_id") val customerId: String? = null,
    val rating: Double = 5.0,
    val comment: String? = null,
    @Json(name = "order_number") val orderNumber: String? = null,
    @Json(name = "customer") val customer: ReviewCustomer? = null,
    @Json(name = "profiles") val profilesCustomer: ReviewCustomer? = null,
    @Json(name = "customer_name") val rawCustomerName: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
) {
    val customerName: String?
        get() = customer?.fullName?.takeIf { it.isNotBlank() }
            ?: profilesCustomer?.fullName?.takeIf { it.isNotBlank() }
            ?: rawCustomerName?.takeIf { it.isNotBlank() }
            ?: "Customer"
}

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
    @Json(name = "vendor_email") val vendorEmail: String? = null,
    @Json(name = "fcm_token") val fcmToken: String,
    val platform: String = "android",
    @Json(name = "is_active") val isActive: Boolean = true,
    @Json(name = "firebase_project") val firebaseProject: String = "native_vendor"
)

@JsonClass(generateAdapter = true)
data class AppVersionInfo(
    val id: String? = null,
    val platform: String? = null,
    @Json(name = "min_version_code") val minVersionCode: Int? = null,
    @Json(name = "latest_version_code") val latestVersionCode: Int? = null,
    @Json(name = "minimum_supported_version") val minimumSupportedVersion: String? = null,
    @Json(name = "current_version") val currentVersion: String? = null,
    @Json(name = "force_update") val forceUpdate: Boolean = false,
    @Json(name = "update_message") val updateMessage: String? = null,
    @Json(name = "update_url") val updateUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class AppSettingItem(
    val key: String? = null,
    val value: Map<String, Any?>? = null
) {
    val isMaintenanceEnabled: Boolean
        get() {
            val enabledVal = value?.get("enabled")
            return (enabledVal as? Boolean) == true || enabledVal?.toString()?.equals("true", ignoreCase = true) == true
        }

    val maintenanceMessage: String?
        get() = value?.get("message")?.toString()
}

@JsonClass(generateAdapter = true)
data class TestAlertResponse(
    val sent: Boolean = false,
    val reason: String? = null
)


