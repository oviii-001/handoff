package com.ovi.handoff.core

import com.ovi.handoff.shared.protocol.SignatureAlgorithm
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

@Serializable
public data class DesktopConfig(
    val pairId: String,
    val relayHost: String = DEFAULT_RELAY_HOST,
    /**
     * Shared secret that authenticates both sockets of this pair to the relay.
     *
     * The relay used to accept anyone who knew the pair id, so a guessed or leaked id was enough to
     * approve arbitrary commands. The secret is 256 bits, travels only inside the pairing QR code,
     * and never appears in a log line.
     *
     * Defaults to empty so a config written by a pre-v2 build still decodes: [DesktopConfigManager]
     * then mints a secret in place rather than discarding the file, which would silently rotate the
     * user's pair id and break an existing pairing.
     */
    val pairSecret: String = "",
    /** Device id the phone announced at pairing, recorded for the audit trail. */
    val mobileDeviceId: String? = null,
    /** Base64url X.509 public key the phone signs decisions with. */
    val mobilePublicKey: String? = null,
    /**
     * Signature algorithm for [mobilePublicKey].
     *
     * Stored rather than assumed: the phone uses a hardware-backed EC P-256 key because Android's
     * keystore offers no Ed25519 at this app's minimum SDK, while the desktop signs with Ed25519.
     */
    val mobileKeyAlgorithm: String = SignatureAlgorithm.ECDSA_P256_SHA256
) {
    public companion object {
        public const val DEFAULT_RELAY_HOST: String = "agentapprove-relay.ismamhasanovi.workers.dev"
    }
}

/**
 * Reads and writes `~/.handoff/config.json`.
 *
 * The config is cached after the first read. Every relay call previously went through
 * `getPairId()` and `getRelayHost()`, each of which re-read and re-parsed the file from disk.
 */
public object DesktopConfigManager {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val random = SecureRandom()

    private val configDir = File(System.getProperty("user.home"), ".handoff")
    private val configFile = File(configDir, "config.json")

    @Volatile
    private var cached: DesktopConfig? = null

    private val lock = Any()

    public fun loadConfig(): DesktopConfig {
        cached?.let { return it }
        synchronized(lock) {
            cached?.let { return it }

            val loaded = readFromDisk() ?: createAndPersist()
            val migrated = ensureSecret(loaded)
            cached = migrated
            return migrated
        }
    }

    private fun readFromDisk(): DesktopConfig? {
        if (!configFile.exists()) return null
        return runCatching { json.decodeFromString(DesktopConfig.serializer(), configFile.readText()) }
            .onFailure {
                System.err.println("[Handoff] config.json is unreadable (${it.message}); regenerating pair identity.")
            }
            .getOrNull()
    }

    /** A config written by a pre-v2 build has no `pairSecret`, so mint one rather than failing. */
    private fun ensureSecret(config: DesktopConfig): DesktopConfig {
        if (config.pairSecret.isNotBlank()) return config
        val upgraded = config.copy(pairSecret = newSecret())
        persist(upgraded)
        System.err.println(
            "[Handoff] Added a relay pairing secret to your config. Re-pair your phone with `handoff --pair`."
        )
        return upgraded
    }

    private fun createAndPersist(): DesktopConfig {
        val config = DesktopConfig(
            pairId = newPairId(),
            relayHost = System.getenv("HANDOFF_RELAY_HOST") ?: DesktopConfig.DEFAULT_RELAY_HOST,
            pairSecret = newSecret()
        )
        persist(config)
        return config
    }

    public fun saveConfig(config: DesktopConfig) {
        synchronized(lock) {
            persist(config)
            cached = config
        }
    }

    private fun persist(config: DesktopConfig) {
        runCatching {
            SecureFiles.writeSecureText(configFile, json.encodeToString(DesktopConfig.serializer(), config))
        }.onFailure {
            System.err.println("[Handoff] Could not write ${configFile.absolutePath}: ${it.message}")
        }
    }

    /**
     * Issues a new pair id *and* a new secret, invalidating the old pairing.
     *
     * Rotating the id alone would leave the previous secret valid on the relay room it already
     * claimed, so both move together.
     */
    public fun rotatePair(): DesktopConfig {
        synchronized(lock) {
            val rotated = loadConfig().copy(
                pairId = newPairId(),
                pairSecret = newSecret(),
                mobileDeviceId = null,
                mobilePublicKey = null
            )
            persist(rotated)
            cached = rotated
            return rotated
        }
    }

    /** Records the phone's signing key so later decisions can be verified. */
    public fun rememberMobileKey(deviceId: String, publicKey: String, algorithm: String) {
        synchronized(lock) {
            val current = loadConfig()
            if (current.mobileDeviceId == deviceId &&
                current.mobilePublicKey == publicKey &&
                current.mobileKeyAlgorithm == algorithm
            ) {
                return
            }
            val updated = current.copy(
                mobileDeviceId = deviceId,
                mobilePublicKey = publicKey,
                mobileKeyAlgorithm = algorithm
            )
            persist(updated)
            cached = updated
        }
    }

    public fun getPairId(): String = System.getenv("HANDOFF_PAIR_ID") ?: loadConfig().pairId

    public fun getRelayHost(): String = System.getenv("HANDOFF_RELAY_HOST") ?: loadConfig().relayHost

    public fun getPairSecret(): String = System.getenv("HANDOFF_PAIR_SECRET") ?: loadConfig().pairSecret

    public fun getMobilePublicKey(): String? = loadConfig().mobilePublicKey

    public fun getMobileKeyAlgorithm(): String = loadConfig().mobileKeyAlgorithm

    /**
     * Whether an unsigned or unverifiable decision may still be honoured.
     *
     * Defaults to false: a decision that cannot be verified is not a decision. `HANDOFF_INSECURE=1`
     * exists only so someone mid-upgrade, with a phone still on the v1 app, can get work done, and
     * it prints a warning every time it is used.
     */
    public fun allowUnverifiedDecisions(): Boolean =
        System.getenv("HANDOFF_INSECURE")?.trim()?.lowercase() in setOf("1", "true", "yes")

    private fun newPairId(): String = "pair-" + UUID.randomUUID().toString().take(8)

    private fun newSecret(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
