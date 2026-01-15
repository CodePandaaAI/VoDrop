package com.liftley.vodrop

import androidx.compose.runtime.Composable
import com.liftley.vodrop.ui.MainScreen
import com.liftley.vodrop.ui.MainViewModel
import com.liftley.vodrop.ui.theme.VoDropTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App() {
    val viewModel: MainViewModel = koinViewModel()

    VoDropTheme {
        MainScreen(viewModel = viewModel)
    }
}