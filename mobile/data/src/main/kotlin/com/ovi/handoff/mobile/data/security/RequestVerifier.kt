package com.ovi.handoff.mobile.data.security

import android.util.Base64
import com.ovi.handoff.shared.crypto.Canonical
import com.ovi.handoff.shared.model.PermissionRequest
import com.ovi.handoff.shared.protocol.SignatureAlgorithm
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Verifies that a request really came from the paired desktop.
 *
 * Defence in depth rather than the primary control: the relay already refuses any socket without the
 * pairing token. The wrinkle is that the desktop signs with Ed25519, which `java.security` only
 * exposes from API 33, so on older devices verification is genuinely impossible.
 *
 * The policy that follows from that is deliberate: a signature that is present and provably *wrong*
 * means something is injecting requests, so the request is refused. A signature that simply cannot be
 * checked on this device is accepted and reported, because silently discarding every request on an
 * API 29 phone would break the product for that user with no explanation.
 */
public object RequestVerifier {

    public enum class Result {
        /** Signature checked and valid. */
        VERIFIED,

        /** No key stored, no signature sent, or this device cannot check this algorithm. */
        UNVERIFIABLE,

        /** A signature was present and did not match. Reject. */
        INVALID
    }

    public fun verify(
        request: PermissionRequest,
        desktopPublicKeyBase64: String?,
        algorithm: String = SignatureAlgorithm.ED25519
    ): Result {
        val signature = request.signature?.takeIf { it.isNotBlank() } ?: return Result.UNVERIFIABLE
        val publicKey = decodePublicKey(desktopPublicKeyBase64, algorithm) ?: return Result.UNVERIFIABLE

        val signatureBytes = decodeBase64(signature) ?: return Result.INVALID

        val verifier = runCatching { Signature.getInstance(algorithm) }.getOrNull()
            ?: return Result.UNVERIFIABLE // Algorithm unavailable on this API level.

        val valid = runCatching {
            verifier.initVerify(publicKey)
            verifier.update(Canonical.requestBytes(request.copy(signature = null)))
            verifier.verify(signatureBytes)
        }.getOrDefault(false)

        return if (valid) Result.VERIFIED else Result.INVALID
    }

    private fun decodePublicKey(encoded: String?, algorithm: String): PublicKey? {
        if (encoded.isNullOrBlank()) return null
        val bytes = decodeBase64(encoded) ?: return null
        val keyFactoryAlgorithm = if (algorithm == SignatureAlgorithm.ECDSA_P256_SHA256) "EC" else "Ed25519"
        return runCatching {
            KeyFactory.getInstance(keyFactoryAlgorithm).generatePublic(X509EncodedKeySpec(bytes))
        }.getOrNull()
    }

    private fun decodeBase64(value: String): ByteArray? =
        runCatching { Base64.decode(value, Base64.URL_SAFE) }.getOrNull()
            ?: runCatching { Base64.decode(value, Base64.DEFAULT) }.getOrNull()
}
