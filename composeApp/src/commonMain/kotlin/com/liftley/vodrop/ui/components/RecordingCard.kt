package com.liftley.vodrop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liftley.vodrop.stt.ModelState
import com.liftley.vodrop.ui.RecordingPhase

/**
 * Main recording section with status, button, and transcription result
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
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Text
            StatusText(phase = phase, modelState = modelState)

            Spacer(modifier = Modifier.height(8.dp))

            // Download Progress
            if (modelState is ModelState.Downloading) {
                DownloadProgress(progress = modelState.progress)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Big Record Button
            RecordButton(
                phase = phase,
                modelState = modelState,
                onClick = onRecordClick
            )

            // Current Transcription Result
            if (currentTranscription.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                TranscriptionResultCard(
                    text = currentTranscription,
                    onCopy = onCopyTranscription
                )
            }

            // Error Message
            error?.let {
                Spacer(modifier = Modifier.height(16.dp))
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
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

private fun getStatusContent(phase: RecordingPhase, modelState: ModelState): Pair<String, String> {
    return when {
        modelState is ModelState.Downloading -> "Downloading Model" to "One-time download, please wait"
        modelState is ModelState.Loading -> "Loading Model" to "Preparing speech recognition..."
        phase == RecordingPhase.LISTENING -> "Listening" to "Speak now..."
        phase == RecordingPhase.PROCESSING -> "Processing" to "Transcribing your voice..."
        phase == RecordingPhase.READY -> "Ready" to "Tap the mic to start"
        else -> "Getting Ready" to "Please wait..."
    }
}

@Composable
private fun DownloadProgress(progress: Float) {
    Column(
        modifier = Modifier.padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TranscriptionResultCard(text: String, onCopy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(
                onClick = onCopy,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy")
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}