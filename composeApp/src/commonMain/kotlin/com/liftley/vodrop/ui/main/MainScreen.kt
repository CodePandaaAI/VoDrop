package com.liftley.vodrop.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liftley.vodrop.ui.components.auth.AuthSheet
import com.liftley.vodrop.ui.components.history.EmptyState
import com.liftley.vodrop.ui.components.history.HistoryCard
import com.liftley.vodrop.ui.components.recording.RecordingCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onLoginClick: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onPurchaseMonthly: () -> Unit = {},
    monthlyPrice: String = "$2.99"
) {
    val state by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    var showAuthSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.statusText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    Box {
                        IconButton(onClick = { if (state.isLoggedIn) showMenu = true else showAuthSheet = true }) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = if (state.isLoggedIn) "Profile" else "Sign In",
                                modifier = Modifier.size(28.dp),
                                tint = if (state.isLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, shape = MaterialTheme.shapes.large) {
                            DropdownMenuItem(
                                text = { Text("Sign Out") },
                                onClick = { showMenu = false; onSignOut() },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) }
                            )
                        }
                    }
                },
                actions = {
                    FilterChip(
                        selected = state.transcriptionMode == TranscriptionMode.WITH_AI_POLISH,
                        onClick = {
                            if (state.isPro) viewModel.selectMode(if (state.transcriptionMode == TranscriptionMode.STANDARD) TranscriptionMode.WITH_AI_POLISH else TranscriptionMode.STANDARD)
                            else viewModel.showUpgradeDialog()
                        },
                        label = { Text(state.transcriptionMode.displayName, fontWeight = FontWeight.Bold) },
                        enabled = state.isLoggedIn,
                        shape = MaterialTheme.shapes.extraLarge,
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
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
                item { Text("Recent Drops", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
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

    // Auth Sheet
    if (showAuthSheet) {
        AuthSheet(onDismiss = { showAuthSheet = false }, onSignInClick = { showAuthSheet = false; onLoginClick() })
    }

    // Delete Dialog
    if (state.deleteConfirmationId != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Delete?", fontWeight = FontWeight.Bold) },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                Button(onClick = viewModel::confirmDelete, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") } },
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    // Edit Dialog
    state.editingTranscription?.let { t ->
        var text by remember { mutableStateOf(t.text) }
        AlertDialog(
            onDismissRequest = viewModel::cancelEdit,
            title = { Text("Edit", fontWeight = FontWeight.Bold) },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) },
            confirmButton = { Button(onClick = { viewModel.saveEdit(text) }) { Text("Save", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = viewModel::cancelEdit) { Text("Cancel") } },
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    // Upgrade Dialog
    if (state.showUpgradeDialog) {
        AlertDialog(
            onDismissRequest = viewModel::hideUpgradeDialog,
            icon = { Icon(Icons.Default.Person, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Unlock Pro", fontWeight = FontWeight.Bold) },
            text = { Text("Get unlimited transcriptions and AI Polish for just $monthlyPrice/month.", textAlign = TextAlign.Center) },
            confirmButton = {
                Button(onClick = { viewModel.hideUpgradeDialog(); onPurchaseMonthly() }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text("Upgrade Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::hideUpgradeDialog) { Text("Maybe Later") } },
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}