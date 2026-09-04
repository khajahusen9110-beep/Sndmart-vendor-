package com.example.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.OperatingHour
import com.example.data.model.Vendor
import com.example.ui.components.ConfirmationDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProfileUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreProfileScreen(
    state: ProfileUiState,
    onSaveProfile: (name: String, ownerName: String?, phone: String?, address: String?, lat: Double?, lng: Double?, openTime: String?, closeTime: String?, bannerUrl: String?, onComplete: (Boolean, String?) -> Unit) -> Unit,
    onAddOperatingHour: (startTime: String, endTime: String, isActive: Boolean, isClosed: Boolean, dayOfWeek: String?, onComplete: (Boolean, String?) -> Unit) -> Unit,
    onUpdateOperatingHour: (id: String, startTime: String, endTime: String, isActive: Boolean, isClosed: Boolean, dayOfWeek: String?, onComplete: (Boolean, String?) -> Unit) -> Unit,
    onToggleOperatingHourActive: (OperatingHour) -> Unit,
    onDeleteOperatingHour: (id: String, onComplete: ((Boolean, String?) -> Unit)?) -> Unit,
    onToggleSoundAlert: (Boolean) -> Unit,
    onTestSoundAlert: () -> Unit,
    onSimulateFullScreenAlert: () -> Unit = {},
    onRegisterCustomFcmToken: (String, (Boolean, String?) -> Unit) -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vendor = state.vendor ?: Vendor()

    var name by remember(vendor) { mutableStateOf(vendor.name) }
    var ownerName by remember(vendor) { mutableStateOf(vendor.ownerName ?: "") }
    var phone by remember(vendor) { mutableStateOf(vendor.phone ?: "") }
    var address by remember(vendor) { mutableStateOf(vendor.address ?: "") }
    var latitudeStr by remember(vendor) { mutableStateOf(vendor.latitude?.toString() ?: "") }
    var longitudeStr by remember(vendor) { mutableStateOf(vendor.longitude?.toString() ?: "") }
    var openingTime by remember(vendor) { mutableStateOf(vendor.openingTime ?: "09:00") }
    var closingTime by remember(vendor) { mutableStateOf(vendor.closingTime ?: "23:00") }
    var bannerUrl by remember(vendor) { mutableStateOf(vendor.bannerUrl ?: "") }

    var showLogoutConfirmation by remember { mutableStateOf(false) }
    var showAddSlotDialog by remember { mutableStateOf(false) }
    var slotToEdit by remember { mutableStateOf<OperatingHour?>(null) }
    var slotToDelete by remember { mutableStateOf<OperatingHour?>(null) }
    var customFcmInput by remember { mutableStateOf("") }
    var isEditingFcm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Store Profile & Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showLogoutConfirmation = true }) {
                        Icon(imageVector = Icons.Default.Logout, contentDescription = "Sign Out", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(PolishBackgroundLight)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Banner Preview / Selector
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PolishSurfaceLight,
                    border = BorderStroke(1.dp, PolishBorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        if (bannerUrl.isNotBlank()) {
                            AsyncImage(
                                model = bannerUrl,
                                contentDescription = "Store Banner",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .background(SndmartOrange.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Storefront,
                                        contentDescription = null,
                                        tint = SndmartOrange,
                                        modifier = Modifier.size(42.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Storefront Cover Banner",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Slate500
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Store Banner Image URL",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate900
                            )
                            OutlinedTextField(
                                value = bannerUrl,
                                onValueChange = { bannerUrl = it },
                                placeholder = { Text("https://images.unsplash.com/photo-...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // 2. Basic Store & Owner Information Card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PolishSurfaceLight,
                    border = BorderStroke(1.dp, PolishBorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Store & Owner Information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Store / Hotel Name *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = ownerName,
                            onValueChange = { ownerName = it },
                            label = { Text("Owner Full Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone / WhatsApp Number") },
                            placeholder = { Text("+91 9876543210") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // 3. Location & Address Card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PolishSurfaceLight,
                    border = BorderStroke(1.dp, PolishBorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Store Address & GPS Location",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )

                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Full Address") },
                            placeholder = { Text("Shop/Plot No, Area, Landmark, City") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            maxLines = 3
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = latitudeStr,
                                onValueChange = { latitudeStr = it },
                                label = { Text("Latitude") },
                                placeholder = { Text("17.3850") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = longitudeStr,
                                onValueChange = { longitudeStr = it },
                                label = { Text("Longitude") },
                                placeholder = { Text("78.4867") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // 4. Multiple Operating Hours Slots (Feature 1)
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PolishSurfaceLight,
                    border = BorderStroke(1.dp, PolishBorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Operating Hours Slots",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                                Text(
                                    text = "Define meal slots (e.g. Breakfast, Lunch, Dinner)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate500
                                )
                            }

                            Button(
                                onClick = { showAddSlotDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Slot", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // List of slots
                        if (state.operatingHours.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = PolishBackgroundLight,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = Slate500,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "No custom meal slots defined. Store will operate on the standard timings below.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Slate500
                                    )
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                state.operatingHours.forEach { slot ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (slot.isActive && !slot.isClosed) SpruceContainerLight.copy(alpha = 0.5f) else PolishBackgroundLight,
                                        border = BorderStroke(1.dp, if (slot.isActive && !slot.isClosed) SprucePrimary.copy(alpha = 0.3f) else PolishBorderLight),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(if (slot.isActive && !slot.isClosed) SprucePrimary.copy(alpha = 0.15f) else Slate200),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Schedule,
                                                        contentDescription = null,
                                                        tint = if (slot.isActive && !slot.isClosed) SprucePrimary else Slate400,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }

                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Text(
                                                            text = "${slot.startTime} — ${slot.endTime}",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp,
                                                            color = if (slot.isActive && !slot.isClosed) Slate900 else Slate400
                                                        )
                                                        if (slot.isClosed) {
                                                            Text(
                                                                text = "Closed",
                                                                color = PolishRose,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = slot.dayOfWeek ?: "Daily (All Days)",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Slate500
                                                    )
                                                }
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Switch(
                                                    checked = slot.isActive && !slot.isClosed,
                                                    onCheckedChange = { onToggleOperatingHourActive(slot) },
                                                    modifier = Modifier.height(24.dp)
                                                )

                                                IconButton(
                                                    onClick = { slotToEdit = slot },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Edit Slot",
                                                        tint = Slate500,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { slotToDelete = slot },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete Slot",
                                                        tint = PolishRose,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = PolishBorderLight)

                        // Standard Store Hours (Additive / Fallback)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Standard Store Timings (Additive / Fallback)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Slate900
                            )
                            Text(
                                text = "Used when no custom operating hour slots are active",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate500
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = openingTime,
                                    onValueChange = { openingTime = it },
                                    label = { Text("Opening (e.g. 09:00)") },
                                    placeholder = { Text("09:00") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                OutlinedTextField(
                                    value = closingTime,
                                    onValueChange = { closingTime = it },
                                    label = { Text("Closing (e.g. 23:00)") },
                                    placeholder = { Text("23:00") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 5. Order Sound Alerts & Push Notifications (Feature 2)
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PolishSurfaceLight,
                    border = BorderStroke(1.dp, PolishBorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Order Sound Alert & Notifications",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )

                        // Sound Alert Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (state.isSoundAlertEnabled) SndmartOrange.copy(alpha = 0.15f) else Slate200),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (state.isSoundAlertEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                        contentDescription = null,
                                        tint = if (state.isSoundAlertEnabled) SndmartOrange else Slate400,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "New Order Sound Chime",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Slate900
                                    )
                                    Text(
                                        text = if (state.isSoundAlertEnabled) "Plays tone and vibrates on pending orders" else "Sound alerts are muted",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Slate500
                                    )
                                }
                            }

                            Switch(
                                checked = state.isSoundAlertEnabled,
                                onCheckedChange = { onToggleSoundAlert(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = SprucePrimary
                                )
                            )
                        }

                        // Test Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onTestSoundAlert,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Icon(imageVector = Icons.Outlined.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Test Chime", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = onSimulateFullScreenAlert,
                                modifier = Modifier.weight(1.3f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Test Swiggy Alert", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        HorizontalDivider(color = PolishBorderLight)

                        // Push FCM Token Status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Push Notification Device Sync",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Slate900
                            )
                            Surface(
                                color = if (state.isFcmRegistered) PolishEmeraldLight else PolishAmberLight,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (state.isFcmRegistered) "Registered" else "Ready",
                                    color = if (state.isFcmRegistered) PolishEmeraldDark else PolishAmberDark,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = "Token: ${state.fcmToken.ifBlank { "android_fcm_client_token" }}",
                            fontSize = 11.sp,
                            color = Slate500,
                            maxLines = 1
                        )

                        TextButton(
                            onClick = { isEditingFcm = !isEditingFcm },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(if (isEditingFcm) "Cancel Token Setup" else "Update Custom FCM Token", fontSize = 12.sp)
                        }

                        if (isEditingFcm) {
                            OutlinedTextField(
                                value = customFcmInput,
                                onValueChange = { customFcmInput = it },
                                label = { Text("Device FCM Token") },
                                placeholder = { Text("Enter FCM token...") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    if (customFcmInput.isNotBlank()) {
                                        onRegisterCustomFcmToken(customFcmInput.trim()) { _, _ -> isEditingFcm = false }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Register Token")
                            }
                        }
                    }
                }
            }

            // 6. Read-Only Admin Controls Card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PolishSurfaceLight,
                    border = BorderStroke(1.dp, PolishBorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Admin Configuration (Read-Only)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Slate500
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Platform Commission:", style = MaterialTheme.typography.bodySmall, color = Slate500)
                            Text(
                                text = "${vendor.commissionPercent ?: 10.0}% (Fixed by City Admin)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate900
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Account Status:", style = MaterialTheme.typography.bodySmall, color = Slate500)
                            Text(
                                text = vendor.approvalStatus.uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = PolishEmeraldDark,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (!vendor.cityId.isNullOrBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Assigned City ID:", style = MaterialTheme.typography.bodySmall, color = Slate500)
                                Text(vendor.cityId, style = MaterialTheme.typography.bodySmall, color = Slate900)
                            }
                        }
                    }
                }
            }

            // 7. Save Profile Changes Button
            item {
                Button(
                    onClick = {
                        val lat = latitudeStr.toDoubleOrNull()
                        val lng = longitudeStr.toDoubleOrNull()
                        onSaveProfile(
                            name.trim(),
                            ownerName.ifBlank { null },
                            phone.ifBlank { null },
                            address.ifBlank { null },
                            lat,
                            lng,
                            openingTime.ifBlank { null },
                            closingTime.ifBlank { null },
                            bannerUrl.ifBlank { null }
                        ) { _, _ -> }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary)
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SAVE STORE CHANGES", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    }
                }
            }

            // 8. Sign Out Button
            item {
                OutlinedButton(
                    onClick = { showLogoutConfirmation = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PolishRose)
                ) {
                    Icon(imageVector = Icons.Default.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out from Vendor Account", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // Add Slot Dialog
    if (showAddSlotDialog) {
        OperatingHourSlotDialog(
            title = "Add Operating Hours Slot",
            initialStart = "12:00",
            initialEnd = "16:00",
            initialDayOfWeek = "Daily",
            initialIsActive = true,
            existingSlots = state.operatingHours,
            currentSlotId = null,
            onDismiss = { showAddSlotDialog = false },
            onConfirm = { start, end, active, isClosed, day ->
                onAddOperatingHour(start, end, active, isClosed, day) { _, _ -> }
                showAddSlotDialog = false
            }
        )
    }

    // Edit Slot Dialog
    slotToEdit?.let { slot ->
        OperatingHourSlotDialog(
            title = "Edit Operating Hours Slot",
            initialStart = slot.startTime,
            initialEnd = slot.endTime,
            initialDayOfWeek = slot.dayOfWeek ?: "Daily",
            initialIsActive = slot.isActive,
            existingSlots = state.operatingHours,
            currentSlotId = slot.id,
            onDismiss = { slotToEdit = null },
            onConfirm = { start, end, active, isClosed, day ->
                onUpdateOperatingHour(slot.id, start, end, active, isClosed, day) { _, _ -> }
                slotToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog
    slotToDelete?.let { slot ->
        ConfirmationDialog(
            title = "Delete Operating Slot",
            message = "Are you sure you want to remove the slot ${slot.startTime} — ${slot.endTime}?",
            confirmText = "Delete Slot",
            isDestructive = true,
            onConfirm = {
                onDeleteOperatingHour(slot.id) { _, _ -> }
                slotToDelete = null
            },
            onDismiss = { slotToDelete = null }
        )
    }

    // Logout Confirmation Dialog
    if (showLogoutConfirmation) {
        ConfirmationDialog(
            title = "Sign Out",
            message = "Are you sure you want to sign out of your vendor account?",
            confirmText = "Sign Out",
            isDestructive = true,
            onConfirm = {
                showLogoutConfirmation = false
                onLogout()
            },
            onDismiss = { showLogoutConfirmation = false }
        )
    }
}

@Composable
private fun OperatingHourSlotDialog(
    title: String,
    initialStart: String,
    initialEnd: String,
    initialDayOfWeek: String,
    initialIsActive: Boolean,
    existingSlots: List<OperatingHour>,
    currentSlotId: String?,
    onDismiss: () -> Unit,
    onConfirm: (start: String, end: String, isActive: Boolean, isClosed: Boolean, dayOfWeek: String?) -> Unit
) {
    var slotStart by remember { mutableStateOf(initialStart) }
    var slotEnd by remember { mutableStateOf(initialEnd) }
    var dayOfWeek by remember { mutableStateOf(initialDayOfWeek) }
    var isActive by remember { mutableStateOf(initialIsActive) }

    val presetSlots = listOf(
        Triple("Breakfast", "07:00", "11:00"),
        Triple("Lunch", "12:00", "16:00"),
        Triple("Dinner", "19:00", "23:00"),
        Triple("Late Night", "23:00", "03:00")
    )

    // Validation
    val startMinutes = parseMinutes(slotStart)
    val endMinutes = parseMinutes(slotEnd)
    val isTimeValid = slotStart.isNotBlank() && slotEnd.isNotBlank() && (endMinutes > startMinutes || (startMinutes > 1200 && endMinutes < 360)) // handles midnight wrap
    val hasOverlap = existingSlots.any { other ->
        other.id != currentSlotId && other.isActive && slotsOverlap(slotStart, slotEnd, other.startTime, other.endTime)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Preset Chips
                Text("Quick Meal Presets:", style = MaterialTheme.typography.labelMedium, color = Slate500)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presetSlots) { (label, pStart, pEnd) ->
                        FilterChip(
                            selected = slotStart == pStart && slotEnd == pEnd,
                            onClick = {
                                slotStart = pStart
                                slotEnd = pEnd
                                if (dayOfWeek.isBlank() || dayOfWeek == "Daily") dayOfWeek = label
                            },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = slotStart,
                        onValueChange = { slotStart = it },
                        label = { Text("Start Time") },
                        placeholder = { Text("08:00") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = slotEnd,
                        onValueChange = { slotEnd = it },
                        label = { Text("End Time") },
                        placeholder = { Text("12:00") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                OutlinedTextField(
                    value = dayOfWeek,
                    onValueChange = { dayOfWeek = it },
                    label = { Text("Slot Name / Day Tag") },
                    placeholder = { Text("e.g. Breakfast, Lunch, Dinner, Daily") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Slot Active Status", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it }
                    )
                }

                if (hasOverlap) {
                    Surface(
                        color = PolishAmberLight,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠️ Note: This time slot overlaps with another configured slot.",
                            color = PolishAmberDark,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                if (!isTimeValid && slotStart.isNotBlank() && slotEnd.isNotBlank()) {
                    Text(
                        text = "End time should be after start time",
                        color = PolishRose,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(slotStart.trim(), slotEnd.trim(), isActive, false, dayOfWeek.trim().ifBlank { null })
                },
                enabled = slotStart.isNotBlank() && slotEnd.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary)
            ) {
                Text("Save Slot")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun parseMinutes(time: String): Int {
    val parts = time.split(":")
    if (parts.size != 2) return 0
    val h = parts[0].trim().toIntOrNull() ?: 0
    val m = parts[1].trim().toIntOrNull() ?: 0
    return h * 60 + m
}

private fun slotsOverlap(start1: String, end1: String, start2: String, end2: String): Boolean {
    val s1 = parseMinutes(start1)
    val e1 = parseMinutes(end1)
    val s2 = parseMinutes(start2)
    val e2 = parseMinutes(end2)
    if (s1 == 0 && e1 == 0 || s2 == 0 && e2 == 0) return false
    return maxOf(s1, s2) < minOf(e1, e2)
}
