package com.liftley.vodrop.ui.components.recording

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.liftley.vodrop.ui.main.MicPhase

/**
 * Record button with different states
 * Shows waveform animation during recording
 */

@Composable
fun RecordButton(
    phase: MicPhase,
    onClick: () -> Unit,
    size: Dp = 144.dp,
    modifier: Modifier = Modifier
) {
    val isRecording = phase is MicPhase.Recording
    val isProcessing = phase is MicPhase.Processing
    val isEnabled = phase !is MicPhase.Processing

    val buttonColor = when {
        isRecording -> MaterialTheme.colorScheme.error
        isProcessing -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }

    val iconColor = when {
        isRecording -> MaterialTheme.colorScheme.onError
        isProcessing -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimary
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(buttonColor)
            .clickable(
                enabled = isEnabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            isProcessing -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(size * 0.45f),
                    color = iconColor,
                    strokeWidth = 5.dp
                )
            }

            isRecording -> {
                SimpleWaveform(color = MaterialTheme.colorScheme.surfaceContainer)
            }

            else -> {
                Icon(Icons.Rounded.Mic, "Record", Modifier.size(size * 0.5f), tint = iconColor)
            }
        }
    }
}

/**
 * Simple waveform visualization with 5 bars
 * Uses the Draw phase to stay recomposition-friendly
 */
@Composable
private fun SimpleWaveform(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Waveform")

    val anim1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )

    val anim2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )

    val anim3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Canvas(
        modifier = modifier
            .size(80.dp, 40.dp) // Add explicit size here!
    ) {
        val barCount = 5
        val spacing = 8.dp.toPx()
        val totalSpacing = spacing * (barCount - 1)
        val barWidth = (size.width - totalSpacing) / barCount
        val maxHeight = size.height
        val cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)

        // Mapping bars to the animations
        val heights = listOf(anim1, anim2, anim3, anim2, anim1)

        heights.forEachIndexed { index, heightFactor ->
            val barHeight = maxHeight * heightFactor
            val xOffset = index * (barWidth + spacing)
            val yOffset = (maxHeight - barHeight) / 2

            drawRoundRect(
                color = color,
                topLeft = Offset(xOffset, yOffset),
                size = Size(barWidth, barHeight),
                cornerRadius = cornerRadius
            )
        }
    }
}