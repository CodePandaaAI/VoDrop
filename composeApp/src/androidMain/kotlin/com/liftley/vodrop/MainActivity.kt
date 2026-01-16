package com.liftley.vodrop

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.liftley.vodrop.auth.FirebaseAuthManager
import com.liftley.vodrop.auth.SubscriptionManager
import com.liftley.vodrop.data.preferences.PreferencesManager
import com.liftley.vodrop.di.appModule
import com.liftley.vodrop.di.platformModule
import com.liftley.vodrop.ui.main.MainScreen
import com.liftley.vodrop.ui.main.MainViewModel
import com.liftley.vodrop.ui.onboarding.OnboardingScreen
import com.liftley.vodrop.ui.theme.VoDropTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.GlobalContext.startKoin

class MainActivity : ComponentActivity() {

    private lateinit var authManager: FirebaseAuthManager
    private lateinit var subscriptionManager: SubscriptionManager
    private val preferencesManager: PreferencesManager by inject()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
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

        checkMicrophonePermission()

        // Initialize Auth
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
                var showOnboarding by remember {
                    mutableStateOf(!preferencesManager.hasCompletedOnboarding())
                }

                if (showOnboarding) {
                    OnboardingScreen(
                        onComplete = { name, style ->
                            lifecycleScope.launch {
                                preferencesManager.setUserName(name)
                                preferencesManager.setCleanupStyle(style)
                                preferencesManager.completeOnboarding()
                                showOnboarding = false
                            }
                        }
                    )
                } else {
                    MainAppContent()
                }
            }
        }
    }

    @Composable
    private fun MainAppContent() {
        val viewModel: MainViewModel = koinViewModel()

        // ✅ FIX: Only run once when first composed, not on every recomposition
        LaunchedEffect(Unit) {
            // TESTING: Force Pro status
            viewModel.setProStatus(true)
            viewModel.setUserInfo(
                isLoggedIn = true,
                name = "Test User",
                email = "test@vodrop.com",
                photoUrl = null
            )
        }

        MainScreen(
            viewModel = viewModel,
            onLoginClick = {
                lifecycleScope.launch {
                    val result = authManager.signInWithGoogle(this@MainActivity)
                    if (result.isSuccess) {
                        val user = result.getOrNull()
                        if (user != null) {
                            subscriptionManager.loginWithFirebaseUser(user.id)
                            viewModel.setUserInfo(true, user.displayName, user.email, user.photoUrl)
                            Toast.makeText(this@MainActivity, "Welcome, ${user.displayName}!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, result.exceptionOrNull()?.message ?: "Sign in failed", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onSignOut = {
                lifecycleScope.launch {
                    authManager.signOut(this@MainActivity)
                    subscriptionManager.logout()
                    viewModel.setUserInfo(false, null, null, null)
                    Toast.makeText(this@MainActivity, "Signed out", Toast.LENGTH_SHORT).show()
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

    private fun checkMicrophonePermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                Toast.makeText(this, "VoDrop needs microphone access to record your voice", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}