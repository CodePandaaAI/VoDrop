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
import androidx.core.content.getSystemService
import com.liftley.vodrop.MainActivity
import com.liftley.vodrop.domain.manager.RecordingSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Foreground service acting as a passive "Notification UI" for the RecordingSessionManager.
 * It observes the central state and updates notifications accordingly.
 * It forwards user intents (STOP) to the manager.
 */
class RecordingService : Service() {

    companion object {
        private const val TAG = "RecordingService"
        const val CHANNEL_ID = "vodrop_recording_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.liftley.vodrop.START_RECORDING"
        const val ACTION_STOP = "com.liftley.vodrop.STOP_RECORDING"
        const val ACTION_COPY_RESULT = "com.liftley.vodrop.COPY_RESULT"

        const val EXTRA_RESULT_TEXT = "extra_result_text"
        const val EXTRA_FROM_RECORDER = "extra_started_by_recorder"
    }

    private val sessionManager: RecordingSessionManager by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var recordingStartTime: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        createNotificationChannel()
        observeSessionState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand - action: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                // Android requirement: Must call startForeground immediately
                recordingStartTime = System.currentTimeMillis()
                startForegroundWithNotification(createRecordingNotification())
                
                // Only trigger logic if NOT started by the Recorder itself (avoid recursion)
                val startedByRecorder = intent.getBooleanExtra(EXTRA_FROM_RECORDER, false)
                if (!startedByRecorder) {
                    sessionManager.startRecording()
                } else {
                    Log.d(TAG, "Service started by AudioRecorder - skipping redundant start call")
                }
            }
            ACTION_STOP -> {
                // Useer tapped Stop on notification
                sessionManager.stopRecording() 
            }
            ACTION_COPY_RESULT -> {
                val text = intent.getStringExtra(EXTRA_RESULT_TEXT) ?: ""
                copyToClipboard(text)

                // Reset Manager to Idle (Ready)
                sessionManager.resetState()
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

    private fun observeSessionState() {
        scope.launch {
            sessionManager.state.collect { state ->
                when (state) {
                    is RecordingSessionManager.SessionState.Idle -> {
                        updateNotification(createIdleNotification())
                    }
                    is RecordingSessionManager.SessionState.Recording -> {
                        // Ensure we stand up functionality if resumed
                    }
                    is RecordingSessionManager.SessionState.Processing -> {
                        updateNotification(createProcessingNotification(state.message))
                    }
                    is RecordingSessionManager.SessionState.Success -> {
                        updateNotification(createResultNotification(state.text))
                    }
                    is RecordingSessionManager.SessionState.Error -> {
                        updateNotification(createErrorNotification(state.message))
                    }
                }
            }
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
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VoDrop Recording",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Shows recording status and controls"
            setShowBadge(true)
        }
        val manager = getSystemService<NotificationManager>()
        manager?.createNotificationChannel(channel)
    }

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
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun createResultNotification(text: String): Notification {
        val openAppIntent = createOpenAppPendingIntent()
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
            .addAction(android.R.drawable.ic_btn_speak_now, "Copy", copyIntent) // Using speak_now as copy icon placeholder
            .setOngoing(true)
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
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun createErrorNotification(message: String): Notification {
         val openAppIntent = createOpenAppPendingIntent()
         return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Error")
            .setContentText(message)
            .setContentIntent(openAppIntent)
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

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("VoDrop Transcription", text)
        clipboard.setPrimaryClip(clip)
    }
}