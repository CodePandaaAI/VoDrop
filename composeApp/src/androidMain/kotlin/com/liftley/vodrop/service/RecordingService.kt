package com.liftley.vodrop.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.liftley.vodrop.MainActivity
import com.liftley.vodrop.data.audio.AudioRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.android.ext.android.inject

/**
 * Foreground service for background audio recording and notification management.
 */
class RecordingService : Service() {

    companion object {
        private const val TAG = "RecordingService"
        const val CHANNEL_ID = "vodrop_recording_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.liftley.vodrop.START_RECORDING"
        const val ACTION_STOP = "com.liftley.vodrop.STOP_RECORDING"
        const val ACTION_TRANSCRIPTION_START = "com.liftley.vodrop.TRANSCRIPTION_START"
        const val ACTION_POLISHING_START = "com.liftley.vodrop.POLISHING_START"
        const val ACTION_RESULT_READY = "com.liftley.vodrop.RESULT_READY"
        const val ACTION_COPY_RESULT = "com.liftley.vodrop.COPY_RESULT"

        const val EXTRA_RESULT_TEXT = "extra_result_text"
    }

    private val audioRecorder: AudioRecorder by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var recordingStartTime: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand - action: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                recordingStartTime = System.currentTimeMillis()
                startForegroundWithNotification(createRecordingNotification())
            }
            ACTION_STOP -> {
                // 1. Update notification immediately to provide feedback
                updateNotification(createProcessingNotification("Stopping..."))
                
                // 2. Request the app/ViewModel to actually stop recording
                audioRecorder.requestStopFromNotification()
            }
            ACTION_TRANSCRIPTION_START -> {
                updateNotification(createProcessingNotification("Processing..."))
            }
            ACTION_POLISHING_START -> {
                updateNotification(createProcessingNotification("AI Polishing..."))
            }
            ACTION_RESULT_READY -> {
                val text = intent.getStringExtra(EXTRA_RESULT_TEXT) ?: "Transcription ready"
                updateNotification(createResultNotification(text))
                // Service stays alive until user actions
            }
            ACTION_COPY_RESULT -> {
                val text = intent.getStringExtra(EXTRA_RESULT_TEXT) ?: ""
                copyToClipboard(text)

                // RESET to "Ready" state (Idle notification)
                updateNotification(createIdleNotification())
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent?.action}")
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun startForegroundWithNotification(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            stopSelf()
        }
    }

    private fun updateNotification(notification: Notification) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VoDrop Recording",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows recording status and controls"
            setShowBadge(true)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    // --- Notification Builders ---

    private fun createRecordingNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, RecordingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val openAppIntent = createOpenAppPendingIntent()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Recording...")
            .setContentText("Tap Stop to finish")
            .setUsesChronometer(true)
            .setWhen(recordingStartTime)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createProcessingNotification(message: String): Notification {
        val openAppIntent = createOpenAppPendingIntent()
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("VoDrop")
            .setContentText(message)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createResultNotification(text: String): Notification {
        val openAppIntent = createOpenAppPendingIntent()
        
        // Truncate text for display
        val displayMessage = if (text.length > 50) text.take(50) + "..." else text

        val copyIntent = PendingIntent.getService(
            this, 2,
            Intent(this, RecordingService::class.java).apply { 
                action = ACTION_COPY_RESULT
                putExtra(EXTRA_RESULT_TEXT, text)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("Transcription Ready")
            .setContentText(displayMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_btn_speak_now, "Copy", copyIntent)
            .setOngoing(true) // Keep it sticking around until copied
            .setAutoCancel(false) // Don't dismiss on click
            .setPriority(NotificationCompat.PRIORITY_HIGH) 
            .build()
    }

    private fun createIdleNotification(): Notification {
        val startIntent = PendingIntent.getService(
            this, 3,
            Intent(this, RecordingService::class.java).apply { action = ACTION_START },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openAppIntent = createOpenAppPendingIntent()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("VoDrop Ready")
            .setContentText("Tap Start to record")
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_media_play, "Start", startIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createOpenAppPendingIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("VoDrop Transcription", text)
        clipboard.setPrimaryClip(clip)
    }
}