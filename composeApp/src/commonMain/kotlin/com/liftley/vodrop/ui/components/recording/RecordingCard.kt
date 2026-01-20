package com.liftley.vodrop.ui.components.recording

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liftley.vodrop.data.stt.TranscriptionState
import com.liftley.vodrop.ui.main.*

@Composable
fun RecordingCard(
    phase: RecordingPhase,
    transcriptionState: TranscriptionState,
    currentTranscription: String,
    progressMessage: String,
    mode: TranscriptionMode,
    error: String?,
    onRecordClick: () -> Unit,
    onClearError: () -> Unit,
    onCopy: () -> Unit
) {
    val (title, subtitle) = when {
        transcriptionState is TranscriptionState.Downloading -> "Downloading..." to "One-time setup"
        transcriptionState is TranscriptionState.Initializing -> "Connecting..." to transcriptionState.message
        phase == RecordingPhase.LISTENING -> "Listening..." to "Tap to stop"
        phase == RecordingPhase.PROCESSING -> "Processing..." to progressMessage.ifEmpty { "Please wait..." }
        phase == RecordingPhase.READY -> "Ready" to "Tap to record"
        else -> "Getting Ready..." to "Connecting..."
    }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

            if (phase == RecordingPhase.READY || phase == RecordingPhase.LISTENING) {
                Spacer(Modifier.height(16.dp))
                Surface(color = if (mode == TranscriptionMode.WITH_AI_POLISH) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp)) {
                    Text(if (mode == TranscriptionMode.WITH_AI_POLISH) "✨ AI Polish" else "🎤 Standard", Modifier.padding(horizontal = 24.dp, vertical = 12.dp), fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(40.dp))
            RecordButton(phase, transcriptionState, onRecordClick, 160.dp)

            if (currentTranscription.isNotEmpty() && phase != RecordingPhase.PROCESSING) {
                Spacer(Modifier.height(40.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))) {
                    Column(Modifier.padding(28.dp)) {
                        Text(currentTranscription, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(20.dp))
                        FilledTonalButton(onClick = onCopy, Modifier.align(Alignment.End), shape = RoundedCornerShape(20.dp)) {
                            Icon(Icons.Rounded.ContentCopy, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Copy")
                        }
                    }
                }
            }

            if (phase == RecordingPhase.PROCESSING && progressMessage.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(progressMessage, style = MaterialTheme.typography.bodyMedium)
                }
            }

            error?.let {
                Spacer(Modifier.height(24.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(it, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = onClearError) { Text("Dismiss") }
                    }
                }
            }
        }
    }
}