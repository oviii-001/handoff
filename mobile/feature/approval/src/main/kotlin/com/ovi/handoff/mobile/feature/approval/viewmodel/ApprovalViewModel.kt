package com.ovi.handoff.mobile.feature.approval.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.handoff.mobile.domain.usecase.ObserveRequestsUseCase
import com.ovi.handoff.mobile.domain.usecase.SendDecisionUseCase
import com.ovi.handoff.shared.model.PermissionDecision
import com.ovi.handoff.shared.model.PermissionRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public data class ApprovalUiState(
    val currentRequest: PermissionRequest? = null,
    val isSendingDecision: Boolean = false,
    val error: String? = null
)

public class ApprovalViewModel(
    private val pairId: String,
    private val observeRequestsUseCase: ObserveRequestsUseCase,
    private val sendDecisionUseCase: SendDecisionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApprovalUiState())
    public val uiState: StateFlow<ApprovalUiState> = _uiState.asStateFlow()

    init {
        observeRequests()
    }

    private fun observeRequests() {
        viewModelScope.launch {
            observeRequestsUseCase(pairId).collect { request ->
                _uiState.update { it.copy(currentRequest = request) }
            }
        }
    }

    public fun onApprove() {
        sendDecision(true)
    }

    public fun onReject() {
        sendDecision(false)
    }

    private fun sendDecision(approved: Boolean) {
        val request = _uiState.value.currentRequest ?: return
        
        _uiState.update { it.copy(isSendingDecision = true, error = null) }
        
        viewModelScope.launch {
            val decision = PermissionDecision(
                requestId = request.id,
                decision = if (approved) "approve_once" else "deny",
                issuedAt = java.time.Instant.now().toString(),
                nonce = java.util.UUID.randomUUID().toString(),
                deviceId = pairId,
                signature = "sig_${System.currentTimeMillis()}"
            )
            
            val result = sendDecisionUseCase(pairId, decision)
            
            _uiState.update { state ->
                if (result.isSuccess) {
                    state.copy(
                        isSendingDecision = false,
                        currentRequest = null,
                        error = null
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
}
