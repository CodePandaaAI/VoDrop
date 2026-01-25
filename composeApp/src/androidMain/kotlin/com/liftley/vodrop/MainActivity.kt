package com.liftley.vodrop

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.liftley.vodrop.auth.PlatformAuth
import com.liftley.vodrop.di.appModule
import com.liftley.vodrop.di.platformModule
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext.startKoin

class MainActivity : ComponentActivity() {

    private val platformAuth: PlatformAuth by inject()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            // If denied, show toast with settings hint
            Toast.makeText(
                this,
                "Microphone denied. Enable in Settings → Apps → VoDrop",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initKoin()
        platformAuth.setActivity(this)
        platformAuth.initialize()

        // Request permission at startup
        requestMicPermissionIfNeeded()

        lifecycleScope.launch { platformAuth.initializeAccess() }

        setContent { App() }
    }

    override fun onResume() {
        super.onResume()
        // Re-check when returning from Settings
        requestMicPermissionIfNeeded()
    }

    private fun initKoin() {
        try {
            startKoin {
                androidLogger()
                androidContext(this@MainActivity)
                modules(appModule, platformModule)
            }
        } catch (_: Exception) { /* Already started */ }
    }

    private fun requestMicPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}