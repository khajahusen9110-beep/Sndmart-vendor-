package com.example

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.util.NotificationHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

class VendorApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50 MB
                    .build()
            }
            .respectCacheHeaders(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        // Crash reports from real phones (not from debug builds); the report carries no customer data
        try { com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG) } catch (e: Exception) { Log.w("VendorApp", "Crashlytics not started: ${e.message}") }
        // Initialize notification channels at application startup
        NotificationHelper.createNotificationChannels(this)

        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            try {
                FirebaseMessaging.getInstance().isAutoInitEnabled = true
            } catch (t: Throwable) {
                Log.w("VendorApplication", "Notice configuring FirebaseMessaging autoInit: ${t.message}")
            }
            Log.d("VendorApplication", "FirebaseApp initialized successfully (auto-init enabled)")
        } catch (e: Exception) {
            Log.w("VendorApplication", "Firebase initialization notice: ${e.message}")
        }
    }
}
