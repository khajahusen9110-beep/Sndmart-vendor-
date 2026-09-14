package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.ui.theme.*
import com.example.util.LocationHelper
import com.example.util.PlaceSearchResult
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Interactive Google Maps Location Picker component.
 * Allows the vendor to:
 * 1. Pan/drag the map to position a pin precisely on their store location.
 * 2. Tap on the map to place the pin.
 * 3. Search via Google Places Autocomplete or Geocoder to jump to their address.
 * 4. Center on the device's current location if granted.
 * 5. Reverse-geocodes coordinates into a readable address to prefill the address field.
 */
@Composable
fun GoogleMapLocationPicker(
    initialLatitude: Double,
    initialLongitude: Double,
    initialAddress: String = "",
    cityName: String? = null,
    onLocationSelected: (lat: Double, lng: Double, reverseGeocodedAddress: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showFullScreenDialog by remember { mutableStateOf(false) }
    var currentLat by remember(initialLatitude) { mutableStateOf(initialLatitude) }
    var currentLng by remember(initialLongitude) { mutableStateOf(initialLongitude) }
    var currentAddress by remember(initialAddress) { mutableStateOf(initialAddress) }
    var isReverseGeocoding by remember { mutableStateOf(false) }

    // Inline preview card with mini interactive map or launcher
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PolishSurfaceLight,
        border = BorderStroke(1.dp, PolishBorderLight),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SpruceContainerLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = PolishRose,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Interactive Store Pin",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                        Text(
                            text = "Google Maps Location Picker",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate500,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            val fineGranted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            val coarseGranted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED

                            if (fineGranted || coarseGranted) {
                                coroutineScope.launch {
                                    isReverseGeocoding = true
                                    val loc = LocationHelper.getCurrentLocation(context)
                                    if (loc != null) {
                                        currentLat = loc.latitude
                                        currentLng = loc.longitude
                                        val addr = LocationHelper.reverseGeocode(context, loc.latitude, loc.longitude)
                                        currentAddress = addr
                                        onLocationSelected(loc.latitude, loc.longitude, addr)
                                        Toast.makeText(context, "Location detected: ${String.format(Locale.US, "%.4f, %.4f", loc.latitude, loc.longitude)}", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Could not acquire current GPS location. Please check device location settings.", Toast.LENGTH_LONG).show()
                                    }
                                    isReverseGeocoding = false
                                }
                            } else {
                                Toast.makeText(context, "Please open the full map to grant location permission.", Toast.LENGTH_SHORT).show()
                                showFullScreenDialog = true
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Slate100,
                            contentColor = Slate700
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Detect Location",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Detect", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }

                    FilledTonalButton(
                        onClick = { showFullScreenDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = SpruceContainerLight,
                            contentColor = SprucePrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Expand Map",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open Map", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Embedded Interactive Map Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, PolishBorderLight, RoundedCornerShape(14.dp))
            ) {
                // When full screen dialog is open, do not run inline GoogleMap to prevent duplicate SurfaceViews
                if (!showFullScreenDialog) {
                    val defaultPosition = remember(currentLat, currentLng) {
                        CameraPosition.fromLatLngZoom(LatLng(currentLat, currentLng), 15f)
                    }
                    val cameraPositionState = rememberCameraPositionState {
                        position = defaultPosition
                    }

                    // Keep camera in sync when lat/lng changes externally
                    LaunchedEffect(currentLat, currentLng) {
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(currentLat, currentLng), 15f)
                    }

                    val uiSettings = remember {
                        MapUiSettings(
                            zoomControlsEnabled = false,
                            compassEnabled = true,
                            myLocationButtonEnabled = false,
                            mapToolbarEnabled = false
                        )
                    }
                    val mapProperties = remember {
                        MapProperties(
                            isMyLocationEnabled = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                        )
                    }

                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        uiSettings = uiSettings,
                        properties = mapProperties,
                        onMapClick = { latLng ->
                            currentLat = latLng.latitude
                            currentLng = latLng.longitude
                            coroutineScope.launch {
                                isReverseGeocoding = true
                                val addr = LocationHelper.reverseGeocode(context, latLng.latitude, latLng.longitude)
                                currentAddress = addr
                                isReverseGeocoding = false
                                onLocationSelected(latLng.latitude, latLng.longitude, addr)
                            }
                        }
                    ) {
                        Marker(
                            state = rememberMarkerState(position = LatLng(currentLat, currentLng)),
                            title = "Store Location",
                            snippet = currentAddress.ifBlank { "Coordinates: $currentLat, $currentLng" },
                            draggable = true
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFE8ECEB)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = null,
                                tint = SprucePrimary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Full Map Open",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate700
                            )
                        }
                    }
                }

                // Overlay badge with quick adjust button
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Slate900.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clickable { showFullScreenDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditLocationAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tap / Drag to Fine Tune",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Coordinates and address display
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Slate100,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lat: ${String.format(Locale.US, "%.5f", currentLat)}  |  Lng: ${String.format(Locale.US, "%.5f", currentLng)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Slate800
                        )
                        if (currentAddress.isNotBlank()) {
                            Text(
                                text = currentAddress,
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate600,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (isReverseGeocoding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = SprucePrimary
                        )
                    }
                }
            }
        }
    }

    // Full screen interactive Map Dialog with Places Autocomplete Search & Current Location
    if (showFullScreenDialog) {
        FullScreenMapPickerDialog(
            initialLat = currentLat,
            initialLng = currentLng,
            cityName = cityName,
            onDismiss = { showFullScreenDialog = false },
            onConfirmLocation = { lat, lng, addr ->
                currentLat = lat
                currentLng = lng
                currentAddress = addr
                showFullScreenDialog = false
                onLocationSelected(lat, lng, addr)
            }
        )
    }
}

/**
 * Full-screen modal map dialog with search, live camera tracking, pin drop, and reverse-geocoding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenMapPickerDialog(
    initialLat: Double,
    initialLng: Double,
    cityName: String? = null,
    onDismiss: () -> Unit,
    onConfirmLocation: (lat: Double, lng: Double, address: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedLat by remember { mutableStateOf(initialLat) }
    var selectedLng by remember { mutableStateOf(initialLng) }
    var detectedAddress by remember { mutableStateOf("") }
    var isGeocoding by remember { mutableStateOf(false) }

    // Search query & results
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PlaceSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var showSearchResults by remember { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(initialLat, initialLng), 16f)
    }

    // Reverse geocode whenever pin coordinate changes
    fun updateCoordinate(lat: Double, lng: Double) {
        selectedLat = lat
        selectedLng = lng
        coroutineScope.launch {
            isGeocoding = true
            val address = LocationHelper.reverseGeocode(context, lat, lng)
            detectedAddress = address
            isGeocoding = false
        }
    }

    // Initial reverse geocode on launch
    LaunchedEffect(Unit) {
        updateCoordinate(initialLat, initialLng)
    }

    // Update coordinates when user stops dragging the map (center pin mode)
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            val target = cameraPositionState.position.target
            // Only update if moved significantly to prevent re-triggering loops
            if (Math.abs(target.latitude - selectedLat) > 0.00005 ||
                Math.abs(target.longitude - selectedLng) > 0.00005) {
                updateCoordinate(target.latitude, target.longitude)
            }
        }
    }

    // Permission launcher for device location
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            coroutineScope.launch {
                isGeocoding = true
                val loc = LocationHelper.getCurrentLocation(context)
                if (loc != null) {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(loc, 17f))
                    updateCoordinate(loc.latitude, loc.longitude)
                }
                isGeocoding = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Set Store Location", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(
                                text = "Drag map or search to position pin",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate500,
                                fontSize = 12.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                onConfirmLocation(selectedLat, selectedLng, detectedAddress)
                            }
                        ) {
                            Text("Confirm", fontWeight = FontWeight.Bold, color = SprucePrimary, fontSize = 15.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PolishSurfaceLight)
                )
            },
            bottomBar = {
                // Bottom address & confirmation sheet
                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = PolishSurfaceLight,
                    border = BorderStroke(1.dp, PolishBorderLight),
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(PolishRoseLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = PolishRose,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Selected Store Location",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                                if (isGeocoding) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 2.dp,
                                            color = SprucePrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Finding address...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Slate500
                                        )
                                    }
                                } else {
                                    Text(
                                        text = detectedAddress.ifBlank { "Coordinates: ${String.format(Locale.US, "%.5f", selectedLat)}, ${String.format(Locale.US, "%.5f", selectedLng)}" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Slate600,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Coordinates pill
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Slate100,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Coordinates: ${String.format(Locale.US, "%.5f", selectedLat)}, ${String.format(Locale.US, "%.5f", selectedLng)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate700,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        Button(
                            onClick = {
                                onConfirmLocation(selectedLat, selectedLng, detectedAddress)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SprucePrimary)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Use This Pin Location", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val uiSettings = remember {
                    MapUiSettings(
                        zoomControlsEnabled = false,
                        compassEnabled = true,
                        myLocationButtonEnabled = false,
                        mapToolbarEnabled = false
                    )
                }
                val mapProperties = remember {
                    MapProperties(
                        isMyLocationEnabled = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                // Google Map View
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = uiSettings,
                    properties = mapProperties,
                    onMapClick = { latLng ->
                        coroutineScope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.newLatLng(latLng))
                            updateCoordinate(latLng.latitude, latLng.longitude)
                        }
                    }
                )

                // Fixed Center Pin Overlay (Standard delivery/taxi app interaction pattern)
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 36.dp) // Offset so the tip of the pin aligns exactly with center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Slate900.copy(alpha = 0.9f),
                            shadowElevation = 4.dp
                        ) {
                            Text(
                                text = "Store Pin",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = "Pin Location",
                            tint = PolishRose,
                            modifier = Modifier
                                .size(44.dp)
                                .shadow(0.dp)
                        )
                    }
                }

                // Top Search Bar (Places Autocomplete)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, PolishBorderLight),
                        shadowElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Slate400
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            TextField(
                                value = searchQuery,
                                onValueChange = { q ->
                                    searchQuery = q
                                    showSearchResults = true
                                    searchJob?.cancel()
                                    searchJob = coroutineScope.launch {
                                        delay(350)
                                        if (q.trim().length >= 2) {
                                            isSearching = true
                                            val queryWithCity = if (cityName != null && !q.contains(cityName, ignoreCase = true)) {
                                                "$q, $cityName"
                                            } else {
                                                q
                                            }
                                            searchResults = LocationHelper.searchPlaces(context, queryWithCity)
                                            isSearching = false
                                        } else {
                                            searchResults = emptyList()
                                        }
                                    }
                                },
                                placeholder = { Text("Search address, area or landmark...", fontSize = 14.sp, color = Slate400) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )

                            if (searchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        searchQuery = ""
                                        searchResults = emptyList()
                                        showSearchResults = false
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Slate400, modifier = Modifier.size(16.dp))
                                }
                            }

                            if (isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = SprucePrimary
                                )
                            }
                        }
                    }

                    // Autocomplete Results Dropdown
                    AnimatedVisibility(
                        visible = showSearchResults && searchResults.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, PolishBorderLight),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .heightIn(max = 240.dp)
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(searchResults) { result ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                coroutineScope.launch {
                                                    val coords = LocationHelper.resolvePlaceCoordinates(result)
                                                    if (coords != null) {
                                                        cameraPositionState.animate(
                                                            CameraUpdateFactory.newLatLngZoom(coords, 17f)
                                                        )
                                                        updateCoordinate(coords.latitude, coords.longitude)
                                                    }
                                                    searchQuery = result.primaryText
                                                    showSearchResults = false
                                                }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Place,
                                            contentDescription = null,
                                            tint = SprucePrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = result.primaryText,
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Slate900
                                            )
                                            if (result.secondaryText.isNotBlank()) {
                                                Text(
                                                    text = result.secondaryText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Slate500,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = PolishBorderLight, thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }

                // Floating Action Buttons (Current Location & Zoom Controls)
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp, top = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // "Locate Me" Button
                    FloatingActionButton(
                        onClick = {
                            val fineGranted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            val coarseGranted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED

                            if (fineGranted || coarseGranted) {
                                coroutineScope.launch {
                                    isGeocoding = true
                                    val loc = LocationHelper.getCurrentLocation(context)
                                    if (loc != null) {
                                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(loc, 17f))
                                        updateCoordinate(loc.latitude, loc.longitude)
                                        Toast.makeText(context, "Centered on current location: ${String.format(Locale.US, "%.4f, %.4f", loc.latitude, loc.longitude)}", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Could not acquire current GPS location. Please check device location settings.", Toast.LENGTH_LONG).show()
                                    }
                                    isGeocoding = false
                                }
                            } else {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                        containerColor = Color.White,
                        contentColor = SprucePrimary,
                        shape = CircleShape,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Center on My Location")
                    }

                    // Zoom In
                    SmallFloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                cameraPositionState.animate(CameraUpdateFactory.zoomIn())
                            }
                        },
                        containerColor = Color.White,
                        contentColor = Slate700,
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In")
                    }

                    // Zoom Out
                    SmallFloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                cameraPositionState.animate(CameraUpdateFactory.zoomOut())
                            }
                        },
                        containerColor = Color.White,
                        contentColor = Slate700,
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                    }
                }
            }
        }
    }
}
