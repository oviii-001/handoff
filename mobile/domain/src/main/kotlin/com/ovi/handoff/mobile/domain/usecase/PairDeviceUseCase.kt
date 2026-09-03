package com.ovi.handoff.mobile.domain.usecase

import com.ovi.handoff.mobile.domain.repository.PairingRepository

class PairDeviceUseCase(private val pairingRepository: PairingRepository) {
    suspend operator fun invoke(qrPayload: String): Result<Unit> {
        val trimmed = qrPayload.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Pairing payload cannot be empty"))
        }

        val pairId = when {
            trimmed.contains("pairId=") -> {
                trimmed.substringAfter("pairId=").substringBefore("&").trim()
            }
            trimmed.startsWith("{") && trimmed.contains("\"pairId\"") -> {
                // simple json extraction without heavy dependencies
                trimmed.substringAfter("\"pairId\"").substringAfter(":").substringAfter("\"").substringBefore("\"").trim()
            }
            else -> trimmed
        }

        if (pairId.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid pairing payload: missing pairId"))
        }

        return pairingRepository.pairDevice(pairId, ByteArray(0))
    }
}
