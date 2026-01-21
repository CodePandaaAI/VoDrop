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
 * Handles auth state synchronization and UI rendering.
 */
@Composable
fun App() {
    val viewModel: MainViewModel = koinViewModel()
    val platformAuth: PlatformAuth = koinInject()
    val scope = rememberCoroutineScope()

    val accessState by platformAuth.accessState.collectAsState()

    // Single LaunchedEffect - sync access state to ViewModel
    LaunchedEffect(accessState) {
        viewModel.setAuth(accessState.isLoggedIn, accessState.isPro, accessState.freeTrialsRemaining)
    }

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