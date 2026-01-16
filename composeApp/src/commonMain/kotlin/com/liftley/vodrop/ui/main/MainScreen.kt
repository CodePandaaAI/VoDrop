package com.liftley.vodrop.ui.main

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
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
import com.liftley.vodrop.ui.components.history.EmptyState
import com.liftley.vodrop.ui.components.history.HistoryCard
import com.liftley.vodrop.ui.components.dialogs.ProfileDialog
import com.liftley.vodrop.ui.components.recording.RecordingCard
import com.liftley.vodrop.ui.components.dialogs.UpgradeDialog
import com.liftley.vodrop.ui.components.dialogs.DeleteDialog
import com.liftley.vodrop.ui.components.dialogs.EditDialog
import com.liftley.vodrop.ui.components.dialogs.TranscriptionModeSheet
import com.liftley.vodrop.ui.settings.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onLoginClick: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onPurchaseMonthly: () -> Unit = {},
    onPurchaseYearly: () -> Unit = {},
    onRestorePurchases: () -> Unit = {},
    monthlyPrice: String = "₹99/month",
    yearlyPrice: String = "₹799/year"
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    // Show Settings Screen or Main Content
    AnimatedContent(
        targetState = uiState.showSettings,
        transitionSpec = {
            if (targetState) {
                slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
            } else {
                slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
            }
        },
        label = "settings_transition"
    ) { showSettings ->
        if (showSettings) {
            SettingsScreen(
                currentMode = uiState.transcriptionMode,
                currentStyle = uiState.cleanupStyle,
                userName = uiState.userName,
                isPro = uiState.isPro,
                onModeChange = viewModel::selectTranscriptionMode,
                onStyleChange = viewModel::setCleanupStyle,
                onNameChange = viewModel::setUserName,
                onNavigateBack = viewModel::hideSettings
            )
        } else {
            MainContent(
                uiState = uiState,
                viewModel = viewModel,
                clipboardManager = clipboardManager,
                onLoginClick = onLoginClick,
                onSignOut = onSignOut,
                onPurchaseMonthly = onPurchaseMonthly,
                onPurchaseYearly = onPurchaseYearly,
                onRestorePurchases = onRestorePurchases,
                monthlyPrice = monthlyPrice,
                yearlyPrice = yearlyPrice
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainContent(
    uiState: MainUiState,
    viewModel: MainViewModel,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onLoginClick: () -> Unit,
    onSignOut: () -> Unit,
    onPurchaseMonthly: () -> Unit,
    onPurchaseYearly: () -> Unit,
    onRestorePurchases: () -> Unit,
    monthlyPrice: String,
    yearlyPrice: String
) {
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
                isLoggedIn = uiState.isLoggedIn,
                isPro = uiState.isPro,
                transcriptionMode = uiState.transcriptionMode,
                remainingFree = uiState.remainingFreeTranscriptions,
                onProfileClick = {
                    if (uiState.isLoggedIn) {
                        viewModel.showProfileDialog()
                    } else {
                        onLoginClick()
                    }
                },
                onModeClick = viewModel::showTranscriptionModeSheet,
                onSettingsClick = viewModel::showSettings
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
                    transcriptionState = uiState.transcriptionState,
                    currentTranscription = uiState.currentTranscription,
                    progressMessage = uiState.progressMessage,
                    transcriptionMode = uiState.transcriptionMode,
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
    isLoggedIn: Boolean,
    isPro: Boolean,
    transcriptionMode: TranscriptionMode,
    remainingFree: Int,
    onProfileClick: () -> Unit,
    onModeClick: () -> Unit,
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mode toggle (tappable)
                    Surface(
                        onClick = onModeClick,
                        color = when (transcriptionMode) {
                            TranscriptionMode.STANDARD -> MaterialTheme.colorScheme.primaryContainer
                            TranscriptionMode.WITH_AI_POLISH -> MaterialTheme.colorScheme.tertiaryContainer
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${transcriptionMode.emoji} ${transcriptionMode.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    // PRO badge or remaining count
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
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "$remainingFree left",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    contentDescription = "Settings",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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

    // Transcription Mode Sheet
    if (uiState.showTranscriptionModeSheet) {
        TranscriptionModeSheet(
            currentMode = uiState.transcriptionMode,
            isPro = uiState.isPro,
            onModeSelected = viewModel::selectTranscriptionMode,
            onDismiss = viewModel::hideTranscriptionModeSheet
        )
    }
}