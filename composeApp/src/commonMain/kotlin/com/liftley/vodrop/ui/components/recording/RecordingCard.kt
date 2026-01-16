package com.liftley.vodrop.ui.components.recording

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liftley.vodrop.data.stt.ModelState
import com.liftley.vodrop.ui.main.RecordingPhase

/**
 * Main recording section with status, button, and transcription result
 * Material 3 Expressive: Bigger, rounder, more spacious
 */
@Composable
fun RecordingCard(
    phase: RecordingPhase,
    modelState: ModelState,
    currentTranscription: String,
    error: String?,
    onRecordClick: () -> Unit,
    onClearError: () -> Unit,
    onCopyTranscription: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(36.dp),  // More expressive rounding
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),  // More generous padding
            horizontalAlignment = Alignment.CenterHorizontally  // ALWAYS centered
        ) {
            // Status Text - Always centered
            StatusText(phase = phase, modelState = modelState)

            Spacer(modifier = Modifier.height(12.dp))

            // Download Progress - Centered below status
            if (modelState is ModelState.Downloading) {
                DownloadProgress(progress = modelState.progress)
                Spacer(modifier = Modifier.height(32.dp))
            } else {
                Spacer(modifier = Modifier.height(40.dp))
            }

            // Big Record Button - ALWAYS centered
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                RecordButton(
                    phase = phase,
                    modelState = modelState,
                    onClick = onRecordClick,
                    size = 140.dp  // Bigger for expressive feel
                )
            }

            // Current Transcription Result
            if (currentTranscription.isNotEmpty()) {
                Spacer(modifier = Modifier.height(32.dp))
                TranscriptionResultCard(
                    text = currentTranscription,
                    onCopy = onCopyTranscription
                )
            }

            // Error Message
            error?.let {
                Spacer(modifier = Modifier.height(20.dp))
                ErrorBanner(message = it, onDismiss = onClearError)
            }
        }
    }
}

@Composable
private fun StatusText(phase: RecordingPhase, modelState: ModelState) {
    val (title, subtitle) = getStatusContent(phase, modelState)

    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,  // Bigger for expressive
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyLarge,  // Slightly bigger
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

private fun getStatusContent(phase: RecordingPhase, modelState: ModelState): Pair<String, String> {
    return when {
        modelState is ModelState.Downloading -> "Downloading Model" to "One-time download, please wait..."
        modelState is ModelState.Loading -> "Loading Model" to "Preparing speech recognition..."
        phase == RecordingPhase.LISTENING -> "Listening..." to "Speak now, tap to stop"
        phase == RecordingPhase.PROCESSING -> "Processing..." to "Transcribing your voice"
        phase == RecordingPhase.READY -> "Ready to Record" to "Tap the microphone to start"
        else -> "Getting Ready..." to "Please wait"
    }
}

@Composable
private fun DownloadProgress(progress: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // More expressive progress bar
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)  // Thicker
                .clip(RoundedCornerShape(5.dp)),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun TranscriptionResultCard(text: String, onCopy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),  // More rounded
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
            )
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(
                onClick = onCopy,
                modifier = Modifier.align(Alignment.End),
                shape = RoundedCornerShape(16.dp),  // Rounder button
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Icon(
                    Icons.Rounded.ContentCopy,  // Rounded icons
                    null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Copy", fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),  // More rounded
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Dismiss", fontWeight = FontWeight.Medium)
            }
        }
    }
}