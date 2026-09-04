package com.ovi.handoff.mobile.data.repository

import com.ovi.handoff.shared.model.PermissionDecision
import com.ovi.handoff.shared.model.PermissionRequest
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.ovi.handoff.mobile.domain.repository.RelayRepository
import com.ovi.handoff.mobile.data.local.RequestDao
import com.ovi.handoff.mobile.data.local.toDomain
import com.ovi.handoff.mobile.data.local.toEntity
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

import com.ovi.handoff.mobile.domain.notification.NotificationNotifier

import com.ovi.handoff.mobile.domain.repository.PairingRepository
import com.ovi.handoff.shared.model.SessionAnnouncement
import com.ovi.handoff.shared.model.cleanName
import com.ovi.handoff.shared.model.resolveProjectOrWorkspace

class RelayRepositoryImpl(
    private val requestDao: RequestDao,
    private val relayHost: String = "agentapprove-relay.ismamhasanovi.workers.dev",
    private val notificationNotifier: NotificationNotifier? = null,
    private val pairingRepository: PairingRepository? = null
) : RelayRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingIntervalMillis = 20_000
        }
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null
    
    // Multiplexes all outbound decisions through the single long-lived WebSocket
    private val outboundMessages = MutableSharedFlow<String>(extraBufferCapacity = 100)

    override fun observeRequests(pairId: String): Flow<PermissionRequest?> {
        startSyncJob(pairId)
        return requestDao.observePendingRequests().map { list ->
            list.firstOrNull()?.toDomain()
        }
    }

    private fun startSyncJob(pairId: String) {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            var attempt = 0
            while (isActive) {
                try {
                    client.webSocket(method = HttpMethod.Get, host = relayHost, path = "/ws/mobile/$pairId") {
                        // Connection successful, reset backoff
                        attempt = 0
                        
                        // Launch concurrent sender job within the websocket scope
                        val sendJob = launch {
                            outboundMessages.collect { message ->
                                send(Frame.Text(message))
                            }
                        }

                        incoming.consumeEach { frame ->
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                try {
                                    val element = json.parseToJsonElement(text)
                                    val obj = element as? kotlinx.serialization.json.JsonObject
                                    val type = obj?.get("type")?.let { 
                                        (it as? kotlinx.serialization.json.JsonPrimitive)?.content 
                                    }

                                    if (type == "session_info") {
                                        val announcement = json.decodeFromString(SessionAnnouncement.serializer(), text)
                                        val cleanIde = announcement.agent.cleanName()
                                        val cleanWs = resolveProjectOrWorkspace(announcement.session.project, announcement.session.workspace)
                                        pairingRepository?.saveConnectedSession(cleanIde, cleanWs)
                                    } else {
                                        val request = json.decodeFromString(PermissionRequest.serializer(), text)
                                        requestDao.insert(request.toEntity())
                                        val cleanIde = request.agent.cleanName()
                                        val cleanWs = request.resolveProjectOrWorkspace()
                                        pairingRepository?.saveConnectedSession(cleanIde, cleanWs)
                                        notificationNotifier?.postPermissionRequestNotification(request, pairId)
                                    }
                                } catch (_: Exception) {
                                    // Non-JSON or unhandled frame
                                }
                            }
                        }
                        
                        // If incoming channel closes, cancel the sender job so we can reconnect
                        sendJob.cancel()
                    }
                } catch (e: Exception) {
                    // Full Jitter Exponential Backoff (max 60s)
                    val baseDelay = 1000L
                    val maxDelay = 60000L
                    val shift = kotlin.math.min(attempt, 30) // Prevent overflow
                    val exponential = kotlin.math.min(baseDelay * (1L shl shift), maxDelay)
                    val jitter = kotlin.random.Random.nextLong(0, baseDelay + 1)
                    val backoff = kotlin.math.min(exponential + jitter, maxDelay)
                    
                    delay(backoff)
                    attempt++
                }
            }
        }
    }

    override fun observeHistory(): Flow<List<PermissionRequest>> {
        return requestDao.observeAllRequests().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun syncRequests(pairId: String): Result<Unit> {
        startSyncJob(pairId)
        return Result.success(Unit)
    }

    override suspend fun sendDecision(pairId: String, decision: PermissionDecision): Result<Unit> {
        return try {
            val json = Json.encodeToString(PermissionDecision.serializer(), decision)
            startSyncJob(pairId)
            outboundMessages.emit(json)
            
            withContext(Dispatchers.IO) {
                requestDao.markAsResolved(decision.requestId)
            }
            notificationNotifier?.dismissNotification(decision.requestId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun registerPushToken(pairId: String, token: String): Result<Unit> = runCatching {
        startSyncJob(pairId)
        val payload = buildJsonObject {
            put("type", "fcm_register")
            put("fcmToken", token)
        }
        outboundMessages.emit(payload.toString())
    }

    override suspend fun abortSession(pairId: String): Result<Unit> {
        return try {
            val abortJson = """{"type":"abort","action":"emergency_stop"}"""
            startSyncJob(pairId)
            outboundMessages.emit(abortJson)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clearHistory(): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                requestDao.clearHistory()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
