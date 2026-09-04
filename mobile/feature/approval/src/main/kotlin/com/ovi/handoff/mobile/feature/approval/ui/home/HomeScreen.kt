package com.ovi.handoff.mobile.feature.approval.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ovi.handoff.mobile.core.components.StatusPill
import com.ovi.handoff.mobile.feature.approval.R
import com.ovi.handoff.mobile.feature.approval.viewmodel.ApprovalTab
import com.ovi.handoff.mobile.feature.approval.viewmodel.ApprovalUiState
import com.ovi.handoff.mobile.feature.approval.viewmodel.ApprovalViewModel

@Composable
fun HomeScreen(
    uiState: ApprovalUiState,
    pairId: String?,
    activeProjectOrWorkspace: String?,
    viewModel: ApprovalViewModel,
    onNavigateToPairingQr: () -> Unit,
    onShowHaltDialog: () -> Unit
) {
    Scaffold(
        topBar = {
            HomeTopAppBar(
                isPaired = uiState.isPaired,
                activeProjectOrWorkspace = activeProjectOrWorkspace,
                onHaltAgent = onShowHaltDialog
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
                targetState = Pair(uiState.isPaired, uiState.displayedRequest != null),
                transitionSpec = {
                    fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                        fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                },
                label = "home_content_animation"
            ) { targetState ->
                val isPaired = targetState.first
                val hasRequest = targetState.second
                
                if (!isPaired) {
                    UnpairedHomeScreen(
                        isPairing = uiState.isPairing,
                        pairingError = uiState.pairingError,
                        onNavigateToPairingQr = onNavigateToPairingQr,
                        onPairWithCode = viewModel::pairWithCode
                    )
                } else if (hasRequest && uiState.displayedRequest != null) {
                    LiveRequestScreen(
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
                    ActiveSessionScreen(
                        pairId = uiState.pairId ?: pairId ?: "session",
                        historyCount = uiState.historyRequests.size,
                        connectedAgent = uiState.connectedAgent,
                        workspaceName = activeProjectOrWorkspace,
                        recentActivity = uiState.recentActivity,
                        onNavigateToAuditLog = { viewModel.switchTab(ApprovalTab.AUDIT) },
                        onHaltAgent = onShowHaltDialog
                    )
                }
            }
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
                    if (isPaired) {
                        StatusPill(isConnected = true, latencyMs = null)
                    }
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
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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

