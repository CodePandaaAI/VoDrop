package com.liftley.vodrop.ui.components.recording

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liftley.vodrop.data.stt.ModelState
import com.liftley.vodrop.ui.main.RecordingPhase

/**
 * Large animated microphone button for recording
 * Material 3 Expressive: Bigger, spring animations, gradient backgrounds
 *
 * ⚡ OPTIMIZED: Animations only run when needed to save battery
 */
@Composable
fun RecordButton(
    phase: RecordingPhase,
    modelState: ModelState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isEnabled = phase == RecordingPhase.READY || phase == RecordingPhase.LISTENING

    // Spring-based animation for press feedback
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    // ⚡ OPTIMIZED: Pulse animation ONLY runs when LISTENING
    // This prevents constant GPU/CPU usage when idle
    val pulseScale by animateFloatAsState(
        targetValue = if (phase == RecordingPhase.LISTENING) 1.08f else 1f,
        animationSpec = if (phase == RecordingPhase.LISTENING) {
            infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            spring(stiffness = Spring.StiffnessMedium)
        },
        label = "pulseScale"
    )

    val actualScale = scale * pulseScale

    val backgroundColor by animateColorAsState(
        targetValue = when {
            modelState is ModelState.Downloading -> MaterialTheme.colorScheme.secondary
            modelState is ModelState.Loading -> MaterialTheme.colorScheme.secondary
            phase == RecordingPhase.LISTENING -> MaterialTheme.colorScheme.error
            phase == RecordingPhase.PROCESSING -> MaterialTheme.colorScheme.outline
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "bgColor"
    )

    // Gradient for more expressive visual
    val gradient = when (phase) {
        RecordingPhase.LISTENING -> Brush.radialGradient(
            colors = listOf(
                MaterialTheme.colorScheme.error,
                MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
            )
        )
        RecordingPhase.READY -> Brush.radialGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            )
        )
        else -> Brush.radialGradient(
            colors = listOf(backgroundColor, backgroundColor)
        )
    }

    // Shadow for depth (less computation when not pressed)
    val shadowElevation = if (isPressed) 4.dp else 12.dp

    Box(
        modifier = modifier
            .size(size)
            .scale(actualScale)
            .shadow(
                elevation = shadowElevation,
                shape = CircleShape,
                ambientColor = backgroundColor.copy(alpha = 0.4f),
                spotColor = backgroundColor.copy(alpha = 0.4f)
            )
            .clip(CircleShape)
            .background(gradient)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isEnabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        val iconSize = size * 0.38f
        val progressSize = size * 0.3f

        when {
            modelState is ModelState.Downloading || modelState is ModelState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(progressSize),
                    color = Color.White,
                    strokeWidth = 4.dp,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
            phase == RecordingPhase.PROCESSING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(progressSize),
                    color = MaterialTheme.colorScheme.onSurface,
                    strokeWidth = 4.dp,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )
            }
            phase == RecordingPhase.LISTENING -> {
                Icon(
                    imageVector = Icons.Rounded.Stop,
                    contentDescription = "Stop Recording",
                    modifier = Modifier.size(iconSize),
                    tint = Color.White
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "Start Recording",
                    modifier = Modifier.size(iconSize),
                    tint = Color.White
                )
            }
        }
    }
}