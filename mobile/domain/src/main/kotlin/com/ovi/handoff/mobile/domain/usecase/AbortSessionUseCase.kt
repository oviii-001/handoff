package com.ovi.handoff.mobile.domain.usecase

import com.ovi.handoff.mobile.domain.repository.RelayRepository

public class AbortSessionUseCase(
    private val relayRepository: RelayRepository
) {
    public suspend operator fun invoke(pairId: String): Result<Unit> {
        return relayRepository.abortSession(pairId)
    }
}
