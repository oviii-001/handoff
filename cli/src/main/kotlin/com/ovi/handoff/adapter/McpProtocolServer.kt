package com.ovi.handoff.adapter

import com.ovi.handoff.core.Log
import com.ovi.handoff.core.PolicyAction
import com.ovi.handoff.core.PolicyEngine
import com.ovi.handoff.core.RequestFactory
import com.ovi.handoff.core.decisionOrNull
import com.ovi.handoff.core.explain
import com.ovi.handoff.core.isApproved
import com.ovi.handoff.shared.model.AgentInfo
import com.ovi.handoff.shared.model.PermissionInfo
import com.ovi.handoff.shared.model.PermissionType
import com.ovi.handoff.shared.model.PlanPayload
import com.ovi.handoff.shared.model.QuestionPayload
import com.ovi.handoff.shared.model.RiskInfo
import com.ovi.handoff.shared.model.RiskLevel
import com.ovi.handoff.shared.model.SessionAnnouncement
import com.ovi.handoff.shared.model.SessionInfo
import com.ovi.handoff.shared.model.cleanName
import com.ovi.handoff.shared.model.shortWorkspaceName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.BufferedReader
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * The MCP protocol server, over a newline-delimited JSON-RPC stdio transport.
 *
 * Constructed with its collaborators rather than reaching for singletons, so the protocol can be
 * exercised in a test without a key pair on disk or a live relay.
 *
 * Behaviours here that exist because their absence was a real failure:
 *
 *  - **The version is negotiated, not asserted.** The server used to answer `2024-11-05` whatever
 *    the client asked for. A client that speaks a newer revision is entitled to treat that as a
 *    refusal, so the reply now echoes the client's version when it is one we support.
 *  - **Server-initiated requests are capability-gated and uniquely identified.** `roots/list` used
 *    to go to every client, including those that never declared the capability, always under the
 *    same hardcoded id — two outstanding requests could therefore share an id, which JSON-RPC
 *    forbids.
 *  - **Workspace is per call.** A shared mutable `workspacePath` was rewritten by every tool call,
 *    so two concurrent calls from different folders could stamp each other's path onto the approval
 *    card the user is being asked to trust.
 *  - **Every call gets exactly one reply.** A failure anywhere in the approval path used to be
 *    logged and dropped, leaving the IDE waiting on a tool result that would never arrive.
 */
internal class McpProtocolServer(
    private val transport: JsonRpcTransport,
    private val gateway: ApprovalGateway,
    private val policyEngine: PolicyEngine,
    private val scope: CoroutineScope,
    defaultWorkspace: String,
    initialAgent: AgentInfo
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** In-flight tool calls, so `notifications/cancelled` can actually cancel one. */
    private val activeCalls = ConcurrentHashMap<String, ActiveCall>()

    /** Requests we sent to the client, awaiting their reply. */
    private val outstandingServerRequests = ConcurrentHashMap<String, String>()

    private val serverRequestCounter = AtomicLong()

    @Volatile
    private var detectedAgent: AgentInfo = initialAgent

    /** Session-wide default. Only `initialize` and `roots/list` write it; tool calls never do. */
    @Volatile
    private var sessionWorkspace: String = defaultWorkspace

    @Volatile
    private var negotiatedProtocol: String = LATEST_PROTOCOL

    @Volatile
    private var initialized: Boolean = false

    @Volatile
    private var clientSupportsRoots: Boolean = false

    private class ActiveCall(val handle: Job) {
        /** The HandOff request id, once one exists, so a cancel can reach the phone. */
        @Volatile
        var handoffRequestId: String? = null
    }

    // -------------------------------------------------------------------------------------
    // Loop
    // -------------------------------------------------------------------------------------

    /** Reads framed messages until the client closes stdin. */
    suspend fun serve(reader: BufferedReader) {
        Log.info(
            "MCP server listening on stdio. Client: ${detectedAgent.cleanName()}, " +
                "workspace: ${shortLabel(sessionWorkspace)}"
        )

        while (true) {
            val line = reader.readLine() ?: break
            handleLine(line)
        }

        Log.info("Client closed stdin; shutting down.")
    }

    /** Handles one wire line. Separated from [serve] so tests can drive the protocol directly. */
    suspend fun handleLine(line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return

        val message = runCatching { json.parseToJsonElement(trimmed).jsonObject }.getOrNull()
        if (message == null) {
            Log.warn("Discarded a non-JSON line from the client.")
            return
        }

        val id = message["id"]
        val method = message["method"]?.jsonPrimitive?.contentOrNull

        // No method means this is the client answering something we asked.
        if (method == null) {
            handleClientResponse(message)
            return
        }

        try {
            dispatch(id, method, message)
        } catch (cause: Exception) {
            // Never leave a call unanswered: an IDE waiting on a tool result it will never receive
            // looks identical to a hung server, and the user has no way to tell them apart.
            Log.error("$method failed", cause)
            if (id != null) {
                transport.error(id, JsonRpcError.INTERNAL_ERROR, "handoff: ${cause.message}")
            }
        }
    }

    private suspend fun dispatch(id: JsonElement?, method: String, message: JsonObject) {
        when (method) {
            "initialize" -> handleInitialize(id, message["params"]?.jsonObject)

            "notifications/initialized", "initialized" -> {
                initialized = true
                requestRootsFromClient()
                announceSession()
            }

            "notifications/roots/list_changed" -> requestRootsFromClient()

            "notifications/cancelled" -> handleCancelled(message["params"]?.jsonObject)

            "tools/list" -> transport.respond(
                id,
                buildJsonObject {
                    putJsonArray("tools") { McpTools.all().forEach { add(it) } }
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
    // Handshake
    // -------------------------------------------------------------------------------------

    private suspend fun handleInitialize(id: JsonElement?, params: JsonObject?) {
        adoptClientInfo(params)
        negotiatedProtocol = negotiateProtocol(params?.get("protocolVersion")?.jsonPrimitive?.contentOrNull)
        clientSupportsRoots = params?.get("capabilities")?.jsonObject?.containsKey("roots") == true

        params?.let { adoptDeclaredWorkspace(it) }

        transport.respond(
            id,
            buildJsonObject {
                put("protocolVersion", negotiatedProtocol)
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

        initialized = true
        Log.info(
            "Handshake complete. Client: ${detectedAgent.cleanName()} v${detectedAgent.version}, " +
                "protocol: $negotiatedProtocol, workspace: ${shortLabel(sessionWorkspace)} ($sessionWorkspace)"
        )
        announceSession()
    }

    /**
     * Picks the protocol revision to answer with.
     *
     * The spec's rule is to echo the client's version when we support it, and otherwise to name one
     * we do support so the client can decide whether to continue. Answering a fixed version, as this
     * did, is a refusal in the eyes of a client that asked for something newer.
     */
    private fun negotiateProtocol(requested: String?): String =
        if (requested != null && requested in SUPPORTED_PROTOCOLS) requested else LATEST_PROTOCOL

    private fun adoptClientInfo(params: JsonObject?) {
        val clientInfo = params?.get("clientInfo")?.jsonObject ?: return
        val clientName = clientInfo["name"]?.jsonPrimitive?.contentOrNull
        if (clientName.isNullOrBlank()) return

        detectedAgent = AgentInfo(
            id = clientName.lowercase().replace(" ", "-"),
            name = clientName,
            version = clientInfo["version"]?.jsonPrimitive?.contentOrNull ?: SERVER_VERSION
        )
    }

    private fun adoptDeclaredWorkspace(params: JsonObject) {
        val declaredUri = params["workspaceFolders"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull
            ?: params["rootUri"]?.jsonPrimitive?.contentOrNull
            ?: params["rootPath"]?.jsonPrimitive?.contentOrNull

        val resolved = RequestFactory.resolveWorkspacePath(declaredUri)
            ?: RequestFactory.resolveWorkspacePath(System.getenv("HANDOFF_WORKSPACE"))
            ?: return

        sessionWorkspace = resolved
    }

    /**
     * Asks the client for its workspace roots, but only if it said it has any.
     *
     * Sending this unconditionally, as this used to, means every client without the capability
     * answers `-32601`. That is noise at best, and some clients treat an unsolicited server request
     * as a protocol violation.
     */
    private suspend fun requestRootsFromClient() {
        if (!clientSupportsRoots) return

        val requestId = "handoff-req-${serverRequestCounter.incrementAndGet()}"
        outstandingServerRequests[requestId] = ROOTS_METHOD
        runCatching { transport.request(requestId, ROOTS_METHOD) }
            .onFailure {
                outstandingServerRequests.remove(requestId)
                Log.warn("Could not request roots: ${it.message}")
            }
    }

    /** Handles the client's reply to a request we sent. */
    private suspend fun handleClientResponse(message: JsonObject) {
        val responseId = message["id"]?.jsonPrimitive?.contentOrNull ?: return
        val method = outstandingServerRequests.remove(responseId) ?: return

        message["error"]?.jsonObject?.let { error ->
            val text = error["message"]?.jsonPrimitive?.contentOrNull ?: "unknown error"
            // Not retried: a client that refuses one capability request will refuse the next.
            Log.warn("Client declined $method: $text")
            return
        }

        if (method != ROOTS_METHOD) return

        val firstUri = message["result"]?.jsonObject
            ?.get("roots")?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull

        val resolved = RequestFactory.resolveWorkspacePath(firstUri) ?: return
        if (resolved == sessionWorkspace) return

        sessionWorkspace = resolved
        Log.info("Workspace updated from roots/list: ${shortLabel(resolved)}")
        announceSession()
    }

    // -------------------------------------------------------------------------------------
    // Tool dispatch
    // -------------------------------------------------------------------------------------

    private suspend fun handleToolCall(id: JsonElement?, params: JsonObject?) {
        if (!initialized) {
            // Answering tool calls before the handshake would mean acting on a session whose client
            // and workspace we have not established.
            transport.error(id, JsonRpcError.INVALID_REQUEST, "handoff: initialize has not completed yet")
            return
        }

        val toolName = params?.get("name")?.jsonPrimitive?.contentOrNull
        val args = params?.get("arguments")?.jsonObject ?: JsonObject(emptyMap())

        // Resolved per call and passed down, never stored: two concurrent calls from different
        // folders must not overwrite each other's workspace.
        val workspace = RequestFactory.resolveWorkspacePath(args["cwd"]?.jsonPrimitive?.contentOrNull)
            ?: sessionWorkspace

        when (toolName) {
            McpTools.STATUS -> transport.respond(id, statusContent(workspace))

            McpTools.APPROVE -> launchApproval(id) { call -> approve(call, args, workspace) }
            McpTools.ASK_QUESTION -> launchApproval(id) { call -> askQuestion(call, args, workspace) }
            McpTools.REQUEST_PLAN -> launchApproval(id) { call -> requestPlan(call, args, workspace) }

            null -> transport.error(id, JsonRpcError.INVALID_PARAMS, "tools/call is missing a tool name")
            else -> transport.error(id, JsonRpcError.METHOD_NOT_FOUND, "Tool not found: $toolName")
        }
    }

    /**
     * Runs an approval off the read loop and guarantees exactly one reply for the call.
     *
     * Off the loop because an approval blocks on a human: handling it inline meant the IDE could not
     * ping, could not cancel and could not issue a second tool call for up to five minutes, which is
     * indistinguishable from a hung server.
     *
     * The call's [Job] is created *before* the coroutine and used as its parent, so a
     * `notifications/cancelled` that arrives before the coroutine is even scheduled still lands.
     * Parented to [scope] so shutdown cancels it too.
     */
    private fun launchApproval(id: JsonElement?, block: suspend (ActiveCall) -> JsonObject) {
        val callKey = id?.toString()
        val handle = Job(scope.coroutineContext[Job])
        val call = ActiveCall(handle)
        if (callKey != null) {
            activeCalls[callKey] = call
        }

        CoroutineScope(scope.coroutineContext + handle).launch {
            try {
                val result = try {
                    block(call)
                } catch (cancellation: CancellationException) {
                    // A cancelled request gets no response at all: answering one the client has
                    // already withdrawn leaves it holding a result it cannot match to anything.
                    throw cancellation
                } catch (cause: Exception) {
                    Log.error("Approval failed", cause)
                    textContent("HandOff could not complete the request: ${cause.message}", isError = true)
                }
                // A call with no id is a notification: run it, but there is nobody to answer.
                if (id != null) {
                    transport.respond(id, result)
                }
            } finally {
                if (callKey != null) {
                    activeCalls.remove(callKey)
                }
            }
        }
    }

    /**
     * Releases a cancelled call locally *and* on the phone.
     *
     * Cancelling only the local job left the phone showing an approval card for a tool call the IDE
     * had already abandoned, and left the request stored on the relay to be replayed on reconnect.
     */
    private fun handleCancelled(params: JsonObject?) {
        val requestId = params?.get("requestId")?.toString() ?: return
        val call = activeCalls.remove(requestId) ?: return

        call.handoffRequestId?.let { handoffId ->
            // On the scope rather than the cancelled call's own job, which is about to die.
            scope.launch { runCatching { gateway.cancel(handoffId) } }
        }
        call.handle.cancel()
        Log.info("Client cancelled call $requestId.")
    }

    // -------------------------------------------------------------------------------------
    // Tools
    // -------------------------------------------------------------------------------------

    private suspend fun approve(call: ActiveCall, args: JsonObject, workspace: String): JsonObject {
        val command = args["command"]?.jsonPrimitive?.contentOrNull ?: ""
        val reason = args["reason"]?.jsonPrimitive?.contentOrNull ?: "Execution requires authorization"
        val actionType = args["action_type"]?.jsonPrimitive?.contentOrNull ?: PermissionType.SHELL
        val riskLevel = args["risk_level"]?.jsonPrimitive?.contentOrNull ?: RiskLevel.HIGH

        when (policyEngine.evaluate(command, actionType)) {
            PolicyAction.ALLOW -> return textContent("Permission auto-approved by local policy.")
            PolicyAction.DENY -> return textContent("Permission blocked by local policy.", isError = true)
        }

        val request = RequestFactory.build(
            pairId = gateway.pairId,
            agent = detectedAgent,
            permission = PermissionInfo(
                type = actionType,
                command = command,
                description = reason,
                cwd = workspace
            ),
            risk = RiskInfo(
                level = riskLevel,
                reasons = listOf("Requested through HandOff by ${detectedAgent.cleanName()}")
            ),
            options = listOf("approve", "deny"),
            workspacePath = workspace
        )
        call.handoffRequestId = request.id

        val outcome = gateway.request(request)
        return textContent(outcome.explain(), isError = !outcome.isApproved())
    }

    private suspend fun askQuestion(call: ActiveCall, args: JsonObject, workspace: String): JsonObject {
        val questionText = args["question"]?.jsonPrimitive?.contentOrNull ?: "Clarification needed"
        val options = args["options"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: listOf("Yes", "No")
        val isMultiSelect = args["is_multi_select"]?.jsonPrimitive?.booleanOrNull ?: false

        val request = RequestFactory.build(
            pairId = gateway.pairId,
            agent = detectedAgent,
            permission = PermissionInfo(
                type = PermissionType.QUESTION,
                description = questionText,
                cwd = workspace
            ),
            risk = RiskInfo(level = RiskLevel.MEDIUM, reasons = listOf("Interactive agent question")),
            options = listOf("answer_question", "cancel"),
            workspacePath = workspace,
            question = QuestionPayload(
                question = questionText,
                options = options,
                isMultiSelect = isMultiSelect
            )
        )
        call.handoffRequestId = request.id

        val outcome = gateway.request(request)
        val decision = outcome.decisionOrNull()
            ?: return textContent(
                "${outcome.explain()} Ask again once it is fixed, or proceed with your best judgement " +
                    "and say clearly that you did not get an answer.",
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

    private suspend fun requestPlan(call: ActiveCall, args: JsonObject, workspace: String): JsonObject {
        val title = args["title"]?.jsonPrimitive?.contentOrNull ?: "Implementation Plan"
        val summary = args["summary"]?.jsonPrimitive?.contentOrNull ?: ""
        val reviewItems = args["user_review_required"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()

        val request = RequestFactory.build(
            pairId = gateway.pairId,
            agent = detectedAgent,
            permission = PermissionInfo(
                type = PermissionType.PLAN,
                description = "Plan review: $title",
                cwd = workspace
            ),
            risk = RiskInfo(level = RiskLevel.HIGH, reasons = listOf("Architectural plan approval")),
            options = listOf("proceed_plan", "deny"),
            workspacePath = workspace,
            plan = PlanPayload(title = title, summary = summary, userReviewRequired = reviewItems)
        )
        call.handoffRequestId = request.id

        val outcome = gateway.request(request)
        return textContent(outcome.explain(), isError = !outcome.isApproved())
    }

    // -------------------------------------------------------------------------------------
    // Status
    // -------------------------------------------------------------------------------------

    /**
     * Reports connectivity in prose first, machine-readable JSON second.
     *
     * The previous version returned only a JSON blob. An agent handed `"phonePaired": false` will
     * usually paraphrase it; what the user needs is the command that fixes it, so the verdict names
     * the next step explicitly.
     */
    private fun statusContent(workspace: String): JsonObject {
        val paired = gateway.isPhonePaired
        val connected = gateway.isRelayConnected
        val phone = gateway.phoneOnline

        val verdict = when {
            !paired ->
                "NOT READY. No phone is paired with this desktop. Tell the user to run `handoff --pair` " +
                    "in a terminal and scan the code with the HandOff app."

            phone == false ->
                "PARTIALLY READY. A phone is paired but is not currently connected to the relay. " +
                    "An approval will try to wake it with a notification; tell the user to open the " +
                    "HandOff app if approvals are not arriving."

            else -> "READY. A phone is paired and HandOff can reach it."
        }

        val summary = buildString {
            appendLine(verdict)
            appendLine()
            appendLine("Pair ID     : ${gateway.pairId}")
            appendLine("Relay       : ${gateway.relayHost} (${if (connected) "connected" else "not connected"})")
            appendLine("Phone       : ${if (paired) "paired" else "not paired"}${phoneLabel(phone)}")
            appendLine("IDE         : ${detectedAgent.cleanName()} ${detectedAgent.version ?: ""}".trimEnd())
            appendLine("Project     : ${shortLabel(workspace)}")
            appendLine("Workspace   : $workspace")
            appendLine("MCP protocol: $negotiatedProtocol")
            appendLine("Diagnostics : run `handoff --doctor`, log at ${Log.logFilePath()}")
        }

        val machine = buildJsonObject {
            put("ready", paired)
            put("pairId", gateway.pairId)
            put("relayHost", gateway.relayHost)
            put("relayConnected", connected)
            put("phonePaired", paired)
            phone?.let { put("phoneOnline", it) }
            put("connectedIde", detectedAgent.cleanName())
            put("ideVersion", detectedAgent.version ?: "unknown")
            put("project", shortLabel(workspace))
            put("workspacePath", workspace)
            put("mcpProtocolVersion", negotiatedProtocol)
            put("handoffProtocolVersion", com.ovi.handoff.shared.protocol.Protocol.VERSION)
        }

        return buildJsonObject {
            putJsonArray("content") {
                add(buildJsonObject { put("type", "text"); put("text", summary.trimEnd()) })
                add(buildJsonObject { put("type", "text"); put("text", machine.toString()) })
            }
            put("isError", false)
        }
    }

    private fun phoneLabel(phoneOnline: Boolean?): String = when (phoneOnline) {
        true -> ", online"
        false -> ", offline"
        null -> ""
    }

    // -------------------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------------------

    private suspend fun announceSession() {
        val pairId = gateway.pairId
        runCatching {
            gateway.announce(
                SessionAnnouncement(
                    pairId = pairId,
                    agent = detectedAgent,
                    session = SessionInfo(
                        id = pairId,
                        project = shortLabel(sessionWorkspace),
                        workspace = sessionWorkspace
                    ),
                    timestamp = Instant.now().toString()
                )
            )
        }.onFailure {
            // Never fatal: the handshake must not depend on the relay being reachable, or an offline
            // network would present as a broken MCP server.
            Log.warn("Could not announce the session: ${it.message}")
        }
    }

    private fun shortLabel(path: String): String = shortWorkspaceName(path) ?: "workspace"

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

    companion object {
        const val SERVER_VERSION: String = "2.1.0"

        /** Newest first. The head is what we answer when the client asks for something we do not know. */
        val SUPPORTED_PROTOCOLS: List<String> = listOf("2025-06-18", "2025-03-26", "2024-11-05")

        val LATEST_PROTOCOL: String = SUPPORTED_PROTOCOLS.first()

        private const val ROOTS_METHOD = "roots/list"

        /** IDE guessed from the environment, used only until `initialize` names the client. */
        fun detectAgentFromEnvironment(env: Map<String, String> = System.getenv()): AgentInfo {
            val id = when {
                env.containsKey("ANTIGRAVITY_IDE") || env.containsKey("ANTIGRAVITY_AGENT") -> "antigravity"
                env.containsKey("CURSOR_VERSION") || env.containsKey("CURSOR_PID") -> "cursor"
                env.containsKey("CLAUDE_CODE") || env.containsKey("CLAUDECODE") -> "claude"
                env.containsKey("GEMINI_CLI") -> "gemini"
                env["TERM_PROGRAM"] == "vscode" -> "vscode"
                env.containsKey("VSCODE_PID") -> "vscode"
                else -> "unknown-agent"
            }
            val name = when (id) {
                "antigravity" -> "Antigravity"
                "cursor" -> "Cursor"
                "claude" -> "Claude Code"
                "gemini" -> "Gemini"
                "vscode" -> "VSCode"
                // Naming an unidentified client after a specific IDE, as this used to default to
                // Antigravity, puts a wrong product name on the approval card the user is trusting.
                else -> "AI Agent"
            }
            return AgentInfo(id = id, name = name, version = SERVER_VERSION)
        }

        fun defaultWorkspace(): String =
            RequestFactory.resolveWorkspacePath(System.getenv("HANDOFF_WORKSPACE"))
                ?: File(".").canonicalFile.absolutePath
    }
}
