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
import com.example.data.model.SelectedDocument
import com.example.data.model.Vendor
import com.example.ui.components.GoogleMapLocationPicker
import com.example.ui.theme.*
import com.example.ui.viewmodel.RegistrationUiState
import com.example.ui.viewmodel.VendorViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    // Reordered 4-Stage Step State:
    // Step 1: Store & Owner Details
    // Step 2: Location & Operating Hours
    // Step 3: Account & Login Credentials (Email + Password -> SignUp -> Verify OTP -> Active Session created)
    // Step 4: KYC Verification Documents (Food License + Aadhar Upload to private storage under auth.uid())
    // Step 5: Final Submission (Profiles + Vendors record created with pending status and storage paths)
    var currentStep by remember { mutableIntStateOf(1) }
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

    // OTP Countdown timer when OTP has been sent
    LaunchedEffect(registrationState.isOtpSent) {
        if (registrationState.isOtpSent && !registrationState.isOtpVerified) {
            otpCountdown = 60
            while (otpCountdown > 0) {
                delay(1000)
                otpCountdown--
            }
        }
    }

    // Auto-advance to Step 4 once OTP verification succeeds and authenticated session exists
    LaunchedEffect(registrationState.isOtpVerified) {
        if (registrationState.isOtpVerified && currentStep == 3) {
            currentStep = 4
        }
    }

    // Launchers for picking KYC documents (only uploads when session is authenticated with auth.uid)
    val foodLicenseLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val doc = parseDocumentUri(context, it, "food_license")
            if (doc != null) {
                foodLicenseDoc = doc
                val userId = viewModel.getEffectiveUserId()
                if (!userId.isNullOrBlank()) {
                    val safeFileName = "food-license-${System.currentTimeMillis()}.${doc.extension}"
                    viewModel.uploadKycDocument(
                        userId = userId,
                        isFoodLicense = true,
                        fileName = safeFileName,
                        bytes = doc.bytes,
                        mimeType = doc.mimeType
                    )
                }
            }
        }
    }

    val aadharLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val doc = parseDocumentUri(context, it, "aadhar_card")
            if (doc != null) {
                aadharDoc = doc
                val userId = viewModel.getEffectiveUserId()
                if (!userId.isNullOrBlank()) {
                    val safeFileName = "aadhar-${System.currentTimeMillis()}.${doc.extension}"
                    viewModel.uploadKycDocument(
                        userId = userId,
                        isFoodLicense = false,
                        fileName = safeFileName,
                        bytes = doc.bytes,
                        mimeType = doc.mimeType
                    )
                }
            }
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

            // Step Progress Indicator
            StepIndicator(
                currentStep = currentStep,
                isOtpVerified = registrationState.isOtpVerified,
                onStepClick = { targetStep ->
                    // Allow navigating back or forward only if previous requirements are met
                    if (targetStep < currentStep) {
                        currentStep = targetStep
                    } else if (targetStep == 2 && ownerName.isNotBlank() && hotelName.isNotBlank() && whatsappNumber.isNotBlank() && selectedCity != null) {
                        currentStep = 2
                    } else if (targetStep == 3 && address.isNotBlank()) {
                        currentStep = 3
                    } else if (targetStep == 4 && registrationState.isOtpVerified) {
                        currentStep = 4
                    }
                }
            )

            // STEP 1: Store & Owner Details
            if (currentStep == 1) {
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

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (ownerName.trim().isBlank()) {
                                formValidationMessage = "Please enter the Owner Name."
                                return@Button
                            }
                            if (hotelName.trim().isBlank()) {
                                formValidationMessage = "Please enter the Hotel/Restaurant Name."
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
                            formValidationMessage = null
                            currentStep = 2
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary)
                    ) {
                        Text("Next: Location & Operating Hours →", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            // STEP 2: Location & Operating Hours
            if (currentStep == 2) {
                SectionCard(
                    stepNumber = "2",
                    title = "Location & Operating Hours",
                    subtitle = "Pin your exact kitchen coordinates and service timings"
                ) {
                    // 5. Exact Hotel Location
                    Text(
                        text = "5. Exact Hotel Location (Interactive Google Maps) *",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate800
                    )
                    Text(
                        text = "Pinpoint your restaurant location on Google Maps. Drop the pin or search with Places Autocomplete to auto-fill address.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500
                    )

                    GoogleMapLocationPicker(
                        initialLatitude = latitude,
                        initialLongitude = longitude,
                        initialAddress = address,
                        cityName = selectedCity?.name,
                        onLocationSelected = { lat, lng, reverseGeocodedAddress ->
                            latitude = lat
                            longitude = lng
                            if (reverseGeocodedAddress.isNotBlank()) {
                                address = reverseGeocodedAddress
                            }
                            formValidationMessage = null
                        }
                    )

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

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { currentStep = 1 },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Slate300)
                        ) {
                            Text("← Back", color = Slate700)
                        }

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                if (address.trim().isBlank()) {
                                    formValidationMessage = "Please enter the physical store address."
                                    return@Button
                                }
                                formValidationMessage = null
                                currentStep = 3
                            },
                            modifier = Modifier
                                .weight(1.5f)
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary)
                        ) {
                            Text("Next: Account & OTP →", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }

            // STEP 3: Account & Login Credentials + Mobile OTP Verification
            // (MUST occur BEFORE KYC upload so auth.uid() session exists for private storage RLS)
            if (currentStep == 3) {
                SectionCard(
                    stepNumber = "3",
                    title = "Account & Login Credentials",
                    subtitle = "Verify your mobile number via SMS OTP to initialize your secure session"
                ) {
                    // Security architecture explanation banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SpruceContainerLight,
                        border = BorderStroke(1.dp, SpruceBorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = SprucePrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Supabase Private Storage requires an authenticated session (auth.uid) before KYC documents can be uploaded. Verifying mobile OTP establishes your active session for Step 4.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate700,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    if (registrationState.isOtpVerified) {
                        // Already verified state
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SpruceContainerLight,
                            border = BorderStroke(1.dp, SprucePrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(SprucePrimary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Mobile Number Verified & Session Active",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Slate900
                                        )
                                        Text(
                                            text = "+91 $whatsappNumber",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Slate600
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "User ID: ${registrationState.authenticatedUserId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate500,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { currentStep = 4 },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary)
                        ) {
                            Text("Proceed to Step 4: KYC Upload →", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    } else if (registrationState.isOtpSent) {
                        // Mobile OTP Verification Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = PolishSurfaceLight,
                            border = BorderStroke(1.5.dp, SprucePrimary.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .background(SpruceContainerLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sms,
                                        contentDescription = null,
                                        tint = SprucePrimary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "Enter 6-Digit SMS Code",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )

                                Text(
                                    text = "Enter the OTP sent to your mobile number +91 $whatsappNumber",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate600,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = otpCode,
                                    onValueChange = { if (it.length <= 8) otpCode = it },
                                    label = { Text("Enter Mobile OTP") },
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

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (otpCountdown > 0) "Resend in ${otpCountdown}s" else "Didn't get OTP?",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Slate500
                                    )

                                    TextButton(
                                        onClick = {
                                            if (otpCountdown == 0) {
                                                otpCountdown = 60
                                                viewModel.requestRegistrationOtp(whatsappNumber.trim(), password) { _, _ -> }
                                            }
                                        },
                                        enabled = otpCountdown == 0 && !registrationState.isSubmittingRegistration
                                    ) {
                                        Text("Resend SMS OTP", fontWeight = FontWeight.Bold, color = SprucePrimary)
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        if (otpCode.trim().length < 4) {
                                            formValidationMessage = "Please enter the complete OTP code."
                                            return@Button
                                        }
                                        viewModel.verifyRegistrationOtp(whatsappNumber.trim(), otpCode.trim())
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
                                        Text("Verifying session...", color = Color.White)
                                    } else {
                                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Verify Mobile OTP & Continue", fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                TextButton(
                                    onClick = { viewModel.resetOtpSent() },
                                    enabled = !registrationState.isSubmittingRegistration
                                ) {
                                    Text("Change Password / Re-verify", color = Slate600, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        // Mobile number display card (reused from Step 1, don't ask twice)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Slate100,
                            border = BorderStroke(1.dp, PolishBorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(SpruceContainerLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = SprucePrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "WhatsApp / Mobile Number",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Slate500
                                    )
                                    Text(
                                        text = "+91 $whatsappNumber",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate900
                                    )
                                }
                                TextButton(onClick = { currentStep = 1 }) {
                                    Text("Change", color = SprucePrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Password input
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                formValidationMessage = null
                            },
                            label = { Text("Password (min 6 chars) *") },
                            placeholder = { Text("Choose a secure password", color = Slate400) },
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

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                val cleanPhone = whatsappNumber.filter { it.isDigit() }
                                if (cleanPhone.length != 10) {
                                    formValidationMessage = "Invalid mobile number. Please go back to Step 1 and provide a 10-digit number."
                                    return@Button
                                }
                                if (password.length < 6) {
                                    formValidationMessage = "Password must be at least 6 characters."
                                    return@Button
                                }
                                formValidationMessage = null
                                viewModel.requestRegistrationOtp(cleanPhone, password) { _, _ -> }
                            },
                            enabled = !registrationState.isRequestingOtp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary)
                        ) {
                            if (registrationState.isRequestingOtp) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sending SMS OTP...", color = Color.White)
                            } else {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sign Up & Send SMS OTP", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { currentStep = 2 },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Slate300)
                    ) {
                        Text("← Back to Location & Hours", color = Slate700)
                    }
                }
            }

            // STEP 4: KYC Verification Documents (Food License, Aadhar Card) & Final Submit
            // (Only reached AFTER OTP verification ensures an active authenticated session)
            if (currentStep == 4) {
                SectionCard(
                    stepNumber = "4",
                    title = "KYC Verification Documents",
                    subtitle = "Upload required legal permits to your authenticated private KYC folder"
                ) {
                    // Active session verification confirmation
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SpruceContainerLight,
                        border = BorderStroke(1.dp, SprucePrimary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = SprucePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Authenticated Session Active (auth.uid)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = SprucePrimary
                                )
                                Text(
                                    text = "Folder: vendor-kyc-documents/${registrationState.authenticatedUserId ?: "user"}/",
                                    fontSize = 11.sp,
                                    color = Slate600
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

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
                        isUploading = registrationState.isUploadingFoodLicense,
                        uploadedStoragePath = registrationState.foodLicenseStoragePath,
                        uploadError = registrationState.foodLicenseUploadError,
                        onPickClick = { foodLicenseLauncher.launch("image/*") },
                        onRemoveClick = {
                            foodLicenseDoc = null
                            viewModel.clearFoodLicenseUpload()
                        },
                        onRetryUpload = {
                            val uid = viewModel.getEffectiveUserId()
                            val doc = foodLicenseDoc
                            if (!uid.isNullOrBlank() && doc != null) {
                                val safeFileName = "food-license-${System.currentTimeMillis()}.${doc.extension}"
                                viewModel.uploadKycDocument(
                                    userId = uid,
                                    isFoodLicense = true,
                                    fileName = safeFileName,
                                    bytes = doc.bytes,
                                    mimeType = doc.mimeType
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

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
                        isUploading = registrationState.isUploadingAadhar,
                        uploadedStoragePath = registrationState.aadharStoragePath,
                        uploadError = registrationState.aadharUploadError,
                        onPickClick = { aadharLauncher.launch("image/*") },
                        onRemoveClick = {
                            aadharDoc = null
                            viewModel.clearAadharUpload()
                        },
                        onRetryUpload = {
                            val uid = viewModel.getEffectiveUserId()
                            val doc = aadharDoc
                            if (!uid.isNullOrBlank() && doc != null) {
                                val safeFileName = "aadhar-${System.currentTimeMillis()}.${doc.extension}"
                                viewModel.uploadKycDocument(
                                    userId = uid,
                                    isFoodLicense = false,
                                    fileName = safeFileName,
                                    bytes = doc.bytes,
                                    mimeType = doc.mimeType
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // In-line error card right above the submit button
                    val submitError = formValidationMessage ?: registrationState.errorMessage
                    if (submitError != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFFEBEE),
                            border = BorderStroke(1.dp, PolishRose),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = PolishRose,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Submission Failed",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = PolishRose
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = submitError,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFB71C1C)
                                    )
                                }
                            }
                        }
                    }

                    // Final Submit Button
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            val effUserId = viewModel.getEffectiveUserId()
                            if (effUserId.isNullOrBlank()) {
                                formValidationMessage = "Active session expired. Please verify OTP again."
                                currentStep = 3
                                return@Button
                            }
                            if (selectedCity == null || selectedCity?.id.isNullOrBlank()) {
                                formValidationMessage = "Please select a valid City from Step 1."
                                currentStep = 1
                                return@Button
                            }
                            if (foodLicenseDoc == null && registrationState.foodLicenseStoragePath.isNullOrBlank()) {
                                formValidationMessage = "Please select your Food License document."
                                return@Button
                            }
                            if (aadharDoc == null && registrationState.aadharStoragePath.isNullOrBlank()) {
                                formValidationMessage = "Please select your Aadhar Card document."
                                return@Button
                            }

                            formValidationMessage = null

                            viewModel.submitFinalRegistration(
                                ownerName = ownerName,
                                hotelName = hotelName,
                                phone = whatsappNumber,
                                cityId = selectedCity?.id ?: "",
                                address = address,
                                latitude = latitude,
                                longitude = longitude,
                                openingTime = openingTime,
                                closingTime = closingTime,
                                email = email,
                                foodLicenseDoc = foodLicenseDoc,
                                aadharDoc = aadharDoc
                            ) { success, errorMsg, _ ->
                                if (!success) {
                                    formValidationMessage = errorMsg ?: "Vendor registration failed. Please review details."
                                }
                            }
                        },
                        enabled = !registrationState.isSubmittingRegistration &&
                                !registrationState.isUploadingFoodLicense &&
                                !registrationState.isUploadingAadhar &&
                                (foodLicenseDoc != null || !registrationState.foodLicenseStoragePath.isNullOrBlank()) &&
                                (aadharDoc != null || !registrationState.aadharStoragePath.isNullOrBlank()),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary)
                    ) {
                        if (registrationState.isSubmittingRegistration ||
                            registrationState.isUploadingFoodLicense ||
                            registrationState.isUploadingAadhar
                        ) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Uploading & Submitting Application...", color = Color.White, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Submit Application to City Admin", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { currentStep = 3 },
                        enabled = !registrationState.isSubmittingRegistration,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Slate300)
                    ) {
                        Text("← Back to Account Credentials", color = Slate700)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Modal Error Alert if registration submission fails
    if (registrationState.errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearMessages() },
            icon = {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = PolishRose,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Registration Failed",
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
            },
            text = {
                Text(
                    text = registrationState.errorMessage ?: "An unexpected error occurred while submitting your registration.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate700
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearMessages() },
                    colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary)
                ) {
                    Text("Understood")
                }
            }
        )
    }
}

@Composable
private fun StepIndicator(
    currentStep: Int,
    isOtpVerified: Boolean,
    onStepClick: (Int) -> Unit
) {
    val steps = listOf(
        1 to "Details",
        2 to "Location",
        3 to "Account & OTP",
        4 to "KYC Upload"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PolishSurfaceLight,
        border = BorderStroke(1.dp, PolishBorderLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            steps.forEachIndexed { index, (stepNum, stepLabel) ->
                val isCompleted = stepNum < currentStep || (stepNum == 3 && isOtpVerified)
                val isCurrent = stepNum == currentStep

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onStepClick(stepNum) }
                        .padding(horizontal = 2.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isCurrent -> SprucePrimary
                                    isCompleted -> SprucePrimary.copy(alpha = 0.85f)
                                    else -> Slate200
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted && !isCurrent) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        } else {
                            Text(
                                text = "$stepNum",
                                color = if (isCurrent || isCompleted) Color.White else Slate500,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = stepLabel,
                        fontSize = 10.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCurrent) SprucePrimary else if (isCompleted) Slate800 else Slate400,
                        maxLines = 1
                    )
                }

                if (index < steps.size - 1) {
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(1.5.dp)
                            .background(if (stepNum < currentStep) SprucePrimary else Slate200)
                    )
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
    isUploading: Boolean = false,
    uploadedStoragePath: String? = null,
    uploadError: String? = null,
    onPickClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onRetryUpload: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (selectedDoc != null) {
            // Document Attached Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (uploadedStoragePath != null) SpruceContainerLight else if (uploadError != null) PolishRoseLight else PolishSurfaceLight,
                border = BorderStroke(
                    1.dp,
                    if (uploadedStoragePath != null) SprucePrimary.copy(alpha = 0.4f)
                    else if (uploadError != null) PolishRose.copy(alpha = 0.4f)
                    else PolishBorderLight
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (uploadedStoragePath != null) SprucePrimary
                                    else if (uploadError != null) PolishRose
                                    else SpruceContainerLight
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isUploading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = SprucePrimary
                                )
                            } else if (uploadedStoragePath != null) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else if (uploadError != null) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = SprucePrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
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
                                text = when {
                                    isUploading -> "Uploading to private KYC storage..."
                                    uploadedStoragePath != null -> "✓ Uploaded & verified (${selectedDoc.fileSizeFormatted})"
                                    uploadError != null -> "Upload failed • Tap retry below"
                                    else -> "${selectedDoc.fileSizeFormatted} • Ready to upload"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uploadedStoragePath != null) SprucePrimary else if (uploadError != null) PolishRose else Slate500,
                                fontSize = 11.sp
                            )
                        }

                        if (!isUploading) {
                            IconButton(onClick = onRemoveClick) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove file",
                                    tint = Slate500
                                )
                            }
                        }
                    }

                    // Display Real Supabase Error if upload failed
                    if (uploadError != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PolishRose.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, PolishRose.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Supabase Storage Error:",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = PolishRose
                                    )
                                    Text(
                                        text = uploadError,
                                        fontSize = 11.sp,
                                        color = PolishRose,
                                        lineHeight = 15.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = onRetryUpload,
                                    colors = ButtonDefaults.buttonColors(containerColor = PolishRose),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Retry", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Display storage path if successfully uploaded
                    if (uploadedStoragePath != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Path: vendor-kyc-documents/$uploadedStoragePath",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            color = Slate500,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
            uriString = uri.toString(),
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
