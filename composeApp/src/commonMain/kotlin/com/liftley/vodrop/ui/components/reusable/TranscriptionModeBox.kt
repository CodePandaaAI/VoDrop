package com.liftley.vodrop.ui.components.reusable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.liftley.vodrop.ui.theme.Dimens

@Composable
fun TranscriptionModeBox(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(onClick = onClick)
            .height(Dimens.huge48)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}