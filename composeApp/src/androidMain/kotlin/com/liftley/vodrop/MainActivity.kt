package com.liftley.vodrop

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.liftley.vodrop.auth.FirebaseAuthManager
import com.liftley.vodrop.auth.SubscriptionManager
import com.liftley.vodrop.di.appModule
import com.liftley.vodrop.di.platformModule
import com.liftley.vodrop.ui.main.MainScreen
import com.liftley.vodrop.ui.main.MainViewModel
import com.liftley.vodrop.ui.theme.VoDropTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.GlobalContext.startKoin

class MainActivity : ComponentActivity() {

    private lateinit var authManager: FirebaseAuthManager
    private lateinit var subscriptionManager: SubscriptionManager

    // Permission state
    private val hasMicPermission = mutableStateOf(false)

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission.value = isGranted
        if (!isGranted) {
            Toast.makeText(
                this,
                "Microphone permission is required for voice recording",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check microphone permission
        checkAndRequestMicrophonePermission()

        // Initialize Koin
        try {
            startKoin {
                androidLogger()
                androidContext(this@MainActivity)
                modules(appModule, platformModule)
            }
        } catch (_: Exception) {
            // Koin already started
        }

        // Initialize Auth with Web Client ID
        authManager = FirebaseAuthManager()
        authManager.initialize("808998462431-v1mec4tnrgbosfkskedeb4kouodb8qm6.apps.googleusercontent.com")

        // Initialize Subscriptions
        subscriptionManager = SubscriptionManager(this)
        subscriptionManager.initialize(authManager.getCurrentUserId())

        lifecycleScope.launch {
            subscriptionManager.checkProStatus()
            subscriptionManager.fetchPackages()
        }

        setContent {
            VoDropTheme {
                val viewModel: MainViewModel = koinViewModel()

                // Observe auth state
                val currentUser by authManager.currentUser.collectAsState()
                val isPro by subscriptionManager.isPro.collectAsState()

                // Update ViewModel with auth state
                viewModel.setUserInfo(
                    isLoggedIn = currentUser != null,
                    name = currentUser?.displayName,
                    email = currentUser?.email,
                    photoUrl = currentUser?.photoUrl
                )
                viewModel.setProStatus(isPro)

                MainScreen(
                    viewModel = viewModel,
                    onLoginClick = {
                        lifecycleScope.launch {
                            val result = authManager.signInWithGoogle(this@MainActivity)
                            if (result.isSuccess) {
                                val user = result.getOrNull()
                                if (user != null) {
                                    subscriptionManager.loginWithFirebaseUser(user.id)
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Welcome, ${user.displayName}!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                val error = result.exceptionOrNull()?.message ?: "Sign in failed"
                                Toast.makeText(
                                    this@MainActivity,
                                    error,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onSignOut = {
                        lifecycleScope.launch {
                            authManager.signOut(this@MainActivity)
                            subscriptionManager.logout()
                            Toast.makeText(
                                this@MainActivity,
                                "Signed out",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onPurchaseMonthly = {
                        lifecycleScope.launch {
                            subscriptionManager.purchaseMonthly(this@MainActivity)
                        }
                    },
                    onPurchaseYearly = {
                        lifecycleScope.launch {
                            subscriptionManager.purchaseYearly(this@MainActivity)
                        }
                    },
                    onRestorePurchases = {
                        lifecycleScope.launch {
                            val restored = subscriptionManager.restorePurchases()
                            Toast.makeText(
                                this@MainActivity,
                                if (restored) "Purchases restored!" else "No purchases found",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    monthlyPrice = subscriptionManager.getMonthlyPrice(),
                    yearlyPrice = subscriptionManager.getYearlyPrice()
                )
            }
        }
    }

    private fun checkAndRequestMicrophonePermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                hasMicPermission.value = true
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                // Show explanation then request
                Toast.makeText(
                    this,
                    "VoDrop needs microphone access to record your voice",
                    Toast.LENGTH_LONG
                ).show()
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> {
                // Directly request
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}