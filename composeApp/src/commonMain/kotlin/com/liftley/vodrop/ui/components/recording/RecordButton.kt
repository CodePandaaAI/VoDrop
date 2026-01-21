package com.liftley.vodrop.ui.components.recording

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liftley.vodrop.ui.main.RecordingPhase

/**
 * Record button with different states
 * Material 3 Expressive: Big, bold, flat (no animations)
 */
@Composable
fun RecordButton(
    phase: RecordingPhase,
    onClick: () -> Unit,
    size: Dp = 160.dp, // Material 3 Expressive: Bigger default
    modifier: Modifier = Modifier
) {
    val isListening = phase == RecordingPhase.LISTENING
    val isProcessing = phase == RecordingPhase.PROCESSING
    val isEnabled = phase != RecordingPhase.PROCESSING

    // Material 3 Expressive: Flat colors (no animations)
    val buttonColor = when {
        isListening -> MaterialTheme.colorScheme.error
        isProcessing -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }

    val iconColor = when {
        isListening -> MaterialTheme.colorScheme.onError
        isProcessing -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimary
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(buttonColor)
            .then(
                if (isEnabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            isProcessing -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(size * 0.45f), // Bigger
                    color = iconColor,
                    strokeWidth = 5.dp // Thicker
                )
            }
            isListening -> {
                Icon(
                    Icons.Rounded.Stop,
                    contentDescription = "Stop recording",
                    modifier = Modifier.size(size * 0.5f), // Bigger icon
                    tint = iconColor
                )
            }
            else -> {
                Icon(
                    Icons.Rounded.Mic,
                    contentDescription = "Start recording",
                    modifier = Modifier.size(size * 0.5f), // Bigger icon
                    tint = iconColor
                )
            }
        }
    }
}