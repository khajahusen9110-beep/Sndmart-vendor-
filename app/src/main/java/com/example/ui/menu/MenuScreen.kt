package com.example.ui.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.example.ui.components.ConfirmationDialog
import com.example.ui.theme.SndmartGreen
import com.example.ui.theme.SndmartOrange
import com.example.ui.theme.polishTextFieldColors
import com.example.ui.theme.*
import com.example.ui.viewmodel.MenuUiState

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
@Composable
fun MenuScreen(
    state: MenuUiState,
    onAddCategory: (name: String, imageUrl: String, onComplete: (Boolean, String?) -> Unit) -> Unit,
    onUpdateCategory: (id: String, name: String, imageUrl: String, onComplete: (Boolean, String?) -> Unit) -> Unit,
    onDeleteCategory: (id: String, onComplete: (Boolean, String?) -> Unit) -> Unit,
    onAddProduct: (categoryId: String?, name: String, description: String?, price: Double, imageUrl: String?, isActive: Boolean, availableFrom: String?, availableUntil: String?, onComplete: (Boolean, String?) -> Unit) -> Unit,
    onUpdateProduct: (productId: String, updates: Map<String, Any?>, onComplete: (Boolean, String?) -> Unit) -> Unit,
    onToggleProductActive: (Product) -> Unit,
    onDeleteProduct: (productId: String, onComplete: (Boolean, String?) -> Unit) -> Unit,
    onUploadCategoryImage: (fileName: String, fileBytes: ByteArray, mimeType: String, onComplete: (Boolean, String?, String?) -> Unit) -> Unit = { _, _, _, cb -> cb(false, null, "Not configured") },
    onUploadProductImage: (fileName: String, fileBytes: ByteArray, mimeType: String, onComplete: (Boolean, String?, String?) -> Unit) -> Unit = { _, _, _, cb -> cb(false, null, "Not configured") },
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSubTab by remember { mutableStateOf(MenuSectionTab.ITEMS) }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }

    // Dialog states
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }

    var showAddProductDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var productToDelete by remember { mutableStateOf<Product?>(null) }

    val filteredProducts = remember(state.products, selectedCategoryFilter) {
        if (selectedCategoryFilter == null) state.products
        else state.products.filter { it.categoryId == selectedCategoryFilter }
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

        when (activeSubTab) {
            MenuSectionTab.ITEMS -> {
                LazyColumn(
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
                                    text = "${state.products.size} Items on Menu",
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

                    // Category Filter Chips
                    if (state.categories.isNotEmpty()) {
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedCategoryFilter == null,
                                        onClick = { selectedCategoryFilter = null },
                                        label = { Text("All (${state.products.size})") }
                                    )
                                }
                                items(state.categories, key = { it.id }) { cat ->
                                    val count = state.products.count { it.categoryId == cat.id }
                                    FilterChip(
                                        selected = selectedCategoryFilter == cat.id,
                                        onClick = { selectedCategoryFilter = if (selectedCategoryFilter == cat.id) null else cat.id },
                                        label = { Text("${cat.name} ($count)") }
                                    )
                                }
                            }
                        }
                    }

                    // Products list
                    if (filteredProducts.isEmpty()) {
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
                                        Text(
                                            text = "₹${"%.2f".format(product.price)}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = SndmartOrange
                                        )
                                    }

                                    // Action buttons & Active Toggle
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
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

                    if (state.categories.isEmpty()) {
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
                                    if (category.imageUrl.isNotBlank()) {
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
                                                ) { _, _ -> isEditingTiming = false }
                                            }
                                        ) {
                                            Text("Clear Timing", color = MaterialTheme.colorScheme.error)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                onUpdateProduct(
                                                    product.id,
                                                    mapOf("available_from" to fromTime.trim(), "available_until" to untilTime.trim())
                                                ) { _, _ -> isEditingTiming = false }
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
                                    fontWeight = FontWeight.Medium
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
                                                errorMsg = catErr
                                            }
                                        }
                                    } else {
                                        onAddCategory(cleanName, uploadedUrl) { catSuccess, catErr ->
                                            isSubmitting = false
                                            if (catSuccess) {
                                                showAddCategoryDialog = false
                                            } else {
                                                errorMsg = catErr
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
                                        errorMsg = err
                                    }
                                }
                            } else {
                                onAddCategory(cleanName, imageUrl) { success, err ->
                                    isSubmitting = false
                                    if (success) {
                                        showAddCategoryDialog = false
                                    } else {
                                        errorMsg = err
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
                                            availableUntil.ifBlank { null }
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
                                    "is_active" to isActive
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
                                    availableUntil.ifBlank { null }
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
