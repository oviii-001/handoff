package com.ovi.handoff.mobile.domain.usecase

import com.ovi.handoff.mobile.domain.repository.PairingRepository
import com.ovi.handoff.mobile.domain.repository.RelayRepository
import com.ovi.handoff.mobile.domain.provider.PushTokenProvider

class PairDeviceUseCase(
    private val pairingRepository: PairingRepository,
    private val relayRepository: RelayRepository,
    private val pushTokenProvider: PushTokenProvider
) {
    suspend operator fun invoke(qrPayload: String): Result<Unit> {
        val trimmed = qrPayload.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Pairing payload cannot be empty"))
        }

        var pairId = ""
        var encodedPubKey = ""

        if (trimmed.contains("pairId=")) {
            pairId = trimmed.substringAfter("pairId=").substringBefore("&").trim()
            if (trimmed.contains("pubKey=")) {
                encodedPubKey = trimmed.substringAfter("pubKey=").substringBefore("&").trim()
            }
        } else if (trimmed.startsWith("{") && trimmed.contains("\"pairId\"")) {
            pairId = trimmed.substringAfter("\"pairId\"").substringAfter(":").substringAfter("\"").substringBefore("\"").trim()
            if (trimmed.contains("\"pubKey\"")) {
                encodedPubKey = trimmed.substringAfter("\"pubKey\"").substringAfter(":").substringAfter("\"").substringBefore("\"").trim()
            }
        } else {
            pairId = trimmed
        }

        if (pairId.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid pairing payload: missing pairId"))
        }

        val pubKeyBytes = if (encodedPubKey.isNotBlank()) {
            try {
                java.util.Base64.getUrlDecoder().decode(encodedPubKey)
            } catch (e: Exception) {
                ByteArray(0)
            }
        } else {
            ByteArray(0)
        }

        val result = pairingRepository.pairDevice(pairId, pubKeyBytes)
        if (result.isSuccess) {
            val token = pushTokenProvider.getToken()
            if (token != null) {
                relayRepository.registerPushToken(pairId, token)
            }
        }
        return result
    }
}
