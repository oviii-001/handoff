package com.ovi.handoff.mobile.domain.security

/**
 * Signs approval decisions with a key held by the device.
 *
 * This is the piece that was missing entirely: decisions went out with `signature = ""`, and the
 * desktop had no way to tell a real answer from a forged one. The implementation keeps the key in
 * hardware-backed storage, so a decision cannot be signed without the device it was paired to.
 */
public interface DecisionSigner {

    /** Stable identifier for this device, recorded in the audit trail. */
    public fun deviceId(): String

    /** Base64url X.509 public key the desktop verifies against. */
    public fun publicKeyBase64(): String?

    /** Signature algorithm name, announced to the desktop alongside the key. */
    public fun algorithm(): String

    /** Base64url signature over [data], or null when the key is unavailable. */
    public fun sign(data: ByteArray): String?
}
