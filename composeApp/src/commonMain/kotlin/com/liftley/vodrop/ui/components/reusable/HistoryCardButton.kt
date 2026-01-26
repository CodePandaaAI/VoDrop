package com.liftley.vodrop.ui.components.reusable

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

@Composable
fun HistoryCardButton(
    shapes: Shape,
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    Button(
        shape = shapes,
        modifier = modifier,
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        content()
    }
}