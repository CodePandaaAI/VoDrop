package com.liftley.vodrop.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import com.liftley.vodrop.ui.components.history.EmptyState
import com.liftley.vodrop.ui.components.history.HistoryCard
import com.liftley.vodrop.ui.components.profile.AppDrawerContent
import com.liftley.vodrop.ui.components.recording.RecordingCard
import com.liftley.vodrop.ui.components.reusable.ExpressiveIconButton
import com.liftley.vodrop.ui.components.reusable.TranscriptionModeBox
import com.liftley.vodrop.ui.theme.Dimens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val appState by viewModel.appState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    val clipboard = LocalClipboardManager.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    HardwareBackHandler(drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                statusText = uiState.statusText,
                onClose = { scope.launch { drawerState.close() } }
            )
        },
        gesturesEnabled = true
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            topBar = {
                TopAppBar(
                    modifier = Modifier.padding(horizontal = Dimens.extraSmall8),
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
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = Dimens.small16)
                            )
                        }
                    },
                    navigationIcon = {
                        ExpressiveIconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu"
                        )
                    },
                    actions = {
                        TranscriptionModeBox(
                            onClick = {
                                viewModel.selectMode(
                                    if (viewModel.currentMode == TranscriptionMode.STANDARD)
                                        TranscriptionMode.WITH_AI_POLISH
                                    else
                                        TranscriptionMode.STANDARD
                                )
                            }
                        ) {
                            Text(
                                viewModel.currentMode.displayName,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = Dimens.extraSmall8)
                            )
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = Dimens.large24, horizontal = Dimens.small16),
                verticalArrangement = Arrangement.spacedBy(Dimens.large24)
            ) {
                item {
                    RecordingCard(
                        appState = appState,
                        onRecordClick = viewModel::onRecordClick,
                        onCancel = viewModel::onCancel,
                        onClearError = viewModel::clearError,
                        onCopy = { text -> clipboard.setText(AnnotatedString(text)) }
                    )
                }
                if (uiState.history.isEmpty()) {
                    item { EmptyState() }
                } else {
                    item {
                        Text(
                            "Recent Drops",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(uiState.history, key = { it.id }) { item ->
                        HistoryCard(
                            transcription = item,
                            isImproving = uiState.improvingId == item.id,
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

    // Delete Dialog
    if (uiState.deleteConfirmationId != null) {
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

    // Edit Dialog
    uiState.editingTranscription?.let {
        AlertDialog(
            onDismissRequest = viewModel::cancelEdit,
            title = { Text("Edit", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    uiState.editText,
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
}