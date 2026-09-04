package com.ovi.handoff.mobile.domain.repository

import com.ovi.handoff.shared.model.PermissionDecision
import com.ovi.handoff.shared.model.PermissionRequest
import kotlinx.coroutines.flow.Flow

interface RelayRepository {
    fun observeRequests(pairId: String): Flow<PermissionRequest?>
    fun observeHistory(): Flow<List<PermissionRequest>>
    suspend fun syncRequests(pairId: String): Result<Unit>
    suspend fun sendDecision(pairId: String, decision: PermissionDecision): Result<Unit>
    suspend fun abortSession(pairId: String): Result<Unit>
    suspend fun registerPushToken(pairId: String, token: String): Result<Unit>
    suspend fun clearHistory(): Result<Unit>
}
