package com.ovi.handoff.mobile.feature.approval.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.handoff.mobile.domain.repository.PairingRepository
import com.ovi.handoff.mobile.domain.usecase.AbortSessionUseCase
import com.ovi.handoff.mobile.domain.usecase.ClearRequestHistoryUseCase
import com.ovi.handoff.mobile.domain.usecase.GetRequestHistoryUseCase
import com.ovi.handoff.mobile.domain.usecase.ObserveRequestsUseCase
import com.ovi.handoff.mobile.domain.usecase.PairDeviceUseCase
import com.ovi.handoff.mobile.domain.usecase.SendDecisionUseCase
import com.ovi.handoff.mobile.domain.usecase.UnpairDeviceUseCase

import com.ovi.handoff.shared.model.PermissionDecision
import com.ovi.handoff.shared.model.PermissionRequest
import com.ovi.handoff.mobile.feature.approval.ui.model.ConnectedAgentUiModel
import com.ovi.handoff.mobile.feature.approval.ui.model.PermissionRequestUiModel
import com.ovi.handoff.mobile.feature.approval.ui.model.toUiModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

public enum class ApprovalTab {
    HOME,
    AUDIT,
    SETTINGS
}

public data class ApprovalUiState(
    val currentRequest: PermissionRequestUiModel? = null,
    val selectedAgentFilter: String? = null,
    val isSendingDecision: Boolean = false,
    val selectedTab: ApprovalTab = ApprovalTab.HOME,
    val historyRequests: List<PermissionRequestUiModel> = emptyList(),
    val searchQuery: String = "",
    val filterRisk: String? = null,
    val isAbortingSession: Boolean = false,
    val pairId: String? = null,
    val isPaired: Boolean = false,
    val isPairing: Boolean = false,
    val pairingError: String? = null,
    val error: String? = null,
    val notificationMessage: String? = null
) {
    val connectedAgent: ConnectedAgentUiModel?
        get() = currentRequest?.let {
            ConnectedAgentUiModel(
                id = it.agentId,
                name = it.agentName,
                version = it.agentVersion
            )
        } ?: historyRequests.firstOrNull()?.let {
            ConnectedAgentUiModel(
                id = it.agentId,
                name = it.agentName,
                version = it.agentVersion
            )
        }

    val activeAgents: List<ConnectedAgentUiModel>
        get() = listOfNotNull(connectedAgent)

    val displayedRequest: PermissionRequestUiModel?
        get() = currentRequest

    val activeProjectOrWorkspace: String?
        get() = currentRequest?.projectOrWorkspace
            ?: historyRequests.firstOrNull()?.projectOrWorkspace

    val recentActivity: List<PermissionRequestUiModel>
        get() = historyRequests.take(3)
}

public class ApprovalViewModel(
    private val initialPairId: String? = null,
    private val observeRequestsUseCase: ObserveRequestsUseCase,
    private val sendDecisionUseCase: SendDecisionUseCase,
    private val getRequestHistoryUseCase: GetRequestHistoryUseCase,
    private val clearRequestHistoryUseCase: ClearRequestHistoryUseCase,
    private val abortSessionUseCase: AbortSessionUseCase,
    private val pairDeviceUseCase: PairDeviceUseCase,
    private val unpairDeviceUseCase: UnpairDeviceUseCase,
    private val pairingRepository: PairingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ApprovalUiState(
            pairId = initialPairId?.takeIf { it.isNotBlank() },
            isPaired = !initialPairId.isNullOrBlank()
        )
    )
    public val uiState: StateFlow<ApprovalUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        checkAndStartPairing()
        observeHistory()
    }

    private fun checkAndStartPairing() {
        viewModelScope.launch {
            val activeId = if (!initialPairId.isNullOrBlank()) {
                initialPairId
            } else {
                pairingRepository.getPairId()
            }

            if (!activeId.isNullOrBlank()) {
                _uiState.update { it.copy(pairId = activeId, isPaired = true) }
                startObservingRequests(activeId)
            } else {
                _uiState.update { it.copy(pairId = null, isPaired = false) }
            }
        }
    }

    private fun startObservingRequests(activePairId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            observeRequestsUseCase(activePairId).collect { request ->
                _uiState.update { it.copy(currentRequest = request?.toUiModel()) }
            }
        }
    }

    private fun observeHistory() {
        viewModelScope.launch {
            getRequestHistoryUseCase().collect { history ->
                _uiState.update { it.copy(historyRequests = history.map { req -> req.toUiModel() }) }
            }
        }
    }

    public fun pairWithCode(code: String) {
        val trimmed = code.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(pairingError = "Pairing code cannot be empty") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPairing = true, pairingError = null) }
            val result = pairDeviceUseCase(trimmed)
            result.fold(
                onSuccess = {
                    val activeId = pairingRepository.getPairId() ?: trimmed
                    _uiState.update {
                        it.copy(
                            isPairing = false,
                            isPaired = true,
                            pairId = activeId,
                            notificationMessage = "Successfully connected to Desktop Relay"
                        )
                    }
                    startObservingRequests(activeId)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isPairing = false,
                            pairingError = err.message ?: "Failed to pair with desktop"
                        )
                    }
                }
            )
        }
    }

    public fun unpair() {
        viewModelScope.launch {
            observeJob?.cancel()
            unpairDeviceUseCase()
            _uiState.update {
                it.copy(
                    isPaired = false,
                    pairId = null,
                    currentRequest = null,
                    notificationMessage = "Session unpaired"
                )
            }
        }
    }

    public fun switchTab(tab: ApprovalTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    public fun setAgentFilter(agentId: String?) {
        _uiState.update { it.copy(selectedAgentFilter = agentId) }
    }

    public fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    public fun setFilterRisk(risk: String?) {
        _uiState.update { it.copy(filterRisk = risk) }
    }

    public fun onApprove() {
        sendDecision(decisionType = "approve_once")
    }

    public fun onReject(feedback: String? = null) {
        sendDecision(decisionType = "deny", feedback = feedback)
    }

    public fun onSubmitQuestion(selectedOptions: List<String>, writeIn: String?) {
        sendDecision(
            decisionType = "answer_question",
            selectedOptions = selectedOptions,
            feedback = writeIn
        )
    }

    public fun onProceedPlan() {
        sendDecision(decisionType = "proceed_plan")
    }

    public fun onRequestPlanChanges(feedback: String) {
        sendDecision(decisionType = "deny", feedback = feedback)
    }

    private fun sendDecision(
        decisionType: String,
        selectedOptions: List<String> = emptyList(),
        feedback: String? = null
    ) {
        val request = _uiState.value.displayedRequest ?: return
        val activePairId = _uiState.value.pairId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSendingDecision = true, error = null) }

            val decision = PermissionDecision(
                requestId = request.id,
                decision = decisionType,
                issuedAt = Instant.now().toString(),
                nonce = UUID.randomUUID().toString(),
                deviceId = "pixel-9-hardware",
                signature = "",
                feedback = feedback,
                selectedOptions = selectedOptions
            )

            val result = sendDecisionUseCase(activePairId, decision)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isSendingDecision = false,
                        currentRequest = null,
                        notificationMessage = "Decision recorded: $decisionType"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isSendingDecision = false,
                        error = result.exceptionOrNull()?.message ?: "Failed to transmit decision"
                    )
                }
            }
        }
    }

    public fun onEmergencyHalt() {
        val activePairId = _uiState.value.pairId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isAbortingSession = true, error = null) }
            val result = abortSessionUseCase(activePairId)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isAbortingSession = false,
                        currentRequest = null,
                        notificationMessage = "Agent session aborted"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isAbortingSession = false,
                        error = result.exceptionOrNull()?.message ?: "Failed to halt agent"
                    )
                }
            }
        }
    }

    public fun onAbortSession() {
        onEmergencyHalt()
    }

    public fun clearAuditHistory() {
        viewModelScope.launch {
            clearRequestHistoryUseCase()
                .onSuccess {
                    _uiState.update { it.copy(notificationMessage = "Audit log cleared") }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message ?: "Failed to clear audit log") }
                }
        }
    }

    public fun clearNotification() {
        _uiState.update { it.copy(notificationMessage = null) }
    }

    public fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    public fun clearPairingError() {
        _uiState.update { it.copy(pairingError = null) }
    }
}
