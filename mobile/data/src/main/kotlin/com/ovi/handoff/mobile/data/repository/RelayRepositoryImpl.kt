package com.ovi.handoff.mobile.data.repository

import com.ovi.handoff.mobile.data.local.RequestDao
import com.ovi.handoff.mobile.data.local.toDomain
import com.ovi.handoff.mobile.data.local.toEntity
import com.ovi.handoff.mobile.data.security.RequestVerifier
import com.ovi.handoff.mobile.domain.notification.NotificationNotifier
import com.ovi.handoff.mobile.domain.repository.ConnectionState
import com.ovi.handoff.mobile.domain.repository.PairingRepository
import com.ovi.handoff.mobile.domain.repository.RelayRepository
import com.ovi.handoff.mobile.domain.repository.RequestRecord
import com.ovi.handoff.mobile.domain.security.DecisionSigner
import com.ovi.handoff.shared.model.DecisionType
import com.ovi.handoff.shared.model.PermissionDecision
import com.ovi.handoff.shared.model.PermissionRequest
import com.ovi.handoff.shared.model.resolveProjectOrWorkspace
import com.ovi.handoff.shared.protocol.AckPayload
import com.ovi.handoff.shared.protocol.EnvelopeCodec
import com.ovi.handoff.shared.protocol.FrameType
import com.ovi.handoff.shared.protocol.PairHello
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import com.ovi.handoff.mobile.domain.repository.PairingInfo
import com.ovi.handoff.mobile.domain.usecase.PairingPayloadParser
import com.ovi.handoff.mobile.data.di.DEFAULT_RELAY_HOST
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.random.Random

/**
 * The phone's relay connection and local request store.
 *
 * The defect this class existed to demonstrate was quiet data loss. Outbound frames were pushed into
 * a `MutableSharedFlow` with no replay, so anything emitted before the socket's collector existed was
 * dropped with no error, and the request was marked resolved locally regardless. From the user's side
 * an approval looked delivered while the agent kept waiting.
 *
 * The queue is now explicit: a frame stays in [outbox] until the relay acknowledges it, is re-sent on
 * every reconnect, and the local row is only marked resolved once that acknowledgement arrives.
 */
public class RelayRepositoryImpl(
    private val requestDao: RequestDao,
    private val pairingRepository: PairingRepository,
    private val signer: DecisionSigner,
    private val defaultRelayHost: String,
    private val notificationNotifier: NotificationNotifier? = null
) : RelayRepository {

    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingIntervalMillis = PING_INTERVAL_MS
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow(ConnectionState.OFFLINE)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    override val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    /** Whether the desktop currently holds a socket, as last reported by the relay. */
    private val _desktopOnline = MutableStateFlow<Boolean?>(null)
    public val desktopOnline: StateFlow<Boolean?> = _desktopOnline.asStateFlow()

    /** Frames awaiting relay acknowledgement, in send order. Survives reconnects. */
    private val outbox = LinkedHashMap<String, String>()
    private val outboxLock = Mutex()

    /**
     * Frames already written on the current connection.
     *
     * Cleared whenever the socket is re-established, which is what makes the outbox a redelivery
     * queue rather than a source of duplicates: everything unacknowledged is re-sent after a
     * reconnect, but nothing is sent twice while a single connection is healthy.
     */
    private val sentOnThisConnection = mutableSetOf<String>()

    /** Signals the writer that the outbox changed, without coupling it to a specific frame. */
    private val wakeWriter = Channel<Unit>(Channel.CONFLATED)

    private val acks = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    @Volatile
    private var connectionJob: Job? = null

    @Volatile
    private var activePairId: String? = null

    /** Last socket-level failure, used only to enrich a diagnosis the relay could not provide. */
    @Volatile
    private var lastSocketError: String? = null

    // -----------------------------------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------------------------------

    override fun observePendingRequests(pairId: String): Flow<List<PermissionRequest>> {
        scope.launch { connect(pairId) }
        return requestDao.observePendingRequests().map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeHistory(): Flow<List<RequestRecord>> =
        requestDao.observeHistory(HISTORY_LIMIT).map { rows ->
            rows.map { row ->
                RequestRecord(
                    request = row.toDomain(),
                    isPending = row.isPending,
                    decision = row.decision,
                    decidedAtEpochMs = row.decidedAtEpochMs
                )
            }
        }

    override suspend fun getRequest(id: String): PermissionRequest? =
        requestDao.findById(id)?.toDomain()

    // -----------------------------------------------------------------------------------------
    // Writes
    // -----------------------------------------------------------------------------------------

    override suspend fun connect(pairId: String): Result<Unit> = runCatching {
        activePairId = pairId
        if (connectionJob?.isActive == true) return@runCatching
        synchronized(this) {
            if (connectionJob?.isActive == true) return@synchronized
            connectionJob = scope.launch { connectionLoop(pairId) }
        }
    }

    /**
     * Connects and waits for the relay to accept us, or reports why it will not.
     *
     * The diagnosis comes from the relay's plain-HTTP pair status rather than from the socket. A
     * rejected WebSocket upgrade surfaces through OkHttp/Ktor as an opaque failure, so the reason —
     * which is the only useful part — has to be asked for separately.
     */
    override suspend fun awaitConnected(pairId: String, timeoutMs: Long): Result<Unit> {
        connect(pairId)

        val connected = withTimeoutOrNull(timeoutMs) {
            connectionState.first { it == ConnectionState.CONNECTED }
            true
        } ?: false

        if (connected) {
            _connectionError.value = null
            return Result.success(Unit)
        }

        val pairing = pairingRepository.getPairing()
        val host = pairing?.relayHost?.takeIf { it.isNotBlank() } ?: defaultRelayHost
        val reason = RelayDiagnostics.describe(
            client = client,
            host = host,
            pairId = pairId,
            token = pairing?.pairSecret,
            socketError = lastSocketError
        )

        _connectionError.value = reason
        return Result.failure(IllegalStateException(reason))
    }

    override suspend fun sendDecision(pairId: String, decision: PermissionDecision): Result<Unit> {
        connect(pairId)

        val delivered = enqueueAndAwaitAck(
            key = "decision:${decision.requestId}",
            frame = EnvelopeCodec.encodeDecision(decision)
        )

        if (!delivered) {
            // Left in the outbox so it still goes out when the network returns, but reported as a
            // failure so the request stays in the queue and the user is not told it was delivered.
            return Result.failure(
                IllegalStateException("Could not reach your desktop. The decision will be sent when the connection returns.")
            )
        }

        requestDao.resolve(decision.requestId, decision.decision, System.currentTimeMillis())
        requestDao.trimHistory(HISTORY_LIMIT)
        notificationNotifier?.dismissNotification(decision.requestId)
        return Result.success(Unit)
    }

    override suspend fun announceIdentity(pairId: String): Result<Unit> = runCatching {
        connect(pairId)
        val publicKey = signer.publicKeyBase64()
            ?: throw IllegalStateException("This device has no signing key yet.")

        // Keyed, so repeated announcements collapse instead of piling up in the outbox.
        enqueue(
            key = "pair_hello",
            frame = EnvelopeCodec.encodePairHello(
                PairHello(
                    deviceId = signer.deviceId(),
                    publicKey = publicKey,
                    algorithm = signer.algorithm()
                )
            )
        )
    }

    override suspend fun registerPushToken(pairId: String, token: String): Result<Unit> = runCatching {
        connect(pairId)
        enqueue(
            key = "fcm_register",
            frame = EnvelopeCodec.encode(
                type = FrameType.FCM_REGISTER,
                payload = buildJsonObject { put("fcmToken", token) }
            )
        )
    }

    override suspend fun abortSession(pairId: String): Result<Unit> = runCatching {
        connect(pairId)
        enqueue(
            key = "abort:${System.currentTimeMillis()}",
            frame = EnvelopeCodec.encode(
                type = FrameType.ABORT,
                payload = buildJsonObject { put("action", "emergency_stop") }
            )
        )
    }

    override suspend fun clearHistory(): Result<Unit> = runCatching {
        requestDao.clearHistory()
    }

    override suspend fun expireOverdueRequests(nowEpochMs: Long): Result<Int> = runCatching {
        val expired = requestDao.pendingRequests().filter { row ->
            row.expiresAtEpochMs != null && row.expiresAtEpochMs <= nowEpochMs
        }
        // Dismiss first: a notification for a request nobody is waiting on any more invites the user
        // to approve something that has already been abandoned.
        expired.forEach { notificationNotifier?.dismissNotification(it.id) }
        requestDao.expireOverdue(nowEpochMs, DecisionType.EXPIRED)
    }

    // -----------------------------------------------------------------------------------------
    // Outbox
    // -----------------------------------------------------------------------------------------

    private suspend fun enqueue(key: String, frame: String) {
        outboxLock.withLock { outbox[key] = frame }
        wakeWriter.trySend(Unit)
    }

    private suspend fun enqueueAndAwaitAck(key: String, frame: String): Boolean {
        val ackKey = key.substringAfter(':', key)
        val deferred = CompletableDeferred<Boolean>()
        acks[ackKey] = deferred
        enqueue(key, frame)

        val acked = withTimeoutOrNull(ACK_TIMEOUT_MS) { deferred.await() } ?: false
        if (!acked) acks.remove(ackKey)
        return acked
    }

    private suspend fun drainOutbox(send: suspend (String) -> Unit) {
        val due = outboxLock.withLock {
            outbox.entries
                .filter { it.key !in sentOnThisConnection }
                .map { it.key to it.value }
        }
        for ((key, frame) in due) {
            send(frame)
            outboxLock.withLock { sentOnThisConnection += key }
        }
    }

    private suspend fun resetConnectionSendState() {
        outboxLock.withLock { sentOnThisConnection.clear() }
    }

    private suspend fun acknowledge(requestId: String, ack: AckPayload?) {
        outboxLock.withLock {
            val matching = outbox.keys.filter { it.substringAfter(':', it) == requestId }
            matching.forEach {
                outbox.remove(it)
                sentOnThisConnection.remove(it)
            }
        }
        ack?.desktopOnline?.let { _desktopOnline.value = it }
        acks.remove(requestId)?.complete(true)
    }

    // -----------------------------------------------------------------------------------------
    // Connection
    // -----------------------------------------------------------------------------------------

    private suspend fun connectionLoop(pairId: String) {
        var attempt = 0

        while (scope.isActive) {
            val pairing = pairingRepository.getPairing()
            val secret = pairing?.pairSecret

            if (pairing == null || secret.isNullOrBlank()) {
                // Without the token the relay refuses the socket, so stop instead of hammering it.
                // This is the state a phone paired with a pre-v2 QR code lands in; the pairing screen
                // reports it rather than leaving a "connected" screen that never receives anything.
                _connectionState.value = ConnectionState.OFFLINE
                _connectionError.value =
                    "This pairing has no relay token. On your computer run `handoff --pair` and scan the code again."
                return
            }

            val host = pairing.relayHost?.takeIf { it.isNotBlank() } ?: defaultRelayHost
            _connectionState.value = ConnectionState.CONNECTING

            try {
                client.webSocket(
                    urlString = socketUrl(host, pairId),
                    request = { header(HttpHeaders.Authorization, "Bearer $secret") }
                ) {
                    attempt = 0
                    resetConnectionSendState()
                    _connectionState.value = ConnectionState.CONNECTED
                    _connectionError.value = null

                    // Re-announce on every connect: the desktop may have restarted and forgotten the
                    // key, and the relay replays this to a desktop that reconnects later.
                    scope.launch { announceIdentity(pairId) }

                    val writer = launch {
                        drainOutbox { send(Frame.Text(it)) }
                        for (unused in wakeWriter) {
                            drainOutbox { send(Frame.Text(it)) }
                        }
                    }

                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                handleFrame(frame.readText(), pairing.desktopPublicKey)
                            }
                        }
                    } finally {
                        writer.cancel()
                    }
                }
            } catch (cause: Exception) {
                // Recorded rather than swallowed. Discarding this is what left a refused phone
                // indistinguishable from a phone with no signal, with nothing on screen either way.
                lastSocketError = cause.message ?: cause::class.simpleName
            } finally {
                _connectionState.value = ConnectionState.OFFLINE
                // Presence is only meaningful while a socket is up; keeping the last value would
                // claim a desktop is attached long after we stopped being told.
                _desktopOnline.value = null
            }

            if (!scope.isActive) return

            val exponential = min(BASE_BACKOFF_MS shl min(attempt, 20), MAX_BACKOFF_MS)
            delay(Random.nextLong(BASE_BACKOFF_MS, exponential + 1))
            attempt++
        }
    }

    private fun socketUrl(host: String, pairId: String): String =
        "${RelayUrls.wsScheme(host)}://$host/ws/mobile/$pairId"

    private suspend fun handleFrame(text: String, desktopPublicKey: String?) {
        val envelope = EnvelopeCodec.decode(text) ?: return

        when (envelope.type) {
            FrameType.REQUEST -> {
                val request = EnvelopeCodec.asRequest(envelope) ?: return
                storeRequest(request, desktopPublicKey)
            }

            FrameType.SESSION_INFO -> {
                val announcement = EnvelopeCodec.asSessionAnnouncement(envelope) ?: return
                pairingRepository.saveConnectedSession(
                    ideName = announcement.agent.name,
                    workspaceName = resolveProjectOrWorkspace(
                        announcement.session.project,
                        announcement.session.workspace
                    )
                )
            }

            FrameType.EXPIRED -> {
                val requestId = envelope.requestId ?: return
                requestDao.resolve(requestId, DecisionType.EXPIRED, System.currentTimeMillis())
                notificationNotifier?.dismissNotification(requestId)
            }

            FrameType.CANCEL -> {
                // The agent stopped waiting. Clearing the card is the whole point: leaving it up
                // invites the user to authorize an action nobody will act on.
                val requestId = envelope.requestId ?: EnvelopeCodec.asCancel(envelope)?.requestId ?: return
                requestDao.resolve(requestId, DecisionType.CANCEL, System.currentTimeMillis())
                notificationNotifier?.dismissNotification(requestId)
            }

            FrameType.PRESENCE -> {
                val presence = EnvelopeCodec.asPresence(envelope) ?: return
                presence.desktopOnline?.let { _desktopOnline.value = it }
            }

            FrameType.ACK -> {
                val requestId = envelope.requestId ?: return
                acknowledge(requestId, EnvelopeCodec.asAck(envelope))
            }
        }
    }

    private suspend fun storeRequest(request: PermissionRequest, desktopPublicKey: String?) {
        val deadline = request.expiresAtEpochMs
        if (deadline != null && deadline <= System.currentTimeMillis()) {
            // A replay of something already past its deadline. Showing it would invite the user to
            // approve an action the agent has stopped waiting on.
            return
        }

        val verification = RequestVerifier.verify(request, desktopPublicKey)
        if (verification == RequestVerifier.Result.INVALID) {
            // A present-but-wrong signature means something other than the paired desktop produced
            // this frame. An unverifiable one (older API, no algorithm) is accepted; see RequestVerifier.
            return
        }

        requestDao.upsertPending(request.toEntity())
        val pairId = activePairId ?: return
        notificationNotifier?.postPermissionRequestNotification(request, pairId)

        pairingRepository.saveConnectedSession(
            ideName = request.agent.name,
            workspaceName = resolveProjectOrWorkspace(
                request.session.project,
                request.session.workspace,
                request.permission.cwd
            )
        )
    }

    override suspend fun resolvePin(pin: String): Result<PairingInfo> = runCatching {
        val targetHost = pairingRepository.getPairing()?.relayHost ?: DEFAULT_RELAY_HOST
        val scheme = RelayUrls.httpScheme(targetHost)
        val response = client.get("$scheme://$targetHost/pin/resolve?code=$pin")
        if (response.status.value == 404) {
            throw IllegalArgumentException("Code $pin not found or expired. Make sure 'handoff --pair' is running on your desktop.")
        }
        if (response.status.value !in 200..299) {
            throw IllegalStateException("Relay error: HTTP ${response.status.value}")
        }
        val bodyText = response.bodyAsText()
        val jsonParser = Json { ignoreUnknownKeys = true }
        val root = jsonParser.parseToJsonElement(bodyText).jsonObject
        val payloadObj = root["payload"]?.jsonObject ?: root
        val pairUrl = payloadObj["pairUrl"]?.jsonPrimitive?.contentOrNull
        if (!pairUrl.isNullOrBlank()) {
            PairingPayloadParser.parse(pairUrl).getOrThrow()
        } else {
            val pairId = payloadObj["pairId"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalStateException("Invalid response: missing pairId")
            PairingInfo(
                pairId = pairId,
                relayHost = payloadObj["host"]?.jsonPrimitive?.contentOrNull ?: targetHost,
                desktopPublicKey = payloadObj["pubKey"]?.jsonPrimitive?.contentOrNull,
                pairSecret = payloadObj["token"]?.jsonPrimitive?.contentOrNull
            )
        }
    }

    private companion object {
        const val PING_INTERVAL_MS = 20_000L
        const val BASE_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 60_000L
        const val ACK_TIMEOUT_MS = 12_000L
        const val HISTORY_LIMIT = 500
    }
}
