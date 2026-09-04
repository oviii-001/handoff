package com.ovi.handoff.mobile.domain.repository

import kotlinx.coroutines.flow.Flow

data class ConnectedSession(
    val ideName: String,
    val workspaceName: String?,
    val lastUpdated: Long = System.currentTimeMillis()
)

interface PairingRepository {
    suspend fun pairDevice(pairId: String, publicKey: ByteArray): Result<Unit>
    suspend fun getPairId(): String?
    suspend fun clearPairing(): Result<Unit>
    suspend fun saveConnectedSession(ideName: String, workspaceName: String?): Result<Unit>
    fun observeConnectedSession(): Flow<ConnectedSession?>
}
