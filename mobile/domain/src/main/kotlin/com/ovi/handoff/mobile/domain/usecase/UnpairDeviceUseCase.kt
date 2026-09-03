package com.ovi.handoff.mobile.domain.usecase

import com.ovi.handoff.mobile.domain.repository.PairingRepository

public class UnpairDeviceUseCase(
    private val pairingRepository: PairingRepository
) {
    public suspend operator fun invoke(): Result<Unit> {
        return pairingRepository.clearPairing()
    }
}
