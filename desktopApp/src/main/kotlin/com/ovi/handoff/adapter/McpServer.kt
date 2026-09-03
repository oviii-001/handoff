package com.ovi.handoff.adapter

import com.ovi.handoff.shared.model.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.util.Scanner
import java.util.UUID
import java.time.Instant

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*

object McpServer {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingIntervalMillis = 20_000
        }
    }
    private val relayHost = "handoff.ovi.workers.dev" // Assuming default host for now

    fun run() = runBlocking {
        val scanner = Scanner(System.`in`)
        while (scanner.hasNextLine()) {
            val line = scanner.nextLine().trim()
            if (line.isEmpty()) continue
            
            try {
                val element = json.parseToJsonElement(line).jsonObject
                val id = element["id"]
                val method = element["method"]?.jsonPrimitive?.content ?: continue
                
                when (method) {
                    "initialize" -> {
                        sendResponse(id, buildJsonObject {
                            put("protocolVersion", "2024-11-05")
                            putJsonObject("capabilities") {
                                putJsonObject("tools") {}
                            }
                            putJsonObject("serverInfo") {
                                put("name", "handoff-server")
                                put("version", "1.0.0")
                            }
                        })
                    }
                    "notifications/initialized" -> {
                        // ignore
                    }
                    "tools/list" -> {
                        sendResponse(id, buildJsonObject {
                            putJsonArray("tools") {
                                add(buildJsonObject {
                                    put("name", "handoff_approve")
                                    put("description", "Request explicit user approval via mobile app for high-risk actions.")
                                    putJsonObject("inputSchema") {
                                        put("type", "object")
                                        putJsonObject("properties") {
                                            putJsonObject("command") {
                                                put("type", "string")
                                                put("description", "The high risk command or action being executed.")
                                            }
                                            putJsonObject("reason") {
                                                put("type", "string")
                                                put("description", "Why this action needs to be executed.")
                                            }
                                        }
                                        putJsonArray("required") {
                                            add("command")
                                            add("reason")
                                        }
                                    }
                                })
                            }
                        })
                    }
                    "tools/call" -> {
                        val params = element["params"]?.jsonObject
                        val toolName = params?.get("name")?.jsonPrimitive?.content
                        val args = params?.get("arguments")?.jsonObject
                        
                        if (toolName == "handoff_approve") {
                            val command = args?.get("command")?.jsonPrimitive?.content ?: ""
                            val reason = args?.get("reason")?.jsonPrimitive?.content ?: ""
                            
                            val request = PermissionRequest(
                                id = UUID.randomUUID().toString(),
                                protocolVersion = "v1",
                                agent = AgentInfo(id = "mcp-client", name = "MCP Client"),
                                session = SessionInfo(id = "session-1"),
                                permission = PermissionInfo(
                                    type = "shell",
                                    command = command,
                                    description = reason
                                ),
                                risk = RiskInfo(level = "high", reasons = listOf("Requested via handoff_approve")),
                                options = listOf("approve", "deny"),
                                createdAt = Instant.now().toString(),
                                expiresAt = Instant.now().plusSeconds(300).toString()
                            )
                            
                            // Send request to relay and wait for decision
                            val pairId = "test-pair" // TODO: Read from config/settings
                            try {
                                client.webSocket(method = HttpMethod.Get, host = relayHost, path = "/ws/desktop/$pairId") {
                                    // Send request
                                    send(Frame.Text(json.encodeToString(PermissionRequest.serializer(), request)))
                                    
                                    // Wait for decision
                                    for (frame in incoming) {
                                        if (frame is Frame.Text) {
                                            val text = frame.readText()
                                            val decision = json.decodeFromString(PermissionDecision.serializer(), text)
                                            
                                            sendResponse(id, buildJsonObject {
                                                putJsonArray("content") {
                                                    add(buildJsonObject {
                                                        put("type", "text")
                                                        put("text", "Permission decision: ${decision.decision}")
                                                    })
                                                }
                                                put("isError", decision.decision != "approve_once")
                                            })
                                            break
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                sendResponse(id, buildJsonObject {
                                    putJsonArray("content") {
                                        add(buildJsonObject {
                                            put("type", "text")
                                            put("text", "Error connecting to relay: ${e.message}")
                                        })
                                    }
                                    put("isError", true)
                                })
                            }
                        } else {
                            sendError(id, -32601, "Tool not found")
                        }
                    }
                    "ping" -> {
                        sendResponse(id, buildJsonObject {})
                    }
                }
            } catch (e: Exception) {
                // Ignore parsing errors, or send standard Parse Error
            }
        }
    }

    private fun sendResponse(id: JsonElement?, result: JsonObject) {
        val response = buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id)
            put("result", result)
        }
        println(response.toString())
    }

    private fun sendError(id: JsonElement?, code: Int, message: String) {
        val response = buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id)
            putJsonObject("error") {
                put("code", code)
                put("message", message)
            }
        }
        println(response.toString())
    }
}
