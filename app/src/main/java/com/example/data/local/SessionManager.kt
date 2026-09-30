package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AppVersionInfo
import com.example.data.model.Vendor
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sndmart_vendor_prefs", Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val vendorAdapter = moshi.adapter(Vendor::class.java)
    private val appVersionAdapter = moshi.adapter(AppVersionInfo::class.java)

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_VENDOR_DATA = "vendor_data"
        private const val KEY_CUSTOM_ANON_KEY = "custom_anon_key"
        private const val KEY_FCM_TOKEN = "fcm_token"
        private const val KEY_SOUND_ALERT_ENABLED = "sound_alert_enabled"
        private const val KEY_SESSION_EXPIRED_MESSAGE = "session_expired_message"
        private const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"
    }

    val sessionExpiredFlow = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)

    var sessionExpiredMessage: String?
        get() = prefs.getString(KEY_SESSION_EXPIRED_MESSAGE, null)
        set(value) {
            if (value != null) prefs.edit().putString(KEY_SESSION_EXPIRED_MESSAGE, value).apply()
            else prefs.edit().remove(KEY_SESSION_EXPIRED_MESSAGE).apply()
        }

    var tokenExpiresAt: Long
        get() = prefs.getLong(KEY_TOKEN_EXPIRES_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_TOKEN_EXPIRES_AT, value).apply()

    val hasValidRefreshToken: Boolean
        get() = !refreshToken.isNullOrBlank()

    fun isTokenExpiredOrExpiringSoon(thresholdSeconds: Long = 180): Boolean {
        if (accessToken.isNullOrBlank()) return true
        val expiry = tokenExpiresAt
        if (expiry <= 0L) {
            // Expiration timestamp not recorded — if we have a refresh token, proactively refresh
            return hasValidRefreshToken
        }
        val nowSeconds = System.currentTimeMillis() / 1000
        return nowSeconds >= (expiry - thresholdSeconds)
    }

    fun updateTokens(newAccessToken: String, newRefreshToken: String? = null, expiresInSeconds: Long? = null) {
        accessToken = newAccessToken
        if (!newRefreshToken.isNullOrBlank()) {
            refreshToken = newRefreshToken
        }
        if (expiresInSeconds != null && expiresInSeconds > 0) {
            tokenExpiresAt = (System.currentTimeMillis() / 1000) + expiresInSeconds
        } else {
            // Default Supabase token expiry is 1 hour (3600 seconds)
            tokenExpiresAt = (System.currentTimeMillis() / 1000) + 3600
        }
    }

    fun notifySessionExpired(message: String = "Your session expired, please log in again") {
        sessionExpiredMessage = message
        sessionExpiredFlow.tryEmit(message)
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

    var hasSeenAutostartTip: Boolean
        get() = prefs.getBoolean("has_seen_autostart_tip", false)
        set(value) = prefs.edit().putBoolean("has_seen_autostart_tip", value).apply()

    var lastVersionCheckTimestamp: Long
        get() = prefs.getLong("last_version_check_time", 0L)
        set(value) = prefs.edit().putLong("last_version_check_time", value).apply()

    var lastDismissedOptionalVersionCode: Int
        get() = prefs.getInt("last_dismissed_opt_version_code", -1)
        set(value) = prefs.edit().putInt("last_dismissed_opt_version_code", value).apply()

    var lastDismissedOptionalVersionName: String?
        get() = prefs.getString("last_dismissed_opt_version_name", null)
        set(value) = prefs.edit().putString("last_dismissed_opt_version_name", value).apply()

    fun setOptionalUpdateDismissed(code: Int?, name: String?) {
        val editor = prefs.edit()
        if (code != null) editor.putInt("last_dismissed_opt_version_code", code)
        if (name != null) editor.putString("last_dismissed_opt_version_name", name)
        editor.apply()
    }

    var cachedForceUpdateInfo: AppVersionInfo?
        get() {
            val json = prefs.getString("cached_force_update_info", null) ?: return null
            return try {
                appVersionAdapter.fromJson(json)
            } catch (_: Exception) {
                null
            }
        }
        set(value) {
            if (value != null) {
                try {
                    val json = appVersionAdapter.toJson(value)
                    prefs.edit().putString("cached_force_update_info", json).apply()
                } catch (_: Exception) {}
            } else {
                prefs.edit().remove("cached_force_update_info").apply()
            }
        }

    fun clearCachedForceUpdate() {
        cachedForceUpdateInfo = null
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_VENDOR_DATA)
            .remove(KEY_TOKEN_EXPIRES_AT)
            .apply()
    }
}
