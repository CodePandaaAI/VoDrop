package com.liftley.vodrop.ui.components.history

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.liftley.vodrop.domain.model.Transcription
import com.liftley.vodrop.ui.components.reusable.HistoryCardButton
import com.liftley.vodrop.ui.theme.Dimens

/**
 * **History Item Card**
 * 
 * Displays a single previous transcription.
 * 
 * **Features:**
 * - **Dual View:** Toggles between 'Original' and 'Polished' text if available.
 * - **Local State:** Uses `remember(transcription.id)` to track the view toggle locally per item.
 * - **Actions:** Copy, Edit (context-aware), Delete, and AI Polish (if missing).
 */
@Composable
fun HistoryCard(
    transcription: Transcription,
    isImproving: Boolean,
    onCopy: (String) -> Unit,
    onEditOriginal: () -> Unit,
    onEditPolished: () -> Unit,
    onDelete: () -> Unit,
    onImproveWithAI: () -> Unit
) {
    // Track which version is being viewed - key ensures reset when transcription changes (e.g. after polish completes)
    var showPolished by remember(transcription.id, transcription.hasPolished) { 
        mutableStateOf(transcription.hasPolished) 
    }
    
    // Current text based on selected tab
    val displayText = if (showPolished && transcription.polishedText != null) {
        transcription.polishedText
    } else {
        transcription.originalText
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier
                .padding(Dimens.small16)
                .animateContentSize()
        ) {
            // Version Toggle (only show if polished exists)
            if (transcription.hasPolished) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !showPolished,
                        onClick = { showPolished = false },
                        label = { Text("Original") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                    FilterChip(
                        selected = showPolished,
                        onClick = { showPolished = true },
                        label = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.AutoAwesome, 
                                    null, 
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Polished")
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                }
                Spacer(Modifier.height(Dimens.extraSmall8))
            }

            // Transcription text
            Text(
                displayText,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(Dimens.small16))

            // Action buttons row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Copy (copies current view)
                HistoryCardButton(
                    shapes = MaterialTheme.shapes.medium.copy(
                        topEnd = CornerSize(Dimens.extraSmall8),
                        bottomStart = CornerSize(Dimens.extraSmall8),
                        bottomEnd = CornerSize(Dimens.extraSmall8)
                    ),
                    onClick = { onCopy(displayText) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        "Copy",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Edit (works for both original and polished)
                HistoryCardButton(
                    shapes = MaterialTheme.shapes.medium.copy(
                        topEnd = CornerSize(Dimens.extraSmall8),
                        bottomEnd = CornerSize(Dimens.extraSmall8),
                        topStart = CornerSize(Dimens.extraSmall8),
                        bottomStart = CornerSize(Dimens.extraSmall8)
                    ),
                    modifier = Modifier.weight(1f),
                    onClick = if (showPolished) onEditPolished else onEditOriginal,
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

            // AI Polish button (shows on polished tab OR if no polish exists yet)
            // Allows re-polishing or initial polishing of old items
            if (showPolished || !transcription.hasPolished) {
                Spacer(Modifier.height(8.dp))
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
                    Text(
                        if (transcription.hasPolished) "Re-polish with AI" else "AI Polish",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}