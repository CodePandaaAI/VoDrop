package com.liftley.vodrop.ui.components.history

import androidx.compose.animation.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liftley.vodrop.domain.model.Transcription
import kotlinx.coroutines.delay

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
    var showCopiedFeedback by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // ═══════════ TIMESTAMP ═══════════
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    Icons.Rounded.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = transcription.timestamp,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ═══════════ TEXT CONTENT ═══════════
            Text(
                text = transcription.text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                modifier = Modifier.fillMaxWidth()
            )

            // Show more/less toggle (only for long text)
            if (transcription.text.length > 200) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (expanded) "Show less" else "Show more",
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ═══════════ IMPROVE WITH AI BUTTON ═══════════
            Button(
                onClick = onImproveWithAI,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !isImproving,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                if (isImproving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Improving...", fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Improve with AI",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!isPro) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "PRO",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ═══════════ ACTION BUTTONS ═══════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Copy button (with feedback)
                FilledTonalButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(transcription.text))
                        showCopiedFeedback = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    AnimatedContent(
                        targetState = showCopiedFeedback,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "copy_feedback"
                    ) { copied ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (copied) "Copied!" else "Copy",
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Edit button
                FilledTonalButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit", fontWeight = FontWeight.Medium)
                }

                // Delete button
                FilledTonalButton(
                    onClick = onDelete,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    // Auto-dismiss copy feedback after 1.5 seconds
    if (showCopiedFeedback) {
        LaunchedEffect(Unit) {
            delay(1500)
            showCopiedFeedback = false
        }
    }
}