package com.ovi.handoff

import com.ovi.handoff.adapter.McpServer
import com.ovi.handoff.core.CommandWrapper
import com.ovi.handoff.core.DesktopConfigManager
import com.ovi.handoff.core.KeyStoreManager
import com.ovi.handoff.core.McpAutoInstaller
import com.ovi.handoff.core.RelayClient
import com.ovi.handoff.core.RequestFactory
import com.ovi.handoff.core.TerminalQrGenerator
import com.ovi.handoff.shared.model.AgentInfo
import com.ovi.handoff.shared.model.PermissionInfo
import com.ovi.handoff.shared.model.PermissionType
import com.ovi.handoff.shared.model.PlanPayload
import com.ovi.handoff.shared.model.QuestionPayload
import com.ovi.handoff.shared.model.RiskInfo
import com.ovi.handoff.shared.model.RiskLevel
import com.ovi.handoff.shared.model.isApproval
import com.ovi.handoff.shared.protocol.Protocol
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

private const val DIVIDER = "=================================================="

public fun main(args: Array<String>) {
    when {
        args.contains("--mcp") || args.contains("--cli") -> {
            System.err.println("[Handoff] MCP server starting on stdio...")
            McpServer.run()
        }

        args.contains("--pair") -> printPairing()
        args.contains("--rotate-pair") -> rotatePairing()
        args.contains("--status") -> printStatus()
        args.contains("--install") -> {
            println(DIVIDER)
            println(" HandOff MCP installer")
            println(DIVIDER)
            McpAutoInstaller.install()
        }

        args.contains("--exec") -> {
            val index = args.indexOf("--exec")
            val commandArgs = args.drop(index + 1)
            if (commandArgs.isEmpty()) {
                System.err.println("Error: missing command after --exec")
                kotlin.system.exitProcess(2)
            }
            CommandWrapper.execute(commandArgs)
        }

        else -> {
            val scenario = TestScenarios.match(args)
            if (scenario != null) {
                runScenario(scenario, args)
            } else {
                printUsage()
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Pairing
// -------------------------------------------------------------------------------------------

private fun printPairing() {
    val config = DesktopConfigManager.loadConfig()
    val keyStore = KeyStoreManager(File(System.getProperty("user.home"), ".handoff/keys"))
    val publicKey = KeyStoreManager.encodePublicKey(keyStore.getOrGenerateKeyPair().public)

    // The pairing link carries the relay token as well as the key. Without the token the phone
    // cannot authenticate to the relay, which is what keeps a guessed pair id from being enough to
    // approve commands.
    val pairUrl = buildString {
        append("handoff://pair")
        append("?v=").append(Protocol.VERSION)
        append("&pairId=").append(config.pairId)
        append("&host=").append(config.relayHost)
        append("&pubKey=").append(publicKey)
        append("&token=").append(config.pairSecret)
    }

    println(DIVIDER)
    println(" HandOff pairing")
    println(DIVIDER)
    println("Pair ID  : ${config.pairId}")
    println("Relay    : ${config.relayHost}")
    println("Protocol : ${Protocol.VERSION}")
    println()
    println("Scan this with the HandOff app:")
    TerminalQrGenerator.printQrCode(pairUrl)
    println("Cannot scan? Paste this link into the app's manual pairing field:")
    println()
    println("  $pairUrl")
    println()
    println("Treat that link like a password: it authorizes a device to approve your agent's actions.")
    println("Run `handoff --rotate-pair` to invalidate it.")
    println(DIVIDER)
}

private fun rotatePairing() {
    val rotated = DesktopConfigManager.rotatePair()
    println(DIVIDER)
    println(" HandOff pairing rotated")
    println(DIVIDER)
    println("New pair ID: ${rotated.pairId}")
    println("Every previously paired phone is now rejected by the relay.")
    println("Run `handoff --pair` to pair again.")
    println(DIVIDER)
}

private fun printStatus() {
    val config = DesktopConfigManager.loadConfig()
    val keyDir = File(System.getProperty("user.home"), ".handoff/keys")

    println(DIVIDER)
    println(" HandOff status")
    println(DIVIDER)
    println("Pair ID       : ${config.pairId}")
    println("Relay         : ${config.relayHost}")
    println("Protocol      : ${Protocol.VERSION}")
    println("Signing key   : ${if (File(keyDir, "device.priv").exists()) "present" else "not generated yet"}")
    println("Phone paired  : ${if (config.mobilePublicKey != null) "yes (${config.mobileDeviceId})" else "no"}")
    println("Relay reachable: ${probeRelay(config.relayHost)}")
    if (config.mobilePublicKey == null) {
        println()
        println("The phone has not announced a signing key yet, so decisions cannot be verified.")
        println("Run `handoff --pair` and scan the code with the app.")
    }
    println(DIVIDER)
}

private fun probeRelay(host: String): String {
    val scheme = if (host.startsWith("localhost") || host.startsWith("127.")) "http" else "https"
    return runCatching {
        val connection = URI("$scheme://$host/health").toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 4_000
        connection.readTimeout = 4_000
        connection.requestMethod = "GET"
        val code = connection.responseCode
        connection.disconnect()
        if (code in 200..299) "yes" else "responded with HTTP $code"
    }.getOrElse { "no (${it.message})" }
}

// -------------------------------------------------------------------------------------------
// Built-in test fixtures
// -------------------------------------------------------------------------------------------

/**
 * The `--test-*` flags.
 *
 * These were five near-identical 60-line blocks that each rebuilt a request, a key store and a relay
 * client by hand. Describing them as data instead means a change to the request shape cannot be
 * applied to four of the five and missed on the last.
 */
private class TestScenario(
    val flags: List<String>,
    val headline: String,
    val permission: PermissionInfo,
    val risk: RiskInfo,
    val options: List<String>,
    val agent: AgentInfo,
    val question: QuestionPayload? = null,
    val plan: PlanPayload? = null
)

private object TestScenarios {

    private val CRITICAL_DIFF = """
        --- a/src/main/kotlin/auth/AuthRepository.kt
        +++ b/src/main/kotlin/auth/AuthRepository.kt
        @@ -14,6 +14,10 @@
         fun authenticate(token: String): Boolean {
        -    return legacyVerify(token)
        +    if (token.isBlank()) return false
        +    val verified = argon2Verify(token)
        +    auditLogger.logAuth(token, verified)
        +    return verified
         }
    """.trimIndent()

    private val all = listOf(
        TestScenario(
            flags = listOf("--test-question"),
            headline = "Interactive question",
            agent = AgentInfo(id = "antigravity", name = "Antigravity", version = "2.2.0"),
            permission = PermissionInfo(
                type = PermissionType.QUESTION,
                description = "Which database should we use for production?"
            ),
            risk = RiskInfo(level = RiskLevel.MEDIUM, reasons = listOf("Architectural dependency decision")),
            options = listOf("answer_question", "cancel"),
            question = QuestionPayload(
                question = "Which database should we use for production?",
                options = listOf(
                    "PostgreSQL with Prisma",
                    "Cloud Firestore with zero-trust rules",
                    "SQLite through Room"
                )
            )
        ),
        TestScenario(
            flags = listOf("--test-plan"),
            headline = "Plan review",
            agent = AgentInfo(id = "antigravity", name = "Antigravity", version = "2.2.0"),
            permission = PermissionInfo(type = PermissionType.PLAN, description = "Implementation plan review"),
            risk = RiskInfo(
                level = RiskLevel.HIGH,
                reasons = listOf("Database schema alterations", "Security infrastructure changes")
            ),
            options = listOf("proceed_plan", "deny"),
            plan = PlanPayload(
                title = "Auth and RBAC overhaul",
                summary = "Adds Argon2 hashing, short-lived JWTs in HttpOnly cookies, and role-based route guards.",
                userReviewRequired = listOf(
                    "Needs a Redis instance for the revoked-token list",
                    "Existing user sessions are invalidated on release"
                )
            )
        ),
        TestScenario(
            flags = listOf("--test-codex", "--test-patch"),
            headline = "Code patch",
            agent = AgentInfo(id = "codex", name = "Codex", version = "2026.1"),
            permission = PermissionInfo(
                type = PermissionType.PATCH,
                description = "Apply an authentication patch to AuthRepository.kt",
                target = "src/main/kotlin/auth/AuthRepository.kt",
                diff = CRITICAL_DIFF
            ),
            risk = RiskInfo(
                level = RiskLevel.HIGH,
                reasons = listOf("Modifies password verification", "Alters a security boundary")
            ),
            options = listOf("approve", "deny")
        ),
        TestScenario(
            flags = listOf("--test-cursor", "--test-migration"),
            headline = "Database migration",
            agent = AgentInfo(id = "cursor", name = "Cursor", version = "0.45.2"),
            permission = PermissionInfo(
                type = PermissionType.TERMINAL,
                command = "npx prisma migrate dev --name add_rbac_tables",
                description = "Run a production schema migration"
            ),
            risk = RiskInfo(
                level = RiskLevel.CRITICAL,
                reasons = listOf("Alters persistent database schema", "Takes exclusive migration locks")
            ),
            options = listOf("approve", "deny")
        ),
        TestScenario(
            flags = listOf("--test-request", "--test-antigravity", "--test-critical"),
            headline = "Critical destructive command",
            agent = AgentInfo(id = "antigravity", name = "Antigravity", version = "2.2.0"),
            permission = PermissionInfo(
                type = PermissionType.TERMINAL,
                command = "rm -rf / --no-preserve-root",
                description = "Delete the filesystem root recursively"
            ),
            risk = RiskInfo(
                level = RiskLevel.CRITICAL,
                reasons = listOf("Targets the system root", "Causes irreversible data loss")
            ),
            options = listOf("approve", "deny")
        )
    )

    fun match(args: Array<String>): TestScenario? =
        all.firstOrNull { scenario -> scenario.flags.any { args.contains(it) } }

    fun flagSummary(): List<Pair<String, String>> = all.map { it.flags.first() to it.headline }
}

private fun runScenario(scenario: TestScenario, args: Array<String>) {
    val pairIdIndex = args.indexOf("--pair-id")
    val pairId = if (pairIdIndex != -1 && pairIdIndex + 1 < args.size) {
        args[pairIdIndex + 1]
    } else {
        DesktopConfigManager.getPairId()
    }

    val cwd = System.getProperty("user.dir")
    val keyStore = KeyStoreManager(File(System.getProperty("user.home"), ".handoff/keys"))
    val keyPair = keyStore.getOrGenerateKeyPair()

    println(DIVIDER)
    println(" HandOff test: ${scenario.headline}")
    println(DIVIDER)
    println("Pair ID : $pairId")
    scenario.permission.command?.let { println("Command : $it") }
    println("Risk    : ${scenario.risk.level.uppercase()}")
    println("Waiting for a decision on your phone...")

    val decision = RelayClient(
        relayHost = DesktopConfigManager.getRelayHost(),
        pairId = pairId,
        pairSecret = DesktopConfigManager.getPairSecret(),
        keyStoreManager = keyStore,
        privateKey = keyPair.private
    ).use { client ->
        runBlocking {
            client.sendRequestAndWaitForDecision(
                RequestFactory.build(
                    pairId = pairId,
                    agent = scenario.agent,
                    permission = scenario.permission.copy(cwd = scenario.permission.cwd ?: cwd),
                    risk = scenario.risk,
                    options = scenario.options,
                    workspacePath = cwd,
                    question = scenario.question,
                    plan = scenario.plan
                )
            )
        }
    }

    println()
    if (decision == null) {
        println("No decision arrived before the request expired.")
        return
    }

    println("Decision : ${decision.decision} (${if (decision.isApproval()) "approved" else "not approved"})")
    decision.selectedOptions?.takeIf { it.isNotEmpty() }?.let { println("Selected : ${it.joinToString(", ")}") }
    decision.feedback?.takeIf { it.isNotBlank() }?.let { println("Feedback : $it") }
    println("Device   : ${decision.deviceId}")
    println("Issued   : ${decision.issuedAt}")
}

// -------------------------------------------------------------------------------------------
// Usage
// -------------------------------------------------------------------------------------------

private fun printUsage() {
    println(DIVIDER)
    println(" HandOff desktop CLI")
    println(DIVIDER)
    println("Usage: handoff [OPTION]")
    println()
    println("  --mcp                Run the MCP server on stdio (for AI agents)")
    println("  --pair               Show the pairing QR code and link")
    println("  --rotate-pair        Issue a new pair id and secret, invalidating paired phones")
    println("  --status             Show pairing, key and relay status")
    println("  --install            Add HandOff to your IDE's MCP configuration")
    println("  --exec <command>     Require phone approval before running a local command")
    println()
    println("Test fixtures (send a sample card to your phone):")
    TestScenarios.flagSummary().forEach { (flag, headline) ->
        println("  ${flag.padEnd(20)} $headline")
    }
    println()
    println("Environment:")
    println("  HANDOFF_PAIR_ID      Override the configured pair id")
    println("  HANDOFF_RELAY_HOST   Override the relay host")
    println("  HANDOFF_WORKSPACE    Override the detected workspace path")
    println("  HANDOFF_INSECURE=1   Accept unsigned decisions (pre-v2 phones only; not recommended)")
    println(DIVIDER)
}
