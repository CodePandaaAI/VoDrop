package com.liftley.vodrop.ui.components.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liftley.vodrop.ui.main.TranscriptionMode

/**
 * Bottom sheet for selecting transcription mode.
 * All modes are unlocked for testing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionModeSheet(
    currentMode: TranscriptionMode,
    onModeSelected: (TranscriptionMode) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Text(
                text = "Transcription Mode",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Choose how your voice is converted to text",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Mode Options
            ModeOption(
                icon = Icons.Rounded.OfflinePin,
                title = "Offline Only",
                description = "Fast & private. Works without internet.",
                benefits = listOf("🔒 Private - never leaves device", "⚡ Fast processing", "📶 No internet needed"),
                isSelected = currentMode == TranscriptionMode.OFFLINE_ONLY,
                onClick = { onModeSelected(TranscriptionMode.OFFLINE_ONLY) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ModeOption(
                icon = Icons.Rounded.AutoAwesome,
                title = "Offline + AI Polish",
                description = "Local transcription with smart AI cleanup.",
                benefits = listOf("📱 Transcribes on device", "🤖 AI fixes grammar & fillers", "✨ Professional results"),
                isSelected = currentMode == TranscriptionMode.OFFLINE_WITH_AI,
                onClick = { onModeSelected(TranscriptionMode.OFFLINE_WITH_AI) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ModeOption(
                icon = Icons.Rounded.Cloud,
                title = "Cloud + AI",
                description = "Best accuracy. Uses cloud processing.",
                benefits = listOf("☁️ Most accurate transcription", "🌍 Handles accents better", "🚀 Best for important notes"),
                isSelected = currentMode == TranscriptionMode.CLOUD_WITH_AI,
                onClick = { onModeSelected(TranscriptionMode.CLOUD_WITH_AI) }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ModeOption(
    icon: ImageVector,
    title: String,
    description: String,
    benefits: List<String>,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outlineVariant

    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    else
        MaterialTheme.colorScheme.surface

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icon
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = if (isSelected)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Benefits
                benefits.forEach { benefit ->
                    Text(
                        text = benefit,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }

            // Checkmark
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}