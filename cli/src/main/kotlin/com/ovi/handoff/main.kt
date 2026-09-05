package com.ovi.handoff

import com.ovi.handoff.adapter.McpServer
import com.ovi.handoff.core.CommandWrapper
import com.ovi.handoff.core.DesktopConfigManager
import com.ovi.handoff.core.Doctor
import com.ovi.handoff.core.KeyStoreManager
import com.ovi.handoff.core.McpAutoInstaller
import com.ovi.handoff.core.PairingFlow
import com.ovi.handoff.core.RelayClient
import com.ovi.handoff.core.RelayEndpoint
import com.ovi.handoff.core.RequestFactory
import com.ovi.handoff.core.decisionOrNull
import com.ovi.handoff.core.explain
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

private const val DIVIDER = "=================================================="

public fun main(args: Array<String>) {
    when {
        args.contains("--mcp") || args.contains("--cli") -> {
            System.err.println("[Handoff] MCP server starting on stdio...")
            McpServer.run()
        }

        args.contains("--pair") -> PairingFlow.run()
        args.contains("--rotate-pair") -> rotatePairing()
        args.contains("--status") -> printStatus()
        args.contains("--doctor") -> Doctor.run()
        args.contains("--install-path") -> installCliPath()
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
    println("Pair ID        : ${config.pairId}")
    println("Relay          : ${config.relayHost}")
    println("Protocol       : ${Protocol.VERSION}")
    println("Signing key    : ${if (File(keyDir, "device.priv").exists()) "present" else "not generated yet"}")
    println("Phone paired   : ${if (config.mobilePublicKey != null) "yes (${config.mobileDeviceId})" else "no"}")
    println("Relay reachable: ${describeHealth(RelayEndpoint.health(config.relayHost))}")
    if (config.mobilePublicKey == null) {
        println()
        println("The phone has not announced a signing key yet, so decisions cannot be verified.")
        println("Run `handoff --pair` and scan the code with the app.")
    }
    println()
    println("Run `handoff --doctor` for a full diagnosis.")
    println(DIVIDER)
}

private fun describeHealth(health: RelayEndpoint.Health): String = when (health) {
    is RelayEndpoint.Health.Up -> "yes (protocol ${health.protocol ?: "unknown"})"
    is RelayEndpoint.Health.Unexpected -> "responded with HTTP ${health.statusCode}"
    is RelayEndpoint.Health.Down -> "no (${health.reason})"
}

private fun installCliPath() {
    println(DIVIDER)
    println(" HandOff CLI Global Path Setup")
    println(DIVIDER)
    val isWindows = System.getProperty("os.name")?.lowercase()?.contains("windows") == true
    if (!isWindows) {
        println("On macOS/Linux, create a symlink to handoff:")
        println("  sudo ln -sf \"$(pwd)/handoff\" /usr/local/bin/handoff")
        println(DIVIDER)
        return
    }

    val localAppData = System.getenv("LOCALAPPDATA") ?: run {
        println("Error: LOCALAPPDATA environment variable not found.")
        println(DIVIDER)
        return
    }
    val targetDir = File(localAppData, "Microsoft/WindowsApps")
    if (!targetDir.exists()) targetDir.mkdirs()
    val shimFile = File(targetDir, "handoff.cmd")

    // Find handoff.bat by checking current working directory, codeSource location, and their ancestors
    var handoffBat: File? = null
    var candidateDir: File? = File(".").canonicalFile
    while (candidateDir != null) {
        val candidate = File(candidateDir, "handoff.bat")
        if (candidate.exists()) {
            handoffBat = candidate
            break
        }
        candidateDir = candidateDir.parentFile
    }

    if (handoffBat == null) {
        val codeSource = runCatching { File(::main.javaClass.protectionDomain.codeSource.location.toURI()) }.getOrNull()
        candidateDir = if (codeSource?.isDirectory == true) codeSource else codeSource?.parentFile
        while (candidateDir != null) {
            val candidate = File(candidateDir, "handoff.bat")
            if (candidate.exists()) {
                handoffBat = candidate
                break
            }
            candidateDir = candidateDir.parentFile
        }
    }

    if (handoffBat == null || !handoffBat.exists()) {
        println("Error: Could not locate 'handoff.bat'. Please run this command from within the HandOff repository.")
        println(DIVIDER)
        return
    }

    shimFile.writeText(
        """
        @echo off
        call "${handoffBat.absolutePath}" %*
        """.trimIndent() + "\r\n"
    )
    println("SUCCESS: HandOff CLI registered in WindowsApps PATH:")
    println("  ${shimFile.absolutePath}")
    println()
    println("You can now run 'handoff' directly from ANY terminal:")
    println("  handoff --pair")
    println("  handoff --status")
    println("  handoff --doctor")
    println(DIVIDER)
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

    val outcome = RelayClient(
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
    val decision = outcome.decisionOrNull()
    if (decision == null) {
        // Says which failure it was and what fixes it, rather than one timeout message for all of them.
        println(outcome.explain())
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
    println("  --pair               Show the pairing code and wait for your phone to scan it")
    println("  --rotate-pair        Issue a new pair id and secret, invalidating paired phones")
    println("  --status             Show pairing, key and relay status")
    println("  --doctor             Diagnose the whole setup and say what to fix")
    println("  --install-path       Install 'handoff' command globally into user PATH (Windows / Linux / macOS)")
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
    println("  HANDOFF_LAUNCHER     Command an IDE should run to start the MCP server")
    println("  HANDOFF_INSECURE=1   Accept unsigned decisions (pre-v2 phones only; not recommended)")
    println(DIVIDER)
}
