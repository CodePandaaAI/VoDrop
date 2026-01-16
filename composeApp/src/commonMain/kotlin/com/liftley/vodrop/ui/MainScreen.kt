package com.liftley.vodrop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liftley.vodrop.ui.components.EmptyState
import com.liftley.vodrop.ui.components.HistoryCard
import com.liftley.vodrop.ui.components.RecordingCard
import com.liftley.vodrop.ui.components.dialogs.DeleteDialog
import com.liftley.vodrop.ui.components.dialogs.EditDialog
import com.liftley.vodrop.ui.components.dialogs.ModelSelectorDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    // Dialogs
    DialogHost(
        uiState = uiState,
        onModelSelected = viewModel::selectModel,
        onDismissModelSelector = viewModel::hideModelSelector,
        onConfirmDelete = viewModel::confirmDelete,
        onCancelDelete = viewModel::cancelDelete,
        onSaveEdit = viewModel::saveEdit,
        onCancelEdit = viewModel::cancelEdit
    )

    Scaffold(
        topBar = {
            TopBar(
                modelName = "${uiState.selectedModel.emoji} ${uiState.selectedModel.displayName}",
                onSettingsClick = viewModel::showModelSelector
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)  // More breathing room
        ) {
            // Recording Card
            item {
                RecordingCard(
                    phase = uiState.recordingPhase,
                    modelState = uiState.modelState,
                    currentTranscription = uiState.currentTranscription,
                    error = uiState.error,
                    onRecordClick = viewModel::onRecordClick,
                    onClearError = viewModel::clearError,
                    onCopyTranscription = {
                        clipboardManager.setText(AnnotatedString(uiState.currentTranscription))
                    }
                )
            }

            // History Section
            if (uiState.history.isNotEmpty()) {
                item {
                    Text(
                        text = "History",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                    )
                }

                items(uiState.history, key = { it.id }) { transcription ->
                    HistoryCard(
                        transcription = transcription,
                        onCopy = { clipboardManager.setText(AnnotatedString(transcription.text)) },
                        onEdit = { viewModel.startEdit(transcription) },
                        onDelete = { viewModel.requestDelete(transcription.id) }
                    )
                }
            } else {
                item { EmptyState() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    modelName: String,
    onSettingsClick: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "VoDrop",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = modelName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Rounded.Settings,  // Rounded icon
                    contentDescription = "Settings"
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
private fun DialogHost(
    uiState: MainUiState,
    onModelSelected: (com.liftley.vodrop.stt.WhisperModel) -> Unit,
    onDismissModelSelector: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onSaveEdit: (String) -> Unit,
    onCancelEdit: () -> Unit
) {
    // Model Selector Dialog
    if (uiState.showModelSelector) {
        ModelSelectorDialog(
            isFirstLaunch = uiState.isFirstLaunch,
            currentModel = uiState.selectedModel,
            onSelect = onModelSelected,
            onDismiss = onDismissModelSelector
        )
    }

    // Delete Confirmation Dialog
    if (uiState.deleteConfirmationId != null) {
        DeleteDialog(
            onConfirm = onConfirmDelete,
            onDismiss = onCancelDelete
        )
    }

    // Edit Dialog
    uiState.editingTranscription?.let { transcription ->
        EditDialog(
            transcription = transcription,
            onSave = onSaveEdit,
            onDismiss = onCancelEdit
        )
    }
}