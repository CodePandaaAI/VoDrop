package com.liftley.vodrop.ui.components.recording

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liftley.vodrop.data.stt.TranscriptionState
import com.liftley.vodrop.ui.main.RecordingPhase

/**
 * Animated record button with different states
 * Material 3 Expressive: Big, bold, animated
 */
@Composable
fun RecordButton(
    phase: RecordingPhase,
    transcriptionState: TranscriptionState,
    onClick: () -> Unit,
    size: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    val isListening = phase == RecordingPhase.LISTENING
    val isProcessing = phase == RecordingPhase.PROCESSING ||
            transcriptionState is TranscriptionState.Downloading ||
            transcriptionState is TranscriptionState.Initializing
    val isEnabled = phase != RecordingPhase.PROCESSING &&
            transcriptionState !is TranscriptionState.Downloading

    // Pulsing animation when listening
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Button color animation
    val buttonColor by animateColorAsState(
        targetValue = when {
            isListening -> MaterialTheme.colorScheme.error
            isProcessing -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(300),
        label = "buttonColor"
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            isListening -> MaterialTheme.colorScheme.onError
            isProcessing -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onPrimary
        },
        animationSpec = tween(300),
        label = "iconColor"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(if (isListening) pulseScale else 1f)
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
                    modifier = Modifier.size(size * 0.4f),
                    color = iconColor,
                    strokeWidth = 4.dp
                )
            }
            isListening -> {
                Icon(
                    Icons.Rounded.Stop,
                    contentDescription = "Stop recording",
                    modifier = Modifier.size(size * 0.45f),
                    tint = iconColor
                )
            }
            else -> {
                Icon(
                    Icons.Rounded.Mic,
                    contentDescription = "Start recording",
                    modifier = Modifier.size(size * 0.45f),
                    tint = iconColor
                )
            }
        }
    }
}