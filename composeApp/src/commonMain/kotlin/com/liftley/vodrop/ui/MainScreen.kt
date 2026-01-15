package com.liftley.vodrop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.liftley.vodrop.model.Transcription
import com.liftley.vodrop.stt.ModelState
import com.liftley.vodrop.stt.WhisperModel

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    // Model Selection Dialog
    if (uiState.showModelSelector) {
        ModelSelectorDialog(
            isFirstLaunch = uiState.isFirstLaunch,
            currentModel = uiState.selectedModel,
            onModelSelected = { viewModel.selectModel(it) },
            onDismiss = { viewModel.hideModelSelector() }
        )
    }

    // Delete Confirmation Dialog
    if (uiState.showDeleteConfirmation != null) {
        DeleteConfirmationDialog(
            onConfirm = { viewModel.confirmDeleteTranscription() },
            onDismiss = { viewModel.cancelDeleteTranscription() }
        )
    }

    // Edit Dialog
    uiState.editingTranscription?.let { transcription ->
        EditTranscriptionDialog(
            transcription = transcription,
            onSave = { viewModel.saveEditTranscription(it) },
            onDismiss = { viewModel.cancelEditTranscription() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar with Settings
        TopBar(
            selectedModel = uiState.selectedModel,
            onSettingsClick = { viewModel.showModelSelector() }
        )

        // Main Content
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Recording Section
            item {
                RecordingSection(
                    recordingPhase = uiState.recordingPhase,
                    modelState = uiState.modelState,
                    currentTranscription = uiState.currentTranscription,
                    error = uiState.error,
                    onRecordClick = { viewModel.onRecordClick() },
                    onClearError = { viewModel.clearError() },
                    onCopyTranscription = {
                        clipboardManager.setText(AnnotatedString(uiState.currentTranscription))
                    }
                )
            }

            // History Section Header
            if (uiState.history.isNotEmpty()) {
                item {
                    Text(
                        text = "History",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                // History Items
                items(uiState.history, key = { it.id }) { transcription ->
                    HistoryItem(
                        transcription = transcription,
                        onCopy = { clipboardManager.setText(AnnotatedString(transcription.text)) },
                        onEdit = { viewModel.startEditTranscription(transcription) },
                        onDelete = { viewModel.requestDeleteTranscription(transcription.id) }
                    )
                }
            } else {
                item {
                    EmptyHistoryPlaceholder()
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    selectedModel: WhisperModel,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "VoDrop",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "${selectedModel.emoji} ${selectedModel.displayName} Model",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun RecordingSection(
    recordingPhase: RecordingPhase,
    modelState: ModelState,
    currentTranscription: String,
    error: String?,
    onRecordClick: () -> Unit,
    onClearError: () -> Unit,
    onCopyTranscription: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status
            Text(
                text = getStatusTitle(recordingPhase, modelState),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = getStatusSubtitle(recordingPhase, modelState),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Download Progress
            if (modelState is ModelState.Downloading) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { modelState.progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                )
                Text(
                    text = "${(modelState.progress * 100).toInt()}%",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Record Button
            RecordButton(
                recordingPhase = recordingPhase,
                modelState = modelState,
                onClick = onRecordClick
            )

            // Current Transcription
            if (currentTranscription.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = currentTranscription,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onCopyTranscription) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy")
                        }
                    }
                }
            }

            // Error
            error?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(it, modifier = Modifier.weight(1f), fontSize = 14.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = onClearError) { Text("OK") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordButton(recordingPhase: RecordingPhase, modelState: ModelState, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isEnabled = recordingPhase == RecordingPhase.READY ||
            recordingPhase == RecordingPhase.LISTENING ||
            recordingPhase == RecordingPhase.COMPLETE

    val scale by animateFloatAsState(if (isPressed) 0.92f else 1f, tween(100))
    val backgroundColor by animateColorAsState(
        when {
            modelState is ModelState.Downloading -> Color(0xFF3B82F6)
            recordingPhase == RecordingPhase.LISTENING -> Color(0xFFEF4444)
            recordingPhase == RecordingPhase.PROCESSING -> Color(0xFF6B7280)
            else -> Color(0xFF8B5CF6)
        }, tween(300)
    )

    Box(
        modifier = Modifier.size(140.dp).scale(scale).clip(CircleShape).background(backgroundColor)
            .clickable(interactionSource, null, isEnabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when {
                modelState is ModelState.Downloading -> "⬇️"
                modelState is ModelState.Loading -> "⏳"
                recordingPhase == RecordingPhase.LISTENING -> "⏹"
                recordingPhase == RecordingPhase.PROCESSING -> "⏳"
                else -> "🎤"
            },
            fontSize = 48.sp
        )
    }
}

@Composable
private fun HistoryItem(
    transcription: Transcription,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = transcription.timestamp,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = transcription.text,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryPlaceholder() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📝", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text("No transcriptions yet", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Tap the mic to start recording", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun ModelSelectorDialog(
    isFirstLaunch: Boolean,
    currentModel: WhisperModel,
    onModelSelected: (WhisperModel) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = { if (!isFirstLaunch) onDismiss() }) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = if (isFirstLaunch) "🎙️ Welcome to VoDrop" else "Change Model",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isFirstLaunch)
                        "To turn your voice into text, choose an AI model. This downloads once and stays on your device."
                    else
                        "Select a different model for transcription.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isFirstLaunch) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚡ Requires internet for one-time download",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                WhisperModel.entries.forEach { model ->
                    ModelOption(
                        model = model,
                        isSelected = model == currentModel && !isFirstLaunch,
                        onClick = { onModelSelected(model) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (!isFirstLaunch) {
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelOption(model: WhisperModel, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    Card(
        modifier = Modifier.fillMaxWidth().border(2.dp, borderColor, RoundedCornerShape(16.dp)).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(model.emoji, fontSize = 32.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(model.displayName, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(model.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(model.sizeDisplay, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Transcription?") },
        text = { Text("This cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditTranscriptionDialog(
    transcription: Transcription,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(transcription.text) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Transcription") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                maxLines = 10
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun getStatusTitle(phase: RecordingPhase, modelState: ModelState) = when {
    modelState is ModelState.Downloading -> "Downloading..."
    modelState is ModelState.Loading -> "Loading..."
    phase == RecordingPhase.LISTENING -> "Listening..."
    phase == RecordingPhase.PROCESSING -> "Processing..."
    phase == RecordingPhase.COMPLETE -> "Done!"
    phase == RecordingPhase.READY -> "Ready"
    else -> "Getting ready..."
}

private fun getStatusSubtitle(phase: RecordingPhase, modelState: ModelState) = when {
    modelState is ModelState.Downloading -> "One-time download, please wait"
    modelState is ModelState.Loading -> "Preparing speech recognition"
    phase == RecordingPhase.LISTENING -> "Speak now..."
    phase == RecordingPhase.PROCESSING -> "Transcribing your voice"
    phase == RecordingPhase.COMPLETE -> "Tap mic to record again"
    phase == RecordingPhase.READY -> "Tap the mic to start"
    else -> "Please wait..."
}