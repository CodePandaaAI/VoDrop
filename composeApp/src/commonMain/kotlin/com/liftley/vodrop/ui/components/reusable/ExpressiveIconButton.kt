package com.liftley.vodrop.ui.components.reusable

import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.liftley.vodrop.ui.theme.Dimens

@Composable
fun ExpressiveIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String?,
    color: Color? = null,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.height(Dimens.huge48),
        colors = IconButtonDefaults.iconButtonColors(containerColor = color ?: MaterialTheme.colorScheme.surface)
    ) {
        Icon(imageVector, contentDescription)
    }
}