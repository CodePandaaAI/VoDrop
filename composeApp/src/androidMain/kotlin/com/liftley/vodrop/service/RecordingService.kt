package com.liftley.vodrop.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.liftley.vodrop.MainActivity
import com.liftley.vodrop.domain.manager.RecordingSessionManager
import com.liftley.vodrop.domain.model.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Foreground service - PURE OBSERVER of RecordingSessionManager state.
 * 
 * This service:
 * - Shows notifications based on AppState
 * - DOES NOT command SessionManager (that's BroadcastReceiver's job)
 * - Only purpose is to keep the app alive in background
 */
class RecordingService : Service() {

    companion object {
        private const val TAG = "RecordingService"
        const val CHANNEL_ID = "vodrop_recording_channel"
        const val RESULT_CHANNEL_ID = "vodrop_result_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.liftley.vodrop.START_SERVICE"
    }

    private val sessionManager: RecordingSessionManager by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var recordingStartTime: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        createNotificationChannel()
        observeState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand - action: ${intent?.action}")
        
        recordingStartTime = System.currentTimeMillis()
        
        // Always start foreground with current state notification
        startForegroundWithNotification(createNotificationFor(sessionManager.state.value))
        
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    /**
     * Observe state and update notification accordingly.
     * This is the ONLY job of this service.
     */
    private fun observeState() {
        scope.launch {
            sessionManager.state.collect { state ->
                Log.d(TAG, "State changed: $state")
                updateNotification(createNotificationFor(state))
            }
        }
    }

    private fun createNotificationFor(state: AppState): Notification {
        return when (state) {
            is AppState.Recording -> createRecordingNotification()
            is AppState.Processing -> createProcessingNotification(state.message)
            is AppState.Success -> createResultNotification(state.text)
            is AppState.Error -> createErrorNotification(state.message)
            is AppState.Ready -> createIdleNotification()
        }
    }

    // --- Helpers ---

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
        val manager = getSystemService<NotificationManager>()
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        // Silent channel for recording/processing
        val silentChannel = NotificationChannel(
            CHANNEL_ID,
            "VoDrop Recording",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows recording status (silent)"
            setShowBadge(false)
            setSound(null, null)
        }
        
        // Sound channel for results
        val resultChannel = NotificationChannel(
            RESULT_CHANNEL_ID,
            "VoDrop Results",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifies when transcription is ready"
            setShowBadge(true)
        }
        
        val manager = getSystemService<NotificationManager>()
        manager?.createNotificationChannels(listOf(silentChannel, resultChannel))
    }

    private fun createRecordingNotification(): Notification {
        // Stop action via BroadcastReceiver
        val stopIntent = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, RecordingCommandReceiver::class.java).apply { 
                action = RecordingCommandReceiver.ACTION_STOP 
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel action via BroadcastReceiver
        val cancelIntent = PendingIntent.getBroadcast(
            this, 2,
            Intent(this, RecordingCommandReceiver::class.java).apply { 
                action = RecordingCommandReceiver.ACTION_CANCEL 
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, RESULT_CHANNEL_ID)  // Pop up with sound
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Recording...")
            .setContentText("Tap Stop to finish, Cancel to discard")
            .setUsesChronometer(true)
            .setWhen(recordingStartTime)
            .setOngoing(true)
            .setContentIntent(createOpenAppPendingIntent())
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createProcessingNotification(message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("VoDrop")
            .setContentText(message)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)  // No sound for processing
            .setContentIntent(createOpenAppPendingIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createResultNotification(text: String): Notification {
        val displayMessage = if (text.length > 50) text.take(50) + "..." else text

        // Copy action via BroadcastReceiver
        val copyIntent = PendingIntent.getBroadcast(
            this, 3,
            Intent(this, RecordingCommandReceiver::class.java).apply {
                action = RecordingCommandReceiver.ACTION_COPY
                putExtra(RecordingCommandReceiver.EXTRA_TEXT, text)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Start New Recording action via BroadcastReceiver
        val startIntent = PendingIntent.getBroadcast(
            this, 5,
            Intent(this, RecordingCommandReceiver::class.java).apply {
                action = RecordingCommandReceiver.ACTION_START
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, RESULT_CHANNEL_ID)  // Use result channel for sound
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("Transcription Ready")
            .setContentText(displayMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(createOpenAppPendingIntent())
            .addAction(android.R.drawable.ic_menu_edit, "Copy", copyIntent)
            .addAction(android.R.drawable.ic_btn_speak_now, "New Recording", startIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun createIdleNotification(): Notification {
        // Start Recording action via BroadcastReceiver
        val startIntent = PendingIntent.getBroadcast(
            this, 4,
            Intent(this, RecordingCommandReceiver::class.java).apply { 
                action = RecordingCommandReceiver.ACTION_START 
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, RESULT_CHANNEL_ID)  // Pop up with sound
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("VoDrop Ready")
            .setContentText("Tap to start recording from anywhere")
            .setOngoing(true)
            .setContentIntent(createOpenAppPendingIntent())
            .addAction(android.R.drawable.ic_btn_speak_now, "Start Recording", startIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun createErrorNotification(message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)  // Silent channel
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Error")
            .setContentText(message)
            .setSilent(true)
            .setContentIntent(createOpenAppPendingIntent())
            .setAutoCancel(true)
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
}