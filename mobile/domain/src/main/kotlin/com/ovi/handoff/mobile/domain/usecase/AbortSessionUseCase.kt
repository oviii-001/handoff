package com.ovi.handoff.mobile.domain.usecase

import com.ovi.handoff.mobile.domain.repository.RelayRepository

class AbortSessionUseCase(
    private val relayRepository: RelayRepository
) {
    suspend operator fun invoke(pairId: String): Result<Unit> {
        return relayRepository.abortSession(pairId)
    }
}
