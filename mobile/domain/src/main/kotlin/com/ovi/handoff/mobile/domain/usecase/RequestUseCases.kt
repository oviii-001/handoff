package com.ovi.handoff.mobile.domain.usecase

import com.ovi.handoff.mobile.domain.repository.RelayRepository

/**
 * Sweeps requests whose deadline has passed.
 *
 * Called when the app returns to the foreground and after each decision, so the queue never offers
 * the user a request the agent has already stopped waiting on. `expiresAt` was previously written by
 * every producer and read by nobody.
 */
public class ExpireOverdueRequestsUseCase(
    private val relayRepository: RelayRepository
) {
    public suspend operator fun invoke(): Result<Int> = relayRepository.expireOverdueRequests()
}
