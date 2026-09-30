package com.example.ui.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Category
import com.example.data.model.Product
import com.example.util.CurrencyFormatter
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.ProductCardSkeleton
import com.example.ui.theme.SndmartGreen
import com.example.ui.theme.SndmartOrange
import com.example.ui.theme.polishTextFieldColors
import com.example.ui.theme.*
import com.example.ui.viewmodel.MenuUiState
import kotlinx.coroutines.delay

enum class MenuSectionTab(val title: String) {
    ITEMS("Menu Items"),
    CATEGORIES("Categories"),
    AVAILABILITY("Availability")
}

// Helpful preset image URLs for food categories and items
val SAMPLE_FOOD_IMAGES = listOf(
    "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=400" to "Salad & Healthy",
    "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=400" to "Burgers & Sandwiches",
    "https://images.unsplash.com/photo-1513104890138-7c749659a591?w=400" to "Pizza & Italian",
    "https://images.unsplash.com/photo-1589302168068-964664d93dc0?w=400" to "Biryani & Rice",
    "https://images.unsplash.com/photo-1565557623262-b51c2513a641?w=400" to "Curry & Indian",
    "https://images.unsplash.com/photo-1578985545062-69928b1d9587?w=400" to "Desserts & Cakes",
    "https://images.unsplash.com/photo-1544145945-f90425340c7e?w=400" to "Beverages & Drinks",
    "https://images.unsplash.com/photo-1550547660-d9450f859349?w=400" to "Fast Food & Snacks"
)

@OptIn(ExperimentalMaterial3Api::class)
/** 24-hour time of day such as 7:00, 07:00 or 22:30:00. */
private val TIME_OF_DAY_REGEX = Regex("^([01]?\\d|2[0-3]):[0-5]\\d(:[0-5]\\d)?$")

@Composable
fun MenuScreen(
    state: MenuUiState,
    onAddCategory: (name: String, imageUrl: String, onComplete: (Boolean, String?) -> Unit) -> Unit,
    onUpdateCategory: (id: String, name: String, imageUrl: String, onComplete: (Boolean, String?) -> Unit) -> Unit,
    onDeleteCategory: (id: String, onComplete: (Boolean, String?) -> Unit) -> Unit,
    onAddProduct: (categoryId: String?, name: String, description: String?, price: Double, imageUrl: String?, isActive: Boolean, availableFrom: String?, availableUntil: String?, stockQty: Int, isAvailable: Boolean, onComplete: (Boolean, String?) -> Unit) -> Unit,
    onUpdateProduct: (productId: String, updates: Map<String, Any?>, onComplete: (Boolean, String?) -> Unit) -> Unit,
    onToggleProductActive: (Product) -> Unit,
    onDeleteProduct: (productId: String, onComplete: (Boolean, String?) -> Unit) -> Unit,
    onUploadCategoryImage: (fileName: String, fileBytes: ByteArray, mimeType: String, onComplete: (Boolean, String?, String?) -> Unit) -> Unit = { _, _, _, cb -> cb(false, null, "Not configured") },
    onUploadProductImage: (fileName: String, fileBytes: ByteArray, mimeType: String, onComplete: (Boolean, String?, String?) -> Unit) -> Unit = { _, _, _, cb -> cb(false, null, "Not configured") },
    onQuickRestockItem: ((productId: String, targetStock: Int) -> Unit)? = null,
    onRestockAllZeroStock: ((targetStock: Int) -> Unit)? = null,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeSubTab by remember { mutableStateOf(MenuSectionTab.ITEMS) }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var filterSoldOutOnly by remember { mutableStateOf(false) }
    var localSearchText by remember { mutableStateOf(state.searchQuery) }

    // Rule 5: Debounce search inputs by 400ms
    LaunchedEffect(localSearchText) {
        if (localSearchText != state.searchQuery) {
            delay(400)
            onSearchQueryChange(localSearchText)
        }
    }

    // Dialog states
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }

    var showAddProductDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var productToDelete by remember { mutableStateOf<Product?>(null) }

    val zeroStockProducts = remember(state.products) {
        state.products.filter { it.isSoldOut }
    }

    val filteredProducts = remember(state.products, selectedCategoryFilter, filterSoldOutOnly, state.searchQuery) {
        var list = if (selectedCategoryFilter == null) state.products
        else state.products.filter { it.categoryId == selectedCategoryFilter }
        if (filterSoldOutOnly) {
            list = list.filter { it.isSoldOut }
        }
        if (state.searchQuery.isNotBlank()) {
            val q = state.searchQuery.trim().lowercase()
            list = list.filter {
                it.name.lowercase().contains(q) ||
                (it.description?.lowercase()?.contains(q) == true)
            }
        }
        list
    }

    val itemsListState = rememberLazyListState()
    val shouldLoadMoreProducts = remember {
        derivedStateOf {
            val totalItems = itemsListState.layoutInfo.totalItemsCount
            val lastVisible = itemsListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 2
        }
    }

    LaunchedEffect(shouldLoadMoreProducts.value) {
        if (shouldLoadMoreProducts.value && state.canLoadMore && !state.isLoadingMore && !state.isLoading) {
            onLoadMore()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Sub-tab selector
        TabRow(
            selectedTabIndex = activeSubTab.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = SndmartOrange
        ) {
            MenuSectionTab.values().forEach { tab ->
                Tab(
                    selected = activeSubTab == tab,
                    onClick = { activeSubTab = tab },
                    text = {
                        Text(
                            text = tab.title,
                            fontWeight = if (activeSubTab == tab) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        if (state.errorMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (state.successMessage != null) {
            Surface(
                color = Color(0xFFE8F5E9),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = state.successMessage,
                        color = Color(0xFF1B5E20),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        when (activeSubTab) {
            MenuSectionTab.ITEMS -> {
                LazyColumn(
                    state = itemsListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    // Header with count & Add Button
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (state.errorMessage != null && state.products.isEmpty()) "Menu Catalog" else "${state.products.size} Items on Menu",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Manage your restaurant food catalog",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { showAddProductDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SndmartOrange),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Item", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Rule 5: Debounced search text field for products
                    item {
                        OutlinedTextField(
                            value = localSearchText,
                            onValueChange = { localSearchText = it },
                            placeholder = { Text("Search menu items...", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (localSearchText.isNotEmpty()) {
                                    IconButton(onClick = { localSearchText = "" }) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = Slate400, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SndmartOrange,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Zero Stock Alert Banner
                    if (zeroStockProducts.isNotEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                                border = BorderStroke(1.dp, Color(0xFFFFB74D))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.WarningAmber,
                                            contentDescription = null,
                                            tint = Color(0xFFE65100),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${zeroStockProducts.size} item(s) showing Sold Out (Stock is 0)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color(0xFFBF360C)
                                            )
                                            Text(
                                                text = "Customer app marks items with 0 stock as unavailable. Quick restock below or edit stock to make them orderable.",
                                                fontSize = 12.sp,
                                                color = Color(0xFF5D4037)
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = { onRestockAllZeroStock?.invoke(100) },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(imageVector = Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Quick Restock All (100)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = { filterSoldOutOnly = !filterSoldOutOnly },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(if (filterSoldOutOnly) "Show All" else "View (${zeroStockProducts.size})", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Category Filter Chips
                    if (state.categories.isNotEmpty() || zeroStockProducts.isNotEmpty()) {
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedCategoryFilter == null && !filterSoldOutOnly,
                                        onClick = {
                                            selectedCategoryFilter = null
                                            filterSoldOutOnly = false
                                        },
                                        label = { Text("All (${state.products.size})") }
                                    )
                                }
                                if (zeroStockProducts.isNotEmpty()) {
                                    item {
                                        FilterChip(
                                            selected = filterSoldOutOnly,
                                            onClick = {
                                                filterSoldOutOnly = !filterSoldOutOnly
                                                if (filterSoldOutOnly) selectedCategoryFilter = null
                                            },
                                            label = { Text("⚠️ Sold Out (${zeroStockProducts.size})") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFFFFEBEE),
                                                selectedLabelColor = Color(0xFFC62828)
                                            )
                                        )
                                    }
                                }
                                items(state.categories, key = { it.id }) { cat ->
                                    val count = state.products.count { it.categoryId == cat.id }
                                    FilterChip(
                                        selected = selectedCategoryFilter == cat.id && !filterSoldOutOnly,
                                        onClick = {
                                            filterSoldOutOnly = false
                                            selectedCategoryFilter = if (selectedCategoryFilter == cat.id) null else cat.id
                                        },
                                        label = { Text("${cat.name} ($count)") }
                                    )
                                }
                            }
                        }
                    }

                    // Products list
                    if (state.isLoading && state.products.isEmpty()) {
                        items(5) {
                            ProductCardSkeleton()
                        }
                    } else if (state.errorMessage != null && state.products.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(42.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Unable to load menu",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = state.errorMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = onRefresh,
                                        colors = ButtonDefaults.buttonColors(containerColor = SndmartOrange)
                                    ) {
                                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Retry Loading Menu")
                                    }
                                }
                            }
                        }
                    } else if (filteredProducts.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Restaurant,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = if (state.categories.isEmpty()) "Add your first category and food items" else "No items in this category",
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            if (state.categories.isEmpty()) showAddCategoryDialog = true
                                            else showAddProductDialog = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SndmartOrange)
                                    ) {
                                        Text(if (state.categories.isEmpty()) "Add Category First" else "Add Item")
                                    }
                                }
                            }
                        }
                    } else {
                        items(filteredProducts, key = { it.id }) { product ->
                            val categoryName = state.categories.find { it.id == product.categoryId }?.name ?: "Uncategorized"
                            ElevatedCard(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Product Image
                                    if (!product.imageUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = product.imageUrl,
                                            contentDescription = product.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(SndmartOrange.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Fastfood,
                                                contentDescription = null,
                                                tint = SndmartOrange,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }

                                    // Product Details
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = product.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = categoryName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (!product.description.isNullOrBlank()) {
                                            Text(
                                                text = product.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = CurrencyFormatter.format(product.price),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = SndmartOrange
                                            )
                                            // Stock Badge
                                            if (product.isSoldOut) {
                                                Surface(
                                                    color = Color(0xFFFFEBEE),
                                                    shape = RoundedCornerShape(6.dp),
                                                    border = BorderStroke(1.dp, Color(0xFFFFCDD2))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Box(modifier = Modifier.size(6.dp).background(Color(0xFFD32F2F), CircleShape))
                                                        Text(
                                                            text = "Sold Out (0)",
                                                            color = Color(0xFFC62828),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            } else {
                                                val stock = product.effectiveStock
                                                val isLow = stock <= 10
                                                Surface(
                                                    color = if (isLow) Color(0xFFFFF3E0) else Color(0xFFE8F5E9),
                                                    shape = RoundedCornerShape(6.dp),
                                                    border = BorderStroke(1.dp, if (isLow) Color(0xFFFFE0B2) else Color(0xFFC8E6C9))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Box(modifier = Modifier.size(6.dp).background(if (isLow) Color(0xFFEF6C00) else Color(0xFF2E7D32), CircleShape))
                                                        Text(
                                                            text = if (isLow) "Low: $stock" else "Stock: $stock",
                                                            color = if (isLow) Color(0xFFE65100) else Color(0xFF1B5E20),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Action buttons & Active Toggle
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (product.isSoldOut && onQuickRestockItem != null) {
                                            FilledTonalButton(
                                                onClick = { onQuickRestockItem(product.id, 100) },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = Color(0xFFFFE0B2),
                                                    contentColor = Color(0xFFE65100)
                                                ),
                                                modifier = Modifier.height(24.dp)
                                            ) {
                                                Text("+100 Stock", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = if (product.isActive) "Active" else "Off",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (product.isActive) SndmartGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Switch(
                                                checked = product.isActive,
                                                onCheckedChange = { onToggleProductActive(product) },
                                                modifier = Modifier.height(24.dp)
                                            )
                                        }

                                        Row {
                                            IconButton(
                                                onClick = { editingProduct = product },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { productToDelete = product },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Rule 1: Pagination footer (30 items at a time)
                    if (state.isLoadingMore) {
                        item {
                            ProductCardSkeleton()
                        }
                    } else if (state.canLoadMore && filteredProducts.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                OutlinedButton(
                                    onClick = onLoadMore,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, SndmartOrange.copy(alpha = 0.5f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SndmartOrange)
                                ) {
                                    Icon(imageVector = Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Load More Items (30 more)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            MenuSectionTab.CATEGORIES -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Menu Categories (${state.categories.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Group food items into organized categories",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { showAddCategoryDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SndmartOrange),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Category", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (state.errorMessage != null && state.categories.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(42.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Unable to load categories",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = state.errorMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = onRefresh,
                                        colors = ButtonDefaults.buttonColors(containerColor = SndmartOrange)
                                    ) {
                                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Retry Loading Categories")
                                    }
                                }
                            }
                        }
                    } else if (state.categories.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Category,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No categories yet",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Create categories like Starters, Main Course, Beverages, etc.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { showAddCategoryDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = SndmartOrange)
                                    ) {
                                        Text("Create First Category")
                                    }
                                }
                            }
                        }
                    } else {
                        items(state.categories, key = { it.id }) { category ->
                            val itemsCount = state.products.count { it.categoryId == category.id }
                            ElevatedCard(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (!category.imageUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = category.imageUrl,
                                            contentDescription = category.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(SndmartOrange.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Category,
                                                contentDescription = null,
                                                tint = SndmartOrange,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = category.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "$itemsCount items assigned",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row {
                                        IconButton(onClick = { editingCategory = category }) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { categoryToDelete = category }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            MenuSectionTab.AVAILABILITY -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    item {
                        Column {
                            Text(
                                text = "Item Availability Timing",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Set specific hours when breakfast, lunch, or special items can be ordered",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(state.products, key = { it.id }) { product ->
                        var isEditingTiming by remember { mutableStateOf(false) }
                        var fromTime by remember { mutableStateOf(product.availableFrom ?: "08:00") }
                        var untilTime by remember { mutableStateOf(product.availableUntil ?: "22:00") }
                        var timingError by remember { mutableStateOf<String?>(null) }

                        ElevatedCard(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = product.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (product.availableFrom != null && product.availableUntil != null) {
                                                "Available: ${product.availableFrom} — ${product.availableUntil}"
                                            } else {
                                                "Available all day (default)"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (product.availableFrom != null) SndmartOrange else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    TextButton(onClick = { isEditingTiming = !isEditingTiming }) {
                                        Text(if (isEditingTiming) "Close" else "Set Timing")
                                    }
                                }

                                if (isEditingTiming) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = fromTime,
                                            onValueChange = { fromTime = it },
                                            label = { Text("From (e.g. 07:00)") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = untilTime,
                                            onValueChange = { untilTime = it },
                                            label = { Text("Until (e.g. 11:30)") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            singleLine = true
                                        )
                                    }

                                    timingError?.let { err ->
                                        Text(text = err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = {
                                                onUpdateProduct(
                                                    product.id,
                                                    mapOf("available_from" to null, "available_until" to null)
                                                ) { success, err ->
                                                    if (success) isEditingTiming = false else timingError = err
                                                }
                                            }
                                        ) {
                                            Text("Clear Timing", color = MaterialTheme.colorScheme.error)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                val from = fromTime.trim()
                                                val until = untilTime.trim()
                                                if (!TIME_OF_DAY_REGEX.matches(from) || !TIME_OF_DAY_REGEX.matches(until)) {
                                                    timingError = "Enter time as HH:MM in 24-hour format, e.g. 07:00 or 22:30"
                                                    return@Button
                                                }
                                                timingError = null
                                                onUpdateProduct(
                                                    product.id,
                                                    mapOf("available_from" to from, "available_until" to until)
                                                ) { success, err ->
                                                    if (success) isEditingTiming = false else timingError = err
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = SndmartOrange),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Save Timing")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Dialogs ---

    // 1. Add / Edit Category Dialog
    if (showAddCategoryDialog || editingCategory != null) {
        val isEdit = editingCategory != null
        var name by remember { mutableStateOf(editingCategory?.name ?: "") }
        var imageUrl by remember { mutableStateOf(editingCategory?.imageUrl ?: "") }
        var selectedCategoryPhoto by remember { mutableStateOf<SelectedImageFile?>(null) }
        var isUploadingCategoryPhoto by remember { mutableStateOf(false) }
        var uploadError by remember { mutableStateOf<String?>(null) }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        var isSubmitting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                if (!isSubmitting && !isUploadingCategoryPhoto) {
                    showAddCategoryDialog = false
                    editingCategory = null
                }
            },
            title = {
                Text(if (isEdit) "Edit Category" else "Add Menu Category", fontWeight = FontWeight.Bold, color = Slate900)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (errorMsg != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = errorMsg ?: "",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; errorMsg = null },
                        label = { Text("Category Name *") },
                        placeholder = { Text("e.g. Biryani, Starters, Drinks") },
                        singleLine = true,
                        colors = polishTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Image Upload Dropzone (Required for categories)
                    ImageUploadDropzone(
                        label = "Category Image",
                        isRequired = true,
                        selectedImage = selectedCategoryPhoto,
                        existingImageUrl = imageUrl.ifBlank { null },
                        isUploading = isUploadingCategoryPhoto,
                        uploadProgressText = "Uploading category photo...",
                        errorMessage = uploadError,
                        onImageSelected = { file ->
                            selectedCategoryPhoto = file
                            uploadError = null
                            errorMsg = null
                        },
                        onImageRemoved = {
                            selectedCategoryPhoto = null
                            imageUrl = ""
                        },
                        onError = { err ->
                            uploadError = err
                        }
                    )

                    Text(
                        text = "Or choose a preset category image:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate500
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(SAMPLE_FOOD_IMAGES) { (url, label) ->
                            val isSelected = selectedCategoryPhoto == null && imageUrl == url
                            Surface(
                                onClick = {
                                    selectedCategoryPhoto = null
                                    imageUrl = url
                                    uploadError = null
                                    errorMsg = null
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, SndmartOrange) else BorderStroke(1.dp, PolishBorderLight),
                                modifier = Modifier.size(52.dp)
                            ) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = name.trim()
                        if (cleanName.isBlank()) {
                            errorMsg = "Category name is required"
                            return@Button
                        }
                        if (selectedCategoryPhoto == null && imageUrl.isBlank()) {
                            errorMsg = "Category image is required. Please upload or choose a photo."
                            return@Button
                        }

                        // Upload image if a new photo was selected from device
                        if (selectedCategoryPhoto != null) {
                            val photo = selectedCategoryPhoto!!
                            val fileName = "${System.currentTimeMillis()}.${photo.extension}"
                            isSubmitting = true
                            isUploadingCategoryPhoto = true
                            uploadError = null
                            errorMsg = null

                            onUploadCategoryImage(fileName, photo.bytes, photo.mimeType) { success, uploadedUrl, err ->
                                isUploadingCategoryPhoto = false
                                if (success && uploadedUrl != null) {
                                    if (isEdit) {
                                        onUpdateCategory(editingCategory!!.id, cleanName, uploadedUrl) { catSuccess, catErr ->
                                            isSubmitting = false
                                            if (catSuccess) {
                                                editingCategory = null
                                            } else {
                                                errorMsg = catErr ?: "Failed to update category"
                                            }
                                        }
                                    } else {
                                        onAddCategory(cleanName, uploadedUrl) { catSuccess, catErr ->
                                            isSubmitting = false
                                            if (catSuccess) {
                                                showAddCategoryDialog = false
                                            } else {
                                                errorMsg = catErr ?: "Failed to add category"
                                            }
                                        }
                                    }
                                } else {
                                    isSubmitting = false
                                    uploadError = "Upload failed: ${err ?: "Could not upload image"}"
                                }
                            }
                        } else {
                            // Using existing or preset URL
                            isSubmitting = true
                            if (isEdit) {
                                onUpdateCategory(editingCategory!!.id, cleanName, imageUrl) { success, err ->
                                    isSubmitting = false
                                    if (success) {
                                        editingCategory = null
                                    } else {
                                        errorMsg = err ?: "Failed to update category"
                                    }
                                }
                            } else {
                                onAddCategory(cleanName, imageUrl) { success, err ->
                                    isSubmitting = false
                                    if (success) {
                                        showAddCategoryDialog = false
                                    } else {
                                        errorMsg = err ?: "Failed to add category"
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isSubmitting && !isUploadingCategoryPhoto,
                    colors = ButtonDefaults.buttonColors(containerColor = SndmartOrange)
                ) {
                    if (isSubmitting || isUploadingCategoryPhoto) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isUploadingCategoryPhoto) "Uploading Photo..." else "Saving...")
                    } else {
                        Text(if (isEdit) "Save Changes" else "Add Category")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showAddCategoryDialog = false
                        editingCategory = null
                    },
                    enabled = !isSubmitting && !isUploadingCategoryPhoto
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 2. Add / Edit Product Dialog
    if (showAddProductDialog || editingProduct != null) {
        val isEdit = editingProduct != null
        val currentStock = editingProduct?.effectiveStock
        val isCurrentSoldOut = isEdit && (currentStock == null || currentStock <= 0)

        var name by remember { mutableStateOf(editingProduct?.name ?: "") }
        var categoryId by remember { mutableStateOf(editingProduct?.categoryId ?: state.categories.firstOrNull()?.id) }
        var priceStr by remember { mutableStateOf(if (isEdit) editingProduct!!.price.toString() else "") }
        var description by remember { mutableStateOf(editingProduct?.description ?: "") }
        var imageUrl by remember { mutableStateOf(editingProduct?.imageUrl ?: "") }
        var selectedProductPhoto by remember { mutableStateOf<SelectedImageFile?>(null) }
        var isUploadingProductPhoto by remember { mutableStateOf(false) }
        var uploadError by remember { mutableStateOf<String?>(null) }
        var isActive by remember { mutableStateOf(editingProduct?.isActive ?: true) }
        var availableFrom by remember { mutableStateOf(editingProduct?.availableFrom ?: "") }
        var availableUntil by remember { mutableStateOf(editingProduct?.availableUntil ?: "") }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        var isSubmitting by remember { mutableStateOf(false) }

        // Stock quantity state - restaurant default is 100 so items are orderable
        var isUnlimitedStock by remember {
            mutableStateOf(!isEdit || (currentStock != null && currentStock >= 100))
        }
        var stockQtyStr by remember {
            mutableStateOf(
                if (isEdit) {
                    if (currentStock != null && currentStock > 0) currentStock.toString() else "100"
                } else {
                    "100"
                }
            )
        }

        var categoryDropdownExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                if (!isSubmitting && !isUploadingProductPhoto) {
                    showAddProductDialog = false
                    editingProduct = null
                }
            },
            title = {
                Text(if (isEdit) "Edit Menu Item" else "Add New Menu Item", fontWeight = FontWeight.Bold, color = Slate900)
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isCurrentSoldOut) {
                        item {
                            Surface(
                                color = Color(0xFFFFEBEE),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFC62828),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Stock is currently 0 (Sold Out)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFFB71C1C)
                                        )
                                        Text(
                                            text = "Customers cannot order this item right now. Update stock below (suggested: 100) to make it orderable.",
                                            fontSize = 11.sp,
                                            color = Color(0xFF5D4037)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (errorMsg != null) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = errorMsg ?: "",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Category dropdown
                    item {
                        ExposedDropdownMenuBox(
                            expanded = categoryDropdownExpanded,
                            onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded }
                        ) {
                            val selectedCat = state.categories.find { it.id == categoryId }
                            OutlinedTextField(
                                value = selectedCat?.name ?: "Select Category",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Category") },
                                colors = polishTextFieldColors(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = categoryDropdownExpanded,
                                onDismissRequest = { categoryDropdownExpanded = false }
                            ) {
                                state.categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.name, color = Slate900) },
                                        onClick = {
                                            categoryId = cat.id
                                            categoryDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Item Name
                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it; errorMsg = null },
                            label = { Text("Item Name *") },
                            placeholder = { Text("e.g. Chicken Hyderabadi Dum Biryani") },
                            singleLine = true,
                            colors = polishTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Price
                    item {
                        OutlinedTextField(
                            value = priceStr,
                            onValueChange = { priceStr = it; errorMsg = null },
                            label = { Text("Price (₹) *") },
                            placeholder = { Text("249.00") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = polishTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // Description
                    item {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description (Optional)") },
                            placeholder = { Text("Ingredients, spice level, portion size...") },
                            colors = polishTextFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            maxLines = 3
                        )
                    }

                    // Photo Upload Dropzone
                    item {
                        ImageUploadDropzone(
                            label = "Item Photo",
                            isRequired = false,
                            selectedImage = selectedProductPhoto,
                            existingImageUrl = imageUrl.ifBlank { null },
                            isUploading = isUploadingProductPhoto,
                            uploadProgressText = "Uploading product photo...",
                            errorMessage = uploadError,
                            onImageSelected = { file ->
                                selectedProductPhoto = file
                                uploadError = null
                                errorMsg = null
                            },
                            onImageRemoved = {
                                selectedProductPhoto = null
                                imageUrl = ""
                            },
                            onError = { err ->
                                uploadError = err
                            }
                        )
                    }

                    item {
                        Text(
                            text = "Or choose a quick preset photo:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate500
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(SAMPLE_FOOD_IMAGES) { (url, label) ->
                                val isSelected = selectedProductPhoto == null && imageUrl == url
                                Surface(
                                    onClick = {
                                        selectedProductPhoto = null
                                        imageUrl = url
                                        uploadError = null
                                        errorMsg = null
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, SndmartOrange) else BorderStroke(1.dp, PolishBorderLight),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = label,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }

                    // Stock & Inventory (Required) Card
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = BorderStroke(1.dp, PolishBorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Inventory2,
                                            contentDescription = null,
                                            tint = SndmartOrange,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Always In Stock (Made to order)",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Slate900
                                            )
                                            Text(
                                                text = if (isUnlimitedStock) "Item stays orderable whenever kitchen is open (100 buffer)" else "Tracking exact portion count",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Slate500,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = isUnlimitedStock,
                                        onCheckedChange = { checked ->
                                            isUnlimitedStock = checked
                                            if (checked && stockQtyStr.toIntOrNull()?.let { it <= 0 } != false) {
                                                stockQtyStr = "100"
                                            }
                                        }
                                    )
                                }

                                Divider(color = PolishBorderLight)

                                OutlinedTextField(
                                    value = stockQtyStr,
                                    onValueChange = {
                                        stockQtyStr = it.filter { char -> char.isDigit() }
                                        errorMsg = null
                                    },
                                    label = { Text("Stock Quantity (Portions Available) *") },
                                    placeholder = { Text("e.g. 100") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = polishTextFieldColors(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    supportingText = {
                                        if (isUnlimitedStock) {
                                            Text(
                                                "✓ Maintained at high buffer ($stockQtyStr) so customers can order whenever store is open.",
                                                color = SndmartGreen,
                                                fontSize = 11.sp
                                            )
                                        } else {
                                            Text(
                                                "Item automatically shows as Sold Out when stock reaches 0.",
                                                color = Slate500,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                )

                                Text(
                                    text = "Quick Stock Fill:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Slate500
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val stockPresets = listOf(10, 25, 50, 100, 250, 500)
                                    items(stockPresets) { preset ->
                                        val isSelected = stockQtyStr == preset.toString()
                                        SuggestionChip(
                                            onClick = {
                                                stockQtyStr = preset.toString()
                                                if (preset >= 100) isUnlimitedStock = true
                                                errorMsg = null
                                            },
                                            label = { Text("$preset units", fontSize = 11.sp) },
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = if (isSelected) SndmartOrange.copy(alpha = 0.15f) else Color.Transparent,
                                                labelColor = if (isSelected) SndmartOrange else Slate700
                                            ),
                                            border = BorderStroke(1.dp, if (isSelected) SndmartOrange else PolishBorderLight)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Active Switch
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Item Available on Store", fontWeight = FontWeight.Medium, color = Slate900)
                            Switch(checked = isActive, onCheckedChange = { isActive = it })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = name.trim()
                        val price = priceStr.toDoubleOrNull()
                        if (cleanName.isBlank()) {
                            errorMsg = "Item name is required"
                            return@Button
                        }
                        if (price == null || price <= 0) {
                            errorMsg = "Please enter a valid price greater than 0"
                            return@Button
                        }

                        val finalStock = stockQtyStr.toIntOrNull()
                        if (finalStock == null || finalStock < 0) {
                            errorMsg = "Please enter a valid stock quantity (0 or greater)"
                            return@Button
                        }

                        // Upload image if a new photo was selected
                        if (selectedProductPhoto != null) {
                            val photo = selectedProductPhoto!!
                            val fileName = "${System.currentTimeMillis()}.${photo.extension}"
                            isSubmitting = true
                            isUploadingProductPhoto = true
                            uploadError = null
                            errorMsg = null

                            onUploadProductImage(fileName, photo.bytes, photo.mimeType) { success, uploadedUrl, err ->
                                isUploadingProductPhoto = false
                                if (success && uploadedUrl != null) {
                                    if (isEdit) {
                                        val updates = mutableMapOf<String, Any?>(
                                            "name" to cleanName,
                                            "price" to price,
                                            "is_active" to isActive,
                                            "stock_qty" to finalStock,
                                            "is_available" to (isActive && finalStock > 0),
                                            "image_url" to uploadedUrl
                                        )
                                        if (categoryId != null) updates["category_id"] = categoryId
                                        updates["description"] = description.ifBlank { null }

                                        onUpdateProduct(editingProduct!!.id, updates) { pSuccess, pErr ->
                                            isSubmitting = false
                                            if (pSuccess) editingProduct = null
                                            else errorMsg = pErr
                                        }
                                    } else {
                                        onAddProduct(
                                            categoryId,
                                            cleanName,
                                            description.ifBlank { null },
                                            price,
                                            uploadedUrl,
                                            isActive,
                                            availableFrom.ifBlank { null },
                                            availableUntil.ifBlank { null },
                                            finalStock,
                                            isActive && finalStock > 0
                                        ) { pSuccess, pErr ->
                                            isSubmitting = false
                                            if (pSuccess) showAddProductDialog = false
                                            else errorMsg = pErr
                                        }
                                    }
                                } else {
                                    isSubmitting = false
                                    uploadError = "Upload failed: ${err ?: "Could not upload image"}"
                                }
                            }
                        } else {
                            // Using existing or preset URL
                            isSubmitting = true
                            if (isEdit) {
                                val updates = mutableMapOf<String, Any?>(
                                    "name" to cleanName,
                                    "price" to price,
                                    "is_active" to isActive,
                                    "stock_qty" to finalStock,
                                    "is_available" to (isActive && finalStock > 0)
                                )
                                if (categoryId != null) updates["category_id"] = categoryId
                                updates["description"] = description.ifBlank { null }
                                updates["image_url"] = imageUrl.ifBlank { null }

                                onUpdateProduct(editingProduct!!.id, updates) { success, err ->
                                    isSubmitting = false
                                    if (success) editingProduct = null
                                    else errorMsg = err
                                }
                            } else {
                                onAddProduct(
                                    categoryId,
                                    cleanName,
                                    description.ifBlank { null },
                                    price,
                                    imageUrl.ifBlank { null },
                                    isActive,
                                    availableFrom.ifBlank { null },
                                    availableUntil.ifBlank { null },
                                    finalStock,
                                    isActive && finalStock > 0
                                ) { success, err ->
                                    isSubmitting = false
                                    if (success) showAddProductDialog = false
                                    else errorMsg = err
                                }
                            }
                        }
                    },
                    enabled = !isSubmitting && !isUploadingProductPhoto,
                    colors = ButtonDefaults.buttonColors(containerColor = SndmartOrange)
                ) {
                    if (isSubmitting || isUploadingProductPhoto) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isUploadingProductPhoto) "Uploading Photo..." else "Saving...")
                    } else {
                        Text(if (isEdit) "Save Item" else "Add Item")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showAddProductDialog = false
                        editingProduct = null
                    },
                    enabled = !isSubmitting && !isUploadingProductPhoto
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 3. Delete Confirmation Dialogs
    if (categoryToDelete != null) {
        ConfirmationDialog(
            title = "Delete Category",
            message = "Are you sure you want to delete \"${categoryToDelete?.name}\"? Items in this category will become uncategorized.",
            confirmText = "Delete Category",
            isDestructive = true,
            onConfirm = {
                val cat = categoryToDelete ?: return@ConfirmationDialog
                onDeleteCategory(cat.id) { _, _ -> categoryToDelete = null }
            },
            onDismiss = { categoryToDelete = null }
        )
    }

    if (productToDelete != null) {
        ConfirmationDialog(
            title = "Delete Menu Item",
            message = "Are you sure you want to remove \"${productToDelete?.name}\" from your menu?",
            confirmText = "Delete Item",
            isDestructive = true,
            onConfirm = {
                val p = productToDelete ?: return@ConfirmationDialog
                onDeleteProduct(p.id) { _, _ -> productToDelete = null }
            },
            onDismiss = { productToDelete = null }
        )
    }
}
