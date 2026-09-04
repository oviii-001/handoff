package com.ovi.handoff.adapter

import com.ovi.handoff.core.DesktopConfigManager
import com.ovi.handoff.core.KeyStoreManager
import com.ovi.handoff.core.PolicyEngine
import com.ovi.handoff.core.RelayClient
import com.ovi.handoff.shared.model.AgentInfo
import com.ovi.handoff.shared.model.PermissionDecision
import com.ovi.handoff.shared.model.PermissionInfo
import com.ovi.handoff.shared.model.PermissionRequest
import com.ovi.handoff.shared.model.PlanPayload
import com.ovi.handoff.shared.model.QuestionPayload
import com.ovi.handoff.shared.model.RiskInfo
import com.ovi.handoff.shared.model.SessionAnnouncement
import com.ovi.handoff.shared.model.SessionInfo
import com.ovi.handoff.shared.model.cleanName
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.io.File
import java.time.Instant
import java.util.Scanner
import java.util.UUID

object McpServer {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Session state detected from MCP client during initialize
    private var detectedAgent = AgentInfo(
        id = detectInitialAgentId(),
        name = detectInitialAgentName(),
        version = "1.2.0"
    )
    private var detectedProjectName = detectCurrentWorkspaceName()
    private var detectedWorkspacePath = File(".").canonicalPath

    private val keyStore by lazy {
        KeyStoreManager(File(System.getProperty("user.home"), ".handoff/keys"))
    }
    private val relayClient by lazy {
        val keyPair = keyStore.getOrGenerateKeyPair()
        RelayClient(
            relayHost = DesktopConfigManager.getRelayHost(),
            keyStoreManager = keyStore,
            privateKey = keyPair.private
        )
    }

    fun run() = runBlocking {
        val scanner = Scanner(System.`in`)
        val policyFile = File(System.getProperty("user.home"), ".handoff/policy.yml")
        val policyEngine = PolicyEngine(policyFile)

        System.err.println("[Handoff MCP] Stdio loop listening. Default IDE: ${detectedAgent.name}, Workspace: $detectedProjectName")

        while (scanner.hasNextLine()) {
            val line = scanner.nextLine().trim()
            if (line.isEmpty()) continue

            try {
                val element = json.parseToJsonElement(line).jsonObject
                val id = element["id"]
                val method = element["method"]?.jsonPrimitive?.contentOrNull

                if (method == null) {
                    val respId = id?.jsonPrimitive?.contentOrNull
                    if (respId == "get-roots") {
                        handleRootsResponse(element["result"]?.jsonObject)
                    }
                    continue
                }

                when (method) {
                    "initialize" -> {
                        val params = element["params"]?.jsonObject
                        handleInitialize(params)

                        sendResponse(id, buildJsonObject {
                            put("protocolVersion", "2024-11-05")
                            putJsonObject("capabilities") {
                                putJsonObject("tools") {}
                                putJsonObject("resources") {}
                                putJsonObject("prompts") {}
                            }
                            putJsonObject("serverInfo") {
                                put("name", "handoff")
                                put("version", "1.2.0")
                            }
                        })
                    }

                    "notifications/initialized", "initialized" -> {
                        // Request active workspace roots from IDE client dynamically
                        requestRootsFromClient()
                        broadcastSessionAnnouncement()
                    }

                    "notifications/roots/list_changed" -> {
                        // User opened or switched folders in IDE
                        requestRootsFromClient()
                    }

                    "tools/list" -> {
                        sendResponse(id, buildJsonObject {
                            putJsonArray("tools") {
                                add(buildToolApprove())
                                add(buildToolAskQuestion())
                                add(buildToolRequestPlan())
                                add(buildToolStatus())
                            }
                        })
                    }

                    "tools/call" -> {
                        val params = element["params"]?.jsonObject
                        val toolName = params?.get("name")?.jsonPrimitive?.contentOrNull
                        val args = params?.get("arguments")?.jsonObject ?: JsonObject(emptyMap())

                        when (toolName) {
                            "handoff_approve" -> handleToolApprove(id, args, policyEngine)
                            "handoff_ask_question" -> handleToolAskQuestion(id, args)
                            "handoff_request_plan_approval" -> handleToolRequestPlan(id, args)
                            "handoff_status" -> handleToolStatus(id, args)
                            else -> sendError(id, -32601, "Tool not found: $toolName")
                        }
                    }

                    "resources/list" -> {
                        sendResponse(id, buildJsonObject {
                            putJsonArray("resources") {}
                        })
                    }

                    "prompts/list" -> {
                        sendResponse(id, buildJsonObject {
                            putJsonArray("prompts") {}
                        })
                    }

                    "ping" -> {
                        sendResponse(id, buildJsonObject {})
                    }

                    else -> {
                        if (id != null) {
                            sendError(id, -32601, "Method not found: $method")
                        }
                    }
                }
            } catch (e: Exception) {
                System.err.println("[Handoff MCP] Error handling frame: ${e.message}")
            }
        }
    }

    private fun handleInitialize(params: JsonObject?) {
        if (params == null) return

        // 1. Extract Client Info (IDE name & version)
        val clientInfo = params["clientInfo"]?.jsonObject
        val clientName = clientInfo?.get("name")?.jsonPrimitive?.contentOrNull
        val clientVersion = clientInfo?.get("version")?.jsonPrimitive?.contentOrNull

        if (!clientName.isNullOrBlank()) {
            val normalizedId = clientName.lowercase().replace(" ", "-")
            detectedAgent = AgentInfo(
                id = normalizedId,
                name = clientName,
                version = clientVersion ?: "1.0.0"
            )
            System.err.println("[Handoff MCP] Client identified: ${detectedAgent.cleanName()} v${detectedAgent.version}")
        }

        // 2. Extract Workspace / Project
        val workspaceFolders = params["workspaceFolders"]?.jsonArray
        val firstFolder = workspaceFolders?.firstOrNull()?.jsonObject
        val wsUri = firstFolder?.get("uri")?.jsonPrimitive?.contentOrNull
            ?: params["rootUri"]?.jsonPrimitive?.contentOrNull
            ?: params["rootPath"]?.jsonPrimitive?.contentOrNull

        val envWorkspace = System.getenv("HANDOFF_WORKSPACE")
        val resolvedPath = when {
            !wsUri.isNullOrBlank() -> {
                val clean = wsUri.removePrefix("file:///").removePrefix("file://")
                File(clean).canonicalFile.absolutePath
            }
            !envWorkspace.isNullOrBlank() -> File(envWorkspace).canonicalFile.absolutePath
            else -> File(".").canonicalFile.absolutePath
        }

        detectedWorkspacePath = resolvedPath
        detectedProjectName = resolvedPath

        System.err.println("[Handoff MCP] Dynamic workspace identified: $detectedProjectName ($detectedWorkspacePath)")
        broadcastSessionAnnouncement()
    }

    private fun broadcastSessionAnnouncement() {
        val pairId = DesktopConfigManager.getPairId()
        val relayHost = DesktopConfigManager.getRelayHost()

        serverScope.launch {
            try {
                val announcement = SessionAnnouncement(
                    type = "session_info",
                    pairId = pairId,
                    agent = detectedAgent,
                    session = SessionInfo(
                        id = pairId,
                        project = detectedProjectName,
                        workspace = detectedWorkspacePath
                    ),
                    timestamp = Instant.now().toString()
                )
                val payload = json.encodeToString(SessionAnnouncement.serializer(), announcement)

                val client = HttpClient(CIO) { install(WebSockets) }
                client.webSocket(method = HttpMethod.Get, host = relayHost, path = "/ws/desktop/$pairId") {
                    send(Frame.Text(payload))
                    close(CloseReason(CloseReason.Codes.NORMAL, "Session info broadcast"))
                }
                client.close()
                System.err.println("[Handoff MCP] Broadcasted session announcement to phone: ${detectedAgent.cleanName()} • $detectedProjectName")
            } catch (e: Exception) {
                System.err.println("[Handoff MCP] Could not send session announcement: ${e.message}")
            }
        }
    }

    private fun requestRootsFromClient() {
        try {
            val req = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", "get-roots")
                put("method", "roots/list")
                putJsonObject("params") {}
            }
            System.out.print(req.toString() + "\n")
            System.out.flush()
        } catch (e: Exception) {
            System.err.println("[Handoff MCP] Could not request roots from client: ${e.message}")
        }
    }

    private fun handleRootsResponse(result: JsonObject?) {
        try {
            val roots = result?.get("roots")?.jsonArray
            val firstUri = roots?.firstOrNull()?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull
            if (!firstUri.isNullOrBlank()) {
                val clean = firstUri.removePrefix("file:///").removePrefix("file://")
                val resolved = File(clean).canonicalFile.absolutePath
                detectedWorkspacePath = resolved
                detectedProjectName = resolved
                System.err.println("[Handoff MCP] Dynamic workspace updated from client roots/list: $detectedProjectName")
                broadcastSessionAnnouncement()
            }
        } catch (e: Exception) {
            System.err.println("[Handoff MCP] Error parsing roots response: ${e.message}")
        }
    }

    private suspend fun handleToolApprove(id: JsonElement?, args: JsonObject, policyEngine: PolicyEngine) {
        val cwdArg = args["cwd"]?.jsonPrimitive?.contentOrNull
        if (!cwdArg.isNullOrBlank()) {
            val resolvedCwd = File(cwdArg).canonicalFile.absolutePath
            detectedWorkspacePath = resolvedCwd
            detectedProjectName = resolvedCwd
        }

        val command = args["command"]?.jsonPrimitive?.contentOrNull ?: ""
        val reason = args["reason"]?.jsonPrimitive?.contentOrNull ?: "Execution requires authorization"
        val actionType = args["action_type"]?.jsonPrimitive?.contentOrNull ?: "shell"
        val riskLevel = args["risk_level"]?.jsonPrimitive?.contentOrNull ?: "high"

        val policyAction = policyEngine.evaluate(command, actionType)
        if (policyAction == "allow") {
            sendResponse(id, buildTextContent("Permission auto-approved by local policy engine."))
            return
        } else if (policyAction == "deny") {
            sendResponse(id, buildTextContent("Permission blocked by local policy engine.", isError = true))
            return
        }

        val pairId = DesktopConfigManager.getPairId()
        val request = PermissionRequest(
            id = UUID.randomUUID().toString(),
            protocolVersion = "1.0",
            agent = detectedAgent,
            session = SessionInfo(
                id = pairId,
                project = detectedProjectName,
                workspace = detectedWorkspacePath
            ),
            permission = PermissionInfo(
                type = actionType,
                command = command,
                description = reason,
                cwd = detectedWorkspacePath
            ),
            risk = RiskInfo(
                level = riskLevel,
                reasons = listOf("Triggered via MCP by ${detectedAgent.cleanName()}")
            ),
            options = listOf("approve", "deny"),
            createdAt = Instant.now().toString(),
            expiresAt = Instant.now().plusSeconds(300).toString()
        )

        val decision = relayClient.sendRequestAndWaitForDecision(pairId, request)
        deliverDecisionResponse(id, decision)
    }

    private suspend fun handleToolAskQuestion(id: JsonElement?, args: JsonObject) {
        val cwdArg = args["cwd"]?.jsonPrimitive?.contentOrNull
        if (!cwdArg.isNullOrBlank()) {
            val resolvedCwd = File(cwdArg).canonicalFile.absolutePath
            detectedWorkspacePath = resolvedCwd
            detectedProjectName = resolvedCwd
        }

        val questionText = args["question"]?.jsonPrimitive?.contentOrNull ?: "Clarification needed"
        val optionsList = args["options"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: listOf("Yes", "No")
        val isMultiSelect = args["is_multi_select"]?.jsonPrimitive?.booleanOrNull ?: false

        val pairId = DesktopConfigManager.getPairId()
        val request = PermissionRequest(
            id = UUID.randomUUID().toString(),
            protocolVersion = "1.0",
            agent = detectedAgent,
            session = SessionInfo(
                id = pairId,
                project = detectedProjectName,
                workspace = detectedWorkspacePath
            ),
            permission = PermissionInfo(
                type = "question",
                description = questionText,
                cwd = detectedWorkspacePath
            ),
            risk = RiskInfo(level = "medium", reasons = listOf("Interactive agent question")),
            options = listOf("answer_question", "cancel"),
            createdAt = Instant.now().toString(),
            expiresAt = Instant.now().plusSeconds(300).toString(),
            question = QuestionPayload(
                question = questionText,
                options = optionsList,
                isMultiSelect = isMultiSelect
            )
        )

        val decision = relayClient.sendRequestAndWaitForDecision(pairId, request)
        if (decision != null) {
            val selected = decision.selectedOptions
            val feedback = decision.feedback
            val resultText = buildString {
                append("User answered question.")
                if (!selected.isNullOrEmpty()) {
                    append(" Selected: ${selected.joinToString(", ")}")
                }
                if (!feedback.isNullOrBlank()) {
                    append(" Comments: $feedback")
                }
            }
            sendResponse(id, buildTextContent(resultText))
        } else {
            sendResponse(id, buildTextContent("Question timed out waiting for user response on mobile device.", isError = true))
        }
    }

    private suspend fun handleToolRequestPlan(id: JsonElement?, args: JsonObject) {
        val cwdArg = args["cwd"]?.jsonPrimitive?.contentOrNull
        if (!cwdArg.isNullOrBlank()) {
            val resolvedCwd = File(cwdArg).canonicalFile.absolutePath
            detectedWorkspacePath = resolvedCwd
            detectedProjectName = resolvedCwd
        }

        val title = args["title"]?.jsonPrimitive?.contentOrNull ?: "Implementation Plan"
        val summary = args["summary"]?.jsonPrimitive?.contentOrNull ?: ""
        val reviewItems = args["user_review_required"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()

        val pairId = DesktopConfigManager.getPairId()
        val request = PermissionRequest(
            id = UUID.randomUUID().toString(),
            protocolVersion = "1.0",
            agent = detectedAgent,
            session = SessionInfo(
                id = pairId,
                project = detectedProjectName,
                workspace = detectedWorkspacePath
            ),
            permission = PermissionInfo(
                type = "plan",
                description = "Plan review: $title",
                cwd = detectedWorkspacePath
            ),
            risk = RiskInfo(level = "high", reasons = listOf("Architectural plan approval")),
            options = listOf("proceed_plan", "deny"),
            createdAt = Instant.now().toString(),
            expiresAt = Instant.now().plusSeconds(300).toString(),
            plan = PlanPayload(
                title = title,
                summary = summary,
                userReviewRequired = reviewItems
            )
        )

        val decision = relayClient.sendRequestAndWaitForDecision(pairId, request)
        deliverDecisionResponse(id, decision)
    }

    private fun handleToolStatus(id: JsonElement?, args: JsonObject = JsonObject(emptyMap())) {
        val cwdArg = args["cwd"]?.jsonPrimitive?.contentOrNull
        if (!cwdArg.isNullOrBlank()) {
            val resolvedCwd = File(cwdArg).canonicalFile.absolutePath
            detectedWorkspacePath = resolvedCwd
            detectedProjectName = resolvedCwd
        }

        val pairId = DesktopConfigManager.getPairId()
        val relayHost = DesktopConfigManager.getRelayHost()

        val status = buildJsonObject {
            put("pairId", pairId)
            put("relayHost", relayHost)
            put("connectedIde", detectedAgent.cleanName())
            put("ideVersion", detectedAgent.version ?: "unknown")
            put("workspace", detectedProjectName)
            put("workspacePath", detectedWorkspacePath)
            put("status", "ready")
        }
        sendResponse(id, buildJsonObject {
            putJsonArray("content") {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", status.toString())
                })
            }
        })
    }

    private fun deliverDecisionResponse(id: JsonElement?, decision: PermissionDecision?) {
        if (decision == null) {
            sendResponse(id, buildTextContent("Request timed out after 5 minutes waiting for approval on mobile device.", isError = true))
            return
        }

        val isApproved = decision.decision in listOf("approve", "approve_once", "proceed_plan", "answer_question")
        val feedbackSuffix = if (!decision.feedback.isNullOrBlank()) " | Feedback: ${decision.feedback}" else ""
        val options = decision.selectedOptions
        val selectedSuffix = if (!options.isNullOrEmpty()) " | Selected: ${options.joinToString(", ")}" else ""

        val responseText = "Permission decision: ${decision.decision}$feedbackSuffix$selectedSuffix"
        sendResponse(id, buildTextContent(responseText, isError = !isApproved))
    }

    private fun buildTextContent(text: String, isError: Boolean = false): JsonObject {
        return buildJsonObject {
            putJsonArray("content") {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", text)
                })
            }
            put("isError", isError)
        }
    }

    private fun buildToolApprove(): JsonObject = buildJsonObject {
        put("name", "handoff_approve")
        put("description", "Prompts the user on their mobile phone for zero-trust authorization to execute a shell command or file change.")
        putJsonObject("inputSchema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("command") {
                    put("type", "string")
                    put("description", "The shell command or operation to execute")
                }
                putJsonObject("reason") {
                    put("type", "string")
                    put("description", "Why this action is necessary")
                }
                putJsonObject("action_type") {
                    put("type", "string")
                    put("description", "Optional action category: 'shell', 'file_write', 'file_read', 'network', 'deploy'")
                }
                putJsonObject("risk_level") {
                    put("type", "string")
                    put("description", "Optional risk level: 'low', 'medium', 'high', 'critical'")
                }
                putJsonObject("cwd") {
                    put("type", "string")
                    put("description", "Optional working directory where the action is being performed")
                }
            }
            putJsonArray("required") {
                add(JsonPrimitive("command"))
            }
        }
    }

    private fun buildToolAskQuestion(): JsonObject = buildJsonObject {
        put("name", "handoff_ask_question")
        put("description", "Prompts the user on their mobile phone with an interactive multiple-choice question to clarify requirements.")
        putJsonObject("inputSchema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("question") {
                    put("type", "string")
                    put("description", "The question to ask the user")
                }
                putJsonObject("options") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                    put("description", "List of selectable answer options")
                }
                putJsonObject("is_multi_select") {
                    put("type", "boolean")
                    put("description", "True if multiple options can be chosen")
                }
                putJsonObject("cwd") {
                    put("type", "string")
                    put("description", "Optional working directory where the action is being performed")
                }
            }
            putJsonArray("required") {
                add(JsonPrimitive("question"))
                add(JsonPrimitive("options"))
            }
        }
    }

    private fun buildToolRequestPlan(): JsonObject = buildJsonObject {
        put("name", "handoff_request_plan_approval")
        put("description", "Submits an architectural implementation plan to the mobile phone for review before executing changes.")
        putJsonObject("inputSchema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("title") {
                    put("type", "string")
                    put("description", "Title of the implementation plan")
                }
                putJsonObject("summary") {
                    put("type", "string")
                    put("description", "Summary of the proposed architectural changes")
                }
                putJsonObject("user_review_required") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                    put("description", "List of critical decisions requiring explicit user consent")
                }
                putJsonObject("cwd") {
                    put("type", "string")
                    put("description", "Optional working directory where the action is being performed")
                }
            }
            putJsonArray("required") {
                add(JsonPrimitive("title"))
                add(JsonPrimitive("summary"))
            }
        }
    }

    private fun buildToolStatus(): JsonObject = buildJsonObject {
        put("name", "handoff_status")
        put("description", "Checks current HandOff pairing ID, connected IDE, and relay connection status.")
        putJsonObject("inputSchema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("cwd") {
                    put("type", "string")
                    put("description", "Optional working directory where the action is being performed")
                }
            }
        }
    }

    private fun sendResponse(id: JsonElement?, result: JsonObject) {
        val response = buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id)
            put("result", result)
        }
        val output = response.toString()
        System.out.print(output + "\n")
        System.out.flush()
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
        val output = response.toString()
        System.out.print(output + "\n")
        System.out.flush()
    }

    private fun detectInitialAgentId(): String {
        val env = System.getenv()
        return when {
            env.containsKey("ANTIGRAVITY_IDE") || env.containsKey("ANTIGRAVITY_AGENT") || env.containsKey("GEMINI_CLI") -> "antigravity"
            env.containsKey("CURSOR_VERSION") || env.containsKey("CURSOR_PID") -> "cursor"
            env.containsKey("CLAUDE_CODE") -> "claude"
            env.containsKey("VSCODE_PID") -> "vscode"
            else -> "antigravity"
        }
    }

    private fun detectInitialAgentName(): String {
        return when (detectInitialAgentId()) {
            "antigravity" -> "Antigravity"
            "cursor" -> "Cursor"
            "claude" -> "Claude Code"
            "vscode" -> "VSCode"
            else -> "Antigravity"
        }
    }

    private fun detectCurrentWorkspaceName(): String {
        val envWorkspace = System.getenv("HANDOFF_WORKSPACE")
        if (!envWorkspace.isNullOrBlank()) {
            return File(envWorkspace).absolutePath
        }
        val currentDir = File(".").canonicalFile
        return currentDir.absolutePath.ifBlank { "Workspace" }
    }

    private fun extractFolderName(pathOrUri: String): String {
        return try {
            val cleanPath = pathOrUri.removePrefix("file:///").removePrefix("file://")
            val file = File(cleanPath)
            file.absolutePath.ifBlank { pathOrUri }
        } catch (e: Exception) {
            pathOrUri
        }
    }
}
