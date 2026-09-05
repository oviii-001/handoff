package com.ovi.handoff.adapter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JsonRpcTransportTest {

    private fun lines(sink: ByteArrayOutputStream): List<String> =
        sink.toString(Charsets.UTF_8).lines().filter { it.isNotBlank() }

    @Test
    fun writesOneCompleteJsonDocumentPerLine() = runTest {
        val sink = ByteArrayOutputStream()
        val transport = JsonRpcTransport(sink)

        transport.respond(JsonPrimitive(1), buildJsonObject { put("ok", true) })
        transport.error(JsonPrimitive(2), JsonRpcError.INVALID_PARAMS, "bad")
        transport.flush()

        val frames = lines(sink)
        assertEquals(2, frames.size)
        frames.forEach { assertNotNull(Json.parseToJsonElement(it).jsonObject) }
    }

    @Test
    fun preservesTheRequestIdAndItsType() = runTest {
        val sink = ByteArrayOutputStream()
        val transport = JsonRpcTransport(sink)

        transport.respond(JsonPrimitive(7), buildJsonObject {})
        transport.respond(JsonPrimitive("call-a"), buildJsonObject {})
        transport.flush()

        val frames = lines(sink).map { Json.parseToJsonElement(it).jsonObject }
        // A numeric id must come back numeric: quoting it makes the client fail to match its call.
        assertEquals(7, frames[0]["id"]!!.jsonPrimitive.int)
        assertEquals("call-a", frames[1]["id"]!!.jsonPrimitive.contentOrNull)
    }

    @Test
    fun notificationsCarryNoId() = runTest {
        val sink = ByteArrayOutputStream()
        val transport = JsonRpcTransport(sink)

        transport.notify("notifications/message")
        transport.flush()

        val frame = Json.parseToJsonElement(lines(sink).single()).jsonObject
        assertFalse(frame.containsKey("id"), "a notification with an id would demand a reply")
        assertEquals("notifications/message", frame["method"]?.jsonPrimitive?.contentOrNull)
    }

    /**
     * The reason writes are serialized at all.
     *
     * Approvals are handled concurrently, so several coroutines write responses. Two documents
     * interleaved on one line corrupt the stream irrecoverably: the client cannot resynchronise, and
     * the server appears to hang rather than to fail.
     */
    @Test
    fun concurrentWritesNeverInterleave() = runTest {
        val sink = ByteArrayOutputStream()
        val transport = JsonRpcTransport(sink)
        val payload = "x".repeat(4_000)

        withContext(Dispatchers.Default) {
            (1..64).map { id ->
                async {
                    transport.respond(JsonPrimitive(id), buildJsonObject { put("blob", payload) })
                }
            }.awaitAll()
        }
        transport.flush()

        val frames = lines(sink)
        assertEquals(64, frames.size)

        val ids = frames.map { line ->
            val frame = Json.parseToJsonElement(line).jsonObject
            assertEquals(payload, frame["result"]!!.jsonObject["blob"]!!.jsonPrimitive.content)
            frame["id"]!!.jsonPrimitive.int
        }
        assertEquals((1..64).toSet(), ids.toSet())
    }

    @Test
    fun errorsCarryTheirCodeAndMessage() = runTest {
        val sink = ByteArrayOutputStream()
        val transport = JsonRpcTransport(sink)

        transport.error(JsonPrimitive(3), JsonRpcError.METHOD_NOT_FOUND, "Method not found: nope")
        transport.flush()

        val error = Json.parseToJsonElement(lines(sink).single()).jsonObject["error"]!!.jsonObject
        assertEquals(JsonRpcError.METHOD_NOT_FOUND, error["code"]!!.jsonPrimitive.int)
        assertTrue(error["message"]!!.jsonPrimitive.content.contains("nope"))
    }
}
