package com.ovi.handoff.mobile.feature.approval.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.handoff.mobile.domain.repository.ConnectionState
import com.ovi.handoff.mobile.domain.repository.PairingRepository
import com.ovi.handoff.mobile.domain.repository.RelayRepository
import com.ovi.handoff.mobile.domain.settings.HandoffSettings
import com.ovi.handoff.mobile.domain.settings.SettingsRepository
import com.ovi.handoff.mobile.domain.usecase.UnpairDeviceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public data class SettingsUiState(
    val pairId: String = "",
    val relayHost: String = "",
    val connectionState: ConnectionState = ConnectionState.OFFLINE,
    val settings: HandoffSettings = HandoffSettings(),
    val isUnpairing: Boolean = false,
    val message: String? = null
)

/**
 * Settings backed by real storage.
 *
 * Previously every toggle lived in this ViewModel's own state and nothing read it: the values reset on
 * process death, and "require biometrics for critical actions" was displayed as off while the approval
 * screen demanded biometrics regardless. Each setter now writes through to the repository that the
 * approval and notification paths actually consult.
 */
public class SettingsViewModel(
    private val pairId: String,
    private val unpairDeviceUseCase: UnpairDeviceUseCase,
    private val settingsRepository: SettingsRepository,
    private val pairingRepository: PairingRepository,
    private val relayRepository: RelayRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(pairId = pairId))
    public val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val pairing = pairingRepository.getPairing()
            _uiState.update {
                it.copy(
                    pairId = pairing?.pairId ?: pairId,
                    relayHost = pairing?.relayHost.orEmpty()
                )
            }
        }
        viewModelScope.launch {
            settingsRepository.observe().collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            relayRepository.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
    }

    public fun togglePushNotifications(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPushNotificationsEnabled(enabled) }
    }

    public fun toggleNotificationActions(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotificationActionsEnabled(enabled) }
    }

    public fun toggleVibration(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setVibrationEnabled(enabled) }
    }

    public fun toggleBiometricsForCritical(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBiometricsForCritical(enabled) }
    }

    public fun toggleBiometricsForShadeActions(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBiometricsForShadeActions(enabled) }
    }

    public fun setAutoDenyMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.setAutoDenyMinutes(minutes) }
    }

    public fun unpair(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUnpairing = true) }
            val result = unpairDeviceUseCase()
            _uiState.update { it.copy(isUnpairing = false) }
            if (result.isSuccess) {
                onSuccess()
            } else {
                _uiState.update { it.copy(message = "Could not unpair. Try again.") }
            }
        }
    }

    public fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
