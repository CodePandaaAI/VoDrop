package com.liftley.vodrop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.liftley.vodrop.ui.HistoryScreen
import com.liftley.vodrop.ui.MainScreen
import com.liftley.vodrop.ui.MainViewModel
import com.liftley.vodrop.ui.theme.VoDropTheme
import org.koin.compose.viewmodel.koinViewModel

enum class Screen {
    MAIN,
    HISTORY
}

@Composable
fun App() {
    var currentScreen by remember { mutableStateOf(Screen.MAIN) }
    val viewModel: MainViewModel = koinViewModel()

    VoDropTheme {
        when (currentScreen) {
            Screen.MAIN -> MainScreen(
                viewModel = viewModel,
                onHistoryClick = { currentScreen = Screen.HISTORY }
            )
            Screen.HISTORY -> HistoryScreen(
                viewModel = viewModel,
                onBackClick = { currentScreen = Screen.MAIN }
            )
        }
    }
}