package com.liftley.vodrop.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liftley.vodrop.ui.components.history.EmptyState
import com.liftley.vodrop.ui.components.history.HistoryCard
import com.liftley.vodrop.ui.components.profile.AppDrawerContent
import com.liftley.vodrop.ui.components.recording.RecordingCard
import kotlinx.coroutines.launch


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

    val drawerState = rememberDrawerState(
        initialValue = DrawerValue.Closed
    )
    val scope = rememberCoroutineScope()

    HardwareBackHandler(drawerState.isOpen) {
        if (drawerState.isOpen) {
            scope.launch {
                drawerState.close()
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                isLoggedIn = state.isLoggedIn,
                isPro = state.isPro,
                statusText = state.statusText,
                onSignIn = {
                    scope.launch {
                        drawerState.close()
                        onLoginClick()
                    }
                },
                onSignOut = {
                    scope.launch {
                        drawerState.close()
                        onSignOut()
                    }
                },
                onClose = {
                    scope.launch {
                        drawerState.close()
                    }
                }
            )
        },
        gesturesEnabled = true
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    title = {
                        Box(
                            Modifier
                                .clip(MaterialTheme.shapes.extraLarge)
                                .height(48.dp)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "VoDrop",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch { drawerState.open() }
                            },
                            modifier = Modifier.height(48.dp),
                            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                        {
                            Icon(Icons.Default.Menu, "Menu")
                        }
                    },
                    actions = {
                        FilterChip(
                            selected = false,
                            onClick = {
                                if (state.isPro) viewModel.selectMode(if (state.transcriptionMode == TranscriptionMode.STANDARD) TranscriptionMode.WITH_AI_POLISH else TranscriptionMode.STANDARD)
                                else viewModel.showUpgradeDialog()
                            },
                            label = {
                                Text(
                                    state.transcriptionMode.displayName,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            },
                            enabled = state.isLoggedIn && !state.isLoading,
                            shape = MaterialTheme.shapes.extraLarge,
                            border = null,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
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
                        phase = state.micPhase,
                        currentTranscription = state.currentTranscription,
                        progressMessage = state.progressMessage,
                        onRecordClick = viewModel::onRecordClick,
                        onCancel = {
                            if (state.micPhase is MicPhase.Recording) viewModel.onCancelRecording()
                            else viewModel.cancelProcessing()
                        },
                        onClearError = viewModel::clearError,
                        onCopy = { clipboard.setText(AnnotatedString(state.currentTranscription)) }
                    )
                }
                if (state.history.isEmpty()) {
                    item { EmptyState() }
                } else {
                    item {
                        Text(
                            "Recent Drops",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(state.history, key = { it.id }) { item ->
                        HistoryCard(
                            transcription = item,
                            isPro = state.isPro,
                            isLoading = state.isLoading,
                            isImproving = state.improvingId == item.id,
                            onCopy = { clipboard.setText(AnnotatedString(item.text)) },
                            onEdit = { viewModel.startEdit(item) },
                            onDelete = { viewModel.requestDelete(item.id) },
                            onImproveWithAI = { viewModel.onImproveWithAI(item) }
                        )
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
            confirmButton = {
                Button(
                    onClick = viewModel::confirmDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") } },
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    state.editingTranscription?.let {
        AlertDialog(
            onDismissRequest = viewModel::cancelEdit,
            title = { Text("Edit", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    state.editText,
                    viewModel::updateEditText,
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                )
            },
            confirmButton = { Button(onClick = viewModel::saveEdit) { Text("Save") } },
            dismissButton = { TextButton(onClick = viewModel::cancelEdit) { Text("Cancel") } },
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    if (state.showUpgradeDialog) {
        AlertDialog(
            onDismissRequest = viewModel::hideUpgradeDialog,
            icon = {
                Icon(
                    Icons.Default.Person,
                    null,
                    Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Unlock Pro", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Unlimited transcriptions and AI Polish for $monthlyPrice/month.",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.hideUpgradeDialog(); onPurchaseMonthly() },
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) { Text("Upgrade Now") }
            },
            dismissButton = { TextButton(onClick = viewModel::hideUpgradeDialog) { Text("Maybe Later") } },
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}