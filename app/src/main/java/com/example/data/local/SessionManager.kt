package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.Vendor
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sndmart_vendor_prefs", Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val vendorAdapter = moshi.adapter(Vendor::class.java)

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_VENDOR_DATA = "vendor_data"
        private const val KEY_CUSTOM_ANON_KEY = "custom_anon_key"
        private const val KEY_FCM_TOKEN = "fcm_token"
        private const val KEY_SOUND_ALERT_ENABLED = "sound_alert_enabled"
    }

    var isSoundAlertEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ALERT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ALERT_ENABLED, value).apply()

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    var customAnonKey: String?
        get() = prefs.getString(KEY_CUSTOM_ANON_KEY, null)
        set(value) = prefs.edit().putString(KEY_CUSTOM_ANON_KEY, value).apply()

    var fcmToken: String?
        get() = prefs.getString(KEY_FCM_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_FCM_TOKEN, value).apply()

    var cachedVendor: Vendor?
        get() {
            val json = prefs.getString(KEY_VENDOR_DATA, null) ?: return null
            return try {
                vendorAdapter.fromJson(json)
            } catch (e: Exception) {
                null
            }
        }
        set(value) {
            if (value != null) {
                val json = vendorAdapter.toJson(value)
                prefs.edit().putString(KEY_VENDOR_DATA, json).apply()
            } else {
                prefs.edit().remove(KEY_VENDOR_DATA).apply()
            }
        }

    val isLoggedIn: Boolean
        get() = !accessToken.isNullOrEmpty() && !userId.isNullOrEmpty()

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_VENDOR_DATA)
            .apply()
    }
}
