package com.ovi.handoff.mobile.domain.repository

import com.ovi.handoff.shared.model.PermissionDecision
import com.ovi.handoff.shared.model.PermissionRequest
import kotlinx.coroutines.flow.Flow

interface RelayRepository {
    fun observeRequests(pairId: String): Flow<PermissionRequest?>
    suspend fun syncRequests(pairId: String): Result<Unit>
    suspend fun sendDecision(pairId: String, decision: PermissionDecision): Result<Unit>
}
