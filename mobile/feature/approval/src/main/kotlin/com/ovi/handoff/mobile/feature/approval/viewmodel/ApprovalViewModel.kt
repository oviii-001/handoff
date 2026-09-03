package com.ovi.handoff.mobile.feature.approval.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.handoff.mobile.domain.usecase.AbortSessionUseCase
import com.ovi.handoff.mobile.domain.usecase.GetRequestHistoryUseCase
import com.ovi.handoff.mobile.domain.usecase.ObserveRequestsUseCase
import com.ovi.handoff.mobile.domain.usecase.SendDecisionUseCase
import com.ovi.handoff.shared.model.PermissionDecision
import com.ovi.handoff.shared.model.PermissionRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

public enum class ApprovalTab {
    LIVE,
    HISTORY
}

public data class ApprovalUiState(
    val currentRequest: PermissionRequest? = null,
    val isSendingDecision: Boolean = false,
    val selectedTab: ApprovalTab = ApprovalTab.LIVE,
    val historyRequests: List<PermissionRequest> = emptyList(),
    val searchQuery: String = "",
    val filterRisk: String? = null,
    val isAbortingSession: Boolean = false,
    val error: String? = null,
    val notificationMessage: String? = null
)

public class ApprovalViewModel(
    private val pairId: String,
    private val observeRequestsUseCase: ObserveRequestsUseCase,
    private val sendDecisionUseCase: SendDecisionUseCase,
    private val getRequestHistoryUseCase: GetRequestHistoryUseCase,
    private val abortSessionUseCase: AbortSessionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApprovalUiState())
    public val uiState: StateFlow<ApprovalUiState> = _uiState.asStateFlow()

    init {
        observeRequests()
        observeHistory()
    }

    private fun observeRequests() {
        viewModelScope.launch {
            observeRequestsUseCase(pairId).collect { request ->
                _uiState.update { it.copy(currentRequest = request) }
            }
        }
    }

    private fun observeHistory() {
        viewModelScope.launch {
            getRequestHistoryUseCase().collect { history ->
                _uiState.update { it.copy(historyRequests = history) }
            }
        }
    }

    public fun switchTab(tab: ApprovalTab) {
        _uiState.update { it.copy(selectedTab = tab) }
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

    public fun onAbortSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isAbortingSession = true) }
            val result = abortSessionUseCase(pairId)
            _uiState.update { state ->
                state.copy(
                    isAbortingSession = false,
                    notificationMessage = if (result.isSuccess) "Agent session aborted" else "Failed to abort session"
                )
            }
        }
    }

    private fun sendDecision(
        decisionType: String,
        selectedOptions: List<String>? = null,
        feedback: String? = null
    ) {
        val request = _uiState.value.currentRequest ?: return

        _uiState.update { it.copy(isSendingDecision = true, error = null) }

        viewModelScope.launch {
            val decision = PermissionDecision(
                requestId = request.id,
                decision = decisionType,
                issuedAt = Instant.now().toString(),
                nonce = UUID.randomUUID().toString(),
                deviceId = pairId,
                signature = "sig_${System.currentTimeMillis()}",
                feedback = feedback,
                selectedOptions = selectedOptions
            )

            val result = sendDecisionUseCase(pairId, decision)

            _uiState.update { state ->
                if (result.isSuccess) {
                    state.copy(
                        isSendingDecision = false,
                        currentRequest = null,
                        error = null,
                        notificationMessage = "Decision dispatched"
                    )
                } else {
                    state.copy(
                        isSendingDecision = false,
                        error = result.exceptionOrNull()?.message ?: "Failed to send decision"
                    )
                }
            }
        }
    }

    public fun clearNotification() {
        _uiState.update { it.copy(notificationMessage = null) }
    }
}

