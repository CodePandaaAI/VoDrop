package com.liftley.vodrop.ui.components.mode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liftley.vodrop.ui.main.TranscriptionMode
import com.liftley.vodrop.ui.theme.Dimens

/**
 * **Mode Selection Bottom Sheet**
 * 
 * Allows the user to choose between:
 * 1. [TranscriptionMode.STANDARD]: Raw transcription only via Chirp 3.
 * 2. [TranscriptionMode.WITH_AI_POLISH]: Chirp 3 + Gemini 3 Flash cleanup.
 * 
 * Persists choice in ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionModeSheet(
    sheetState: SheetState,
    currentMode: TranscriptionMode,
    onModeSelected: (TranscriptionMode) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.large24)
                .padding(bottom = Dimens.large24)
        ) {
            Text(
                "Select Mode",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(Dimens.small16))

            ModeOption(
                icon = Icons.Rounded.Speed,
                title = "Standard",
                subtitle = "Fast, word-for-word transcription",
                isSelected = currentMode == TranscriptionMode.STANDARD,
                onClick = { onModeSelected(TranscriptionMode.STANDARD) }
            )

            Spacer(Modifier.height(Dimens.extraSmall8))

            ModeOption(
                icon = Icons.Rounded.AutoAwesome,
                title = "AI Polish",
                subtitle = "Structured, clean & formatted",
                isSelected = currentMode == TranscriptionMode.WITH_AI_POLISH,
                onClick = { onModeSelected(TranscriptionMode.WITH_AI_POLISH) }
            )
        }
    }
}

@Composable
private fun ModeOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(onClick = onClick)
            .padding(Dimens.small16),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.width(Dimens.small16))

        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isSelected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
