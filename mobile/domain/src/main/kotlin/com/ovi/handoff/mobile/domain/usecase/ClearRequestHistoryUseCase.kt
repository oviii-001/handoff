package com.ovi.handoff.mobile.domain.usecase

import com.ovi.handoff.mobile.domain.repository.RelayRepository

class ClearRequestHistoryUseCase(
    private val relayRepository: RelayRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return relayRepository.clearHistory()
    }
}
