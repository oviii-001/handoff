package com.ovi.handoff.core

import com.ovi.handoff.shared.crypto.Canonical
import com.ovi.handoff.shared.model.DecisionType
import com.ovi.handoff.shared.model.PermissionDecision
import com.ovi.handoff.shared.model.PermissionRequest
import com.ovi.handoff.shared.model.SessionAnnouncement
import com.ovi.handoff.shared.protocol.EnvelopeCodec
import com.ovi.handoff.shared.protocol.FrameType
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.security.PrivateKey
import java.security.PublicKey
import java.time.Instant
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.random.Random

/**
 * The desktop's connection to the relay.
 *
 * Replaces a design that opened a fresh WebSocket per request, read the first text frame it saw,
 * and trusted it as the decision. Three consequences of that are fixed here:
 *
 *  - **Frames are matched to requests.** A decision must name the request it answers *and* carry a
 *    hash of that request's canonical bytes. Previously any first frame won, so a decision for a
 *    harmless request could authorize a dangerous one.
 *  - **Signatures are verified.** The phone signs `Canonical.decisionBytes` with the Ed25519 key it
 *    announced at pairing. An unsigned or mismatched decision is dropped.
 *  - **Nothing waits forever.** Each wait is bounded by the request's own deadline, and the relay
 *    also pushes an `expired` frame, so the agent is released either way.
 *
 * One connection is shared by every in-flight request, which also means a burst of tool calls no
 * longer pays for a TLS handshake each.
 */
public class RelayClient(
    private val relayHost: String = DesktopConfig.DEFAULT_RELAY_HOST,
    private val pairId: String = DesktopConfigManager.getPairId(),
    private val pairSecret: String = DesktopConfigManager.getPairSecret(),
    private val keyStoreManager: KeyStoreManager? = null,
    private val privateKey: PrivateKey? = null,
    private val onAbort: () -> Unit = {}
) : java.io.Closeable {

    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingIntervalMillis = PING_INTERVAL_MS
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val outbound = Channel<String>(Channel.UNLIMITED)
    private val inFlight = ConcurrentHashMap<String, PendingApproval>()

    /** Nonces already honoured, so a captured decision frame cannot be replayed. */
    private val seenNonces: MutableSet<String> = Collections.synchronizedSet(
        Collections.newSetFromMap(
            object : LinkedHashMap<String, Boolean>(NONCE_CACHE_SIZE, 0.75f, false) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean =
                    size > NONCE_CACHE_SIZE
            }
        )
    )

    @Volatile
    private var mobileKey: PublicKey? = KeyStoreManager.decodePublicKey(
        DesktopConfigManager.getMobilePublicKey(),
        DesktopConfigManager.getMobileKeyAlgorithm()
    )

    @Volatile
    private var mobileKeyAlgorithm: String = DesktopConfigManager.getMobileKeyAlgorithm()

    @Volatile
    private var connectionJob: Job? = null

    @Volatile
    public var isConnected: Boolean = false
        private set

    private class PendingApproval(
        val request: PermissionRequest,
        val requestHash: String,
        val deferred: CompletableDeferred<PermissionDecision?>
    )

    // ---------------------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------------------

    /**
     * Sends [request] and suspends until the user decides or the request's deadline passes.
     * Returns null when no verified decision arrived.
     */
    public suspend fun sendRequestAndWaitForDecision(request: PermissionRequest): PermissionDecision? {
        val signed = sign(request)
        val hash = Canonical.requestHash(signed)
        val deferred = CompletableDeferred<PermissionDecision?>()

        inFlight[signed.id] = PendingApproval(signed, hash, deferred)
        try {
            ensureConnected()
            outbound.send(EnvelopeCodec.encodeRequest(signed))
            return withTimeoutOrNull(waitBudgetMs(signed)) { deferred.await() }
        } finally {
            inFlight.remove(signed.id)
        }
    }

    /** Tells the phone which IDE and workspace is attached. Fire-and-forget. */
    public suspend fun announceSession(announcement: SessionAnnouncement) {
        ensureConnected()
        outbound.send(EnvelopeCodec.encodeSessionInfo(announcement))
    }

    /** Cancels a wait locally, for example when the IDE sends `notifications/cancelled`. */
    public fun cancelRequest(requestId: String) {
        inFlight.remove(requestId)?.deferred?.complete(null)
    }

    override fun close() {
        outbound.close()
        scope.cancel()
        client.close()
    }

    // ---------------------------------------------------------------------------------------
    // Connection
    // ---------------------------------------------------------------------------------------

    private fun ensureConnected() {
        if (connectionJob?.isActive == true) return
        synchronized(this) {
            if (connectionJob?.isActive == true) return
            connectionJob = scope.launch { connectionLoop() }
        }
    }

    private suspend fun connectionLoop() {
        var attempt = 0
        while (scope.isActive) {
            try {
                client.webSocket(
                    urlString = socketUrl(),
                    request = {
                        // Header rather than a query parameter, so the pairing secret stays out of
                        // access logs and out of any URL that might be printed while debugging.
                        header(HttpHeaders.Authorization, "Bearer $pairSecret")
                    }
                ) {
                    attempt = 0
                    isConnected = true

                    val writer = launch {
                        for (frame in outbound) {
                            send(Frame.Text(frame))
                        }
                    }

                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                handleFrame(frame.readText())
                            }
                        }
                    } finally {
                        writer.cancel()
                    }
                }
            } catch (cause: Exception) {
                if (!scope.isActive) return
                System.err.println("[Handoff] Relay connection lost: ${cause.message}")
            } finally {
                isConnected = false
            }

            if (!scope.isActive) return

            // Full-jitter exponential backoff, capped, so a relay outage does not become a
            // reconnect storm from every paired desktop at once.
            val exponential = min(BASE_BACKOFF_MS shl min(attempt, 20), MAX_BACKOFF_MS)
            delay(Random.nextLong(BASE_BACKOFF_MS, exponential + 1))
            attempt++
        }
    }

    private fun socketUrl(): String {
        val scheme = if (isLocalHost(relayHost)) "ws" else "wss"
        return "$scheme://$relayHost/ws/desktop/$pairId"
    }

    private fun isLocalHost(host: String): Boolean {
        val bare = host.substringBefore(':').lowercase()
        return bare == "localhost" || bare == "127.0.0.1" || bare == "[::1]" || bare == "0.0.0.0"
    }

    // ---------------------------------------------------------------------------------------
    // Inbound frames
    // ---------------------------------------------------------------------------------------

    private fun handleFrame(text: String) {
        val envelope = EnvelopeCodec.decode(text) ?: return

        when (envelope.type) {
            FrameType.DECISION -> {
                val decision = EnvelopeCodec.asDecision(envelope) ?: return
                acceptDecision(decision)
            }

            FrameType.EXPIRED -> {
                val requestId = envelope.requestId ?: return
                inFlight.remove(requestId)?.deferred?.complete(null)
            }

            FrameType.PAIR_HELLO -> {
                val hello = EnvelopeCodec.asPairHello(envelope) ?: return
                val parsed = KeyStoreManager.decodePublicKey(hello.publicKey, hello.algorithm)
                if (parsed == null) {
                    System.err.println("[Handoff] Phone announced an unreadable signing key; ignoring.")
                    return
                }
                mobileKey = parsed
                mobileKeyAlgorithm = hello.algorithm
                DesktopConfigManager.rememberMobileKey(hello.deviceId, hello.publicKey, hello.algorithm)
                System.err.println(
                    "[Handoff] Paired phone ${hello.deviceId} announced a ${hello.algorithm} signing key."
                )
            }

            FrameType.ABORT -> {
                System.err.println("[Handoff] Emergency halt received from phone.")
                for (entry in inFlight.values) {
                    entry.deferred.complete(
                        PermissionDecision(
                            requestId = entry.request.id,
                            decision = DecisionType.CANCEL,
                            issuedAt = Instant.now().toString(),
                            nonce = "abort",
                            deviceId = "phone",
                            requestHash = entry.requestHash,
                            signature = ""
                        )
                    )
                }
                inFlight.clear()
                onAbort()
            }

            FrameType.ACK -> Unit // Storage confirmation; nothing to do on the desktop side.
        }
    }

    /**
     * Applies a decision only if it provably answers the request it names.
     *
     * Each check below closes a distinct hole: an unknown id means the frame answers nothing we
     * asked; a hash mismatch means it answers a *different* request; a replayed nonce means it is a
     * captured frame; a bad signature means it did not come from the paired phone.
     */
    private fun acceptDecision(decision: PermissionDecision) {
        val pending = inFlight[decision.requestId]
        if (pending == null) {
            System.err.println("[Handoff] Ignoring a decision for an unknown request ${decision.requestId}.")
            return
        }

        if (decision.requestHash != pending.requestHash) {
            System.err.println(
                "[Handoff] Rejected a decision for ${decision.requestId}: it was signed over a different request."
            )
            return
        }

        if (!seenNonces.add(decision.nonce)) {
            System.err.println("[Handoff] Rejected a replayed decision for ${decision.requestId}.")
            return
        }

        val verified = KeyStoreManager.verify(
            data = Canonical.decisionBytes(decision),
            signatureBase64 = decision.signature,
            publicKey = mobileKey,
            algorithm = mobileKeyAlgorithm
        )

        if (!verified) {
            if (!DesktopConfigManager.allowUnverifiedDecisions()) {
                System.err.println(
                    "[Handoff] Rejected an unverified decision for ${decision.requestId}. " +
                        "Re-pair the phone with `handoff --pair`, or set HANDOFF_INSECURE=1 to accept " +
                        "unsigned decisions from a pre-v2 app."
                )
                return
            }
            System.err.println(
                "[Handoff] WARNING: accepting an UNVERIFIED decision for ${decision.requestId} " +
                    "because HANDOFF_INSECURE is set. Anyone who can reach your relay room can approve commands."
            )
        }

        inFlight.remove(decision.requestId)
        pending.deferred.complete(decision)
    }

    // ---------------------------------------------------------------------------------------
    // Signing
    // ---------------------------------------------------------------------------------------

    private fun sign(request: PermissionRequest): PermissionRequest {
        val store = keyStoreManager
        val key = privateKey
        if (store == null || key == null) return request.copy(signature = null)

        // Signed over canonical bytes, not over a JSON rendering, so the phone can recompute exactly
        // the same input without depending on serializer field order.
        val unsigned = request.copy(signature = null)
        val signature = runCatching {
            KeyStoreManager.encodeSignature(store.sign(Canonical.requestBytes(unsigned), key))
        }.getOrElse {
            System.err.println("[Handoff] Could not sign request ${request.id}: ${it.message}")
            null
        }
        return unsigned.copy(signature = signature)
    }

    /** How long to wait: the request's own deadline, clamped to something sane. */
    private fun waitBudgetMs(request: PermissionRequest): Long {
        val deadline = request.expiresAtEpochMs ?: return DEFAULT_WAIT_MS
        val remaining = deadline - System.currentTimeMillis()
        return remaining.coerceIn(MIN_WAIT_MS, MAX_WAIT_MS)
    }

    private companion object {
        const val PING_INTERVAL_MS = 20_000L
        const val BASE_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 60_000L
        const val NONCE_CACHE_SIZE = 512
        const val DEFAULT_WAIT_MS = 300_000L
        const val MIN_WAIT_MS = 5_000L
        const val MAX_WAIT_MS = 900_000L
    }
}
