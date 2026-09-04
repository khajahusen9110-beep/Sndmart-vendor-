package com.example

import android.app.Application
import android.util.Log
import com.example.util.NotificationHelper
import com.google.firebase.FirebaseApp

class VendorApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize notification channels at application startup
        NotificationHelper.createNotificationChannels(this)

        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            Log.d("VendorApplication", "FirebaseApp initialized successfully")
        } catch (e: Exception) {
            Log.e("VendorApplication", "Firebase initialization notice: ${e.message}", e)
        }
    }
}
