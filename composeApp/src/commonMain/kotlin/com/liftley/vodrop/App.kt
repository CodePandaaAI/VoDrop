package com.liftley.vodrop

import androidx.compose.runtime.Composable
import com.liftley.vodrop.ui.main.MainScreen
import com.liftley.vodrop.ui.main.MainViewModel
import com.liftley.vodrop.ui.theme.VoDropTheme
import org.koin.compose.koinInject

@Composable
fun App() {
    VoDropTheme {
        val viewModel: MainViewModel = koinInject()
        MainScreen(viewModel)
    }
}