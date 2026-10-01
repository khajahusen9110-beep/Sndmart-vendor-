package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.local.SessionManager
import com.example.service.OrderAlarmService
import com.google.firebase.messaging.FirebaseMessaging

/** Single-device login: ends the session on this phone when the account is opened on another phone. */
object DeviceSession {
    const val OTHER_DEVICE_MESSAGE =
        "Your account was logged in on another phone, so you were logged out here."

    /**
     * Clears the local session and invalidates this phone's push token so it can no longer
     * receive the vendor's order alerts.
     */
    fun endLocalSession(context: Context, sessionManager: SessionManager, message: String) {
        OrderAlarmService.stopAlarm(context)
        OrderAlertTracker.clear()
        sessionManager.clearSession()
        sessionManager.fcmToken = null
        sessionManager.sessionExpiredMessage = message
        try {
            FirebaseMessaging.getInstance().deleteToken()
        } catch (e: Exception) {
            Log.w("DeviceSession", "Could not delete FCM token: ${e.message}")
        }
    }
}
