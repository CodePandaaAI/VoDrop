package com.liftley.vodrop.ui.components.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liftley.vodrop.domain.model.Transcription

/**
 * Stateless card displaying a transcription with action buttons.
 * Pure UI - no business logic, no clipboard access.
 */
@Composable
fun HistoryCard(
    transcription: Transcription,
    isPro: Boolean,
    isLoading: Boolean = false,  // NEW: Prevent PRO badge flicker during loading
    isImproving: Boolean,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onImproveWithAI: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(20.dp)) {
            // Timestamp
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AccessTime, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(transcription.timestamp, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(12.dp))

            // Text content
            Text(transcription.text, style = MaterialTheme.typography.bodyLarge)

            Spacer(Modifier.height(16.dp))

            // AI Improve button
            Button(
                onClick = onImproveWithAI,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !isImproving && !isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                if (isImproving) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Improving...")
                } else {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Improve with AI", fontWeight = FontWeight.SemiBold)
                    // FIXED: Only show PRO badge when NOT loading and NOT pro
                    if (!isPro && !isLoading) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "PRO",
                                Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action buttons - icons only for compact layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Copy button
                FilledTonalButton(
                    onClick = onCopy,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, "Copy", Modifier.size(18.dp))
                }

                // Edit button
                FilledTonalButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Edit, "Edit", Modifier.size(18.dp))
                }

                // Delete button
                FilledTonalButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Icon(Icons.Rounded.Delete, "Delete", Modifier.size(18.dp))
                }
            }
        }
    }
}