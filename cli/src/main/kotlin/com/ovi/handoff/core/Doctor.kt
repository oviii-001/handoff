package com.ovi.handoff.core

import com.ovi.handoff.adapter.JsonRpcTransport
import com.ovi.handoff.adapter.McpProtocolServer
import com.ovi.handoff.adapter.McpTools
import com.ovi.handoff.adapter.OfflineApprovalGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * `handoff --doctor`: proves the setup works, in the order things actually break.
 *
 * Wiring an MCP server into an IDE is a blind operation. If it does not appear, the user cannot tell
 * whether the launch command is wrong, the relay is unreachable, the pair room is claimed by another
 * machine, or the phone was never paired — the IDE reports all of these identically, as a server
 * that failed to start. Every check here maps to one of those, and each failure states the command
 * that fixes it.
 */
public object Doctor {

    private const val DIVIDER = "=================================================="

    private val json = Json { ignoreUnknownKeys = true }

    public fun run() {
        println(DIVIDER)
        println(" HandOff doctor")
        println(DIVIDER)

        val config = DesktopConfigManager.loadConfig()
        var blocking = 0

        blocking += section("Local identity") { checkIdentity(config) }
        blocking += section("Relay") { checkRelay(config) }
        blocking += section("Pairing") { checkPairing(config) }
        blocking += section("MCP server") { checkMcpServer() }
        blocking += section("IDE registration") { checkIdeRegistration() }

        println(DIVIDER)
        if (blocking == 0) {
            println("Everything checks out. Approvals should reach your phone.")
        } else {
            println("$blocking problem(s) need attention. Each is marked FAIL above with its fix.")
        }
        println("Log file: ${Log.logFilePath()}")
        println(DIVIDER)
    }

    // -------------------------------------------------------------------------------------

    private fun section(title: String, body: () -> Int): Int {
        println()
        println("[$title]")
        return body()
    }

    private fun ok(label: String, detail: String): Int {
        println("  PASS  ${label.padEnd(22)} $detail")
        return 0
    }

    private fun warn(label: String, detail: String, fix: String? = null): Int {
        println("  WARN  ${label.padEnd(22)} $detail")
        fix?.let { println("        ${" ".repeat(22)} $it") }
        return 0
    }

    private fun fail(label: String, detail: String, fix: String): Int {
        println("  FAIL  ${label.padEnd(22)} $detail")
        println("        ${" ".repeat(22)} Fix: $fix")
        return 1
    }

    // -------------------------------------------------------------------------------------

    private fun checkIdentity(config: DesktopConfig): Int {
        var problems = 0
        problems += ok("Pair ID", config.pairId)

        problems += if (config.pairSecret.isBlank()) {
            fail("Pairing secret", "missing", "run `handoff --rotate-pair` to mint a new pair id and secret")
        } else {
            ok("Pairing secret", "present (${config.pairSecret.length} chars)")
        }

        val keyDir = File(System.getProperty("user.home"), ".handoff/keys")
        problems += if (File(keyDir, "device.priv").exists()) {
            ok("Signing key", "present in ${keyDir.absolutePath}")
        } else {
            warn(
                "Signing key",
                "not generated yet",
                "created automatically on the next `handoff --pair`"
            )
        }
        return problems
    }

    private fun checkRelay(config: DesktopConfig): Int {
        return when (val health = RelayEndpoint.health(config.relayHost)) {
            is RelayEndpoint.Health.Up ->
                ok("Reachable", "${config.relayHost} (protocol ${health.protocol ?: "unknown"})")

            is RelayEndpoint.Health.Unexpected -> fail(
                "Reachable",
                "answered HTTP ${health.statusCode}",
                "if you self-host, confirm `wrangler deploy` succeeded for ${config.relayHost}"
            )

            is RelayEndpoint.Health.Down -> fail(
                "Reachable",
                "no answer (${health.reason})",
                "check your network, or set HANDOFF_RELAY_HOST to your own relay"
            )
        }
    }

    private fun checkPairing(config: DesktopConfig): Int {
        var problems = 0

        problems += when (val status = RelayEndpoint.pairStatus(config.relayHost, config.pairId, config.pairSecret)) {
            is RelayEndpoint.PairStatus.Ok -> {
                ok("Relay room", "claimed by this desktop") +
                    if (status.phoneOnline) {
                        ok("Phone socket", "connected right now")
                    } else {
                        warn(
                            "Phone socket",
                            "no phone connected right now",
                            "approvals will try to wake it with a push notification"
                        )
                    }
            }

            RelayEndpoint.PairStatus.Unclaimed -> warn(
                "Relay room",
                "not claimed yet",
                "claimed automatically when `handoff --pair` or your IDE starts the server"
            )

            is RelayEndpoint.PairStatus.TokenRejected -> fail(
                "Relay room",
                "claimed by another device (${status.message})",
                "run `handoff --rotate-pair`, then `handoff --pair` to pair again"
            )

            RelayEndpoint.PairStatus.Unsupported -> warn(
                "Relay room",
                "this relay is too old to report pair status",
                "redeploy the relay from apps/relay to enable presence diagnostics"
            )

            is RelayEndpoint.PairStatus.Unreachable ->
                warn("Relay room", "could not be checked (${status.reason})")
        }

        problems += if (DesktopConfigManager.isPhonePaired()) {
            ok("Phone paired", "device ${config.mobileDeviceId ?: "unknown"} (${config.mobileKeyAlgorithm})")
        } else {
            fail(
                "Phone paired",
                "no phone has announced a signing key",
                "run `handoff --pair` and scan the code with the HandOff app"
            )
        }
        return problems
    }

    /**
     * Drives a real handshake through the real protocol server, in process.
     *
     * The point is to fail here rather than inside an IDE. A broken handshake presents to the user
     * as "the MCP server did not start", with the actual JSON-RPC exchange buried in a log pane
     * whose location differs per IDE.
     */
    private fun checkMcpServer(): Int = runBlocking {
        val sink = ByteArrayOutputStream()
        val transport = JsonRpcTransport(sink)
        val server = McpProtocolServer(
            transport = transport,
            gateway = OfflineApprovalGateway(),
            policyEngine = PolicyEngine(File(System.getProperty("user.home"), ".handoff/policy.yml")),
            scope = CoroutineScope(Job()),
            defaultWorkspace = File(".").canonicalFile.absolutePath,
            initialAgent = McpProtocolServer.detectAgentFromEnvironment()
        )

        server.handleLine(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"${McpProtocolServer.LATEST_PROTOCOL}","capabilities":{},"clientInfo":{"name":"handoff-doctor","version":"1"}}}"""
        )
        server.handleLine("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")
        server.handleLine("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")
        transport.flush()

        val frames = sink.toString(Charsets.UTF_8).lines().filter { it.isNotBlank() }
            .mapNotNull { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }

        var problems = 0

        val negotiated = frames
            .mapNotNull { it["result"]?.jsonObject }
            .firstOrNull { it.containsKey("serverInfo") }
            ?.get("protocolVersion")?.jsonPrimitive?.contentOrNull

        problems += if (negotiated != null) {
            ok("Handshake", "replies with protocol $negotiated")
        } else {
            fail("Handshake", "no valid initialize result", "report this output as a bug; the log has details")
        }

        val tools = frames.firstNotNullOfOrNull { it["result"]?.jsonObject?.get("tools")?.jsonArray }
        val names = tools?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }.orEmpty()
        val expected = listOf(McpTools.APPROVE, McpTools.ASK_QUESTION, McpTools.REQUEST_PLAN, McpTools.STATUS)

        problems += if (names.containsAll(expected)) {
            ok("Tools", "${names.size} exposed: ${names.joinToString(", ")}")
        } else {
            fail("Tools", "missing ${expected - names.toSet()}", "rebuild with `gradlew :cli:installDist`")
        }
        problems
    }

    private fun checkIdeRegistration(): Int {
        val launcher = McpAutoInstaller.resolveLauncher()
        println("  Launch command:")
        println("    ${launcher.command} ${launcher.args.joinToString(" ")}")
        println()

        val states = McpAutoInstaller.registrationReport()
        if (states.isEmpty()) {
            return warn("Configs", "no supported IDE config files found", "run `handoff --install`")
        }

        var registered = 0
        for (state in states) {
            val line = when {
                !state.configExists -> "  ....  ${state.label.padEnd(22)} no config file at ${state.path}"

                state.registered && state.upToDate -> {
                    registered++
                    "  PASS  ${state.label.padEnd(22)} registered and up to date"
                }

                state.registered -> {
                    registered++
                    "  WARN  ${state.label.padEnd(22)} registered with a different command; run `handoff --install`"
                }

                else -> "  WARN  ${state.label.padEnd(22)} not registered; run `handoff --install`"
            }
            println(line)
        }

        return if (registered == 0) {
            println()
            warn("Configs", "HandOff is not registered with any IDE", "run `handoff --install`, then restart your IDE")
        } else {
            0
        }
    }
}
