package com.example.ui.menu

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.model.Category
import com.example.data.model.ProductCustomization
import com.example.ui.VendorLabels
import com.example.ui.theme.*
import com.example.util.ImageShrink
import com.example.util.globalTapFeedback
import kotlinx.coroutines.launch
import java.io.File

/**
 * Quick add for shops: take a photo with the camera (or pick one), type the name and the price, save. "Save & add next"
 * keeps the category and clears the rest, so a shelf of products is added in a few taps each.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickAddProductSheet(
    categories: List<Category>,
    labels: VendorLabels,
    onUploadImage: (fileName: String, bytes: ByteArray, mime: String, onComplete: (Boolean, String?, String?) -> Unit) -> Unit,
    onAddProduct: (categoryId: String?, name: String, description: String?, price: Double, imageUrl: String?, isActive: Boolean, availableFrom: String?, availableUntil: String?, stockQty: Int, isAvailable: Boolean, unit: String?, custom: ProductCustomization?, onComplete: (Boolean, String?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var priceStr by remember { mutableStateOf("") }
    var unitStr by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf(categories.firstOrNull()?.id) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var addedCount by remember { mutableIntStateOf(0) }
    var pendingCameraFile by remember { mutableStateOf<Uri?>(null) }

    fun usePhoto(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            busy = true; error = null
            val bytes = ImageShrink.shrink(context, uri)
            if (bytes == null) error = "Could not read this photo. Please try again."
            else { photoUri = uri; photoBytes = bytes }
            busy = false
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) usePhoto(pendingCameraFile) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> usePhoto(uri) }

    fun launchCamera() {
        try {
            val dir = File(context.cacheDir, "camera").also { it.mkdirs() }
            dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 3600_000L) it.delete() }
            val f = File(dir, "quick_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            pendingCameraFile = uri
            camera.launch(uri)
        } catch (e: Exception) {
            error = "Could not open the camera. Pick a photo from the gallery instead."
        }
    }

    fun save(addNext: Boolean) {
        val n = name.trim()
        val price = priceStr.toDoubleOrNull()
        if (n.isEmpty()) { error = "Please enter the product name"; return }
        if (price == null || price <= 0) { error = "Please enter a price above 0"; return }
        busy = true; error = null
        val custom = ProductCustomization(enabled = labels.allowsCustomisation)
        val create: (String?) -> Unit = { imageUrl ->
            onAddProduct(categoryId, n, null, price, imageUrl, true, null, null, 100, true, unitStr.trim().ifBlank { null }, custom) { ok, err ->
                busy = false
                if (ok) {
                    addedCount += 1
                    if (addNext) { name = ""; priceStr = ""; unitStr = ""; photoUri = null; photoBytes = null }
                    else onDismiss()
                } else error = err ?: "Could not add the product"
            }
        }
        val bytes = photoBytes
        if (bytes == null) create(null)
        else onUploadImage("${System.currentTimeMillis()}.jpg", bytes, "image/jpeg") { ok, url, err ->
            if (ok && url != null) create(url) else { busy = false; error = "Photo upload failed: ${err ?: "please try again"}" }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth().globalTapFeedback().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Quick add product", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text("Photo, name and price. That is all.", fontSize = 12.sp, color = Slate500)
                }
                if (addedCount > 0) Surface(shape = RoundedCornerShape(20.dp), color = SpruceContainerLight) {
                    Text("$addedCount added ✓", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SprucePrimary)
                }
            }

            // photo
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = PolishBackgroundLight, border = BorderStroke(1.dp, PolishBorderLight), modifier = Modifier.size(104.dp)) {
                    if (photoUri != null) AsyncImage(model = photoUri, contentDescription = "Product photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)))
                    else Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Slate400, modifier = Modifier.size(34.dp)) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { launchCamera() }, enabled = !busy, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary)) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (photoUri == null) "Take photo" else "Retake", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("From gallery")
                    }
                    if (photoBytes != null) Text("Photo ready (${photoBytes!!.size / 1024} KB)", fontSize = 11.sp, color = Slate500)
                }
            }

            OutlinedTextField(
                value = name, onValueChange = { name = it; error = null }, label = { Text(if (labels.isHotel) "Item name *" else "Product name *") },
                singleLine = true, colors = polishTextFieldColors(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = priceStr, onValueChange = { priceStr = it.filter { c -> c.isDigit() || c == '.' }; error = null }, label = { Text("Price ₹ *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, colors = polishTextFieldColors(),
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = unitStr, onValueChange = { unitStr = it; error = null }, label = { Text(if (labels.unitFirst) "Unit (1 kg)" else "Unit (optional)") },
                    singleLine = true, colors = polishTextFieldColors(), modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                )
            }
            if (categories.isNotEmpty()) {
                Text("Category", fontSize = 12.sp, color = Slate500)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { c -> FilterChip(selected = categoryId == c.id, onClick = { categoryId = c.id }, label = { Text(c.name) }) }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            Button(onClick = { save(true) }, enabled = !busy, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = SndmartOrange)) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                else Text("Save & add next", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = { save(false) }, enabled = !busy, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(46.dp)) { Text("Save & close", fontWeight = FontWeight.SemiBold) }
        }
    }
}
