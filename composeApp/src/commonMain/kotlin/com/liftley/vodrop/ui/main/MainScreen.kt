package com.liftley.vodrop.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liftley.vodrop.ui.components.recording.RecordingCard
import com.liftley.vodrop.ui.components.history.HistoryCard
import com.liftley.vodrop.ui.components.history.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onLoginClick: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onPurchaseMonthly: () -> Unit = {},
    onPurchaseYearly: () -> Unit = {},
    onRestorePurchases: () -> Unit = {},
    monthlyPrice: String = "$2.99",
    yearlyPrice: String = ""
) {
    val state by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("VoDrop", fontWeight = FontWeight.Bold)
                        Text(state.statusText, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (state.isLoggedIn) onSignOut() else onLoginClick() }) {
                        Icon(Icons.Default.Person, "Account",
                            tint = if (state.isLoggedIn) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::showModeSheet) {
                        Text(state.transcriptionMode.displayName)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                RecordingCard(
                    phase = state.recordingPhase,
                    transcriptionState = state.transcriptionState,
                    currentTranscription = state.currentTranscription,
                    progressMessage = state.progressMessage,
                    transcriptionMode = state.transcriptionMode,
                    error = state.error,
                    onRecordClick = viewModel::onRecordClick,
                    onClearError = viewModel::clearError,
                    onCopyTranscription = { clipboard.setText(AnnotatedString(state.currentTranscription)) }
                )
            }

            if (state.history.isEmpty()) {
                item { EmptyState() }
            } else {
                item { Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(state.history, key = { it.id }) { item ->
                    HistoryCard(
                        transcription = item,
                        isPro = state.isPro,
                        isImproving = state.improvingId == item.id,
                        onEdit = { viewModel.startEdit(item) },
                        onDelete = { viewModel.requestDelete(item.id) },
                        onImproveWithAI = { viewModel.onImproveWithAI(item) }
                    )
                }
            }
        }
    }

    // ═══════════ DIALOGS ═══════════

    if (state.deleteConfirmationId != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Delete?") },
            confirmButton = { Button(onClick = viewModel::confirmDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") } }
        )
    }

    state.editingTranscription?.let { t ->
        var text by remember { mutableStateOf(t.text) }
        AlertDialog(
            onDismissRequest = viewModel::cancelEdit,
            title = { Text("Edit") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = { viewModel.saveEdit(text) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = viewModel::cancelEdit) { Text("Cancel") } }
        )
    }

    if (state.showUpgradeDialog) {
        AlertDialog(
            onDismissRequest = viewModel::hideUpgradeDialog,
            title = { Text("Upgrade to Pro") },
            text = { Text("Get unlimited transcriptions for just $monthlyPrice/month") },
            confirmButton = { Button(onClick = { viewModel.hideUpgradeDialog(); onPurchaseMonthly() }) { Text("Upgrade") } },
            dismissButton = { TextButton(onClick = viewModel::hideUpgradeDialog) { Text("Later") } }
        )
    }

    if (state.showModeSheet) {
        AlertDialog(
            onDismissRequest = viewModel::hideModeSheet,
            title = { Text("Select Mode") },
            text = {
                Column {
                    TranscriptionMode.entries.forEach { mode ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = state.transcriptionMode == mode, onClick = { viewModel.selectMode(mode) })
                            Text(mode.displayName, modifier = Modifier.padding(start = 8.dp))
                            if (mode == TranscriptionMode.WITH_AI_POLISH && !state.isPro) {
                                Text(" (Pro)", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::hideModeSheet) { Text("Done") } }
        )
    }
}