package com.ovi.handoff.core

import com.ovi.handoff.shared.model.PermissionDecision
import com.ovi.handoff.shared.model.PermissionRequest
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.json.Json

class RelayClient(
    private val relayHost: String = "agentapprove-relay.ismamhasanovi.workers.dev"
) {
    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingIntervalMillis = 20_000
        }
    }

    suspend fun sendRequestAndWaitForDecision(
        pairId: String, 
        request: PermissionRequest
    ): PermissionDecision? {
        var decision: PermissionDecision? = null
        
        try {
            client.webSocket(method = HttpMethod.Get, host = relayHost, path = "/ws/desktop/$pairId") {
                val requestJson = Json.encodeToString(PermissionRequest.serializer(), request)
                send(Frame.Text(requestJson))

                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        // Assume first JSON response is the decision
                        decision = Json.decodeFromString(PermissionDecision.serializer(), text)
                        close(CloseReason(CloseReason.Codes.NORMAL, "Decision received"))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // In a real implementation, we would implement backoff and retry logic here
        }
        
        return decision
    }
}
