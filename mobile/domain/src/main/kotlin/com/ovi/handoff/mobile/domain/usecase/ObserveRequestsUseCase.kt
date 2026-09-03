package com.ovi.handoff.mobile.domain.usecase

import com.ovi.handoff.mobile.domain.repository.RelayRepository
import com.ovi.handoff.shared.model.PermissionRequest
import kotlinx.coroutines.flow.Flow

class ObserveRequestsUseCase(
    private val relayRepository: RelayRepository
) {
    operator fun invoke(pairId: String): Flow<PermissionRequest?> {
        return relayRepository.observeRequests(pairId)
    }
}
