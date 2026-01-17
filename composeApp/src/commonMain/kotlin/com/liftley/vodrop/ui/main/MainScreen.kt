package com.liftley.vodrop.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liftley.vodrop.ui.components.history.EmptyState
import com.liftley.vodrop.ui.components.history.HistoryCard
import com.liftley.vodrop.ui.components.recording.RecordingCard

/**
 * Main screen of VoDrop app.
 *
 * Layout:
 * - Top bar with user account (left) and mode selector (right)
 * - Recording card for voice capture
 * - History list of previous transcriptions
 *
 * Dialogs:
 * - Delete confirmation
 * - Edit transcription
 * - Upgrade to Pro
 * - Mode selection
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onLoginClick: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onPurchaseMonthly: () -> Unit = {},
    monthlyPrice: String = "$2.99"
    // TODO: Add onRestorePurchases when implementing purchase restoration
    // TODO: Add yearlyPrice when yearly plan is added post-v1
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
                    if (state.isLoggedIn) {
                        // Logged in: Show profile menu
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.Person, "Profile",
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Sign Out") },
                                    onClick = { showMenu = false; onSignOut() },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) }
                                )
                            }
                        }
                    } else {
                        // Not logged in: Show login button
                        TextButton(onClick = onLoginClick) {
                            Text("Sign In", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                },
                actions = {
                    // Simple mode toggle (no dialog needed)
                    FilterChip(
                        selected = state.transcriptionMode == TranscriptionMode.WITH_AI_POLISH,
                        onClick = {
                            if (state.isPro) {
                                viewModel.selectMode(
                                    if (state.transcriptionMode == TranscriptionMode.STANDARD)
                                        TranscriptionMode.WITH_AI_POLISH
                                    else TranscriptionMode.STANDARD
                                )
                            } else {
                                viewModel.showUpgradeDialog()
                            }
                        },
                        label = { Text(state.transcriptionMode.displayName) },
                        enabled = state.isLoggedIn
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ═══════════ RECORDING SECTION ═══════════
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

            // ═══════════ HISTORY SECTION ═══════════
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

    // Delete Confirmation Dialog
    if (state.deleteConfirmationId != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Delete?") },
            confirmButton = { Button(onClick = viewModel::confirmDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") } }
        )
    }

    // Edit Transcription Dialog
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

    // Upgrade to Pro Dialog
    if (state.showUpgradeDialog) {
        AlertDialog(
            onDismissRequest = viewModel::hideUpgradeDialog,
            title = { Text("Upgrade to Pro") },
            text = { Text("Get unlimited transcriptions for just $monthlyPrice/month") },
            confirmButton = { Button(onClick = { viewModel.hideUpgradeDialog(); onPurchaseMonthly() }) { Text("Upgrade") } },
            dismissButton = { TextButton(onClick = viewModel::hideUpgradeDialog) { Text("Later") } }
        )
    }

    // REMOVED: Mode selection dialog - now using FilterChip toggle in top bar
}