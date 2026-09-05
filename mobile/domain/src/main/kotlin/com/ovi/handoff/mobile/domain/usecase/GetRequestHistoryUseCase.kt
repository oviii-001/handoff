package com.ovi.handoff.mobile.domain.usecase

import com.ovi.handoff.mobile.domain.repository.RelayRepository
import com.ovi.handoff.mobile.domain.repository.RequestRecord
import kotlinx.coroutines.flow.Flow

/** Audit history, newest first, including what was decided for each request. */
public class GetRequestHistoryUseCase(
    private val relayRepository: RelayRepository
) {
    public operator fun invoke(): Flow<List<RequestRecord>> = relayRepository.observeHistory()
}
