package com.ovi.handoff.mobile.domain.usecase

import com.ovi.handoff.mobile.domain.repository.RelayRepository
import com.ovi.handoff.shared.model.PermissionRequest
import kotlinx.coroutines.flow.Flow

/**
 * The queue of requests still awaiting a decision, oldest first.
 *
 * Emits a list rather than a single request. The repository used to collapse the queue with
 * `firstOrNull()` over an unordered query, so a second concurrent request was unreachable and could
 * only ever expire unanswered.
 */
public class ObserveRequestsUseCase(
    private val relayRepository: RelayRepository
) {
    public operator fun invoke(pairId: String): Flow<List<PermissionRequest>> =
        relayRepository.observePendingRequests(pairId)
}
