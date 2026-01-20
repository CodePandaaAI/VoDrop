package com.liftley.vodrop.ui.components.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liftley.vodrop.domain.model.Transcription

@Composable
fun HistoryCard(
    transcription: Transcription,
    isPro: Boolean,
    isImproving: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onImproveWithAI: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AccessTime, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(transcription.timestamp, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Text(transcription.text, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(20.dp))

            Button(onClick = onImproveWithAI, Modifier.fillMaxWidth().height(56.dp), enabled = !isImproving, shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                if (isImproving) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)); Text("Improving...") }
                else { Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Improve with AI", fontWeight = FontWeight.SemiBold)
                    if (!isPro) { Spacer(Modifier.width(8.dp)); Surface(color = MaterialTheme.colorScheme.tertiary, shape = RoundedCornerShape(6.dp)) { Text("PRO", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiary) } }
                }
            }
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = { clipboard.setText(AnnotatedString(transcription.text)) }, Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Copy") }
                FilledTonalButton(onClick = onEdit, Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Edit") }
                FilledTonalButton(onClick = onDelete, Modifier.height(48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Icon(Icons.Rounded.Delete, null, Modifier.size(20.dp)) }
            }
        }
    }
}