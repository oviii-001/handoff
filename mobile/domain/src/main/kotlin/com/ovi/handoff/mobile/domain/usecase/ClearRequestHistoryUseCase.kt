package com.ovi.handoff.mobile.domain.usecase

import com.ovi.handoff.mobile.domain.repository.RelayRepository

public class ClearRequestHistoryUseCase(
    private val relayRepository: RelayRepository
) {
    public suspend operator fun invoke(): Result<Unit> {
        return relayRepository.clearHistory()
    }
}
