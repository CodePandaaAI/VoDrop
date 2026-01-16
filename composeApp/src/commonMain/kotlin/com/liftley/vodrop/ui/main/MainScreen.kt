package com.liftley.vodrop.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liftley.vodrop.ui.components.history.EmptyState
import com.liftley.vodrop.ui.components.history.HistoryCard
import com.liftley.vodrop.ui.components.dialogs.ProfileDialog
import com.liftley.vodrop.ui.components.recording.RecordingCard
import com.liftley.vodrop.ui.components.dialogs.UpgradeDialog
import com.liftley.vodrop.ui.components.dialogs.DeleteDialog
import com.liftley.vodrop.ui.components.dialogs.EditDialog
import com.liftley.vodrop.ui.components.dialogs.ModelSelectorDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onLoginClick: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onPurchaseMonthly: () -> Unit = {},
    onPurchaseYearly: () -> Unit = {},
    onRestorePurchases: () -> Unit = {},
    monthlyPrice: String = "₹129/month",
    yearlyPrice: String = "₹999/year"
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    // Dialogs
    DialogHost(
        uiState = uiState,
        viewModel = viewModel,
        onLoginClick = onLoginClick,
        onSignOut = onSignOut,
        onPurchaseMonthly = onPurchaseMonthly,
        onPurchaseYearly = onPurchaseYearly,
        onRestorePurchases = onRestorePurchases,
        monthlyPrice = monthlyPrice,
        yearlyPrice = yearlyPrice
    )

    Scaffold(
        topBar = {
            TopBar(
                modelName = "${uiState.selectedModel.emoji} ${uiState.selectedModel.displayName}",
                isLoggedIn = uiState.isLoggedIn,
                isPro = uiState.isPro,
                transcriptionMode = uiState.transcriptionMode,
                onSettingsClick = viewModel::showModelSelector,
                onProfileClick = {
                    if (uiState.isLoggedIn) {
                        viewModel.showProfileDialog()
                    } else {
                        onLoginClick()
                    }
                },
                onModeClick = viewModel::cycleTranscriptionMode
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
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
                        isPro = uiState.isPro,
                        isImproving = uiState.improvingTranscriptionId == transcription.id,
                        onEdit = { viewModel.startEdit(transcription) },
                        onDelete = { viewModel.requestDelete(transcription.id) },
                        onImproveWithAI = { viewModel.onImproveWithAI(transcription) }
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
    isLoggedIn: Boolean,
    isPro: Boolean,
    transcriptionMode: TranscriptionMode,
    onSettingsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onModeClick: () -> Unit
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Model badge
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

                    // Mode toggle (tappable)
                    Surface(
                        onClick = onModeClick,
                        color = when (transcriptionMode) {
                            TranscriptionMode.OFFLINE_ONLY -> MaterialTheme.colorScheme.surfaceVariant
                            TranscriptionMode.OFFLINE_WITH_AI -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.tertiaryContainer
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = when (transcriptionMode) {
                                TranscriptionMode.OFFLINE_ONLY -> "📱 Offline"
                                TranscriptionMode.OFFLINE_WITH_AI -> "📱+🤖"
                                else -> "☁️ Cloud"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // PRO badge
                    if (isPro) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "PRO",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onProfileClick) {
                Icon(
                    Icons.Rounded.AccountCircle,
                    contentDescription = if (isLoggedIn) "Profile" else "Sign in",
                    modifier = Modifier.size(28.dp),
                    tint = if (isLoggedIn)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Rounded.Settings,
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
    viewModel: MainViewModel,
    onLoginClick: () -> Unit,
    onSignOut: () -> Unit,
    onPurchaseMonthly: () -> Unit,
    onPurchaseYearly: () -> Unit,
    onRestorePurchases: () -> Unit,
    monthlyPrice: String,
    yearlyPrice: String
) {
    // Model Selector Dialog
    if (uiState.showModelSelector) {
        ModelSelectorDialog(
            isFirstLaunch = uiState.isFirstLaunch,
            currentModel = uiState.selectedModel,
            onSelect = viewModel::selectModel,
            onDismiss = viewModel::hideModelSelector
        )
    }

    // Delete Confirmation Dialog
    if (uiState.deleteConfirmationId != null) {
        DeleteDialog(
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete
        )
    }

    // Edit Dialog
    uiState.editingTranscription?.let { transcription ->
        EditDialog(
            transcription = transcription,
            onSave = viewModel::saveEdit,
            onDismiss = viewModel::cancelEdit
        )
    }

    // Profile Dialog
    if (uiState.showProfileDialog) {
        ProfileDialog(
            userName = uiState.userName,
            userEmail = uiState.userEmail,
            isPro = uiState.isPro,
            onUpgradeClick = {
                viewModel.hideProfileDialog()
                viewModel.showUpgradeDialog()
            },
            onRestorePurchases = {
                viewModel.hideProfileDialog()
                onRestorePurchases()
            },
            onSignOut = {
                viewModel.hideProfileDialog()
                onSignOut()
            },
            onDismiss = viewModel::hideProfileDialog
        )
    }

    // Upgrade Dialog
    if (uiState.showUpgradeDialog) {
        UpgradeDialog(
            monthlyPrice = monthlyPrice,
            yearlyPrice = yearlyPrice,
            isLoading = false,
            onSelectMonthly = {
                viewModel.hideUpgradeDialog()
                onPurchaseMonthly()
            },
            onSelectYearly = {
                viewModel.hideUpgradeDialog()
                onPurchaseYearly()
            },
            onDismiss = viewModel::hideUpgradeDialog,
            onRestorePurchases = {
                viewModel.hideUpgradeDialog()
                onRestorePurchases()
            }
        )
    }

    // Login Prompt Dialog
    if (uiState.showLoginPrompt) {
        AlertDialog(
            onDismissRequest = viewModel::hideLoginPrompt,
            icon = { Icon(Icons.Rounded.AccountCircle, null) },
            title = { Text("Sign in Required") },
            text = { Text("Please sign in to use AI features and sync your subscription across devices.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.hideLoginPrompt()
                    onLoginClick()
                }) {
                    Text("Sign in with Google")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::hideLoginPrompt) {
                    Text("Cancel")
                }
            }
        )
    }
}