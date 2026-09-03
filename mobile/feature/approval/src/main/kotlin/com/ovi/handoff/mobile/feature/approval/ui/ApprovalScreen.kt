package com.ovi.handoff.mobile.feature.approval.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ovi.handoff.mobile.core.components.StatusPill
import com.ovi.handoff.mobile.core.theme.HandoffTheme
import com.ovi.handoff.mobile.core.theme.RiskCriticalColor
import com.ovi.handoff.mobile.core.theme.ShapeExtraLarge
import com.ovi.handoff.mobile.core.theme.ShapeFull
import com.ovi.handoff.mobile.feature.approval.R
import com.ovi.handoff.mobile.feature.approval.ui.components.ActiveSessionDashboard
import com.ovi.handoff.mobile.feature.approval.ui.components.AuditHistoryView
import com.ovi.handoff.mobile.feature.approval.ui.components.LiveRequestView
import com.ovi.handoff.mobile.feature.approval.ui.components.UnpairedHomeView
import com.ovi.handoff.mobile.feature.approval.ui.settings.SettingsScreen
import com.ovi.handoff.mobile.feature.approval.viewmodel.ApprovalTab
import com.ovi.handoff.mobile.feature.approval.viewmodel.ApprovalViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ApprovalScreen(
    pairId: String? = null,
    onNavigateToPairingQr: () -> Unit = {},
    viewModel: ApprovalViewModel = koinViewModel { parametersOf(pairId) }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showHaltDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.notificationMessage) {
        uiState.notificationMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearNotification()
        }
    }

    val activeProjectOrWorkspace: String? = uiState.activeProjectOrWorkspace

    var showClearAuditDialog by remember { mutableStateOf(false) }

    HandoffTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 3.dp
                ) {
                    // Home
                    NavigationBarItem(
                        selected = uiState.selectedTab == ApprovalTab.HOME,
                        onClick = { viewModel.switchTab(ApprovalTab.HOME) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (uiState.displayedRequest != null) {
                                        Badge {
                                            Text(stringResource(R.string.badge_single_request))
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (uiState.selectedTab == ApprovalTab.HOME) {
                                        Icons.Filled.Shield
                                    } else {
                                        Icons.Outlined.Shield
                                    },
                                    contentDescription = stringResource(R.string.nav_home)
                                )
                            }
                        },
                        label = { Text(stringResource(R.string.nav_home)) }
                    )

                    // Audit Log
                    NavigationBarItem(
                        selected = uiState.selectedTab == ApprovalTab.AUDIT,
                        onClick = { viewModel.switchTab(ApprovalTab.AUDIT) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (uiState.historyRequests.isNotEmpty()) {
                                        Badge {
                                            Text("${uiState.historyRequests.size}")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (uiState.selectedTab == ApprovalTab.AUDIT) {
                                        Icons.Filled.ReceiptLong
                                    } else {
                                        Icons.Outlined.ReceiptLong
                                    },
                                    contentDescription = stringResource(R.string.nav_audit)
                                )
                            }
                        },
                        label = { Text(stringResource(R.string.nav_audit)) }
                    )

                    // Settings
                    NavigationBarItem(
                        selected = uiState.selectedTab == ApprovalTab.SETTINGS,
                        onClick = { viewModel.switchTab(ApprovalTab.SETTINGS) },
                        icon = {
                            Icon(
                                imageVector = if (uiState.selectedTab == ApprovalTab.SETTINGS) {
                                    Icons.Filled.Settings
                                } else {
                                    Icons.Outlined.Settings
                                },
                                contentDescription = stringResource(R.string.nav_settings)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_settings)) }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = paddingValues.calculateBottomPadding())
            ) {
                AnimatedContent(
                    targetState = uiState.selectedTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)) +
                            scaleIn(initialScale = 0.98f, animationSpec = tween(220, easing = LinearOutSlowInEasing)) togetherWith
                            fadeOut(animationSpec = tween(180, easing = FastOutLinearInEasing)) +
                            scaleOut(targetScale = 0.98f, animationSpec = tween(180, easing = FastOutLinearInEasing))
                    },
                    label = "tab_content_animation"
                ) { tab ->
                    when (tab) {
                        ApprovalTab.HOME -> {
                            Scaffold(
                                topBar = {
                                    HomeTopAppBar(
                                        isPaired = uiState.isPaired,
                                        activeProjectOrWorkspace = activeProjectOrWorkspace,
                                        onHaltAgent = { showHaltDialog = true }
                                    )
                                },
                                containerColor = MaterialTheme.colorScheme.surface
                            ) { homePadding ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(homePadding)
                                ) {
                                    AnimatedContent(
                                        targetState = uiState.isPaired to (uiState.displayedRequest != null),
                                        transitionSpec = {
                                            fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                                                fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                                        },
                                        label = "home_content_animation"
                                    ) { (isPaired, hasRequest) ->
                                        if (!isPaired) {
                                            UnpairedHomeView(
                                                isPairing = uiState.isPairing,
                                                pairingError = uiState.pairingError,
                                                onNavigateToPairingQr = onNavigateToPairingQr,
                                                onPairWithCode = viewModel::pairWithCode
                                            )
                                        } else if (hasRequest && uiState.displayedRequest != null) {
                                            LiveRequestView(
                                                request = uiState.displayedRequest!!,
                                                isSending = uiState.isSendingDecision,
                                                onApprove = viewModel::onApprove,
                                                onReject = viewModel::onReject,
                                                onSubmitQuestion = viewModel::onSubmitQuestion,
                                                onProceedPlan = viewModel::onProceedPlan,
                                                onRequestPlanChanges = viewModel::onRequestPlanChanges,
                                                modifier = Modifier.padding(16.dp)
                                            )
                                        } else {
                                            ActiveSessionDashboard(
                                                pairId = uiState.pairId ?: pairId ?: "session",
                                                historyCount = uiState.historyRequests.size,
                                                connectedAgent = uiState.connectedAgent,
                                                workspaceName = activeProjectOrWorkspace,
                                                recentActivity = uiState.recentActivity,
                                                onNavigateToAuditLog = { viewModel.switchTab(ApprovalTab.AUDIT) },
                                                onHaltAgent = { showHaltDialog = true }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        ApprovalTab.AUDIT -> {
                            Scaffold(
                                topBar = {
                                    AuditTopAppBar(
                                        hasRequests = uiState.historyRequests.isNotEmpty(),
                                        requestCount = uiState.historyRequests.size,
                                        onClearHistory = { showClearAuditDialog = true }
                                    )
                                },
                                containerColor = MaterialTheme.colorScheme.surface
                            ) { auditPadding ->
                                AuditHistoryView(
                                    requests = uiState.historyRequests,
                                    searchQuery = uiState.searchQuery,
                                    filterRisk = uiState.filterRisk,
                                    selectedAgentId = uiState.selectedAgentFilter,
                                    onSearchChanged = viewModel::setSearchQuery,
                                    onFilterRiskChanged = viewModel::setFilterRisk,
                                    onClearHistory = viewModel::clearAuditHistory,
                                    modifier = Modifier.padding(auditPadding)
                                )
                            }
                        }
                        ApprovalTab.SETTINGS -> {
                            SettingsScreen(
                                pairId = uiState.pairId ?: pairId ?: "",
                                isBottomTab = true,
                                onUnpaired = {
                                    viewModel.unpair()
                                    viewModel.switchTab(ApprovalTab.HOME)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Audit Log Clear Confirmation Dialog
        if (showClearAuditDialog) {
            AlertDialog(
                onDismissRequest = { showClearAuditDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.audit_clear_dialog_title),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.audit_clear_dialog_msg),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showClearAuditDialog = false
                            viewModel.clearAuditHistory()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RiskCriticalColor,
                            contentColor = Color.White
                        ),
                        shape = ShapeFull
                    ) {
                        Text(
                            text = stringResource(R.string.audit_clear_confirm),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearAuditDialog = false }) {
                        Text(
                            text = stringResource(R.string.cancel),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                shape = ShapeExtraLarge,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        }

        // Emergency Halt Confirmation Dialog
        if (showHaltDialog) {
            AlertDialog(
                onDismissRequest = { showHaltDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.halt_agent_dialog_title),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.halt_agent_dialog_msg),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showHaltDialog = false
                            viewModel.onEmergencyHalt()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RiskCriticalColor,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = ShapeFull
                    ) {
                        Text(
                            text = stringResource(R.string.confirm_halt),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showHaltDialog = false }) {
                        Text(
                            text = stringResource(R.string.cancel),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                shape = ShapeExtraLarge,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopAppBar(
    isPaired: Boolean,
    activeProjectOrWorkspace: String?,
    onHaltAgent: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    StatusPill(isConnected = isPaired, latencyMs = null)
                }
                if (!activeProjectOrWorkspace.isNullOrBlank() && isPaired) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = activeProjectOrWorkspace,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        actions = {
            if (isPaired) {
                IconButton(onClick = onHaltAgent) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = stringResource(R.string.halt_agent),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuditTopAppBar(
    hasRequests: Boolean,
    requestCount: Int,
    onClearHistory: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(R.string.audit_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (hasRequests) {
                        stringResource(R.string.audit_events_count, requestCount)
                    } else {
                        stringResource(R.string.audit_subtitle)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            if (hasRequests) {
                IconButton(onClick = onClearHistory) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = stringResource(R.string.audit_clear_btn),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}
