package com.liftley.vodrop.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liftley.vodrop.ui.components.profile.AppDrawerContent
import com.liftley.vodrop.ui.components.history.*
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
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Sync drawer with ViewModel
    LaunchedEffect(state.isDrawerOpen) { if (state.isDrawerOpen) drawerState.open() else drawerState.close() }
    LaunchedEffect(drawerState.currentValue) { if (drawerState.isClosed) viewModel.closeDrawer() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                isLoggedIn = state.isLoggedIn,
                isPro = state.isPro,
                statusText = state.statusText,
                onSignIn = { viewModel.closeDrawer(); onLoginClick() },
                onSignOut = { viewModel.closeDrawer(); onSignOut() },
                onClose = { viewModel.closeDrawer() }
            )
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    title = { Text("VoDrop", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.openDrawer() }) {
                            Icon(Icons.Default.Menu, "Menu", Modifier.size(28.dp))
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
                            colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface, selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.height(48.dp).padding(end = 8.dp)
                        )
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    RecordingCard(
                        phase = state.recordingPhase,
                        transcriptionState = state.transcriptionState,
                        currentTranscription = state.currentTranscription,
                        progressMessage = state.progressMessage,
                        mode = state.transcriptionMode,
                        error = state.error,
                        onRecordClick = viewModel::onRecordClick,
                        onClearError = viewModel::clearError,
                        onCopy = { clipboard.setText(AnnotatedString(state.currentTranscription)) }
                    )
                }
                if (state.history.isEmpty()) {
                    item { EmptyState() }
                } else {
                    item { Text("Recent Drops", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                    items(state.history, key = { it.id }) { item ->
                        HistoryCard(item, state.isPro, state.improvingId == item.id, { viewModel.startEdit(item) }, { viewModel.requestDelete(item.id) }, { viewModel.onImproveWithAI(item) })
                    }
                }
            }
        }
    }

    // Dialogs
    if (state.deleteConfirmationId != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Delete?", fontWeight = FontWeight.Bold) },
            text = { Text("This cannot be undone.") },
            confirmButton = { Button(onClick = viewModel::confirmDelete, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") } },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") } },
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    state.editingTranscription?.let {
        AlertDialog(
            onDismissRequest = viewModel::cancelEdit,
            title = { Text("Edit", fontWeight = FontWeight.Bold) },
            text = { OutlinedTextField(state.editText, viewModel::updateEditText, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) },
            confirmButton = { Button(onClick = viewModel::saveEdit) { Text("Save") } },
            dismissButton = { TextButton(onClick = viewModel::cancelEdit) { Text("Cancel") } },
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    if (state.showUpgradeDialog) {
        AlertDialog(
            onDismissRequest = viewModel::hideUpgradeDialog,
            icon = { Icon(Icons.Default.Person, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Unlock Pro", fontWeight = FontWeight.Bold) },
            text = { Text("Unlimited transcriptions and AI Polish for $monthlyPrice/month.", textAlign = TextAlign.Center) },
            confirmButton = { Button(onClick = { viewModel.hideUpgradeDialog(); onPurchaseMonthly() }, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("Upgrade Now") } },
            dismissButton = { TextButton(onClick = viewModel::hideUpgradeDialog) { Text("Maybe Later") } },
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}