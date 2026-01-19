package com.liftley.vodrop.ui.components.history

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Empty state placeholder when no transcriptions exist
 * Material 3 Expressive: Bigger icon, bolder text, generous spacing
 */
@Composable
fun EmptyState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp, horizontal = 32.dp), // Better vertical breathing room
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Large icon with subtle color
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = null,
                modifier = Modifier.size(120.dp), // Even bigger for prominence
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) // Slightly more subtle
            )

            Spacer(modifier = Modifier.height(40.dp)) // Generous spacing

            // Primary message - Bold & Clear
            Text(
                text = "No Drops Yet",
                style = MaterialTheme.typography.headlineMedium, // Bigger for M3 Expressive
                fontWeight = FontWeight.ExtraBold, // Extra bold for emphasis
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp)) // Good spacing

            // Secondary message - Softer & Instructional
            Text(
                text = "Tap the microphone to start\nyour first transcription",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.4f // Better readability
            )
        }
    }
}