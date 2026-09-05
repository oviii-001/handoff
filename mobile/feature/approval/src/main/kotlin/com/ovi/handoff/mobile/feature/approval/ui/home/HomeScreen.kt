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
import com.ovi.handoff.mobile.core.components.LinkState
import com.ovi.handoff.mobile.core.components.StatusPill
import com.ovi.handoff.mobile.domain.repository.ConnectionState
import com.ovi.handoff.mobile.feature.approval.R
import com.ovi.handoff.mobile.feature.approval.viewmodel.ApprovalUiState

/**
 * Home tab: pairing, the approval queue, or the standby screen.
 *
 * Takes callbacks instead of the ViewModel. Passing the whole ViewModel meant this subtree was
 * recomposed by any state change at all, including a keystroke in the audit tab's search field.
 */
@Composable
public fun HomeScreen(
    uiState: ApprovalUiState,
    onPairWithCode: (String) -> Unit,
    onNavigateToPairingQr: () -> Unit,
    onNavigateToAuditLog: () -> Unit,
    onShowHaltDialog: () -> Unit,
    onApprove: () -> Unit,
    onReject: (String?) -> Unit,
    onSubmitQuestion: (List<String>, String?) -> Unit,
    onProceedPlan: () -> Unit,
    onRequestPlanChanges: (String) -> Unit,
    onExtendDeadline: () -> Unit = {},
    onShowPrevious: () -> Unit,
    onShowNext: () -> Unit,
    onBlocked: (String) -> Unit
) {
    Scaffold(
        topBar = {
            HomeTopAppBar(
                isPaired = uiState.isPaired,
                connectionState = uiState.connectionState,
                pendingCount = uiState.pendingCount,
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
            // Keyed on the coarse screen identity only. Keying on the request too would cross-fade the
            // whole card every time the user steps through the queue.
            val screen = when {
                !uiState.isPaired -> HomeContent.UNPAIRED
                uiState.displayedRequest != null -> HomeContent.REQUEST
                else -> HomeContent.STANDBY
            }

            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                        fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                },
                label = "home_content"
            ) { target ->
                when (target) {
                    HomeContent.UNPAIRED -> UnpairedHomeScreen(
                        isPairing = uiState.isPairing,
                        pairingError = uiState.pairingError,
                        onNavigateToPairingQr = onNavigateToPairingQr,
                        onPairWithCode = onPairWithCode
                    )

                    HomeContent.REQUEST -> {
                        val request = uiState.displayedRequest
                        if (request != null) {
                            LiveRequestScreen(
                                request = request,
                                queueIndex = uiState.activeRequestIndex,
                                queueSize = uiState.pendingCount,
                                isSending = uiState.isSendingDecision,
                                connectionState = uiState.connectionState,
                                connectionError = uiState.connectionError,
                                requireBiometricsForCritical = uiState.settings.biometricsForCritical,
                                onApprove = onApprove,
                                onReject = onReject,
                                onSubmitQuestion = onSubmitQuestion,
                                onProceedPlan = onProceedPlan,
                                onRequestPlanChanges = onRequestPlanChanges,
                                onExtendDeadline = onExtendDeadline,
                                onShowPrevious = onShowPrevious,
                                onShowNext = onShowNext,
                                onBlocked = onBlocked,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    HomeContent.STANDBY -> ActiveSessionScreen(
                        pairId = uiState.pairId.orEmpty(),
                        connectionState = uiState.connectionState,
                        reviewedCount = uiState.decidedCount,
                        pendingCount = uiState.pendingCount,
                        connectedAgent = uiState.connectedAgent,
                        workspaceLabel = uiState.activeWorkspaceLabel,
                        recentActivity = uiState.recentActivity,
                        onNavigateToAuditLog = onNavigateToAuditLog,
                        onHaltAgent = onShowHaltDialog
                    )
                }
            }
        }
    }
}

private enum class HomeContent { UNPAIRED, REQUEST, STANDBY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopAppBar(
    isPaired: Boolean,
    connectionState: ConnectionState,
    pendingCount: Int,
    onHaltAgent: () -> Unit
) {
    TopAppBar(
        title = {
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
                    StatusPill(state = connectionState.toLinkState())
                }
                if (pendingCount > 1) {
                    Text(
                        text = stringResource(R.string.queue_pending_count, pendingCount),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
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

internal fun ConnectionState.toLinkState(): LinkState = when (this) {
    ConnectionState.CONNECTED -> LinkState.CONNECTED
    ConnectionState.CONNECTING -> LinkState.CONNECTING
    ConnectionState.OFFLINE -> LinkState.OFFLINE
}
