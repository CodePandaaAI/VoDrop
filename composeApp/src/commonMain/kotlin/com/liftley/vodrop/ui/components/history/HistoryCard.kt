package com.liftley.vodrop.ui.components.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liftley.vodrop.domain.model.Transcription

/**
 * History item card showing a saved transcription.
 *
 * Features:
 * - Expandable text (show more/less for long texts)
 * - Copy to clipboard with visual feedback
 * - Edit and Delete actions
 * - "Improve with AI" button (Pro feature)
 *
 * @param transcription The transcription data to display
 * @param isPro Whether user has Pro subscription
 * @param isImproving Whether this item is currently being improved by AI
 * @param onEdit Called when user taps Edit
 * @param onDelete Called when user taps Delete
 * @param onImproveWithAI Called when user taps "Improve with AI"
 */
@Composable
fun HistoryCard(
    transcription: Transcription,
    isPro: Boolean,
    isImproving: Boolean = false,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onImproveWithAI: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp), // Material 3 Expressive: Bigger corners
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(28.dp) // Material 3 Expressive: More padding
        ) {
            // ═══════════ TIMESTAMP (Material 3 Expressive: Bigger) ═══════════
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp) // More spacing
            ) {
                Icon(
                    Icons.Rounded.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp), // Bigger icon
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(10.dp)) // More spacing
                Text(
                    text = transcription.timestamp,
                    style = MaterialTheme.typography.bodyMedium, // Bigger
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ═══════════ TEXT CONTENT (Material 3 Expressive: Bigger) ═══════════
            Text(
                text = transcription.text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.3f, // More line spacing
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp)) // More spacing

            // ═══════════ IMPROVE WITH AI BUTTON (Material 3 Expressive: Bigger) ═══════════
            Button(
                onClick = onImproveWithAI,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp), // Bigger button
                enabled = !isImproving,
                shape = RoundedCornerShape(20.dp), // Bigger corners
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp), // More padding
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                if (isImproving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp), // Bigger
                        strokeWidth = 3.dp, // Thicker
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp)) // More spacing
                    Text(
                        "Improving...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp) // Bigger icon
                    )
                    Spacer(modifier = Modifier.width(12.dp)) // More spacing
                    Text(
                        "Improve with AI",
                        style = MaterialTheme.typography.titleMedium, // Bigger
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!isPro) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary,
                            shape = RoundedCornerShape(8.dp) // Bigger corners
                        ) {
                            Text(
                                "PRO",
                                style = MaterialTheme.typography.labelMedium, // Bigger
                                color = MaterialTheme.colorScheme.onTertiary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp) // More padding
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp)) // More spacing

            // ═══════════ ACTION BUTTONS (Material 3 Expressive: Better spacing) ═══════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp) // Reduced spacing between buttons
            ) {
                // Copy button (Material 3 Expressive: Optimized spacing)
                FilledTonalButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(transcription.text))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp) // Reduced horizontal padding
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp) // Slightly smaller icon
                    )
                    Spacer(modifier = Modifier.width(6.dp)) // Reduced spacing
                    Text(
                        "Copy",
                        style = MaterialTheme.typography.labelLarge, // Smaller text style
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Edit button (Material 3 Expressive: Optimized spacing)
                FilledTonalButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp) // Reduced horizontal padding
                ) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp) // Slightly smaller icon
                    )
                    Spacer(modifier = Modifier.width(6.dp)) // Reduced spacing
                    Text(
                        "Edit",
                        style = MaterialTheme.typography.labelLarge, // Smaller text style
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Delete button (Material 3 Expressive: Optimized spacing)
                FilledTonalButton(
                    onClick = onDelete,
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), // Reduced padding
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(22.dp) // Slightly smaller icon
                    )
                }
            }
        }
    }

}