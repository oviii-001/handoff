package com.ovi.handoff.mobile.data.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.ovi.handoff.mobile.domain.security.DecisionSigner
import com.ovi.handoff.shared.crypto.Sha256
import com.ovi.handoff.shared.protocol.SignatureAlgorithm
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Signs decisions with a hardware-backed key held in the Android keystore.
 *
 * EC P-256 rather than Ed25519: the keystore offers no Ed25519 at this app's `minSdk` of 29, and a
 * software Ed25519 key would be a downgrade, since the point of the keystore is that the private key
 * cannot leave the device even if the app is compromised. The algorithm travels in `PairHello`, so the
 * desktop verifies with whatever the phone actually used.
 *
 * The key deliberately does not require user authentication to *use*. Biometric confirmation is a
 * separate, configurable gate on the approval itself; binding it to the key instead would make every
 * decision impossible to sign on a device with no enrolled biometric, including from the notification
 * shade where no prompt can be shown.
 */
public class AndroidDecisionSigner : DecisionSigner {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    @Volatile
    private var cachedPublicKey: PublicKey? = null

    override fun deviceId(): String {
        val encoded = publicKey()?.encoded ?: return "unpaired-device"
        // Derived from the signing key rather than a hardware identifier: it is stable for the life of
        // the pairing, changes when the user re-pairs, and reveals nothing else about the device.
        return "phone-" + Sha256.hashHex(encoded).take(12)
    }

    override fun publicKeyBase64(): String? =
        publicKey()?.encoded?.let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }

    override fun algorithm(): String = SignatureAlgorithm.ECDSA_P256_SHA256

    override fun sign(data: ByteArray): String? {
        val privateKey = privateKey() ?: return null
        return runCatching {
            val signature = Signature.getInstance(SignatureAlgorithm.ECDSA_P256_SHA256)
            signature.initSign(privateKey)
            signature.update(data)
            Base64.encodeToString(
                signature.sign(),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
        }.getOrNull()
    }

    /** Discards the key pair, so a re-pair issues a fresh identity the old desktop cannot verify. */
    public fun reset() {
        cachedPublicKey = null
        runCatching { keyStore.deleteEntry(KEY_ALIAS) }
    }

    private fun publicKey(): PublicKey? {
        cachedPublicKey?.let { return it }
        val resolved = runCatching {
            ensureKeyPair()
            keyStore.getCertificate(KEY_ALIAS)?.publicKey
        }.getOrNull()
        cachedPublicKey = resolved
        return resolved
    }

    private fun privateKey(): PrivateKey? = runCatching {
        ensureKeyPair()
        keyStore.getKey(KEY_ALIAS, null) as? PrivateKey
    }.getOrNull()

    private fun ensureKeyPair() {
        if (keyStore.containsAlias(KEY_ALIAS)) return

        // StrongBox where the device has it, with a graceful fall back: requesting it on hardware
        // without a secure element throws, and an unsignable decision is worse than a TEE-backed key.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val generated = runCatching { generate(useStrongBox = true) }.isSuccess
            if (generated) return
        }
        generate(useStrongBox = false)
    }

    private fun generate(useStrongBox: Boolean) {
        val builder = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)

        if (useStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }

        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        generator.initialize(builder.build())
        generator.generateKeyPair()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "handoff_decision_signing_key"
    }
}
