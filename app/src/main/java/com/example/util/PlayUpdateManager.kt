package com.example.util

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.example.BuildConfig
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

class PlayUpdateManager(private val context: Context) {
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)
    private var installStateUpdatedListener: InstallStateUpdatedListener? = null

    fun startImmediateUpdate(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>? = null,
        onFallback: () -> Unit
    ) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                try {
                    if (launcher != null) {
                        appUpdateManager.startUpdateFlowForResult(
                            info,
                            launcher,
                            AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE)
                        )
                    } else {
                        appUpdateManager.startUpdateFlowForResult(
                            info,
                            activity,
                            AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE),
                            REQUEST_CODE_IMMEDIATE_UPDATE
                        )
                    }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Failed to start immediate update flow: ${e.message}")
                    onFallback()
                }
            } else {
                if (BuildConfig.DEBUG) Log.d(TAG, "Immediate update not available on Play, falling back to store link")
                onFallback()
            }
        }.addOnFailureListener { e ->
            if (BuildConfig.DEBUG) Log.w(TAG, "appUpdateInfo failed: ${e.message}")
            onFallback()
        }
    }

    fun startFlexibleUpdate(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>? = null,
        onDownloaded: () -> Unit,
        onFallback: () -> Unit
    ) {
        registerInstallListener(onDownloaded)
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                try {
                    if (launcher != null) {
                        appUpdateManager.startUpdateFlowForResult(
                            info,
                            launcher,
                            AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE)
                        )
                    } else {
                        appUpdateManager.startUpdateFlowForResult(
                            info,
                            activity,
                            AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE),
                            REQUEST_CODE_FLEXIBLE_UPDATE
                        )
                    }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Failed to start flexible update flow: ${e.message}")
                    onFallback()
                }
            } else {
                if (BuildConfig.DEBUG) Log.d(TAG, "Flexible update not available on Play, falling back to store link")
                onFallback()
            }
        }.addOnFailureListener { e ->
            if (BuildConfig.DEBUG) Log.w(TAG, "appUpdateInfo failed for flexible: ${e.message}")
            onFallback()
        }
    }

    fun resumeImmediateUpdate(activity: Activity) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                try {
                    appUpdateManager.startUpdateFlowForResult(
                        info,
                        activity,
                        AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE),
                        REQUEST_CODE_IMMEDIATE_UPDATE
                    )
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "Error resuming immediate update: ${e.message}")
                }
            } else if (info.installStatus() == InstallStatus.DOWNLOADED) {
                // If an update was previously downloaded, complete it or inform listener
            }
        }
    }

    fun registerInstallListener(onDownloaded: () -> Unit) {
        if (installStateUpdatedListener == null) {
            val listener = InstallStateUpdatedListener { state ->
                if (state.installStatus() == InstallStatus.DOWNLOADED) {
                    onDownloaded()
                }
            }
            installStateUpdatedListener = listener
            appUpdateManager.registerListener(listener)
        }
    }

    fun completeUpdate() {
        try {
            appUpdateManager.completeUpdate()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to complete update: ${e.message}")
        }
    }

    fun onDestroy() {
        installStateUpdatedListener?.let {
            appUpdateManager.unregisterListener(it)
            installStateUpdatedListener = null
        }
    }

    companion object {
        private const val TAG = "PlayUpdateManager"
        const val REQUEST_CODE_IMMEDIATE_UPDATE = 8001
        const val REQUEST_CODE_FLEXIBLE_UPDATE = 8002
    }
}
