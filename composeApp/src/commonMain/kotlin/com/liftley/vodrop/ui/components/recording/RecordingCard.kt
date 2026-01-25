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
import com.liftley.vodrop.ui.main.MicPhase

@Composable
fun RecordingCard(
    phase: MicPhase,
    currentTranscription: String,
    progressMessage: String,
    onRecordClick: () -> Unit,
    onCancel: () -> Unit,
    onClearError: () -> Unit,
    onCopy: () -> Unit
) {
    val (title, subtitle) = when (phase) {
        is MicPhase.Recording -> "Recording..." to "Tap to stop"
        is MicPhase.Processing -> "Processing..." to progressMessage.ifEmpty { "Please wait..." }
        is MicPhase.Error -> "Error" to phase.message
        else -> "Ready" to "Tap to record"
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

            Spacer(Modifier.height(40.dp))
            RecordButton(phase, onRecordClick, 160.dp)

            if (phase is MicPhase.Recording || phase is MicPhase.Processing) {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onCancel, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Cancel")
                }
            }

            if (currentTranscription.isNotEmpty()) {
                Spacer(Modifier.height(40.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                ) {
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

            if (phase is MicPhase.Processing && progressMessage.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(progressMessage, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (phase is MicPhase.Error) {
                Spacer(Modifier.height(24.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(phase.message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = onClearError) { Text("Dismiss") }
                    }
                }
            }
        }
    }
}