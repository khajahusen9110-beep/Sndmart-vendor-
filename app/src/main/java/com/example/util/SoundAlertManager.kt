package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SoundAlertManager(private val context: Context) {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private var toneGenerator: ToneGenerator? = null
    private var activeMediaPlayer: MediaPlayer? = null
    private var repeatingAlertJob: Job? = null
    private var alertRepeatCount = 0
    private val maxRepeatCount = 5

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        } catch (e: Exception) {
            Log.w("SoundAlertManager", "Could not initialize ToneGenerator: ${e.message}")
        }
    }

    /**
     * Play the 10-second order alert ringtone sound and vibration.
     */
    fun playNewOrderSound(vibrate: Boolean = true) {
        try {
            stopAlertSound()
            val mp = MediaPlayer()
            val afd = context.resources.openRawResourceFd(R.raw.order_alert_sound)
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.isLooping = false
            mp.setOnCompletionListener {
                it.release()
                if (activeMediaPlayer == it) activeMediaPlayer = null
            }
            mp.prepare()
            mp.start()
            activeMediaPlayer = mp

            if (vibrate) {
                triggerVibration()
            }
        } catch (e: Exception) {
            Log.e("SoundAlertManager", "Error playing 10s sound alert, falling back to tone", e)
            playFallbackTone(vibrate)
        }
    }

    private fun playFallbackTone(vibrate: Boolean) {
        try {
            if (toneGenerator != null) {
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 600)
            } else {
                playSystemNotificationSound()
            }
            if (vibrate) triggerVibration()
        } catch (e: Exception) {
            Log.e("SoundAlertManager", "Fallback tone failed", e)
        }
    }

    /**
     * Stop active ringing alert immediately.
     */
    fun stopAlertSound() {
        try {
            activeMediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            // Ignore
        }
        activeMediaPlayer = null
    }

    /**
     * Start repeating alert for pending orders (every 18 seconds, up to 5 times)
     */
    fun startRepeatingOrderAlert(
        scope: CoroutineScope,
        isSoundEnabled: () -> Boolean,
        hasPendingOrders: () -> Boolean
    ) {
        stopRepeatingAlert()
        alertRepeatCount = 0

        repeatingAlertJob = scope.launch(Dispatchers.Default) {
            while (alertRepeatCount < maxRepeatCount) {
                if (!hasPendingOrders()) {
                    Log.d("SoundAlertManager", "No pending orders remaining. Stopping alert loop.")
                    break
                }

                if (isSoundEnabled()) {
                    playNewOrderSound(vibrate = true)
                }

                alertRepeatCount++
                if (alertRepeatCount >= maxRepeatCount) {
                    Log.d("SoundAlertManager", "Reached max repeat cap of $maxRepeatCount for order alert.")
                    break
                }

                delay(18000) // 18 seconds interval
            }
        }
    }

    /**
     * Stop repeating alert when vendor accepts/rejects order or views order list.
     */
    fun stopRepeatingAlert() {
        stopAlertSound()
        repeatingAlertJob?.cancel()
        repeatingAlertJob = null
        alertRepeatCount = 0
    }

    private fun playSystemNotificationSound() {
        try {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, notificationUri)
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone?.play()
        } catch (e: Exception) {
            Log.w("SoundAlertManager", "Could not play system ringtone: ${e.message}")
        }
    }

    private fun triggerVibration() {
        try {
            val vib = vibrator ?: return
            if (!vib.hasVibrator()) return

            val pattern = longArrayOf(0, 200, 100, 200)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(pattern, -1)
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Log.w("SoundAlertManager", "Could not trigger vibration: ${e.message}")
        }
    }

    fun release() {
        stopRepeatingAlert()
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            // Ignore
        }
    }
}
