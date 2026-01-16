package com.liftley.vodrop

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.liftley.vodrop.auth.FirebaseAuthManager
import com.liftley.vodrop.auth.SubscriptionManager
import com.liftley.vodrop.di.appModule
import com.liftley.vodrop.di.platformModule
import com.liftley.vodrop.ui.MainScreen
import com.liftley.vodrop.ui.MainViewModel
import com.liftley.vodrop.ui.theme.VoDropTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.GlobalContext.startKoin

class MainActivity : ComponentActivity() {

    private lateinit var authManager: FirebaseAuthManager
    private lateinit var subscriptionManager: SubscriptionManager

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

        // Initialize Auth with Web Client ID
        authManager = FirebaseAuthManager(this)
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
                val isAuthLoading by authManager.isLoading.collectAsState()

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
                        // Use the new Credential Manager sign-in
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
                            authManager.signOut()
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
}