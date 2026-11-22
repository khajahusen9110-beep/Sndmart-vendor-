package com.example.ui.menu

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Category
import com.example.ui.VendorLabels
import com.example.ui.theme.*
import com.example.util.BulkImportResult
import com.example.util.ParsedSheet
import com.example.util.ProductSheetParser
import com.example.util.ProductSheetRow
import com.example.util.globalTapFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Import many products at once from a CSV or Excel sheet: get the sample, fill it in Excel / Google Sheets, pick the file,
 * check the preview, import. Missing categories are created, products that already exist can be skipped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkImportDialog(
    existingCategories: List<Category>,
    labels: VendorLabels,
    onImport: (rows: List<ProductSheetRow>, skipExisting: Boolean, onProgress: (done: Int, total: Int) -> Unit, onDone: (BulkImportResult) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var parsed by remember { mutableStateOf<ParsedSheet?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var readError by remember { mutableStateOf<String?>(null) }
    var skipExisting by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var result by remember { mutableStateOf<BulkImportResult?>(null) }
    val importing = progress != null && result == null

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        readError = null
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                try {
                    val declared = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else 0L } ?: 0L
                    if (declared > 5 * 1024 * 1024) null else context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } catch (_: Exception) { null }
            }
            if (bytes == null) { readError = "Could not read this file (it must be under 5 MB)."; return@launch }
            fileName = uri.lastPathSegment?.substringAfterLast('/')
            parsed = withContext(Dispatchers.Default) { try { ProductSheetParser.parseFile(bytes) } catch (_: Exception) { ParsedSheet(emptyList(), emptyList(), "This file could not be read. Please use the sample file or save your sheet as CSV.") } }
        }
    }
    val saveSample = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try { context.contentResolver.openOutputStream(uri)?.use { it.write(ProductSheetParser.SAMPLE_CSV.toByteArray(Charsets.UTF_8)) } } catch (_: Exception) {}
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!importing) onDismiss() }) {
        Column(
            modifier = Modifier.fillMaxWidth().globalTapFeedback().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Import products from a sheet", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)

            val res = result
            val p = parsed
            when {
                res != null -> {
                    Surface(shape = RoundedCornerShape(14.dp), color = SpruceContainerLight, border = BorderStroke(1.dp, PolishBorderLight), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("✓ ${res.created} ${if (labels.isHotel) "items" else "products"} added", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SprucePrimary)
                            if (res.skipped > 0) Text("${res.skipped} skipped (already in your catalogue)", fontSize = 13.sp, color = Slate700)
                            if (res.failed > 0) Text("${res.failed} could not be added", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                            res.messages.take(8).forEach { Text("• $it", fontSize = 12.sp, color = Slate600) }
                        }
                    }
                    Text("Add the photos next: open a product and tap its photo, or use Quick add for new ones.", fontSize = 12.sp, color = Slate500)
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary)) { Text("Done", fontWeight = FontWeight.Bold) }
                }
                importing -> {
                    val (done, total) = progress!!
                    Text("Importing $done of $total ...", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                    Text("Please keep the app open until this finishes.", fontSize = 12.sp, color = Slate500)
                }
                p == null -> {
                    Text("1. Get the sample sheet and fill your products in Excel or Google Sheets.\n2. Save it as CSV (or keep it as Excel).\n3. Choose the file here. You will see a preview before anything is added.", fontSize = 13.sp, color = Slate700)
                    Text("Columns: name, price (these two are needed), mrp, unit, category, description, stock, min_order_qty.", fontSize = 12.sp, color = Slate500)
                    readError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    OutlinedButton(onClick = { saveSample.launch("sndmart_products_sample.csv") }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Get sample sheet")
                    }
                    Button(
                        onClick = { pick.launch(arrayOf("text/csv", "text/comma-separated-values", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel", "text/plain", "application/octet-stream")) },
                        modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = SndmartOrange)
                    ) { Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Choose CSV / Excel file", fontWeight = FontWeight.Bold) }
                }
                else -> {
                    fileName?.let { Text(it, fontSize = 12.sp, color = Slate500) }
                    p.headerError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                    if (p.rows.isNotEmpty()) {
                        val newCats = p.rows.mapNotNull { it.category }.distinctBy { it.lowercase() }.filter { c -> existingCategories.none { it.name.equals(c, ignoreCase = true) } }
                        Surface(shape = RoundedCornerShape(14.dp), color = SpruceContainerLight, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("${p.rows.size} ${if (labels.isHotel) "items" else "products"} ready to import", fontWeight = FontWeight.Bold, color = SprucePrimary)
                                if (newCats.isNotEmpty()) Text("New categories will be created: ${newCats.take(6).joinToString(", ")}${if (newCats.size > 6) " ..." else ""}", fontSize = 12.sp, color = Slate700)
                            }
                        }
                        Text("Preview", fontSize = 12.sp, color = Slate500)
                        p.rows.take(5).forEach { r ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(listOfNotNull(r.name, r.unit).joinToString(" · "), fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                Text("₹${if (r.price % 1.0 == 0.0) "%.0f".format(r.price) else "%.2f".format(r.price)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (p.rows.size > 5) Text("... and ${p.rows.size - 5} more", fontSize = 12.sp, color = Slate500)
                    }
                    if (p.problems.isNotEmpty()) {
                        Surface(shape = RoundedCornerShape(12.dp), color = androidx.compose.ui.graphics.Color(0xFFFFF3E0), border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFFFB74D)), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${p.problems.size} row${if (p.problems.size > 1) "s" else ""} will be left out", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = androidx.compose.ui.graphics.Color(0xFFE65100))
                                p.problems.take(8).forEach { Text("Row ${it.rowNumber}: ${it.message}", fontSize = 12.sp, color = Slate700) }
                                if (p.problems.size > 8) Text("... and ${p.problems.size - 8} more", fontSize = 12.sp, color = Slate500)
                            }
                        }
                    }
                    if (p.rows.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = skipExisting, onCheckedChange = { skipExisting = it })
                            Text("Skip products that are already in my catalogue (same name and unit)", fontSize = 13.sp)
                        }
                        Button(
                            onClick = {
                                progress = 0 to p.rows.size
                                onImport(p.rows, skipExisting, { d, t -> progress = d to t }, { result = it })
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = SndmartOrange)
                        ) { Text("Import ${p.rows.size} ${if (labels.isHotel) "items" else "products"}", fontWeight = FontWeight.Bold) }
                    }
                    OutlinedButton(onClick = { parsed = null; fileName = null }, modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(14.dp)) { Text("Choose another file") }
                }
            }
        }
    }
}
