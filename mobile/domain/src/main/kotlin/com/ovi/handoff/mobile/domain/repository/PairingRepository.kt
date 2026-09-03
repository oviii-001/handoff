package com.ovi.handoff.mobile.domain.repository

interface PairingRepository {
    suspend fun pairDevice(pairId: String, publicKey: ByteArray): Result<Unit>
    suspend fun getPairId(): String?
}
