package com.ovi.handoff.adapter

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import kotlin.text.Charsets

/**
 * Newline-delimited JSON-RPC writer for the MCP stdio transport.
 *
 * Serialized behind a mutex because tool calls are now handled concurrently. Writing responses from
 * several coroutines straight to `System.out` would let two JSON documents interleave on one line
 * and corrupt the stream for the IDE.
 */
internal class JsonRpcTransport(stream: OutputStream) {

    private val writer: BufferedWriter = BufferedWriter(OutputStreamWriter(stream, Charsets.UTF_8))
    private val mutex = Mutex()

    suspend fun respond(id: JsonElement?, result: JsonObject) {
        write(
            buildJsonObject {
                put("jsonrpc", "2.0")
                if (id != null) put("id", id)
                put("result", result)
            }
        )
    }

    suspend fun error(id: JsonElement?, code: Int, message: String) {
        write(
            buildJsonObject {
                put("jsonrpc", "2.0")
                if (id != null) put("id", id)
                put(
                    "error",
                    buildJsonObject {
                        put("code", code)
                        put("message", message)
                    }
                )
            }
        )
    }

    /** Server-initiated request, used to ask the client for its workspace roots. */
    suspend fun request(id: String, method: String, params: JsonObject = JsonObject(emptyMap())) {
        write(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("method", method)
                put("params", params)
            }
        )
    }

    private suspend fun write(frame: JsonObject) {
        val line = frame.toString()
        mutex.withLock {
            writer.write(line)
            writer.write("\n")
            writer.flush()
        }
    }
}

/** JSON-RPC error codes used by the MCP adapter. */
internal object JsonRpcError {
    const val INVALID_REQUEST: Int = -32600
    const val METHOD_NOT_FOUND: Int = -32601
    const val INVALID_PARAMS: Int = -32602
    const val INTERNAL_ERROR: Int = -32603
}
