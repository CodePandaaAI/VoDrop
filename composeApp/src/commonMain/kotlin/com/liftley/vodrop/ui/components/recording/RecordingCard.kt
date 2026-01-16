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
import com.liftley.vodrop.data.stt.TranscriptionState
import com.liftley.vodrop.ui.main.RecordingPhase
import com.liftley.vodrop.ui.main.TranscriptionMode

/**
 * Main recording section with robust status display
 */
@Composable
fun RecordingCard(
    phase: RecordingPhase,
    transcriptionState: TranscriptionState,
    currentTranscription: String,
    progressMessage: String = "",
    transcriptionMode: TranscriptionMode = TranscriptionMode.STANDARD,
    error: String?,
    onRecordClick: () -> Unit,
    onClearError: () -> Unit,
    onCopyTranscription: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(36.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Text with mode indicator
            StatusSection(
                phase = phase,
                transcriptionState = transcriptionState,
                progressMessage = progressMessage,
                transcriptionMode = transcriptionMode
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Download Progress (Desktop only)
            if (transcriptionState is TranscriptionState.Downloading) {
                DownloadProgress(progress = transcriptionState.progress)
                Spacer(modifier = Modifier.height(32.dp))
            } else {
                Spacer(modifier = Modifier.height(40.dp))
            }

            // Big Record Button
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                RecordButton(
                    phase = phase,
                    transcriptionState = transcriptionState,
                    onClick = onRecordClick,
                    size = 140.dp
                )
            }

            // Current Transcription Result
            if (currentTranscription.isNotEmpty() && !currentTranscription.startsWith("☁️") && !currentTranscription.startsWith("✨")) {
                Spacer(modifier = Modifier.height(32.dp))
                TranscriptionResultCard(
                    text = currentTranscription,
                    onCopy = onCopyTranscription
                )
            }

            // Progress indicator during transcription
            if (phase == RecordingPhase.PROCESSING && progressMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                ProcessingIndicator(message = progressMessage)
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
private fun StatusSection(
    phase: RecordingPhase,
    transcriptionState: TranscriptionState,
    progressMessage: String,
    transcriptionMode: TranscriptionMode
) {
    val (title, subtitle) = getStatusContent(phase, transcriptionState, progressMessage)

    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    // Mode indicator
    if (phase == RecordingPhase.READY || phase == RecordingPhase.LISTENING) {
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            color = when (transcriptionMode) {
                TranscriptionMode.STANDARD -> MaterialTheme.colorScheme.surfaceVariant
                TranscriptionMode.WITH_AI_POLISH -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
            },
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = when (transcriptionMode) {
                    TranscriptionMode.STANDARD -> "🎤 Standard • Groq Whisper"
                    TranscriptionMode.WITH_AI_POLISH -> "✨ AI Polish • Groq + Gemini"
                },
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

private fun getStatusContent(
    phase: RecordingPhase,
    state: TranscriptionState,
    progressMessage: String
): Pair<String, String> {
    return when {
        state is TranscriptionState.Downloading -> "Downloading Model" to "One-time download, please wait..."
        state is TranscriptionState.Initializing -> "Connecting..." to state.message
        phase == RecordingPhase.LISTENING -> "Listening..." to "Speak now, tap to stop"
        phase == RecordingPhase.PROCESSING -> {
            when {
                progressMessage.contains("Transcribing") -> "Transcribing..." to "Sending audio to cloud"
                progressMessage.contains("Polishing") -> "Polishing..." to "Applying AI cleanup"
                else -> "Processing..." to progressMessage.ifEmpty { "Please wait..." }
            }
        }
        phase == RecordingPhase.READY -> "Ready to Record" to "Tap the microphone to start"
        else -> "Getting Ready..." to "Connecting to cloud..."
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
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
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
private fun ProcessingIndicator(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TranscriptionResultCard(text: String, onCopy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
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
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Icon(
                    Icons.Rounded.ContentCopy,
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
        shape = RoundedCornerShape(20.dp),
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