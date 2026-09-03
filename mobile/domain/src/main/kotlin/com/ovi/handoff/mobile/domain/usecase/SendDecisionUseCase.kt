package com.ovi.handoff.mobile.domain.usecase

import com.ovi.handoff.mobile.domain.repository.RelayRepository
import com.ovi.handoff.shared.model.PermissionDecision

class SendDecisionUseCase(
    private val relayRepository: RelayRepository
) {
    suspend operator fun invoke(pairId: String, decision: PermissionDecision): Result<Unit> {
        return relayRepository.sendDecision(pairId, decision)
    }
}
