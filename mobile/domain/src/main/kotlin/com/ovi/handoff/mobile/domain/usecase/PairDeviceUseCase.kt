package com.ovi.handoff.mobile.domain.usecase

import com.ovi.handoff.mobile.domain.repository.PairingRepository
import com.ovi.handoff.mobile.domain.repository.PairingInfo
import com.ovi.handoff.mobile.domain.repository.RelayRepository
import com.ovi.handoff.mobile.domain.provider.PushTokenProvider

/**
 * Parses a pairing payload and stores it.
 *
 * The parser is separated out and made total because the previous version chained
 * `substringAfter("pairId=").substringBefore("&")` calls over the raw string. That silently produced
 * a wrong pair id for any parameter ordering it did not anticipate, and had no way to report that it
 * had failed rather than guessed.
 */
public object PairingPayloadParser {

    public fun parse(payload: String): Result<PairingInfo> {
        val trimmed = payload.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Nothing to pair with: the code was empty."))
        }

        val info = when {
            trimmed.contains('?') && trimmed.contains('=') -> parseUrl(trimmed)
            trimmed.startsWith("{") -> parseJson(trimmed)
            // A bare pair id, typed by hand. It carries no relay secret, so it cannot authenticate.
            else -> PairingInfo(pairId = trimmed, relayHost = null, desktopPublicKey = null, pairSecret = null)
        }

        if (info.pairId.isBlank()) {
            return Result.failure(IllegalArgumentException("That code does not contain a pair id."))
        }
        return Result.success(info)
    }

    private fun parseUrl(payload: String): PairingInfo {
        val query = payload.substringAfter('?', "")
        val params = query.split('&')
            .mapNotNull { part ->
                val key = part.substringBefore('=', "")
                val value = part.substringAfter('=', "")
                if (key.isBlank()) null else key to decodeComponent(value)
            }
            .toMap()

        return PairingInfo(
            pairId = params["pairId"].orEmpty().trim(),
            relayHost = params["host"]?.trim()?.takeIf { it.isNotBlank() },
            desktopPublicKey = params["pubKey"]?.trim()?.takeIf { it.isNotBlank() },
            pairSecret = params["token"]?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    private fun parseJson(payload: String): PairingInfo = PairingInfo(
        pairId = jsonString(payload, "pairId").orEmpty(),
        relayHost = jsonString(payload, "host"),
        desktopPublicKey = jsonString(payload, "pubKey"),
        pairSecret = jsonString(payload, "token")
    )

    /** Minimal string-field read, enough for the flat object the desktop emits. */
    private fun jsonString(payload: String, key: String): String? {
        val marker = "\"$key\""
        val at = payload.indexOf(marker)
        if (at < 0) return null
        val afterColon = payload.indexOf(':', at + marker.length)
        if (afterColon < 0) return null
        val openQuote = payload.indexOf('"', afterColon)
        if (openQuote < 0) return null
        val closeQuote = payload.indexOf('"', openQuote + 1)
        if (closeQuote < 0) return null
        return payload.substring(openQuote + 1, closeQuote).trim().takeIf { it.isNotBlank() }
    }

    private fun decodeComponent(value: String): String =
        value.replace("%3A", ":").replace("%2F", "/").replace("%3a", ":").replace("%2f", "/")
}

/**
 * Pairs this phone with a desktop, and only reports success once the relay agrees.
 *
 * The confirmation step is the point. Pairing used to consist of parsing the code and writing it to
 * disk, both of which succeed regardless of whether the pairing is usable: the app then navigated to
 * "Paired. Waiting for your agent." while the relay was refusing its socket, and the user had no way
 * to discover that. The relay refuses for nameable, fixable reasons — most often that no desktop has
 * claimed the pair room because `handoff --pair` was never left running — so the fix is to wait for
 * the socket and, on failure, roll the pairing back and repeat the relay's own explanation.
 */
public class PairDeviceUseCase(
    private val pairingRepository: PairingRepository,
    private val relayRepository: RelayRepository,
    private val pushTokenProvider: PushTokenProvider
) {
    public suspend operator fun invoke(qrPayload: String): Result<Unit> {
        val trimmed = qrPayload.trim()
        val digitsOnly = trimmed.filter { it.isDigit() }
        val isPin = digitsOnly.length == 6 && trimmed.matches(Regex("^[0-9\\s-]+$"))

        val info = if (isPin) {
            relayRepository.resolvePin(digitsOnly).getOrElse { return Result.failure(it) }
        } else {
            PairingPayloadParser.parse(qrPayload).getOrElse { return Result.failure(it) }
        }

        if (info.pairSecret.isNullOrBlank()) {
            // Without the relay token the socket will be refused, so say so now instead of leaving
            // the user staring at a "connected" screen that never receives anything.
            return Result.failure(
                IllegalArgumentException(
                    "That code is missing its relay token. On your computer run `handoff --pair` and " +
                        "scan the QR code it shows, or paste the whole handoff://pair link."
                )
            )
        }

        pairingRepository.pairDevice(info).getOrElse { return Result.failure(it) }

        // Stored first because the socket needs the host and token to connect at all, then rolled
        // back if the relay will not have us: a half-pairing left on disk is what produced the
        // permanently "connecting" home screen.
        relayRepository.awaitConnected(info.pairId).getOrElse { cause ->
            pairingRepository.clearPairing()
            return Result.failure(cause)
        }

        // Announce this phone's signing key before anything else, so the very first decision the
        // desktop receives is already verifiable.
        relayRepository.announceIdentity(info.pairId)

        pushTokenProvider.getToken()?.let { token ->
            relayRepository.registerPushToken(info.pairId, token)
        }
        return Result.success(Unit)
    }
}
