package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.util.NotificationHelper

class OrderAlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var stopRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP_ALARM) {
            Log.d(TAG, "OrderAlarmService received STOP_ALARM action")
            stopAlarmInternal()
            stopSelf()
            return START_NOT_STICKY
        }

        val orderId = intent?.getStringExtra(EXTRA_ORDER_ID) ?: "order_${System.currentTimeMillis()}"
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "New Order Received"
        val body = intent?.getStringExtra(EXTRA_BODY) ?: "Open app immediately to prepare order"

        Log.d(TAG, "OrderAlarmService starting foreground alarm for order: $orderId")

        // 1. Ensure notification channels are registered
        NotificationHelper.createNotificationChannels(this)

        // 2. Build high priority notification
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("order_id", orderId)
            putExtra("NAVIGATE_TO", "orders")
            putExtra("SELECTED_ORDER_ID", orderId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            orderId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, OrderAlarmService::class.java).apply {
            this.action = ACTION_STOP_ALARM
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            orderId.hashCode() + 1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID_NEW_ORDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openAppPendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .addAction(R.drawable.ic_notification, "Open Order", openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Sound", dismissPendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .setSound(null) // Sound played by MediaPlayer on ALARM stream
            .build()

        // 3. Promote to Foreground Service
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}", e)
        }

        // 4. Acquire Partial WakeLock for up to 12 seconds so phone CPU doesn't sleep while locked
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VendorApp:OrderAlarmWakeLock")?.apply {
                setReferenceCounted(false)
                acquire(12_000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice acquiring wake lock: ${e.message}")
        }

        // 5. Play 10-second alert sound on ALARM audio stream
        playAlertSound()

        // 6. Hard safety timeout after 12 seconds (replace any timer left from a previous order)
        stopRunnable?.let { handler.removeCallbacks(it) }
        stopRunnable = Runnable {
            Log.d(TAG, "12-second timeout reached, stopping alarm service")
            stopAlarmInternal()
            stopSelf()
        }
        handler.postDelayed(stopRunnable!!, 12_000L)

        return START_NOT_STICKY
    }

    private fun playAlertSound() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                val afd = resources.openRawResourceFd(R.raw.order_alert_sound)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = false
                setOnCompletionListener {
                    Log.d(TAG, "MediaPlayer alert sound completed")
                    stopAlarmInternal()
                    stopSelf()
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPlayer for order alert", e)
            stopAlarmInternal()
            stopSelf()
        }
    }

    private fun stopAlarmInternal() {
        try {
            stopRunnable?.let { handler.removeCallbacks(it) }
            stopRunnable = null

            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null

            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping alarm: ${e.message}")
        }
    }

    override fun onDestroy() {
        stopAlarmInternal()
        isRunning = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "OrderAlarmService"
        const val NOTIFICATION_ID = 9001
        const val ACTION_START_ALARM = "com.example.action.START_ORDER_ALARM"
        const val ACTION_STOP_ALARM = "com.example.action.STOP_ORDER_ALARM"
        const val EXTRA_ORDER_ID = "extra_order_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_BODY = "extra_body"

        @Volatile
        private var isRunning = false

        fun startAlarm(context: Context, orderId: String, title: String, body: String) {
            val intent = Intent(context, OrderAlarmService::class.java).apply {
                action = ACTION_START_ALARM
                putExtra(EXTRA_ORDER_ID, orderId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_BODY, body)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground alarm service, using notification fallback", e)
                NotificationHelper.showNewOrderNotification(context, orderId, title, body, playSound = true)
            }
        }

        fun stopAlarm(context: Context) {
            if (!isRunning) return
            try {
                val intent = Intent(context, OrderAlarmService::class.java).apply {
                    action = ACTION_STOP_ALARM
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Error requesting alarm stop: ${e.message}")
            }
        }
    }
}
