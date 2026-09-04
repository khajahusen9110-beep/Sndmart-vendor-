package com.example.ui.auth

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.City
import com.example.data.model.Vendor
import com.example.ui.theme.*
import com.example.ui.viewmodel.RegistrationUiState
import com.example.ui.viewmodel.VendorViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SelectedDocument(
    val uri: Uri,
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
        return uri == other.uri
    }

    override fun hashCode(): Int = uri.hashCode()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterRestaurantScreen(
    viewModel: VendorViewModel,
    onNavigateBackToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val registrationState by viewModel.registrationState.collectAsState()

    // Form inputs in specified order
    // 1. Owner Name
    var ownerName by remember { mutableStateOf("") }
    // 2. Hotel/Restaurant Name
    var hotelName by remember { mutableStateOf("") }
    // 3. WhatsApp Number
    var whatsappNumber by remember { mutableStateOf("") }
    // 4. City
    var selectedCity by remember { mutableStateOf<City?>(null) }
    var cityDropdownExpanded by remember { mutableStateOf(false) }
    // 5. Exact Hotel Location (lat/lng)
    var latitude by remember { mutableStateOf(19.0760) }
    var longitude by remember { mutableStateOf(72.8777) }
    var showMapDialog by remember { mutableStateOf(false) }
    // 6. Address
    var address by remember { mutableStateOf("") }
    // 7. Opening / Closing Time
    var openingTime by remember { mutableStateOf("08:00") }
    var closingTime by remember { mutableStateOf("22:00") }
    var showTimeDialog by remember { mutableStateOf(false) }
    // 8. Food License
    var foodLicenseDoc by remember { mutableStateOf<SelectedDocument?>(null) }
    // 9. Aadhar Card
    var aadharDoc by remember { mutableStateOf<SelectedDocument?>(null) }
    // 10. Email
    var email by remember { mutableStateOf("") }
    // 11. Password
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // 12. OTP Verification Dialog State
    var showOtpDialog by remember { mutableStateOf(false) }
    var otpCode by remember { mutableStateOf("") }
    var otpCountdown by remember { mutableIntStateOf(60) }
    var formValidationMessage by remember { mutableStateOf<String?>(null) }

    // Load active cities once
    LaunchedEffect(Unit) {
        viewModel.loadCities()
    }

    // Default city selection when cities arrive
    LaunchedEffect(registrationState.cities) {
        if (selectedCity == null && registrationState.cities.isNotEmpty()) {
            selectedCity = registrationState.cities.first()
        }
    }

    // OTP Countdown timer
    LaunchedEffect(showOtpDialog) {
        if (showOtpDialog) {
            otpCountdown = 60
            while (otpCountdown > 0) {
                delay(1000)
                otpCountdown--
            }
        }
    }

    // Launchers for picking KYC documents
    val foodLicenseLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val doc = parseDocumentUri(context, it, "food_license")
            if (doc != null) foodLicenseDoc = doc
        }
    }

    val aadharLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val doc = parseDocumentUri(context, it, "aadhar_card")
            if (doc != null) aadharDoc = doc
        }
    }

    // If registration is complete, show the Application Confirmation Screen
    val registeredVendor = registrationState.registeredVendor
    if (registeredVendor != null) {
        RegistrationSuccessView(
            vendor = registeredVendor,
            onBackToLogin = {
                viewModel.resetRegistrationState()
                onNavigateBackToLogin()
            },
            modifier = modifier
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Register Restaurant",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = Slate900
                        )
                        Text(
                            text = "Hotel & Food Merchant Onboarding",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate500
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBackToLogin) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Login",
                            tint = Slate800
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PolishBackgroundLight
                )
            )
        },
        containerColor = PolishBackgroundLight,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Banner
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SpruceContainerLight,
                border = BorderStroke(1.dp, SpruceBorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(SprucePrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestaurantMenu,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Join Sndmart Food Network",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate900
                        )
                        Text(
                            text = "Provide your store info and KYC documents for quick city approval.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate600
                        )
                    }
                }
            }

            // Error banner if any
            val activeError = formValidationMessage ?: registrationState.errorMessage
            if (activeError != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PolishRoseLight,
                    border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = PolishRose,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = activeError,
                            color = PolishRose,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        if (registrationState.errorMessage != null) {
                            TextButton(
                                onClick = { viewModel.loadCities() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Retry", color = PolishRose, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // SECTION 1: Restaurant & Owner Profile
            SectionCard(
                stepNumber = "1",
                title = "Owner & Store Details",
                subtitle = "Basic information about your restaurant"
            ) {
                // 1. Owner Name
                OutlinedTextField(
                    value = ownerName,
                    onValueChange = {
                        ownerName = it
                        formValidationMessage = null
                    },
                    label = { Text("1. Owner Name *") },
                    placeholder = { Text("e.g. Ramesh Sharma", color = Slate400) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = Slate400)
                    },
                    colors = polishTextFieldColors(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // 2. Hotel/Restaurant Name
                OutlinedTextField(
                    value = hotelName,
                    onValueChange = {
                        hotelName = it
                        formValidationMessage = null
                    },
                    label = { Text("2. Hotel / Restaurant Name *") },
                    placeholder = { Text("e.g. Spice Route Hotel & Kitchen", color = Slate400) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Storefront, contentDescription = null, tint = Slate400)
                    },
                    colors = polishTextFieldColors(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // 3. WhatsApp Number
                OutlinedTextField(
                    value = whatsappNumber,
                    onValueChange = {
                        whatsappNumber = it
                        formValidationMessage = null
                    },
                    label = { Text("3. WhatsApp Number *") },
                    placeholder = { Text("+91 98765 43210", color = Slate400) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = Slate400)
                    },
                    colors = polishTextFieldColors(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // 4. City Dropdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "4. City *",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate800
                    )
                    if (registrationState.isLoadingCities) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = SprucePrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Loading...",
                                style = MaterialTheme.typography.bodySmall,
                                color = SprucePrimary,
                                fontSize = 12.sp
                            )
                        }
                    } else if (registrationState.cities.isEmpty()) {
                        TextButton(
                            onClick = { viewModel.loadCities() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                tint = SprucePrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry", fontSize = 12.sp, color = SprucePrimary)
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = cityDropdownExpanded,
                    onExpandedChange = { 
                        if (!registrationState.isLoadingCities) {
                            cityDropdownExpanded = !cityDropdownExpanded 
                        }
                    }
                ) {
                    OutlinedTextField(
                        value = selectedCity?.name ?: if (registrationState.isLoadingCities) "Fetching active cities..." else if (registrationState.cities.isEmpty()) "No cities found (tap to retry)" else "Select City",
                        onValueChange = {},
                        readOnly = true,
                        colors = polishTextFieldColors(),
                        trailingIcon = { 
                            if (registrationState.isLoadingCities) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = SprucePrimary
                                )
                            } else {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityDropdownExpanded) 
                            }
                        },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.LocationCity, contentDescription = null, tint = Slate400)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = cityDropdownExpanded,
                        onDismissRequest = { cityDropdownExpanded = false }
                    ) {
                        if (registrationState.cities.isEmpty()) {
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        text = if (registrationState.isLoadingCities) "Loading cities from database..." else "No active cities available. Tap to reload.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Slate500
                                    ) 
                                },
                                onClick = {
                                    viewModel.loadCities()
                                    cityDropdownExpanded = false
                                }
                            )
                        } else {
                            registrationState.cities.forEach { city ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(city.name, fontWeight = FontWeight.SemiBold, color = Slate900)
                                            if (!city.state.isNullOrBlank()) {
                                                Text(
                                                    text = city.state,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Slate500
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedCity = city
                                        cityDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 2: Location & Timing
            SectionCard(
                stepNumber = "2",
                title = "Location & Operating Hours",
                subtitle = "Pin your exact kitchen coordinates and service timings"
            ) {
                // 5. Exact Hotel Location
                Text(
                    text = "5. Exact Hotel Location (Map Picker) *",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate800
                )

                // Location Picker Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PolishSurfaceLight,
                    border = BorderStroke(1.dp, PolishBorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Interactive Mini Map Canvas
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFE5ECE9))
                                .clickable { showMapDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height
                                // Draw stylized map grid lines
                                for (i in 0..10) {
                                    val x = (w / 10) * i
                                    drawLine(Color(0xFFD0DCD6), Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                                }
                                for (j in 0..6) {
                                    val y = (h / 6) * j
                                    drawLine(Color(0xFFD0DCD6), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                                }
                                // Center radar pulse
                                drawCircle(SprucePrimary.copy(alpha = 0.2f), radius = 32f, center = center)
                                drawCircle(SprucePrimary.copy(alpha = 0.4f), radius = 18f, center = center)
                            }

                            // Pin icon in center
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = "Location Pin",
                                    tint = PolishRose,
                                    modifier = Modifier.size(36.dp)
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Slate900.copy(alpha = 0.85f),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Text(
                                        text = "${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Tap to adjust overlay tag
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EditLocationAlt,
                                        contentDescription = null,
                                        tint = SprucePrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Change Pin",
                                        color = SprucePrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Lat: ${String.format("%.5f", latitude)} | Lng: ${String.format("%.5f", longitude)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate600
                            )
                            TextButton(
                                onClick = {
                                    // Quick toggle to center of selected city
                                    latitude = 19.0760 + (Math.random() - 0.5) * 0.05
                                    longitude = 72.8777 + (Math.random() - 0.5) * 0.05
                                    if (address.isBlank()) {
                                        address = "Near Central Market, ${selectedCity?.name ?: "Main Road"}"
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Get Location", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // 6. Address
                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it
                        formValidationMessage = null
                    },
                    label = { Text("6. Full Physical Address *") },
                    placeholder = { Text("Shop 4, Grand Plaza, Station Road, Opp Metro Pillar 124...", color = Slate400) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = Slate400)
                    },
                    colors = polishTextFieldColors(),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // 7. Opening / Closing Time
                Text(
                    text = "7. Service Hours *",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate800
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = openingTime,
                        onValueChange = { openingTime = it },
                        label = { Text("Opening Time") },
                        leadingIcon = {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = Slate400)
                        },
                        colors = polishTextFieldColors(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = closingTime,
                        onValueChange = { closingTime = it },
                        label = { Text("Closing Time") },
                        leadingIcon = {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = Slate400)
                        },
                        colors = polishTextFieldColors(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Quick presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("08:00 - 22:00", "07:00 - 23:00", "10:00 - 02:00", "24 Hours").forEach { preset ->
                        val isSelected = when (preset) {
                            "08:00 - 22:00" -> openingTime == "08:00" && closingTime == "22:00"
                            "07:00 - 23:00" -> openingTime == "07:00" && closingTime == "23:00"
                            "10:00 - 02:00" -> openingTime == "10:00" && closingTime == "02:00"
                            "24 Hours" -> openingTime == "00:00" && closingTime == "23:59"
                            else -> false
                        }
                        SuggestionChip(
                            onClick = {
                                val parts = preset.split(" - ")
                                if (parts.size == 2) {
                                    openingTime = parts[0]
                                    closingTime = parts[1]
                                } else if (preset == "24 Hours") {
                                    openingTime = "00:00"
                                    closingTime = "23:59"
                                }
                            },
                            label = { Text(preset, fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (isSelected) SprucePrimary.copy(alpha = 0.15f) else Color.Transparent
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = if (isSelected) SprucePrimary else PolishBorderLight
                            )
                        )
                    }
                }
            }

            // SECTION 3: KYC Documents Upload
            SectionCard(
                stepNumber = "3",
                title = "KYC Verification Documents",
                subtitle = "Upload required legal permits (stored securely in private storage)"
            ) {
                // 8. Food License Upload
                Text(
                    text = "8. Food License (FSSAI / Municipal Permit) *",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate800
                )

                DocumentUploadSlot(
                    title = "Upload Food License",
                    description = "PDF, JPG, or PNG under 10MB",
                    icon = Icons.Default.Description,
                    selectedDoc = foodLicenseDoc,
                    onPickClick = { foodLicenseLauncher.launch("image/*") },
                    onRemoveClick = { foodLicenseDoc = null }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 9. Aadhar Card Upload
                Text(
                    text = "9. Aadhar Card / Owner Identity *",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate800
                )

                DocumentUploadSlot(
                    title = "Upload Aadhar Card",
                    description = "Clear front/back image or PDF",
                    icon = Icons.Default.Badge,
                    selectedDoc = aadharDoc,
                    onPickClick = { aadharLauncher.launch("image/*") },
                    onRemoveClick = { aadharDoc = null }
                )
            }

            // SECTION 4: Account Credentials
            SectionCard(
                stepNumber = "4",
                title = "Account & Login Credentials",
                subtitle = "Credentials you'll use to log in once approved"
            ) {
                // 10. Email
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        formValidationMessage = null
                    },
                    label = { Text("10. Email Address *") },
                    placeholder = { Text("vendor@myrestaurant.com", color = Slate400) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Email, contentDescription = null, tint = Slate400)
                    },
                    colors = polishTextFieldColors(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // 11. Password
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        formValidationMessage = null
                    },
                    label = { Text("11. Password (min 6 chars) *") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Slate400)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = Slate400
                            )
                        }
                    },
                    colors = polishTextFieldColors(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Submit Button
            Button(
                onClick = {
                    focusManager.clearFocus()
                    // Comprehensive validation in requested order
                    if (ownerName.trim().isBlank()) {
                        formValidationMessage = "Please provide the Owner Name."
                        return@Button
                    }
                    if (hotelName.trim().isBlank()) {
                        formValidationMessage = "Please provide your Hotel/Restaurant Name."
                        return@Button
                    }
                    if (whatsappNumber.trim().isBlank()) {
                        formValidationMessage = "Please enter your WhatsApp Number."
                        return@Button
                    }
                    if (selectedCity == null) {
                        formValidationMessage = "Please select a City from the dropdown."
                        return@Button
                    }
                    if (address.trim().isBlank()) {
                        formValidationMessage = "Please enter the physical store address."
                        return@Button
                    }
                    if (foodLicenseDoc == null) {
                        formValidationMessage = "Please upload your Food License document."
                        return@Button
                    }
                    if (aadharDoc == null) {
                        formValidationMessage = "Please upload your Aadhar Card document."
                        return@Button
                    }
                    if (email.trim().isBlank() || !email.contains("@")) {
                        formValidationMessage = "Please enter a valid email address."
                        return@Button
                    }
                    if (password.length < 6) {
                        formValidationMessage = "Password must be at least 6 characters long."
                        return@Button
                    }

                    formValidationMessage = null
                    // Trigger Step 12: Request OTP via GoTrue signUp
                    viewModel.requestRegistrationOtp(email, password) { success, _ ->
                        if (success) {
                            showOtpDialog = true
                        }
                    }
                },
                enabled = !registrationState.isRequestingOtp && !registrationState.isSubmittingRegistration,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary)
            ) {
                if (registrationState.isRequestingOtp) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Sending Verification OTP...", color = Color.White, fontWeight = FontWeight.Bold)
                } else {
                    Icon(imageVector = Icons.Default.VerifiedUser, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Proceed to OTP Verification", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Step 12: OTP Verification Dialog
    if (showOtpDialog) {
        Dialog(
            onDismissRequest = {
                if (!registrationState.isSubmittingRegistration) showOtpDialog = false
            },
            properties = DialogProperties(dismissOnBackPress = !registrationState.isSubmittingRegistration, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = PolishSurfaceLight,
                border = BorderStroke(1.dp, PolishBorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(SpruceContainerLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MarkEmailRead,
                            contentDescription = null,
                            tint = SprucePrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Verify Your Email",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "We sent a 6-digit confirmation code to:\n$email",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate600,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { if (it.length <= 8) otpCode = it },
                        label = { Text("6-Digit OTP Code") },
                        placeholder = { Text("123456", color = Slate400) },
                        singleLine = true,
                        colors = polishTextFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (registrationState.errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = registrationState.errorMessage ?: "",
                            color = PolishRose,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (otpCountdown > 0) "Resend in ${otpCountdown}s" else "Didn't get code?",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate500
                        )

                        TextButton(
                            onClick = {
                                if (otpCountdown == 0) {
                                    otpCountdown = 60
                                    viewModel.requestRegistrationOtp(email, password) { _, _ -> }
                                }
                            },
                            enabled = otpCountdown == 0 && !registrationState.isSubmittingRegistration
                        ) {
                            Text("Resend OTP", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (otpCode.trim().length < 4) {
                                return@Button
                            }
                            viewModel.submitRegistrationWithOtp(
                                ownerName = ownerName,
                                hotelName = hotelName,
                                phone = whatsappNumber,
                                cityId = selectedCity?.id ?: "city_default",
                                address = address,
                                latitude = latitude,
                                longitude = longitude,
                                openingTime = openingTime,
                                closingTime = closingTime,
                                email = email,
                                token = otpCode,
                                foodLicenseBytes = foodLicenseDoc?.bytes,
                                foodLicenseExt = foodLicenseDoc?.extension ?: "jpg",
                                foodLicenseMime = foodLicenseDoc?.mimeType ?: "image/jpeg",
                                aadharBytes = aadharDoc?.bytes,
                                aadharExt = aadharDoc?.extension ?: "jpg",
                                aadharMime = aadharDoc?.mimeType ?: "image/jpeg"
                            ) { success, _, _ ->
                                if (success) {
                                    showOtpDialog = false
                                }
                            }
                        },
                        enabled = !registrationState.isSubmittingRegistration && otpCode.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary)
                    ) {
                        if (registrationState.isSubmittingRegistration) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Uploading Documents & Submitting...", color = Color.White, fontSize = 13.sp)
                        } else {
                            Text("Verify & Submit Application", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { showOtpDialog = false },
                        enabled = !registrationState.isSubmittingRegistration
                    ) {
                        Text("Cancel / Edit Details", color = Slate600)
                    }
                }
            }
        }
    }

    // Map Picker Dialog
    if (showMapDialog) {
        Dialog(onDismissRequest = { showMapDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = PolishSurfaceLight,
                border = BorderStroke(1.dp, PolishBorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Pick Restaurant Coordinates",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                    Text(
                        text = "Adjust the pin coordinates for your kitchen",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Coordinate inputs
                    var tempLat by remember { mutableStateOf(latitude.toString()) }
                    var tempLng by remember { mutableStateOf(longitude.toString()) }

                    OutlinedTextField(
                        value = tempLat,
                        onValueChange = { tempLat = it },
                        label = { Text("Latitude") },
                        colors = polishTextFieldColors(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = tempLng,
                        onValueChange = { tempLng = it },
                        label = { Text("Longitude") },
                        colors = polishTextFieldColors(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showMapDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                latitude = tempLat.toDoubleOrNull() ?: latitude
                                longitude = tempLng.toDoubleOrNull() ?: longitude
                                showMapDialog = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary)
                        ) {
                            Text("Set Coordinates")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    stepNumber: String,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = PolishSurfaceLight,
        border = BorderStroke(1.dp, PolishBorderLight),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(SprucePrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stepNumber,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500,
                        fontSize = 11.sp
                    )
                }
            }

            HorizontalDivider(color = PolishBorderLight)

            content()
        }
    }
}

@Composable
private fun DocumentUploadSlot(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selectedDoc: SelectedDocument?,
    onPickClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    if (selectedDoc != null) {
        // Document Attached Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SpruceContainerLight,
            border = BorderStroke(1.dp, SprucePrimary.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SprucePrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedDoc.fileName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate900,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${selectedDoc.fileSizeFormatted} • Ready to upload to KYC storage",
                        style = MaterialTheme.typography.bodySmall,
                        color = SprucePrimary,
                        fontSize = 11.sp
                    )
                }

                IconButton(onClick = onRemoveClick) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove file",
                        tint = Slate500
                    )
                }
            }
        }
    } else {
        // Empty Upload Trigger Area
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFFBFDFD),
            border = BorderStroke(1.dp, PolishBorderLight),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPickClick() }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(PolishSurfaceSubtle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = SprucePrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate900
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500,
                        fontSize = 11.sp
                    )
                }

                OutlinedButton(
                    onClick = onPickClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SprucePrimary),
                    border = BorderStroke(1.dp, SprucePrimary)
                ) {
                    Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Select File", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun RegistrationSuccessView(
    vendor: Vendor,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = PolishBackgroundLight
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(SpruceContainerLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = SprucePrimary,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Application Submitted!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Slate900,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Our team will review your documents and approve your restaurant within 24-48 hours. You'll be notified once approved.",
                style = MaterialTheme.typography.bodyMedium,
                color = Slate600,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Application Summary Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = PolishSurfaceLight,
                border = BorderStroke(1.dp, PolishBorderLight),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Application Summary",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SndmartAmber.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "PENDING REVIEW",
                                color = SndmartAmber,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = PolishBorderLight)

                    SummaryItem(label = "Restaurant Name", value = vendor.name)
                    if (!vendor.ownerName.isNullOrBlank()) {
                        SummaryItem(label = "Owner Name", value = vendor.ownerName)
                    }
                    if (!vendor.phone.isNullOrBlank()) {
                        SummaryItem(label = "WhatsApp Contact", value = vendor.phone)
                    }
                    if (!vendor.address.isNullOrBlank()) {
                        SummaryItem(label = "Store Location", value = vendor.address)
                    }
                    SummaryItem(label = "KYC Documents", value = "Food License & Aadhar Submitted ✓")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onBackToLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary)
            ) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Return to Sign In", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Slate500, fontSize = 12.sp)
        Text(
            text = value,
            color = Slate900,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun parseDocumentUri(context: Context, uri: Uri, fallbackName: String): SelectedDocument? {
    return try {
        var fileName = "$fallbackName.jpg"
        var fileSize = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
            }
        }

        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (fileSize == 0L) fileSize = bytes.size.toLong()

        val mimeType = context.contentResolver.getType(uri) ?: when {
            fileName.endsWith(".png", true) -> "image/png"
            fileName.endsWith(".pdf", true) -> "application/pdf"
            else -> "image/jpeg"
        }

        val ext = when {
            fileName.contains(".") -> fileName.substringAfterLast(".").lowercase()
            mimeType == "application/pdf" -> "pdf"
            mimeType == "image/png" -> "png"
            else -> "jpg"
        }

        val sizeFormatted = when {
            fileSize > 1024 * 1024 -> String.format("%.1f MB", fileSize / (1024.0 * 1024.0))
            else -> "${fileSize / 1024} KB"
        }

        SelectedDocument(
            uri = uri,
            fileName = fileName,
            fileSizeFormatted = sizeFormatted,
            bytes = bytes,
            extension = ext,
            mimeType = mimeType
        )
    } catch (_: Exception) {
        null
    }
}
