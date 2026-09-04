package com.ovi.handoff.mobile.domain.usecase

import com.ovi.handoff.mobile.domain.repository.PairingRepository

class UnpairDeviceUseCase(
    private val pairingRepository: PairingRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return pairingRepository.clearPairing()
    }
}
