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
import com.liftley.vodrop.ui.components.reusable.HistoryCardButton
import com.liftley.vodrop.ui.theme.Dimens

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
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(Dimens.small16)) {
            Text(
                transcription.text,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(Dimens.small16))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Copy
                HistoryCardButton(
                    shapes = MaterialTheme.shapes.medium.copy(
                        topEnd = CornerSize(Dimens.extraSmall8),
                        bottomStart = CornerSize(Dimens.extraSmall8),
                        bottomEnd = CornerSize(Dimens.extraSmall8)
                    ),
                    onClick = onCopy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        "Copy",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Edit
                HistoryCardButton(
                    shapes = MaterialTheme.shapes.medium.copy(
                        topEnd = CornerSize(Dimens.extraSmall8),
                        bottomEnd = CornerSize(Dimens.extraSmall8),
                        topStart = CornerSize(Dimens.extraSmall8),
                        bottomStart = CornerSize(Dimens.extraSmall8)
                    ),
                    modifier = Modifier.weight(1f),
                    onClick = onEdit,
                ) {
                    Icon(Icons.Rounded.Edit, "Edit", tint = MaterialTheme.colorScheme.onSurface)
                }

                // Delete
                HistoryCardButton(
                    shapes = MaterialTheme.shapes.medium.copy(
                        topStart = CornerSize(Dimens.extraSmall8),
                        bottomStart = CornerSize(Dimens.extraSmall8),
                        bottomEnd = CornerSize(Dimens.extraSmall8)
                    ),
                    modifier = Modifier.weight(1f),
                    onClick = onDelete
                ) {
                    Icon(Icons.Rounded.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
            // AI Button
            Button(
                onClick = onImproveWithAI,
                enabled = !isImproving,
                colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.surfaceContainer),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isImproving) {
                    CircularProgressIndicator(Modifier.size(Dimens.large24), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.width(Dimens.small16))
                Text("AI Polish", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}