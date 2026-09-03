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
import kotlinx.serialization.json.Json
import com.ovi.handoff.mobile.domain.repository.RelayRepository
import com.ovi.handoff.mobile.data.local.RequestDao
import com.ovi.handoff.mobile.data.local.toDomain
import com.ovi.handoff.mobile.data.local.toEntity
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class RelayRepositoryImpl(
    private val requestDao: RequestDao,
    private val relayHost: String = "agentapprove-relay.ismamhasanovi.workers.dev"
) : RelayRepository {
    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingIntervalMillis = 20_000
        }
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null

    override fun observeRequests(pairId: String): Flow<PermissionRequest?> {
        startSyncJob(pairId)
        return requestDao.observePendingRequests().map { list ->
            list.firstOrNull()?.toDomain()
        }
    }

    private fun startSyncJob(pairId: String) {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            while (isActive) {
                try {
                    client.webSocket(method = HttpMethod.Get, host = relayHost, path = "/ws/mobile/$pairId") {
                        incoming.consumeEach { frame ->
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                try {
                                    val request = Json.decodeFromString(PermissionRequest.serializer(), text)
                                    requestDao.insert(request.toEntity())
                                } catch (e: Exception) {
                                    // Non-PermissionRequest frame (e.g. heartbeat)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    delay(3000) // Reconnect backoff
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
            client.webSocket(method = HttpMethod.Get, host = relayHost, path = "/ws/mobile/$pairId") {
                val json = Json.encodeToString(PermissionDecision.serializer(), decision)
                send(Frame.Text(json))
                close(CloseReason(CloseReason.Codes.NORMAL, "Decision sent"))
            }
            withContext(Dispatchers.IO) {
                requestDao.markAsResolved(decision.requestId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun abortSession(pairId: String): Result<Unit> {
        return try {
            client.webSocket(method = HttpMethod.Get, host = relayHost, path = "/ws/mobile/$pairId") {
                val abortJson = """{"type":"abort","action":"emergency_stop"}"""
                send(Frame.Text(abortJson))
                close(CloseReason(CloseReason.Codes.NORMAL, "Session aborted"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
