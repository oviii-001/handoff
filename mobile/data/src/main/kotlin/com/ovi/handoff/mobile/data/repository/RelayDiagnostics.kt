package com.ovi.handoff.mobile.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Relay URL rules, shared by the socket and the diagnostic probe so the two cannot disagree. */
internal object RelayUrls {

    /**
     * `10.0.2.2` is the host machine as seen from the Android emulator, so it belongs with the
     * loopback names: a relay running on the developer's laptop is plain HTTP, and choosing `wss`
     * for it silently fails against a local `wrangler dev`.
     */
    private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2", "[::1]", "0.0.0.0")

    fun isLocalHost(host: String): Boolean = host.substringBefore(':').lowercase() in LOCAL_HOSTS

    fun wsScheme(host: String): String = if (isLocalHost(host)) "ws" else "wss"

    fun httpScheme(host: String): String = if (isLocalHost(host)) "http" else "https"
}

/**
 * Turns "the socket did not come up" into something the user can act on.
 *
 * A rejected WebSocket upgrade reaches the app as an opaque client exception, so the reason lives
 * only on the relay. The relay exposes it over plain HTTP for exactly this: the difference between
 * "no desktop has claimed this pair" and "you have no network" is the difference between a
 * ten-second fix and a support thread.
 */
internal object RelayDiagnostics {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun describe(
        client: HttpClient,
        host: String,
        pairId: String,
        token: String?,
        socketError: String? = null
    ): String {
        if (token.isNullOrBlank()) {
            return "This pairing has no relay token. On your computer run `handoff --pair` and scan the code again."
        }

        val probe = runCatching {
            val response = client.get("${RelayUrls.httpScheme(host)}://$host/pair/$pairId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            response.status.value to response.bodyAsText()
        }.getOrElse {
            return "Could not reach the HandOff relay at $host. Check this phone's internet connection."
        }

        val (status, body) = probe
        val parsed = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()

        if (status == 401) {
            return parsed?.get("message")?.jsonPrimitive?.contentOrNull
                ?: "The relay rejected this pairing code. Ask for a fresh code with `handoff --pair`."
        }

        // A relay without this route answers 200 with its plain banner text, so the `claimed` field
        // is what proves the route exists. Absent means "cannot tell", not "claimed".
        if (parsed?.get("claimed")?.jsonPrimitive?.booleanOrNull == false) {
            return "No computer has claimed this pairing code yet. On your computer run `handoff --pair` and " +
                "leave it running, then scan the code it shows."
        }

        // Either the relay accepted the token, or it is too old to say. Both leave the block between
        // this phone and the relay rather than in the pairing itself.
        val detail = socketError?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        return "Could not open the live channel to your computer$detail. This is usually a network that " +
            "blocks WebSockets, such as some public or corporate Wi-Fi. Try mobile data."
    }
}
