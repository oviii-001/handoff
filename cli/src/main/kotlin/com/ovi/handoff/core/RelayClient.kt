package com.ovi.handoff.core

import com.ovi.handoff.shared.crypto.Canonical
import com.ovi.handoff.shared.model.DecisionType
import com.ovi.handoff.shared.model.PermissionDecision
import com.ovi.handoff.shared.model.PermissionRequest
import com.ovi.handoff.shared.model.SessionAnnouncement
import com.ovi.handoff.shared.protocol.AckPayload
import com.ovi.handoff.shared.protocol.CancelPayload
import com.ovi.handoff.shared.protocol.EnvelopeCodec
import com.ovi.handoff.shared.protocol.FrameType
import com.ovi.handoff.shared.protocol.PairHello
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
 * Frames are matched to the request they answer, decisions are signature-verified against the key
 * the phone announced at pairing, and one connection is shared by every in-flight request.
 *
 * The behaviour worth calling out is what happens when nobody answers. The wait used to run for the
 * request's full deadline in every failure case, so an agent whose user had never paired a phone sat
 * blocked for five minutes before being told "no decision arrived". The relay knows, at the instant
 * it accepts a request, whether a phone socket was attached and whether a push went out; that now
 * comes back on the ack and converts a five-minute stall into a [RelayWaitBudget.UNREACHABLE_GRACE_MS]
 * one with an outcome the agent can explain to the user. A relay too old to report it keeps the old
 * full-length wait, so the change cannot shorten a legitimate approval.
 */
public class RelayClient(
    private val relayHost: String = DesktopConfig.DEFAULT_RELAY_HOST,
    private val pairId: String = DesktopConfigManager.getPairId(),
    private val pairSecret: String = DesktopConfigManager.getPairSecret(),
    private val keyStoreManager: KeyStoreManager? = null,
    private val privateKey: PrivateKey? = null,
    private val onAbort: () -> Unit = {},
    /** Called when the phone announces its signing key, which is what completes pairing. */
    private val onPairHello: (PairHello) -> Unit = {}
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

    /**
     * Whether a phone currently holds a socket on this pair, as last reported by the relay.
     *
     * Null when the relay has not said, which an older relay never will. Callers must render that
     * as "unknown" rather than "offline".
     */
    @Volatile
    public var phoneOnline: Boolean? = null
        private set

    /** Why the last connection attempt failed, for `--doctor` and for the status tool. */
    @Volatile
    public var lastConnectionError: String? = null
        private set

    private class PendingApproval(
        val request: PermissionRequest,
        val requestHash: String,
        val deferred: CompletableDeferred<ApprovalOutcome>
    ) {
        /** Set once the relay tells us nothing could reach the phone, so we only shorten once. */
        @Volatile
        var graceStarted: Boolean = false
    }

    // ---------------------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------------------

    /**
     * Sends [request] and suspends until the user decides, or until it becomes clear that nobody
     * will.
     *
     * Never returns null and never throws for an ordinary failure: every way this can end is a named
     * [ApprovalOutcome] carrying an explanation, because the caller's job is to tell a human what to
     * do next.
     */
    public suspend fun sendRequestAndWaitForDecision(request: PermissionRequest): ApprovalOutcome {
        // Asking a phone that has never paired can only ever time out, so say so now rather than
        // holding the agent open for the request's full deadline first.
        if (!DesktopConfigManager.isPhonePaired()) {
            return ApprovalOutcome.NotPaired
        }

        val signed = sign(request)
        val hash = Canonical.requestHash(signed)
        val pending = PendingApproval(signed, hash, CompletableDeferred())

        inFlight[signed.id] = pending
        try {
            try {
                ensureConnected()
                outbound.send(EnvelopeCodec.encodeRequest(signed))
            } catch (cause: Exception) {
                // The frame never left this process, so there is nothing to wait for. Reporting it
                // as a timeout would blame the user for a local failure.
                Log.error("Could not queue request ${signed.id}", cause)
                return ApprovalOutcome.RelayUnreachable(cause.message ?: "the outbound queue is closed")
            }

            val budget = RelayWaitBudget.forDeadline(signed.expiresAtEpochMs, System.currentTimeMillis())
            return withTimeoutOrNull(budget) { pending.deferred.await() } ?: ApprovalOutcome.Expired
        } finally {
            inFlight.remove(signed.id)
        }
    }

    /** Tells the phone which IDE and workspace is attached. Fire-and-forget. */
    public suspend fun announceSession(announcement: SessionAnnouncement) {
        ensureConnected()
        outbound.send(EnvelopeCodec.encodeSessionInfo(announcement))
    }

    /** Opens the socket without sending anything, which is what claims the relay room. */
    public fun start() {
        ensureConnected()
    }

    /**
     * Abandons a request: releases the local waiter and tells the phone to drop its card.
     *
     * Both halves matter. Without the frame the phone keeps offering an approval for a tool call the
     * IDE already cancelled, and the relay keeps the request stored and replays it on reconnect.
     */
    public suspend fun cancelRequest(requestId: String, reason: String = "cancelled_by_agent") {
        val removed = inFlight.remove(requestId)
        removed?.deferred?.complete(ApprovalOutcome.Expired)
        runCatching {
            outbound.send(EnvelopeCodec.encodeCancel(CancelPayload(requestId = requestId, reason = reason)))
        }
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
                    lastConnectionError = null

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
                lastConnectionError = cause.message ?: cause::class.simpleName ?: "unknown error"
                Log.warn("Relay connection lost: $lastConnectionError")
            } finally {
                isConnected = false
                // Presence is only meaningful while we hold a socket. Keeping the last value would
                // let the status tool report a phone as online long after we stopped being told.
                phoneOnline = null
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
        val scheme = if (RelayEndpoint.isLocalHost(relayHost)) "ws" else "wss"
        return "$scheme://$relayHost/ws/desktop/$pairId"
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
                inFlight.remove(requestId)?.deferred?.complete(ApprovalOutcome.Expired)
            }

            FrameType.ACK -> handleAck(envelope.requestId, EnvelopeCodec.asAck(envelope))

            FrameType.PRESENCE -> {
                val presence = EnvelopeCodec.asPresence(envelope) ?: return
                presence.phoneOnline?.let { phoneOnline = it }
            }

            FrameType.PAIR_HELLO -> {
                val hello = EnvelopeCodec.asPairHello(envelope) ?: return
                val parsed = KeyStoreManager.decodePublicKey(hello.publicKey, hello.algorithm)
                if (parsed == null) {
                    Log.warn("Phone announced an unreadable signing key; ignoring.")
                    return
                }
                mobileKey = parsed
                mobileKeyAlgorithm = hello.algorithm
                DesktopConfigManager.rememberMobileKey(hello.deviceId, hello.publicKey, hello.algorithm)
                Log.info("Paired phone ${hello.deviceId} announced a ${hello.algorithm} signing key.")
                runCatching { onPairHello(hello) }
            }

            FrameType.ABORT -> {
                Log.warn("Emergency halt received from phone.")
                for (entry in inFlight.values) {
                    entry.deferred.complete(
                        ApprovalOutcome.Decided(
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
                    )
                }
                inFlight.clear()
                onAbort()
            }
        }
    }

    /**
     * Applies what the relay says about a request we sent.
     *
     * The ack is the only signal that arrives *before* a human is involved, which makes it the only
     * chance to release the agent quickly when there is nobody to involve.
     */
    private fun handleAck(requestId: String?, ack: AckPayload?) {
        val id = requestId ?: ack?.requestId ?: return
        val pending = inFlight[id] ?: return
        ack?.phoneOnline?.let { phoneOnline = it }

        if (ack == null) return

        if (ack.status == ACK_STATUS_REJECTED) {
            inFlight.remove(id)
            pending.deferred.complete(
                ApprovalOutcome.RejectedByRelay("too many approvals are already waiting on your phone")
            )
            return
        }

        val grace = RelayWaitBudget.graceAfterAck(ack) ?: return
        if (pending.graceStarted) return
        pending.graceStarted = true

        Log.info(
            "Request $id reached no phone and no push was sent; waiting ${grace / 1000}s more before " +
                "releasing the agent."
        )

        // A grace period rather than an immediate failure: the phone may be reconnecting right now,
        // and the relay replays stored requests on attach, so a short wait still recovers many of
        // these as real answers.
        scope.launch {
            delay(grace)
            // Conditional removal, so a decision that arrived during the grace window wins the race
            // rather than being overwritten by a "phone unreachable" verdict.
            if (inFlight.remove(id, pending)) {
                pending.deferred.complete(ApprovalOutcome.PhoneUnreachable)
            }
        }
    }

    /**
     * Applies a decision only if it provably answers the request it names.
     *
     * Each check closes a distinct hole: an unknown id means the frame answers nothing we asked; a
     * hash mismatch means it answers a *different* request; a replayed nonce means it is a captured
     * frame; a bad signature means it did not come from the paired phone.
     */
    private fun acceptDecision(decision: PermissionDecision) {
        val pending = inFlight[decision.requestId]
        if (pending == null) {
            Log.warn("Ignoring a decision for an unknown request ${decision.requestId}.")
            return
        }

        if (decision.requestHash != pending.requestHash) {
            Log.warn(
                "Rejected a decision for ${decision.requestId}: it was signed over a different request."
            )
            return
        }

        if (!seenNonces.add(decision.nonce)) {
            Log.warn("Rejected a replayed decision for ${decision.requestId}.")
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
                Log.error(
                    "Rejected an unverified decision for ${decision.requestId}. " +
                        "Re-pair the phone with `handoff --pair`, or set HANDOFF_INSECURE=1 to accept " +
                        "unsigned decisions from a pre-v2 app."
                )
                return
            }
            Log.warn(
                "WARNING: accepting an UNVERIFIED decision for ${decision.requestId} because " +
                    "HANDOFF_INSECURE is set. Anyone who can reach your relay room can approve commands."
            )
        }

        inFlight.remove(decision.requestId)
        pending.deferred.complete(ApprovalOutcome.Decided(decision))
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
            Log.error("Could not sign request ${request.id}", it)
            null
        }
        return unsigned.copy(signature = signature)
    }

    private companion object {
        const val PING_INTERVAL_MS = 20_000L
        const val BASE_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 60_000L
        const val NONCE_CACHE_SIZE = 512
        const val ACK_STATUS_REJECTED = "rejected"
    }
}
