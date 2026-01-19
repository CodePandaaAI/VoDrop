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
    val isPro by platformAuth.isPro.collectAsState()

    // Sync access state to ViewModel
    LaunchedEffect(accessState) {
        viewModel.setAuth(accessState.isLoggedIn, accessState.isPro, accessState.freeTrialsRemaining)
    }

    // Update when Pro status changes
    LaunchedEffect(isPro) {
        if (accessState.isLoggedIn) {
            platformAuth.updateProStatus(isPro)
        }
    }

    // Setup transcription callback
    LaunchedEffect(viewModel) {
        viewModel.onTranscriptionComplete = { seconds ->
            scope.launch { platformAuth.recordUsage(seconds) }
        }
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