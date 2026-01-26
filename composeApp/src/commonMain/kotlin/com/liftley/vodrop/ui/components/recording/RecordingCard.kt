package com.liftley.vodrop.ui.components.recording

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liftley.vodrop.ui.main.MicPhase
import com.liftley.vodrop.ui.theme.Dimens

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
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(Dimens.small16),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Dimens.extraSmall8))
            Text(
                subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Dimens.huge48))
            RecordButton(phase, onRecordClick, 144.dp)

            if (phase is MicPhase.Recording || phase is MicPhase.Processing) {
                Spacer(Modifier.height(Dimens.small16))
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.size(height = 56.dp, width = 132.dp)
                ) {
                    Text(
                        "Cancel",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            if (currentTranscription.isNotEmpty()) {
                Spacer(Modifier.height(Dimens.huge48))
                Card(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        Modifier.padding(Dimens.small16),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Dimens.large24)
                    ) {
                        Text(
                            currentTranscription,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Button(
                            onClick = onCopy,
                            Modifier.align(Alignment.End),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                null,
                                Modifier.size(Dimens.large24),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(Dimens.small16))
                            Text(
                                "Copy",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            if (phase is MicPhase.Processing && progressMessage.isNotEmpty()) {
                Spacer(Modifier.height(Dimens.large24))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        Modifier.size(Dimens.large24),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(Dimens.small16))
                    Text(progressMessage, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (phase is MicPhase.Error) {
                Spacer(Modifier.height(Dimens.large24))
                Card(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        Modifier.padding(Dimens.large24),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            phase.message,
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = onClearError) { Text("Dismiss") }
                    }
                }
            }
        }
    }
}