package com.ovi.handoff.mobile.feature.approval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ovi.handoff.mobile.core.theme.*
import com.ovi.handoff.mobile.feature.approval.R
import com.ovi.handoff.mobile.feature.approval.ui.components.ActiveSessionDashboard
import com.ovi.handoff.mobile.feature.approval.ui.components.AuditHistoryView
import com.ovi.handoff.mobile.feature.approval.ui.components.LiveRequestView
import com.ovi.handoff.mobile.feature.approval.viewmodel.ApprovalTab
import com.ovi.handoff.mobile.feature.approval.viewmodel.ApprovalViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ApprovalScreen(
    pairId: String,
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

    HandoffTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = DarkBg,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(AntigravityViolet)
                            )
                            Text(
                                text = "HandOff",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "for Antigravity",
                                color = TextMuted,
                                fontSize = 12.sp,
                                fontFamily = MonospaceFont
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DarkBg
                    ),
                    actions = {
                        // Emergency Halt Agent Action
                        IconButton(onClick = { showHaltDialog = true }) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(RiskCritical)
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Tab Navigation
                PrimaryTabRow(
                    selectedTabIndex = uiState.selectedTab.ordinal,
                    containerColor = DarkBg,
                    contentColor = AntigravityViolet,
                    divider = { HorizontalDivider(color = DarkBorder) }
                ) {
                    Tab(
                        selected = uiState.selectedTab == ApprovalTab.LIVE,
                        onClick = { viewModel.switchTab(ApprovalTab.LIVE) },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.tab_live),
                                    fontSize = 14.sp,
                                    fontWeight = if (uiState.selectedTab == ApprovalTab.LIVE) FontWeight.Bold else FontWeight.Normal
                                )
                                if (uiState.currentRequest != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(RiskHigh)
                                    )
                                }
                            }
                        }
                    )
                    Tab(
                        selected = uiState.selectedTab == ApprovalTab.HISTORY,
                        onClick = { viewModel.switchTab(ApprovalTab.HISTORY) },
                        text = {
                            Text(
                                text = "${stringResource(R.string.tab_history)} (${uiState.historyRequests.size})",
                                fontSize = 14.sp,
                                fontWeight = if (uiState.selectedTab == ApprovalTab.HISTORY) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }

                // Tab Content
                when (uiState.selectedTab) {
                    ApprovalTab.LIVE -> {
                        val request = uiState.currentRequest
                        if (request != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                LiveRequestView(
                                    request = request,
                                    isSending = uiState.isSendingDecision,
                                    onApprove = viewModel::onApprove,
                                    onReject = viewModel::onReject,
                                    onSubmitQuestion = viewModel::onSubmitQuestion,
                                    onProceedPlan = viewModel::onProceedPlan,
                                    onRequestPlanChanges = viewModel::onRequestPlanChanges
                                )
                            }
                        } else {
                            ActiveSessionDashboard(
                                pairId = pairId,
                                historyCount = uiState.historyRequests.size,
                                recentRequests = uiState.historyRequests,
                                onHaltAgent = { showHaltDialog = true }
                            )
                        }
                    }
                    ApprovalTab.HISTORY -> {
                        AuditHistoryView(
                            requests = uiState.historyRequests,
                            searchQuery = uiState.searchQuery,
                            filterRisk = uiState.filterRisk,
                            onSearchChanged = viewModel::setSearchQuery,
                            onFilterRiskChanged = viewModel::setFilterRisk
                        )
                    }
                }
            }
        }

        // Emergency Halt Confirmation Dialog
        if (showHaltDialog) {
            AlertDialog(
                onDismissRequest = { showHaltDialog = false },
                containerColor = DarkSurface,
                title = {
                    Text(
                        text = stringResource(R.string.halt_agent_dialog_title),
                        color = RiskCritical,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.halt_agent_dialog_msg),
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showHaltDialog = false
                            viewModel.onAbortSession()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RiskCritical, contentColor = Color.White)
                    ) {
                        Text(stringResource(R.string.confirm_halt), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showHaltDialog = false }) {
                        Text(stringResource(R.string.cancel), color = TextSecondary)
                    }
                }
            )
        }
    }
}

