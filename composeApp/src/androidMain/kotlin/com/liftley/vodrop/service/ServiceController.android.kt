package com.liftley.vodrop.service

import android.content.Context
import android.content.Intent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Android implementation of ServiceController.
 * Controls the RecordingService lifecycle.
 */
class AndroidServiceController : ServiceController, KoinComponent {
    
    private val context: Context by inject()
    
    override fun startForeground() {
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
        }
        context.startForegroundService(intent)
    }
    
    override fun stopForeground() {
        context.stopService(Intent(context, RecordingService::class.java))
    }
}

actual fun createServiceController(): ServiceController = AndroidServiceController()
