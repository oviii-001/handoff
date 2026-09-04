package com.ovi.handoff.mobile.domain.usecase

import com.ovi.handoff.mobile.domain.repository.RelayRepository
import com.ovi.handoff.shared.model.PermissionRequest
import kotlinx.coroutines.flow.Flow

class GetRequestHistoryUseCase(
    private val relayRepository: RelayRepository
) {
    operator fun invoke(): Flow<List<PermissionRequest>> {
        return relayRepository.observeHistory()
    }
}
