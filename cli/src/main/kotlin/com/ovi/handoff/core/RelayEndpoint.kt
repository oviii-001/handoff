package com.ovi.handoff.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

/**
 * Where the relay is and what it says about this pair.
 *
 * The scheme rules used to be duplicated in three places that had already drifted apart: the
 * WebSocket URL builders on the desktop and the phone each carried their own `isLocalHost` list, and
 * `--status` used `startsWith("127.")`, so `http://127.0.0.1` was probed over plain HTTP while the
 * socket to the same host chose `wss`. One definition removes the class of bug where a local relay
 * is reachable by one code path and not the other.
 *
 * [pairStatus] exists because a failed WebSocket handshake tells the user almost nothing. Ktor
 * surfaces a rejected upgrade as an opaque exception, and the relay's reason — the room is
 * unclaimed, or the token does not match — is exactly what the user needs. Asking over plain HTTP
 * gets that reason in a form both the CLI and the phone can render.
 */
public object RelayEndpoint {

    private val json = Json { ignoreUnknownKeys = true }

    private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "[::1]", "0.0.0.0", "10.0.2.2")

    public fun isLocalHost(host: String): Boolean =
        host.substringBefore(':').lowercase() in LOCAL_HOSTS

    /** `http` for a local relay, `https` otherwise. The WebSocket scheme follows the same rule. */
    public fun httpScheme(host: String): String = if (isLocalHost(host)) "http" else "https"

    public fun baseUrl(host: String): String = "${httpScheme(host)}://$host"

    /** Result of `GET /health`. */
    public sealed interface Health {
        public data class Up(val protocol: String?) : Health
        public data class Unexpected(val statusCode: Int) : Health
        public data class Down(val reason: String) : Health
    }

    public fun health(host: String, timeoutMs: Int = DEFAULT_TIMEOUT_MS): Health =
        runCatching {
            val (code, body) = get("${baseUrl(host)}/health", token = null, timeoutMs = timeoutMs)
            if (code !in 200..299) return@runCatching Health.Unexpected(code)
            Health.Up(body?.let { field(it, "protocol") })
        }.getOrElse { Health.Down(it.message ?: it::class.simpleName ?: "unknown error") }

    /** What the relay knows about one pair room. */
    public sealed interface PairStatus {
        /**
         * The room exists and our token was accepted.
         *
         * [desktopOnline] counts sockets other than the caller's, so `--doctor` can distinguish
         * "your IDE has the daemon running" from "nothing is attached".
         */
        public data class Ok(
            val claimed: Boolean,
            val phoneOnline: Boolean,
            val desktopOnline: Boolean
        ) : PairStatus

        /** The room was never claimed, so a phone scanning the code would be refused. */
        public data object Unclaimed : PairStatus

        /** The room is claimed by a different secret than the one on this machine. */
        public data class TokenRejected(val message: String) : PairStatus

        /** An older relay with no status route. Not an error: nothing can be concluded. */
        public data object Unsupported : PairStatus

        public data class Unreachable(val reason: String) : PairStatus
    }

    public fun pairStatus(
        host: String,
        pairId: String,
        token: String,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS
    ): PairStatus = runCatching {
        val (code, body) = get("${baseUrl(host)}/pair/$pairId", token = token, timeoutMs = timeoutMs)

        if (code == 401) {
            return@runCatching PairStatus.TokenRejected(
                body?.let { field(it, "message") } ?: "The relay rejected this desktop's pairing token."
            )
        }
        if (code !in 200..299 || body == null) return@runCatching PairStatus.Unsupported

        // A relay without this route answers 200 with its plain banner text, so the presence of the
        // `claimed` field — not the status code — is what proves the route exists. Reading a missing
        // field as `true` would report every older relay as a claimed room with an offline phone.
        when (flag(body, "claimed")) {
            null -> PairStatus.Unsupported
            false -> PairStatus.Unclaimed
            true -> PairStatus.Ok(
                claimed = true,
                phoneOnline = flag(body, "phoneOnline") ?: false,
                desktopOnline = flag(body, "desktopOnline") ?: false
            )
        }
    }.getOrElse { PairStatus.Unreachable(it.message ?: it::class.simpleName ?: "unknown error") }

    // -------------------------------------------------------------------------------------

    private fun get(url: String, token: String?, timeoutMs: Int): Pair<Int, String?> {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            if (!token.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            val code = connection.responseCode
            // A 4xx puts the body on the error stream, and that is precisely where the relay's
            // explanation of the rejection lives, so both streams have to be read.
            val body = (if (code in 200..399) connection.inputStream else connection.errorStream)
                ?.let(::readAll)
            code to body
        } finally {
            connection.disconnect()
        }
    }

    public fun registerPin(host: String, pin: String, pairUrl: String, timeoutMs: Int = DEFAULT_TIMEOUT_MS): Boolean = runCatching {
        val url = "${baseUrl(host)}/pin/register"
        // Minimal json payload
        val escapedUrl = pairUrl.replace("\\", "\\\\").replace("\"", "\\\"")
        val jsonBody = """{"pin":"$pin","pairUrl":"$escapedUrl"}"""
        val (code, _) = post(url, jsonBody, timeoutMs)
        code in 200..299
    }.getOrDefault(false)

    private fun post(url: String, jsonBody: String, timeoutMs: Int): Pair<Int, String?> {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val body = (if (code in 200..399) connection.inputStream else connection.errorStream)
                ?.let(::readAll)
            code to body
        } finally {
            connection.disconnect()
        }
    }

    private fun readAll(stream: InputStream): String? =
        runCatching { stream.bufferedReader().use { it.readText() } }.getOrNull()

    private fun field(body: String, name: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject[name]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private fun flag(body: String, name: String): Boolean? = runCatching {
        json.parseToJsonElement(body).jsonObject[name]?.jsonPrimitive?.booleanOrNull
    }.getOrNull()

    private const val DEFAULT_TIMEOUT_MS = 5_000
}
