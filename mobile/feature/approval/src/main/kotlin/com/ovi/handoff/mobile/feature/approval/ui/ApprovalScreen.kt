package com.ovi.handoff.mobile.feature.approval.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.DeleteSweep
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ovi.handoff.mobile.core.theme.HandoffTheme
import com.ovi.handoff.mobile.core.theme.RiskCriticalColor
import com.ovi.handoff.mobile.core.theme.ShapeExtraLarge
import com.ovi.handoff.mobile.core.theme.ShapeFull
import com.ovi.handoff.mobile.feature.approval.R
import com.ovi.handoff.mobile.feature.approval.ui.audit.AuditScreen
import com.ovi.handoff.mobile.feature.approval.ui.home.HomeScreen
import com.ovi.handoff.mobile.feature.approval.ui.settings.SettingsScreen
import com.ovi.handoff.mobile.feature.approval.viewmodel.ApprovalTab
import com.ovi.handoff.mobile.feature.approval.viewmodel.ApprovalViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalScreen(
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
                                        Icons.AutoMirrored.Filled.ReceiptLong
                                    } else {
                                        Icons.AutoMirrored.Outlined.ReceiptLong
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
                            HomeScreen(
                                uiState = uiState,
                                pairId = pairId,
                                activeProjectOrWorkspace = activeProjectOrWorkspace,
                                viewModel = viewModel,
                                onNavigateToPairingQr = onNavigateToPairingQr,
                                onShowHaltDialog = { showHaltDialog = true }
                            )
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
                                AuditScreen(
                                    requests = uiState.historyRequests,
                                    searchQuery = uiState.searchQuery,
                                    filterRisk = uiState.filterRisk,
                                    selectedAgentId = uiState.selectedAgentFilter,
                                    onSearchChanged = viewModel::setSearchQuery,
                                    onFilterRiskChanged = viewModel::setFilterRisk,
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
