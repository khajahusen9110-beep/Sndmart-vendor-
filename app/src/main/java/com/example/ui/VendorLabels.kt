package com.example.ui

import androidx.compose.runtime.compositionLocalOf

/**
 * What kind of business this vendor runs. A hotel keeps the food screens; a shop gets product screens, and which
 * options it sees depends on its category template (set by the City Admin when the shop's category is confirmed).
 */
enum class VendorKind { HOTEL, SHOP_STANDARD, SHOP_WEIGHT, SHOP_CUSTOM, SHOP_SPECS }

data class VendorLabels(val kind: VendorKind = VendorKind.HOTEL, val categoryName: String? = null) {
    val isHotel: Boolean get() = kind == VendorKind.HOTEL
    val isShop: Boolean get() = !isHotel

    /** Made-to-order options (message / photo / notice) belong to customised categories: bakery, gifts, frames, printing. */
    val allowsCustomisation: Boolean get() = kind == VendorKind.SHOP_CUSTOM
    /** Sold by weight or count: the unit ("1 kg", "500 g", "1 dozen") matters most. */
    val unitFirst: Boolean get() = kind == VendorKind.SHOP_WEIGHT

    private val what: String get() = categoryName?.takeIf { it.isNotBlank() } ?: "shop"

    // ---- words ----
    val navMenu: String get() = if (isHotel) "Menu" else "Products"
    val itemsTab: String get() = if (isHotel) "Menu Items" else "Products"
    val catalogTitle: String get() = if (isHotel) "Menu Catalog" else "Product Catalog"
    fun itemsCount(n: Int): String = if (isHotel) "$n Items on Menu" else "$n Products"
    val catalogSubtitle: String get() = if (isHotel) "Manage your restaurant food catalog" else "Manage your $what products"
    val searchHint: String get() = if (isHotel) "Search menu items..." else "Search products..."
    val loadFailed: String get() = if (isHotel) "Unable to load menu" else "Unable to load products"
    val retryLoad: String get() = if (isHotel) "Retry Loading Menu" else "Retry Loading Products"
    val emptyCategories: String get() = if (isHotel) "Add your first category and food items" else "Add your first category and products"
    fun categoriesTitle(n: Int): String = if (isHotel) "Menu Categories ($n)" else "Product Categories ($n)"
    val categoriesHint: String get() = if (isHotel) "Group food items into organized categories" else "Group your products into categories"
    val addCategory: String get() = if (isHotel) "Add Menu Category" else "Add Product Category"
    fun itemDialogTitle(edit: Boolean): String = if (isHotel) (if (edit) "Edit Menu Item" else "Add New Menu Item") else (if (edit) "Edit Product" else "Add New Product")
    val stockOn: String get() = if (isHotel) "Item stays orderable whenever kitchen is open (100 buffer)" else "Product stays orderable whenever the shop is open (100 buffer)"
    val deleteTitle: String get() = if (isHotel) "Delete Menu Item" else "Delete Product"
    fun deleteMessage(name: String?): String = "Are you sure you want to remove \"$name\" from your ${if (isHotel) "menu" else "products"}?"
    val orderInstructions: String get() = if (isHotel) "🍳 Cooking Instructions" else "📝 Customer Instructions"
    val itemsSubtotal: String get() = if (isHotel) "Food Subtotal (Hotel):" else "Items Subtotal:"
    val earningLabel: String get() = if (isHotel) "Hotel Earning:" else "Your Earning:"
    val creditedNote: String get() = if (isHotel) "Food amount credited to hotel wallet upon delivery." else "Your amount (after commission) is credited to your wallet upon delivery."
    val subtotalEarned: String get() = if (isHotel) "Food subtotal earned from delivered orders" else "Item amount earned from delivered orders"
    val storeNameLabel: String get() = if (isHotel) "Store / Hotel Name *" else "Shop Name *"
    val portalName: String get() = if (isHotel) "Hotel & Food Merchant Portal" else "Shop Merchant Portal"

    companion object {
        /** Template names come from store_categories.template. Prescription shops are parked: they use the standard screens. */
        fun forShop(template: String?, categoryName: String?): VendorLabels = VendorLabels(
            when (template?.lowercase()) {
                "weight" -> VendorKind.SHOP_WEIGHT
                "custom" -> VendorKind.SHOP_CUSTOM
                "specs" -> VendorKind.SHOP_SPECS
                else -> VendorKind.SHOP_STANDARD
            },
            categoryName
        )
    }
}

val LocalVendorLabels = compositionLocalOf { VendorLabels() }
