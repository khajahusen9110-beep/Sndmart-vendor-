package com.example.ui.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.VendorQuote
import kotlinx.coroutines.delay

private val Blue = Color(0xFF2563EB)
private val Muted = Color(0xFF64748B)

/** Price requests from customers for custom work: send a price per piece, or decline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotesScreen(
    quotes: List<VendorQuote>,
    error: String?,
    onRefresh: () -> Unit,
    onSend: (String, Double, String?, (String?) -> Unit) -> Unit,
    onDecline: (String, String?, (String?) -> Unit) -> Unit,
    loadPhotoUrl: suspend (String) -> String?,
    onBack: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var answering by remember { mutableStateOf<VendorQuote?>(null) }
    LaunchedEffect(Unit) { while (true) { onRefresh(); delay(30_000) } }
    val waiting = quotes.filter { it.status == "requested" }
    val sent = quotes.filter { it.status in setOf("quoted", "accepted") }
    val done = quotes.filter { it.status in setOf("declined", "cancelled", "expired", "used") }
    val shown = when (tab) { 0 -> waiting; 1 -> sent; else -> done }

    Scaffold(topBar = { TopAppBar(title = { Text("Price requests", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                listOf("Waiting (${waiting.size})", "Sent (${sent.size})", "Past").forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontWeight = FontWeight.Bold) }) }
            }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                error?.let { item { Text("$it (tap to retry)", color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable(onClick = onRefresh)) } }
                if (shown.isEmpty()) item { Text(if (tab == 0) "No requests waiting. Customers ask for a price on products marked “Price by quote”." else "Nothing here.", color = Muted) }
                items(shown, key = { it.id }) { q ->
                    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE2E8F0)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${q.productName}${q.variantLabel?.let { " - $it" } ?: ""} × ${q.quantity}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Surface(shape = RoundedCornerShape(8.dp), color = if (q.status == "requested") Color(0xFFFEF3C7) else Color(0xFFDBEAFE)) {
                                    Text(q.statusLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (q.status == "requested") Color(0xFF92400E) else Color(0xFF1D4ED8), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                            }
                            Text(q.customerName, fontSize = 12.sp, color = Muted)
                            q.note?.let { Text("✏️ $it", fontSize = 14.sp) }
                            q.photoPath?.let { p ->
                                val url by produceState<String?>(null, p) { value = loadPhotoUrl(p) }
                                if (url != null) AsyncImage(model = url, contentDescription = "Customer's photo", modifier = Modifier.size(140.dp).padding(top = 2.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                else Text("📷 Loading photo…", fontSize = 12.sp, color = Muted)
                            }
                            q.quotedUnitPrice?.let { Text("Your price: ₹${"%.0f".format(it)} per piece · total ₹${"%.0f".format(it * q.quantity)}", fontWeight = FontWeight.Bold, color = Blue) }
                            if (q.status == "accepted") Text("The customer accepted. They will place the order.", fontSize = 12.sp, color = Color(0xFF166534), fontWeight = FontWeight.SemiBold)
                            if (q.basePrice != null && q.status == "requested") Text("Listed price: ₹${"%.0f".format(q.basePrice)} (a guide)", fontSize = 12.sp, color = Muted)
                            if (q.status == "requested" || q.status == "quoted") Button(onClick = { answering = q }, colors = ButtonDefaults.buttonColors(containerColor = Blue), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                Text(if (q.status == "requested") "Send price" else "Change price", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    answering?.let { q ->
        var price by remember(q.id) { mutableStateOf(q.quotedUnitPrice?.let { "%.0f".format(it) } ?: "") }
        var note by remember(q.id) { mutableStateOf(q.vendorNote ?: "") }
        var busy by remember { mutableStateOf(false) }
        var err by remember { mutableStateOf<String?>(null) }
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { if (!busy) answering = null },
            title = { Text("${q.productName} × ${q.quantity}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    q.note?.let { Text("“$it”", fontSize = 13.sp, color = Muted) }
                    OutlinedTextField(value = price, onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text("Price for ONE piece (₹)") }, singleLine = true)
                    price.toDoubleOrNull()?.let { Text("Total for ${q.quantity}: ₹${"%.0f".format(it * q.quantity)}", fontWeight = FontWeight.Bold, color = Blue) }
                    OutlinedTextField(value = note, onValueChange = { if (it.length <= 300) note = it }, label = { Text("Note to the customer (optional)") }, minLines = 2)
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                Button(enabled = !busy, onClick = {
                    val p = price.toDoubleOrNull()
                    if (p == null || p < 1) { err = "Enter a price of ₹1 or more"; return@Button }
                    busy = true; err = null
                    onSend(q.id, p, note.trim().ifBlank { null }) { e -> busy = false; if (e == null) { answering = null; android.widget.Toast.makeText(context, "Price sent to the customer", android.widget.Toast.LENGTH_SHORT).show() } else err = e }
                }) { Text(if (busy) "Sending…" else "Send price") }
            },
            dismissButton = {
                Row {
                    if (q.status == "requested") TextButton(enabled = !busy, onClick = {
                        busy = true
                        onDecline(q.id, note.trim().ifBlank { null }) { e -> busy = false; if (e == null) answering = null else err = e }
                    }) { Text("Decline", color = MaterialTheme.colorScheme.error) }
                    TextButton(enabled = !busy, onClick = { answering = null }) { Text("Close") }
                }
            }
        )
    }
}
