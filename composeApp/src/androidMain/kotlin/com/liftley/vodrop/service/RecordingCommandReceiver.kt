package com.liftley.vodrop.service

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.liftley.vodrop.domain.manager.RecordingSessionManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * **Notification Action Receiver**
 * 
 * Intercepts button clicks from the Notification shade (Stop, Cancel, Copy).
 * 
 * **Pattern: Application Decoupling:**
 * The Service cannot directly call [RecordingSessionManager] for logic flow reasons (Circular dependency / Threading).
 * Instead, notifications fire PendingIntents that this Receiver catches.
 * This Receiver then injects the SessionManager and safely executes the command.
 */
class RecordingCommandReceiver : BroadcastReceiver(), KoinComponent {
    
    companion object {
        private const val TAG = "RecordingCommandReceiver"
        const val ACTION_START = "com.liftley.vodrop.ACTION_START"
        const val ACTION_STOP = "com.liftley.vodrop.ACTION_STOP"
        const val ACTION_CANCEL = "com.liftley.vodrop.ACTION_CANCEL"
        const val ACTION_COPY = "com.liftley.vodrop.ACTION_COPY"
        const val EXTRA_TEXT = "extra_text"
    }
    
    private val sessionManager: RecordingSessionManager by inject()
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received action: ${intent.action}")
        
        when (intent.action) {
            ACTION_START -> {
                sessionManager.startRecording()
            }
            ACTION_STOP -> {
                sessionManager.stopRecording()
            }
            ACTION_CANCEL -> {
                sessionManager.cancelRecording()
            }
            ACTION_COPY -> {
                val text = intent.getStringExtra(EXTRA_TEXT) ?: ""
                copyToClipboard(context, text)
                
                // Reset state so the notification can return to idle/ready
                sessionManager.resetState()
            }
        }
    }
    
    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("VoDrop Transcription", text)
        clipboard.setPrimaryClip(clip)
    }
}
