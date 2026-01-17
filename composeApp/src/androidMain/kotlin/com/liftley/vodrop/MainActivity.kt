package com.liftley.vodrop

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.liftley.vodrop.auth.AccessManager
import com.liftley.vodrop.auth.AuthConfig
import com.liftley.vodrop.auth.FirebaseAuthManager
import com.liftley.vodrop.auth.SubscriptionManager
import com.liftley.vodrop.di.appModule
import com.liftley.vodrop.di.platformModule
import com.liftley.vodrop.ui.main.MainScreen
import com.liftley.vodrop.ui.main.MainViewModel
import com.liftley.vodrop.ui.theme.VoDropTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.GlobalContext.startKoin

class MainActivity : ComponentActivity() {

    private val authManager: FirebaseAuthManager by inject()
    private val subscriptionManager: SubscriptionManager by inject()
    private val accessManager: AccessManager by inject()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // FIX: Handle permission result
        if (!isGranted) {
            Toast.makeText(this, "Microphone permission is required for recording", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initKoin()
        initAuth()
        requestMicPermission()

        setContent {
            VoDropTheme {
                App()
            }
        }
    }

    private fun initKoin() {
        try {
            startKoin {
                androidLogger()
                androidContext(this@MainActivity)
                modules(appModule, platformModule)
            }
        } catch (_: Exception) {
            // Koin already started
        }
    }

    private fun initAuth() {
        authManager.initialize("808998462431-v1mec4tnrgbosfkskedeb4kouodb8qm6.apps.googleusercontent.com")
        subscriptionManager.initialize(authManager.getCurrentUserId())
        lifecycleScope.launch {
            subscriptionManager.checkProStatus()
            accessManager.initialize()
        }
    }

    private fun requestMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    @Composable
    private fun App() {
        val viewModel: MainViewModel = koinViewModel()
        val accessState by accessManager.accessState.collectAsState()
        val isPro by subscriptionManager.isPro.collectAsState()

        // Sync access state to ViewModel
        LaunchedEffect(accessState) {
            viewModel.setAuth(accessState.isLoggedIn, accessState.isPro, accessState.freeTrialsRemaining)
        }

        // Update AccessManager when Pro status changes (from RevenueCat)
        LaunchedEffect(isPro) {
            if (accessState.isLoggedIn) {
                accessManager.updateProStatus(isPro)
            }
        }

        LaunchedEffect(viewModel) {
            viewModel.onTranscriptionComplete = { seconds ->
                lifecycleScope.launch { accessManager.recordTranscriptionUsage(seconds) }
            }
        }

        MainScreen(
            viewModel = viewModel,
            onLoginClick = { signIn() },
            onSignOut = { signOut() },
            onPurchaseMonthly = { purchase() },
            monthlyPrice = AuthConfig.PRICE_MONTHLY_USD
        )
    }

    private fun signIn() {
        lifecycleScope.launch {
            authManager.signInWithGoogle(this@MainActivity)
                .onSuccess { user ->
                    subscriptionManager.loginWithFirebaseUser(user.id)
                    accessManager.onUserLoggedIn()
                    Toast.makeText(this@MainActivity, "Welcome!", Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    // FIX: Show actual error message
                    Toast.makeText(this@MainActivity, "Sign in failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun signOut() {
        lifecycleScope.launch {
            authManager.signOut(this@MainActivity)
            subscriptionManager.logout()
            accessManager.onUserLoggedOut()
        }
    }

    private fun purchase() {
        lifecycleScope.launch { subscriptionManager.purchaseMonthly(this@MainActivity) }
    }
}