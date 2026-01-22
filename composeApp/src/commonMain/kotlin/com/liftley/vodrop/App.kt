package com.liftley.vodrop

import androidx.compose.runtime.*
import com.liftley.vodrop.auth.AuthConfig
import com.liftley.vodrop.auth.PlatformAuth
import com.liftley.vodrop.ui.main.MainScreen
import com.liftley.vodrop.ui.main.MainViewModel
import com.liftley.vodrop.ui.theme.VoDropTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * App entry point - shared across all platforms.
 * ViewModel handles auth state observation internally.
 */
@Composable
fun App() {
    val viewModel: MainViewModel = koinViewModel()
    val platformAuth: PlatformAuth = koinInject()
    val scope = rememberCoroutineScope()

    VoDropTheme {
        MainScreen(
            viewModel = viewModel,
            onLoginClick = { scope.launch { platformAuth.signIn() } },
            onSignOut = { scope.launch { platformAuth.signOut() } },
            onPurchaseMonthly = { scope.launch { platformAuth.purchaseMonthly() } },
            monthlyPrice = AuthConfig.PRICE_MONTHLY_USD
        )
    }
}