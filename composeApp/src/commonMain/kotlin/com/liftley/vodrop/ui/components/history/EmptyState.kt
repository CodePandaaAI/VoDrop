package com.liftley.vodrop.ui.components.history

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liftley.vodrop.ui.theme.Dimens
import org.jetbrains.compose.resources.painterResource
import vodrop.composeapp.generated.resources.Res
import vodrop.composeapp.generated.resources.empty_state

/**
 * **Empty State View**
 * 
 * Shown when there are no transcription history items.
 * Uses a vector resource and a helpful prompt.
 */
@Composable
fun EmptyState() {
    Box(
        Modifier.fillMaxWidth().padding(vertical = Dimens.small16),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(Res.drawable.empty_state),
                contentDescription = null,
                modifier = Modifier.size(144.dp)
            )
            Text(
                "No Drops Yet",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(Dimens.small16))
            Text(
                "Tap the microphone to start\nyour first transcription",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}