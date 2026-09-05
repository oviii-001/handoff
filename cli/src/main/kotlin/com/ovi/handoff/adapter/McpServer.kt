package com.ovi.handoff.adapter

import com.ovi.handoff.core.DesktopConfigManager
import com.ovi.handoff.core.KeyStoreManager
import com.ovi.handoff.core.PolicyAction
import com.ovi.handoff.core.PolicyEngine
import com.ovi.handoff.core.RelayClient
import com.ovi.handoff.core.RequestFactory
import com.ovi.handoff.shared.model.AgentInfo
import com.ovi.handoff.shared.model.PermissionDecision
import com.ovi.handoff.shared.model.PermissionInfo
import com.ovi.handoff.shared.model.PermissionType
import com.ovi.handoff.shared.model.PlanPayload
import com.ovi.handoff.shared.model.QuestionPayload
import com.ovi.handoff.shared.model.RiskInfo
import com.ovi.handoff.shared.model.RiskLevel
import com.ovi.handoff.shared.model.SessionAnnouncement
import com.ovi.handoff.shared.model.SessionInfo
import com.ovi.handoff.shared.model.cleanName
import com.ovi.handoff.shared.model.isApproval
import com.ovi.handoff.shared.model.shortWorkspaceName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.text.Charsets

/**
 * MCP server over stdio.
 *
 * The important change from the original is concurrency. `run()` used to await the phone's answer
 * inline inside its single read loop, so for up to five minutes the IDE could not ping, could not
 * cancel, and could not issue a second tool call: the server simply looked hung. Each approval now
 * runs on [scope] while the loop keeps reading, and every reply is written through a mutex-guarded
 * transport so concurrent responses cannot interleave on the wire.
 *
 * Two smaller but user-visible fixes: an exception while handling a call now produces a JSON-RPC
 * error instead of silently leaving the call unanswered forever, and `session.project` is the folder
 * name rather than the same absolute path as `session.workspace`.
 */
public object McpServer {

    private const val SERVER_VERSION = "2.0.0"
    private const val MCP_PROTOCOL_VERSION = "2024-11-05"
    private const val ROOTS_REQUEST_ID = "handoff-get-roots"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val transport = JsonRpcTransport(System.out)

    /** In-flight tool calls, so `notifications/cancelled` can actually cancel one. */
    private val activeCalls = ConcurrentHashMap<String, Job>()

    @Volatile
    private var detectedAgent: AgentInfo = AgentInfo(
        id = detectInitialAgentId(),
        name = detectInitialAgentName(),
        version = SERVER_VERSION
    )

    @Volatile
    private var workspacePath: String = RequestFactory.resolveWorkspacePath(System.getenv("HANDOFF_WORKSPACE"))
        ?: File(".").canonicalFile.absolutePath

    private val keyStore by lazy {
        KeyStoreManager(File(System.getProperty("user.home"), ".handoff/keys"))
    }

    private val relayClient by lazy {
        val keyPair = keyStore.getOrGenerateKeyPair()
        RelayClient(
            relayHost = DesktopConfigManager.getRelayHost(),
            pairId = DesktopConfigManager.getPairId(),
            pairSecret = DesktopConfigManager.getPairSecret(),
            keyStoreManager = keyStore,
            privateKey = keyPair.private
        )
    }

    private val policyEngine by lazy {
        PolicyEngine(File(System.getProperty("user.home"), ".handoff/policy.yml"))
    }

    public fun run(): Unit = runBlocking {
        val reader = BufferedReader(InputStreamReader(System.`in`, Charsets.UTF_8))

        System.err.println(
            "[Handoff MCP] Listening on stdio. IDE: ${detectedAgent.cleanName()}, workspace: ${shortLabel()}"
        )

        while (true) {
            val line = reader.readLine() ?: break
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val message = runCatching { json.parseToJsonElement(trimmed).jsonObject }.getOrNull()
            if (message == null) {
                System.err.println("[Handoff MCP] Discarded a non-JSON line from the client.")
                continue
            }

            val id = message["id"]
            val method = message["method"]?.jsonPrimitive?.contentOrNull

            if (method == null) {
                handleClientResponse(message)
                continue
            }

            // Anything that needs the phone is dispatched; everything else answers immediately so
            // the client's handshake and capability probes are never behind an approval.
            try {
                dispatch(id, method, message)
            } catch (cause: Exception) {
                System.err.println("[Handoff MCP] ${method} failed: ${cause.message}")
                if (id != null) {
                    transport.error(id, JsonRpcError.INTERNAL_ERROR, "handoff: ${cause.message}")
                }
            }
        }

        relayClient.close()
    }

    private suspend fun dispatch(id: JsonElement?, method: String, message: JsonObject) {
        when (method) {
            "initialize" -> {
                handleInitialize(message["params"]?.jsonObject)
                transport.respond(
                    id,
                    buildJsonObject {
                        put("protocolVersion", MCP_PROTOCOL_VERSION)
                        putJsonObject("capabilities") {
                            putJsonObject("tools") {}
                            putJsonObject("resources") {}
                            putJsonObject("prompts") {}
                        }
                        putJsonObject("serverInfo") {
                            put("name", "handoff")
                            put("version", SERVER_VERSION)
                        }
                    }
                )
            }

            "notifications/initialized", "initialized" -> {
                requestRootsFromClient()
                announceSession()
            }

            "notifications/roots/list_changed" -> requestRootsFromClient()

            "notifications/cancelled" -> handleCancelled(message["params"]?.jsonObject)

            "tools/list" -> transport.respond(
                id,
                buildJsonObject {
                    putJsonArray("tools") {
                        add(toolApprove())
                        add(toolAskQuestion())
                        add(toolRequestPlan())
                        add(toolStatus())
                    }
                }
            )

            "tools/call" -> handleToolCall(id, message["params"]?.jsonObject)

            "resources/list" -> transport.respond(id, buildJsonObject { putJsonArray("resources") {} })

            "prompts/list" -> transport.respond(id, buildJsonObject { putJsonArray("prompts") {} })

            "ping" -> transport.respond(id, buildJsonObject {})

            else -> if (id != null) {
                transport.error(id, JsonRpcError.METHOD_NOT_FOUND, "Method not found: $method")
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // Tool dispatch
    // -------------------------------------------------------------------------------------

    private suspend fun handleToolCall(id: JsonElement?, params: JsonObject?) {
        val toolName = params?.get("name")?.jsonPrimitive?.contentOrNull
        val args = params?.get("arguments")?.jsonObject ?: JsonObject(emptyMap())

        adoptWorkspaceFromArgs(args)

        when (toolName) {
            "handoff_status" -> transport.respond(id, statusContent())

            "handoff_approve" -> launchApproval(id) { approve(args) }
            "handoff_ask_question" -> launchApproval(id) { askQuestion(args) }
            "handoff_request_plan_approval" -> launchApproval(id) { requestPlan(args) }

            null -> transport.error(id, JsonRpcError.INVALID_PARAMS, "tools/call is missing a tool name")
            else -> transport.error(id, JsonRpcError.METHOD_NOT_FOUND, "Tool not found: $toolName")
        }
    }

    /**
     * Runs an approval off the read loop and guarantees exactly one reply for the call.
     *
     * The guarantee matters: the previous loop caught exceptions and only logged them, so a failure
     * anywhere in the approval path left the IDE waiting on a tool result that would never come.
     */
    private fun launchApproval(id: JsonElement?, block: suspend () -> JsonObject) {
        val callKey = id?.toString()

        val job = scope.launch {
            val result = try {
                block()
            } catch (cause: Exception) {
                textContent("HandOff could not complete the request: ${cause.message}", isError = true)
            }
            // A call with no id is a notification: run it, but there is nobody to answer.
            if (id != null) {
                transport.respond(id, result)
            }
            if (callKey != null) {
                activeCalls.remove(callKey)
            }
        }
        if (callKey != null) {
            activeCalls[callKey] = job
        }
    }

    private fun handleCancelled(params: JsonObject?) {
        val requestId = params?.get("requestId")?.toString() ?: return
        val job = activeCalls.remove(requestId) ?: return
        job.cancel()
        System.err.println("[Handoff MCP] Client cancelled call $requestId.")
    }

    private suspend fun approve(args: JsonObject): JsonObject {
        val command = args["command"]?.jsonPrimitive?.contentOrNull ?: ""
        val reason = args["reason"]?.jsonPrimitive?.contentOrNull ?: "Execution requires authorization"
        val actionType = args["action_type"]?.jsonPrimitive?.contentOrNull ?: PermissionType.SHELL
        val riskLevel = args["risk_level"]?.jsonPrimitive?.contentOrNull ?: RiskLevel.HIGH

        when (policyEngine.evaluate(command, actionType)) {
            PolicyAction.ALLOW -> return textContent("Permission auto-approved by local policy.")
            PolicyAction.DENY -> return textContent("Permission blocked by local policy.", isError = true)
        }

        val request = RequestFactory.build(
            pairId = DesktopConfigManager.getPairId(),
            agent = detectedAgent,
            permission = PermissionInfo(
                type = actionType,
                command = command,
                description = reason,
                cwd = workspacePath
            ),
            risk = RiskInfo(
                level = riskLevel,
                reasons = listOf("Requested through HandOff by ${detectedAgent.cleanName()}")
            ),
            options = listOf("approve", "deny"),
            workspacePath = workspacePath
        )

        return describeDecision(relayClient.sendRequestAndWaitForDecision(request))
    }

    private suspend fun askQuestion(args: JsonObject): JsonObject {
        val questionText = args["question"]?.jsonPrimitive?.contentOrNull ?: "Clarification needed"
        val options = args["options"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: listOf("Yes", "No")
        val isMultiSelect = args["is_multi_select"]?.jsonPrimitive?.booleanOrNull ?: false

        val request = RequestFactory.build(
            pairId = DesktopConfigManager.getPairId(),
            agent = detectedAgent,
            permission = PermissionInfo(
                type = PermissionType.QUESTION,
                description = questionText,
                cwd = workspacePath
            ),
            risk = RiskInfo(level = RiskLevel.MEDIUM, reasons = listOf("Interactive agent question")),
            options = listOf("answer_question", "cancel"),
            workspacePath = workspacePath,
            question = QuestionPayload(
                question = questionText,
                options = options,
                isMultiSelect = isMultiSelect
            )
        )

        val decision = relayClient.sendRequestAndWaitForDecision(request)
            ?: return textContent(
                "No answer arrived before the question expired. Ask again or proceed with your best judgement.",
                isError = true
            )

        val answer = buildString {
            append("User answered.")
            decision.selectedOptions?.takeIf { it.isNotEmpty() }?.let {
                append(" Selected: ${it.joinToString(", ")}.")
            }
            decision.feedback?.takeIf { it.isNotBlank() }?.let { append(" Comments: $it") }
        }
        return textContent(answer)
    }

    private suspend fun requestPlan(args: JsonObject): JsonObject {
        val title = args["title"]?.jsonPrimitive?.contentOrNull ?: "Implementation Plan"
        val summary = args["summary"]?.jsonPrimitive?.contentOrNull ?: ""
        val reviewItems = args["user_review_required"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()

        val request = RequestFactory.build(
            pairId = DesktopConfigManager.getPairId(),
            agent = detectedAgent,
            permission = PermissionInfo(
                type = PermissionType.PLAN,
                description = "Plan review: $title",
                cwd = workspacePath
            ),
            risk = RiskInfo(level = RiskLevel.HIGH, reasons = listOf("Architectural plan approval")),
            options = listOf("proceed_plan", "deny"),
            workspacePath = workspacePath,
            plan = PlanPayload(title = title, summary = summary, userReviewRequired = reviewItems)
        )

        return describeDecision(relayClient.sendRequestAndWaitForDecision(request))
    }

    private fun describeDecision(decision: PermissionDecision?): JsonObject {
        if (decision == null) {
            return textContent(
                "No decision arrived before the request expired. Nothing was authorized.",
                isError = true
            )
        }

        val detail = buildString {
            append("Decision: ${decision.decision}")
            decision.selectedOptions?.takeIf { it.isNotEmpty() }?.let {
                append(" | Selected: ${it.joinToString(", ")}")
            }
            decision.feedback?.takeIf { it.isNotBlank() }?.let { append(" | Feedback: $it") }
        }
        return textContent(detail, isError = !decision.isApproval())
    }

    // -------------------------------------------------------------------------------------
    // Session and workspace detection
    // -------------------------------------------------------------------------------------

    private suspend fun handleInitialize(params: JsonObject?) {
        if (params == null) return

        val clientInfo = params["clientInfo"]?.jsonObject
        val clientName = clientInfo?.get("name")?.jsonPrimitive?.contentOrNull
        val clientVersion = clientInfo?.get("version")?.jsonPrimitive?.contentOrNull

        if (!clientName.isNullOrBlank()) {
            detectedAgent = AgentInfo(
                id = clientName.lowercase().replace(" ", "-"),
                name = clientName,
                version = clientVersion ?: SERVER_VERSION
            )
            System.err.println("[Handoff MCP] Client: ${detectedAgent.cleanName()} v${detectedAgent.version}")
        }

        val declaredUri = params["workspaceFolders"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull
            ?: params["rootUri"]?.jsonPrimitive?.contentOrNull
            ?: params["rootPath"]?.jsonPrimitive?.contentOrNull

        val resolved = RequestFactory.resolveWorkspacePath(declaredUri)
            ?: RequestFactory.resolveWorkspacePath(System.getenv("HANDOFF_WORKSPACE"))

        if (resolved != null) {
            workspacePath = resolved
        }

        System.err.println("[Handoff MCP] Workspace: ${shortLabel()} ($workspacePath)")
        announceSession()
    }

    private suspend fun requestRootsFromClient() {
        runCatching { transport.request(ROOTS_REQUEST_ID, "roots/list") }
            .onFailure { System.err.println("[Handoff MCP] Could not request roots: ${it.message}") }
    }

    /** Handles the client's reply to our own `roots/list` request. */
    private suspend fun handleClientResponse(message: JsonObject) {
        val responseId = message["id"]?.jsonPrimitive?.contentOrNull ?: return
        if (responseId != ROOTS_REQUEST_ID) return

        val firstUri = message["result"]?.jsonObject
            ?.get("roots")?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull

        val resolved = RequestFactory.resolveWorkspacePath(firstUri) ?: return
        if (resolved == workspacePath) return

        workspacePath = resolved
        System.err.println("[Handoff MCP] Workspace updated from roots/list: ${shortLabel()}")
        announceSession()
    }

    /** Adopts a `cwd` argument a client may pass per call, for multi-root setups. */
    private fun adoptWorkspaceFromArgs(args: JsonObject) {
        val resolved = RequestFactory.resolveWorkspacePath(args["cwd"]?.jsonPrimitive?.contentOrNull) ?: return
        workspacePath = resolved
    }

    private suspend fun announceSession() {
        val pairId = DesktopConfigManager.getPairId()
        runCatching {
            relayClient.announceSession(
                SessionAnnouncement(
                    pairId = pairId,
                    agent = detectedAgent,
                    session = SessionInfo(id = pairId, project = shortLabel(), workspace = workspacePath),
                    timestamp = Instant.now().toString()
                )
            )
        }.onFailure {
            System.err.println("[Handoff MCP] Could not announce the session: ${it.message}")
        }
    }

    private fun shortLabel(): String = shortWorkspaceName(workspacePath) ?: "workspace"

    // -------------------------------------------------------------------------------------
    // Tool schemas
    // -------------------------------------------------------------------------------------

    private fun statusContent(): JsonObject {
        val status = buildJsonObject {
            put("pairId", DesktopConfigManager.getPairId())
            put("relayHost", DesktopConfigManager.getRelayHost())
            put("relayConnected", relayClient.isConnected)
            put("connectedIde", detectedAgent.cleanName())
            put("ideVersion", detectedAgent.version ?: "unknown")
            put("project", shortLabel())
            put("workspacePath", workspacePath)
            put("phonePaired", DesktopConfigManager.getMobilePublicKey() != null)
            put("protocolVersion", com.ovi.handoff.shared.protocol.Protocol.VERSION)
        }
        return textContent(status.toString())
    }

    private fun textContent(text: String, isError: Boolean = false): JsonObject = buildJsonObject {
        putJsonArray("content") {
            add(
                buildJsonObject {
                    put("type", "text")
                    put("text", text)
                }
            )
        }
        put("isError", isError)
    }

    private fun toolApprove(): JsonObject = buildJsonObject {
        put("name", "handoff_approve")
        put(
            "description",
            "Ask the paired phone to authorize a shell command or file change. Blocks until the user " +
                "decides or the request expires. Returns isError=true when the user denies."
        )
        putJsonObject("inputSchema") {
            put("type", "object")
            putJsonObject("properties") {
                stringProperty("command", "The shell command or operation to execute")
                stringProperty("reason", "Why this action is necessary")
                enumProperty(
                    "action_type",
                    "Action category",
                    listOf(
                        PermissionType.SHELL, PermissionType.TERMINAL, PermissionType.FILE_WRITE,
                        PermissionType.FILE_READ, PermissionType.PATCH, PermissionType.NETWORK,
                        PermissionType.MCP, PermissionType.OTHER
                    )
                )
                enumProperty(
                    "risk_level",
                    "How dangerous this action is. Critical requires biometric confirmation on the phone.",
                    listOf(RiskLevel.LOW, RiskLevel.MEDIUM, RiskLevel.HIGH, RiskLevel.CRITICAL)
                )
                stringProperty("cwd", "Working directory this action applies to")
            }
            putJsonArray("required") { add(JsonPrimitive("command")) }
        }
    }

    private fun toolAskQuestion(): JsonObject = buildJsonObject {
        put("name", "handoff_ask_question")
        put("description", "Ask the paired phone a multiple-choice question and wait for the answer.")
        putJsonObject("inputSchema") {
            put("type", "object")
            putJsonObject("properties") {
                stringProperty("question", "The question to ask")
                putJsonObject("options") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                    put("description", "Selectable answers")
                }
                putJsonObject("is_multi_select") {
                    put("type", "boolean")
                    put("description", "True when more than one option may be chosen")
                }
                stringProperty("cwd", "Working directory this question relates to")
            }
            putJsonArray("required") {
                add(JsonPrimitive("question"))
                add(JsonPrimitive("options"))
            }
        }
    }

    private fun toolRequestPlan(): JsonObject = buildJsonObject {
        put("name", "handoff_request_plan_approval")
        put("description", "Send an implementation plan to the paired phone for review before executing it.")
        putJsonObject("inputSchema") {
            put("type", "object")
            putJsonObject("properties") {
                stringProperty("title", "Title of the plan")
                stringProperty("summary", "Summary of the proposed changes")
                putJsonObject("user_review_required") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                    put("description", "Decisions that need explicit consent")
                }
                stringProperty("cwd", "Working directory the plan applies to")
            }
            putJsonArray("required") {
                add(JsonPrimitive("title"))
                add(JsonPrimitive("summary"))
            }
        }
    }

    private fun toolStatus(): JsonObject = buildJsonObject {
        put("name", "handoff_status")
        put("description", "Report the HandOff pair id, relay connectivity, detected IDE and workspace.")
        putJsonObject("inputSchema") {
            put("type", "object")
            putJsonObject("properties") {
                stringProperty("cwd", "Working directory to report on")
            }
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.stringProperty(name: String, description: String) {
        putJsonObject(name) {
            put("type", "string")
            put("description", description)
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.enumProperty(
        name: String,
        description: String,
        values: List<String>
    ) {
        putJsonObject(name) {
            put("type", "string")
            put("description", description)
            putJsonArray("enum") { values.forEach { add(JsonPrimitive(it)) } }
        }
    }

    // -------------------------------------------------------------------------------------
    // Fallback IDE detection, used before `initialize` names the client
    // -------------------------------------------------------------------------------------

    private fun detectInitialAgentId(): String {
        val env = System.getenv()
        return when {
            env.containsKey("ANTIGRAVITY_IDE") || env.containsKey("ANTIGRAVITY_AGENT") -> "antigravity"
            env.containsKey("CURSOR_VERSION") || env.containsKey("CURSOR_PID") -> "cursor"
            env.containsKey("CLAUDE_CODE") || env.containsKey("CLAUDECODE") -> "claude"
            env.containsKey("GEMINI_CLI") -> "gemini"
            env.containsKey("TERM_PROGRAM") && env["TERM_PROGRAM"] == "vscode" -> "vscode"
            env.containsKey("VSCODE_PID") -> "vscode"
            else -> "unknown-agent"
        }
    }

    private fun detectInitialAgentName(): String = when (detectInitialAgentId()) {
        "antigravity" -> "Antigravity"
        "cursor" -> "Cursor"
        "claude" -> "Claude Code"
        "gemini" -> "Gemini"
        "vscode" -> "VSCode"
        // Naming an unidentified client after a specific IDE, as this used to default to
        // Antigravity, puts a wrong product name on the approval card the user is trusting.
        else -> "AI Agent"
    }
}
