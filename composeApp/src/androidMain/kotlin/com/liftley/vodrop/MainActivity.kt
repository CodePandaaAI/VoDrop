package com.liftley.vodrop

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.liftley.vodrop.service.ServiceController
import org.koin.android.ext.android.inject

/**
 * **Android Entry Point**
 * 
 * Handles:
 * 1. Runtime Permissions (Microphone, Notifications).
 * 2. Starting the background KMP Application.
 * 3. Lifecycle-aware Service startup.
 */
class MainActivity : ComponentActivity() {

    private val serviceController: ServiceController by inject()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (!micGranted) {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Critical: Ensure permissions before starting any recording logic
        requestPermissionsIfNeeded()
        
        // Start service immediately so it's ready for recording.
        // This ensures the Notification Channel is created and ready.
        serviceController.startForeground()
        
        setContent { App() }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop service ONLY when the app is fully closed (swiped away/finished).
        // If simply backgrounded, the service stays running to show the "Ready" notification.
        if (isFinishing) {
            serviceController.stopForeground()
        }
    }

    private fun requestPermissionsIfNeeded() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        // Android 13+ Notification Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}