package com.ovi.handoff.mobile.feature.approval.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.handoff.mobile.domain.usecase.UnpairDeviceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public data class SettingsUiState(
    val pairId: String = "",
    val relayHost: String = "agentapprove-relay.ismamhasanovi.workers.dev",
    val pushNotificationsEnabled: Boolean = true,
    val directActionsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val biometricsRequiredForCritical: Boolean = false,
    val autoDenyTimeout: String = "15m",
    val isUnpairing: Boolean = false
)

public class SettingsViewModel(
    private val pairId: String,
    private val unpairDeviceUseCase: UnpairDeviceUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(pairId = pairId))
    public val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    public fun togglePushNotifications(enabled: Boolean) {
        _uiState.update { it.copy(pushNotificationsEnabled = enabled) }
    }

    public fun toggleDirectActions(enabled: Boolean) {
        _uiState.update { it.copy(directActionsEnabled = enabled) }
    }

    public fun toggleVibration(enabled: Boolean) {
        _uiState.update { it.copy(vibrationEnabled = enabled) }
    }

    public fun toggleBiometrics(required: Boolean) {
        _uiState.update { it.copy(biometricsRequiredForCritical = required) }
    }

    public fun unpair(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUnpairing = true) }
            val result = unpairDeviceUseCase()
            _uiState.update { it.copy(isUnpairing = false) }
            if (result.isSuccess) {
                onSuccess()
            }
        }
    }
}
