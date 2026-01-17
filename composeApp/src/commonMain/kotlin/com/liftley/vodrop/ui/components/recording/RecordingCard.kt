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
        shape = RoundedCornerShape(32.dp), // Material 3 Expressive: Larger corners
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(48.dp), // Material 3 Expressive: More padding
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Text (simplified - inline)
            val (title, subtitle) = when {
                transcriptionState is TranscriptionState.Downloading -> "Downloading Model" to "One-time download, please wait..."
                transcriptionState is TranscriptionState.Initializing -> "Connecting..." to transcriptionState.message
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
            
            // Material 3 Expressive: Bigger, bolder text
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge, // Bigger
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp)) // More spacing
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleMedium, // Bigger
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            // Mode indicator (Material 3 Expressive: Bigger)
            if (phase == RecordingPhase.READY || phase == RecordingPhase.LISTENING) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = when (transcriptionMode) {
                        TranscriptionMode.STANDARD -> MaterialTheme.colorScheme.surfaceVariant
                        TranscriptionMode.WITH_AI_POLISH -> MaterialTheme.colorScheme.tertiaryContainer
                    },
                    shape = RoundedCornerShape(20.dp) // Bigger corners
                ) {
                    Text(
                        text = when (transcriptionMode) {
                            TranscriptionMode.STANDARD -> "🎤 Standard"
                            TranscriptionMode.WITH_AI_POLISH -> "✨ AI Polish"
                        },
                        style = MaterialTheme.typography.titleSmall, // Bigger
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp) // More padding
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp)) // More spacing

            // Download Progress (Desktop only - simplified)
            if (transcriptionState is TranscriptionState.Downloading) {
                Spacer(modifier = Modifier.height(20.dp))
                LinearProgressIndicator(
                    progress = { transcriptionState.progress },
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "${(transcriptionState.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(20.dp))
            } else {
                Spacer(modifier = Modifier.height(40.dp))
            }

            // Material 3 Expressive: Bigger record button
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                RecordButton(
                    phase = phase,
                    transcriptionState = transcriptionState,
                    onClick = onRecordClick,
                    size = 160.dp // Bigger button
                )
            }

            // Current Transcription Result (Material 3 Expressive: Bigger text, more padding)
            if (currentTranscription.isNotEmpty() && phase != RecordingPhase.PROCESSING) {
                Spacer(modifier = Modifier.height(40.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp), // Bigger corners
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    )
                ) {
                    Column(modifier = Modifier.padding(28.dp)) { // More padding
                        Text(
                            text = currentTranscription,
                            style = MaterialTheme.typography.bodyLarge, // Already good size
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2f // More line spacing
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        FilledTonalButton(
                            onClick = onCopyTranscription,
                            modifier = Modifier.align(Alignment.End),
                            shape = RoundedCornerShape(20.dp), // Bigger corners
                            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp) // More padding
                        ) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                null,
                                modifier = Modifier.size(22.dp) // Bigger icon
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Copy",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Progress indicator (Material 3 Expressive: Bigger)
            if (phase == RecordingPhase.PROCESSING && progressMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), // More padding
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp), // Bigger
                        strokeWidth = 3.dp, // Thicker
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp)) // More spacing
                    Text(
                        text = progressMessage,
                        style = MaterialTheme.typography.bodyLarge, // Bigger
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Error Message (Material 3 Expressive: Bigger text, more padding)
            error?.let {
                Spacer(modifier = Modifier.height(28.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp), // Bigger corners
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp), // More padding
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge, // Bigger
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(
                            onClick = onClearError,
                            shape = RoundedCornerShape(16.dp), // Bigger corners
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                "Dismiss",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

// REMOVED: All sub-components inlined into main RecordingCard for simplicity