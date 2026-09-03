package com.ovi.handoff.adapter

import com.ovi.handoff.core.DesktopConfigManager
import com.ovi.handoff.core.PolicyEngine
import com.ovi.handoff.shared.model.AgentInfo
import com.ovi.handoff.shared.model.PermissionDecision
import com.ovi.handoff.shared.model.PermissionInfo
import com.ovi.handoff.shared.model.PermissionRequest
import com.ovi.handoff.shared.model.RiskInfo
import com.ovi.handoff.shared.model.SessionInfo
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.io.File
import java.time.Instant
import java.util.Scanner
import java.util.UUID

object McpServer {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingIntervalMillis = 20_000
        }
    }

    fun run() = runBlocking {
        val scanner = Scanner(System.`in`)
        val policyFile = File(System.getProperty("user.home"), ".handoff/policy.yml")
        val policyEngine = PolicyEngine(policyFile)

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
                                put("name", "handoff-approval")
                                put("version", "1.1.0")
                            }
                        })
                    }
                    "tools/list" -> {
                        sendResponse(id, buildJsonObject {
                            putJsonArray("tools") {
                                add(buildJsonObject {
                                    put("name", "handoff_approve")
                                    put("description", "Prompts the user on their mobile phone for permission to execute a shell command or file change.")
                                    putJsonObject("inputSchema") {
                                        put("type", "object")
                                        putJsonObject("properties") {
                                            putJsonObject("command") {
                                                put("type", "string")
                                                put("description", "The shell command to run")
                                            }
                                            putJsonObject("reason") {
                                                put("type", "string")
                                                put("description", "Why this action is necessary")
                                            }
                                        }
                                        putJsonArray("required") {
                                            add(JsonPrimitive("command"))
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
                            
                            // Evaluate local policy before querying remote mobile
                            val policyAction = policyEngine.evaluate(command, "shell")
                            if (policyAction == "allow") {
                                sendResponse(id, buildJsonObject {
                                    putJsonArray("content") {
                                        add(buildJsonObject {
                                            put("type", "text")
                                            put("text", "Permission auto-approved by local policy engine.")
                                        })
                                    }
                                    put("isError", false)
                                })
                                continue
                            } else if (policyAction == "deny") {
                                sendResponse(id, buildJsonObject {
                                    putJsonArray("content") {
                                        add(buildJsonObject {
                                            put("type", "text")
                                            put("text", "Permission blocked by local policy engine.")
                                        })
                                    }
                                    put("isError", true)
                                })
                                continue
                            }

                            val pairId = DesktopConfigManager.getPairId()
                            val relayHost = DesktopConfigManager.getRelayHost()

                            val request = PermissionRequest(
                                id = UUID.randomUUID().toString(),
                                protocolVersion = "1.0",
                                agent = AgentInfo(id = "mcp-client", name = "MCP Client", version = "1.1.0"),
                                session = SessionInfo(id = pairId, project = "HandOff", workspace = "handoff"),
                                permission = PermissionInfo(
                                    type = "shell",
                                    command = command,
                                    description = reason.ifBlank { "Tool invocation requiring approval" }
                                ),
                                risk = RiskInfo(level = "high", reasons = listOf("External command execution via MCP")),
                                options = listOf("approve", "deny"),
                                createdAt = Instant.now().toString(),
                                expiresAt = Instant.now().plusSeconds(300).toString()
                            )
                            
                            try {
                                client.webSocket(method = HttpMethod.Get, host = relayHost, path = "/ws/desktop/$pairId") {
                                    send(Frame.Text(json.encodeToString(PermissionRequest.serializer(), request)))
                                    
                                    for (frame in incoming) {
                                        if (frame is Frame.Text) {
                                            val text = frame.readText()
                                            val decision = json.decodeFromString(PermissionDecision.serializer(), text)
                                            
                                            val isApproved = decision.decision in listOf("approve", "approve_once", "proceed_plan", "answer_question")
                                            val feedbackSuffix = if (!decision.feedback.isNullOrBlank()) " | Feedback: ${decision.feedback}" else ""
                                            val selected = decision.selectedOptions
                                            val selectedSuffix = if (!selected.isNullOrEmpty()) " | Selected: ${selected.joinToString(", ")}" else ""

                                            sendResponse(id, buildJsonObject {
                                                putJsonArray("content") {
                                                    add(buildJsonObject {
                                                        put("type", "text")
                                                        put("text", "Permission decision: ${decision.decision}$feedbackSuffix$selectedSuffix")
                                                    })
                                                }
                                                put("isError", !isApproved)
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
                                            put("text", "Error connecting to relay ($relayHost) for pair $pairId: ${e.message}")
                                        })
                                    }
                                    put("isError", true)
                                })
                            }
                        } else {
                            sendError(id, -32601, "Tool not found: $toolName")
                        }
                    }
                    "ping" -> {
                        sendResponse(id, buildJsonObject {})
                    }
                }
            } catch (e: Exception) {
                // Ignore parsing errors or invalid frames
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
