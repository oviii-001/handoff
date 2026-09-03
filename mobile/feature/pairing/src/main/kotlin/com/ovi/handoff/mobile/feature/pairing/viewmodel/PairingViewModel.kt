package com.ovi.handoff.mobile.feature.pairing.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ovi.handoff.mobile.domain.usecase.PairDeviceUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public data class PairingUiState(
    val isScanning: Boolean = true,
    val isPairing: Boolean = false,
    val error: String? = null
)

public sealed class PairingEvent {
    public data object PairingSuccess : PairingEvent()
    public data class PairingError(val message: String) : PairingEvent()
}

public class PairingViewModel(
    private val pairDeviceUseCase: PairDeviceUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairingUiState())
    public val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PairingEvent>()
    public val events: SharedFlow<PairingEvent> = _events.asSharedFlow()

    public fun onQrCodeScanned(payload: String) {
        if (_uiState.value.isPairing) return // Ignore if already pairing

        _uiState.update { it.copy(isScanning = false, isPairing = true, error = null) }

        viewModelScope.launch {
            val result = pairDeviceUseCase(payload)
            if (result.isSuccess) {
                _uiState.update { it.copy(isPairing = false) }
                _events.emit(PairingEvent.PairingSuccess)
            } else {
                val errorMessage = result.exceptionOrNull()?.message ?: "Unknown error"
                _uiState.update { it.copy(isPairing = false, isScanning = true, error = errorMessage) }
                _events.emit(PairingEvent.PairingError(errorMessage))
            }
        }
    }

    public fun onResumeScanning() {
        _uiState.update { it.copy(isScanning = true, error = null) }
    }
}
