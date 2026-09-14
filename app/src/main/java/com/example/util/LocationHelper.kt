package com.example.util

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume

data class PlaceSearchResult(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String,
    val latLng: LatLng? = null
)

object LocationHelper {
    private const val TAG = "LocationHelper"
    private val httpClient = OkHttpClient()

    /**
     * Get the device's current location if permissions are granted.
     */
    suspend fun getCurrentLocation(context: Context): LatLng? = withContext(Dispatchers.IO) {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            suspendCancellableCoroutine { continuation ->
                try {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { location ->
                            if (location != null) {
                                val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    location.isMock
                                } else {
                                    @Suppress("DEPRECATION")
                                    location.isFromMockProvider
                                }
                                Log.i(TAG, "Location obtained: lat=${location.latitude}, lng=${location.longitude}, accuracy=${location.accuracy}m, isMock=$isMock, provider=${location.provider}")
                                continuation.resume(LatLng(location.latitude, location.longitude))
                            } else {
                                Log.w(TAG, "getCurrentLocation returned null, attempting fallback to lastLocation")
                                fusedLocationClient.lastLocation
                                    .addOnSuccessListener { lastLoc ->
                                        if (lastLoc != null) {
                                            val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                lastLoc.isMock
                                            } else {
                                                @Suppress("DEPRECATION")
                                                lastLoc.isFromMockProvider
                                            }
                                            Log.i(TAG, "Fallback lastLocation: lat=${lastLoc.latitude}, lng=${lastLoc.longitude}, isMock=$isMock")
                                            continuation.resume(LatLng(lastLoc.latitude, lastLoc.longitude))
                                        } else {
                                            Log.w(TAG, "lastLocation is also null")
                                            continuation.resume(null)
                                        }
                                    }
                                    .addOnFailureListener { ex ->
                                        Log.w(TAG, "lastLocation failed: ${ex.message}")
                                        continuation.resume(null)
                                    }
                            }
                        }
                        .addOnFailureListener { ex ->
                            Log.w(TAG, "getCurrentLocation failed: ${ex.message}")
                            continuation.resume(null)
                        }
                } catch (e: SecurityException) {
                    Log.w(TAG, "Location permission not granted: ${e.message}")
                    continuation.resume(null)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get current location: ${e.message}")
                    continuation.resume(null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getCurrentLocation", e)
            null
        }
    }

    /**
     * Reverse-geocode latitude and longitude into a readable address string.
     * Tries Android native Geocoder first, and falls back to Google Geocoding API if key is present.
     */
    suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        // 1. Try Android Geocoder
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val addressStr = suspendCancellableCoroutine<String?> { cont ->
                        geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                val addr = addresses.firstOrNull()?.getAddressLine(0)
                                cont.resume(addr)
                            }
                            override fun onError(errorMessage: String?) {
                                cont.resume(null)
                            }
                        })
                    }
                    if (!addressStr.isNullOrBlank()) return@withContext addressStr
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(lat, lng, 1)
                    val addr = addresses?.firstOrNull()?.getAddressLine(0)
                    if (!addr.isNullOrBlank()) return@withContext addr
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Android Geocoder failed: ${e.message}")
        }

        // 2. Fallback to Google Geocoding API if API key is provided
        val apiKey = BuildConfig.GOOGLE_MAPS_API_KEY
        if (apiKey.isNotBlank() && apiKey != "YOUR_GOOGLE_MAPS_API_KEY") {
            try {
                val url = "https://maps.googleapis.com/maps/api/geocode/json?latlng=$lat,$lng&key=$apiKey"
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            val json = JSONObject(body)
                            val results = json.optJSONArray("results")
                            if (results != null && results.length() > 0) {
                                val formatted = results.getJSONObject(0).optString("formatted_address")
                                if (formatted.isNotBlank()) return@withContext formatted
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Google Geocoding API failed: ${e.message}")
            }
        }

        "Lat: ${String.format(Locale.US, "%.5f", lat)}, Lng: ${String.format(Locale.US, "%.5f", lng)}"
    }

    /**
     * Search for places / addresses via Google Places Autocomplete or Geocoder fallback.
     */
    suspend fun searchPlaces(context: Context, query: String): List<PlaceSearchResult> = withContext(Dispatchers.IO) {
        if (query.trim().length < 2) return@withContext emptyList()

        val results = mutableListOf<PlaceSearchResult>()
        val apiKey = BuildConfig.GOOGLE_MAPS_API_KEY

        // 1. If Google Maps API key is configured, query Places Autocomplete API
        if (apiKey.isNotBlank() && apiKey != "YOUR_GOOGLE_MAPS_API_KEY") {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "https://maps.googleapis.com/maps/api/place/autocomplete/json?input=$encodedQuery&key=$apiKey"
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            val json = JSONObject(body)
                            val predictions = json.optJSONArray("predictions")
                            if (predictions != null) {
                                for (i in 0 until predictions.length()) {
                                    val item = predictions.getJSONObject(i)
                                    val placeId = item.optString("place_id")
                                    val desc = item.optString("description")
                                    val formatting = item.optJSONObject("structured_formatting")
                                    val mainText = formatting?.optString("main_text") ?: desc
                                    val secondaryText = formatting?.optString("secondary_text") ?: ""
                                    results.add(
                                        PlaceSearchResult(
                                            placeId = placeId,
                                            primaryText = mainText,
                                            secondaryText = secondaryText
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Places Autocomplete API error: ${e.message}")
            }
        }

        // 2. Also query Android native Geocoder for address name matches as fallback/complement
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addressMatches = geocoder.getFromLocationName(query, 5)
                addressMatches?.forEach { addr ->
                    val line = addr.getAddressLine(0) ?: "${addr.featureName ?: ""}, ${addr.locality ?: ""}"
                    val exists = results.any { it.primaryText.contains(addr.featureName ?: "___", ignoreCase = true) }
                    if (!exists && line.isNotBlank()) {
                        results.add(
                            PlaceSearchResult(
                                placeId = "geo_${addr.latitude}_${addr.longitude}",
                                primaryText = addr.featureName ?: line,
                                secondaryText = line,
                                latLng = LatLng(addr.latitude, addr.longitude)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Geocoder search error: ${e.message}")
        }

        results
    }

    /**
     * Resolve a placeId to LatLng (using Place Details API if needed).
     */
    suspend fun resolvePlaceCoordinates(place: PlaceSearchResult): LatLng? = withContext(Dispatchers.IO) {
        if (place.latLng != null) return@withContext place.latLng

        val apiKey = BuildConfig.GOOGLE_MAPS_API_KEY
        if (apiKey.isBlank() || apiKey == "YOUR_GOOGLE_MAPS_API_KEY") return@withContext null

        try {
            val url = "https://maps.googleapis.com/maps/api/place/details/json?place_id=${place.placeId}&fields=geometry&key=$apiKey"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val json = JSONObject(body)
                        val locationObj = json.optJSONObject("result")
                            ?.optJSONObject("geometry")
                            ?.optJSONObject("location")
                        if (locationObj != null) {
                            val lat = locationObj.optDouble("lat")
                            val lng = locationObj.optDouble("lng")
                            return@withContext LatLng(lat, lng)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve place coordinates: ${e.message}")
        }

        null
    }
}
