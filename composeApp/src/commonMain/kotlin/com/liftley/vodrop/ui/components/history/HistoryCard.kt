package com.liftley.vodrop.ui.components.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.liftley.vodrop.domain.model.Transcription

@Composable
fun HistoryCard(
    transcription: Transcription,
    isImproving: Boolean,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onImproveWithAI: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                transcription.text,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Copy
                Button(
                    shape = MaterialTheme.shapes.large.copy(
                        topEnd = CornerSize(8.dp),
                        bottomStart = CornerSize(8.dp),
                        bottomEnd = CornerSize(8.dp)
                    ),
                    modifier = Modifier.weight(1f),
                    onClick = onCopy,
                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        "Copy",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Edit
                Button(
                    shape = MaterialTheme.shapes.large.copy(
                        topEnd = CornerSize(8.dp),
                        bottomEnd = CornerSize(8.dp),
                        topStart = CornerSize(8.dp),
                        bottomStart = CornerSize(8.dp)
                    ),
                    modifier = Modifier.weight(1f),
                    onClick = onEdit,
                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Icon(Icons.Rounded.Edit, "Edit", tint = MaterialTheme.colorScheme.onSurface)
                }

                // Delete
                Button(
                    shape = MaterialTheme.shapes.large.copy(
                        topStart = CornerSize(8.dp),
                        bottomStart = CornerSize(8.dp),
                        bottomEnd = CornerSize(8.dp)
                    ),
                    modifier = Modifier.weight(1f),
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Icon(Icons.Rounded.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
            // AI Button
            Button(
                onClick = onImproveWithAI,
                enabled = !isImproving,
                colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isImproving) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("AI Polish", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}