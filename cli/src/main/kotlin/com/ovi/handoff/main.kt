package com.ovi.handoff

import com.ovi.handoff.adapter.McpServer
import com.ovi.handoff.core.DesktopConfigManager
import com.ovi.handoff.core.RelayClient
import com.ovi.handoff.shared.model.AgentInfo
import com.ovi.handoff.shared.model.PermissionInfo
import com.ovi.handoff.shared.model.PermissionRequest
import com.ovi.handoff.shared.model.RiskInfo
import com.ovi.handoff.shared.model.SessionInfo
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.UUID
import java.util.Base64
import java.io.File
import com.ovi.handoff.core.KeyStoreManager
fun main(args: Array<String>) {
    when {
        args.contains("--cli") || args.contains("--mcp") -> {
            System.err.println("[Handoff] Starting MCP Server on stdio...")
            McpServer.run()
        }
        args.contains("--pair") -> {
            val pairId = DesktopConfigManager.getPairId()
            val relay = DesktopConfigManager.getRelayHost()
            
            val keyStore = KeyStoreManager(File(System.getProperty("user.home"), ".handoff/keys"))
            val keyPair = keyStore.getOrGenerateKeyPair()
            val encodedPubKey = Base64.getUrlEncoder().withoutPadding().encodeToString(keyPair.public.encoded)

            println("==================================================")
            println("         Handoff Desktop Pairing Mode             ")
            println("==================================================")
            println("Pair ID: $pairId")
            println("Relay  : $relay")
            println()
            
            val pairUrl = "handoff://pair?pairId=$pairId&host=$relay&pubKey=$encodedPubKey"
            
            println("Scan this QR Code with your HandOff mobile app:")
            com.ovi.handoff.core.TerminalQrGenerator.printQrCode(pairUrl)
            
            println("Or enter this code manually on your phone:")
            println(">>>  $pairId  <<<")
            println("==================================================")
            println("Keep this terminal open, or use this pair ID for requests.")
        }
        args.contains("--test-question") -> {
            val pairIndex = args.indexOf("--pair-id")
            val pairId = if (pairIndex != -1 && pairIndex + 1 < args.size) args[pairIndex + 1] else DesktopConfigManager.getPairId()

            println("==================================================")
            println(" Dispatching Antigravity ask_question to Mobile  ")
            println("==================================================")
            println("Target Pair ID : $pairId")
            println("Question       : Which database architecture should we use for production?")
            println("Connecting to Cloudflare Relay...")

            val keyStore = KeyStoreManager(File(System.getProperty("user.home"), ".handoff/keys"))
            val keyPair = keyStore.getOrGenerateKeyPair()
            val client = RelayClient(keyStoreManager = keyStore, privateKey = keyPair.private)
            val request = PermissionRequest(
                id = UUID.randomUUID().toString(),
                protocolVersion = "1.0",
                agent = AgentInfo(id = "antigravity", name = "Antigravity", version = "2.2.0"),
                session = SessionInfo(id = pairId, project = "HandOff", workspace = "handoff"),
                permission = PermissionInfo(type = "question", description = "Architecture clarification"),
                risk = RiskInfo(level = "medium", reasons = listOf("Architectural dependency decision")),
                options = listOf("answer_question", "cancel"),
                createdAt = Instant.now().toString(),
                expiresAt = Instant.now().plusSeconds(300).toString(),
                question = com.ovi.handoff.shared.model.QuestionPayload(
                    question = "Which database architecture should we use for production?",
                    options = listOf(
                        "PostgreSQL + Prisma ORM (Recommended)",
                        "Cloud Firestore with Zero-Trust Rules",
                        "SQLite with Room KMP Multiplatform"
                    ),
                    isMultiSelect = false
                )
            )

            runBlocking {
                println("Waiting for user decision on mobile phone...")
                val decision = client.sendRequestAndWaitForDecision(pairId, request)
                println()
                if (decision != null) {
                    println("🎉 Decision Received from Mobile Phone!")
                    println("Decision        : ${decision.decision}")
                    println("Selected Options: ${decision.selectedOptions?.joinToString(", ") ?: "None"}")
                    println("User Feedback   : ${decision.feedback ?: "None"}")
                    println("Device ID       : ${decision.deviceId}")
                } else {
                    println("❌ Request timed out or was cancelled.")
                }
            }
        }
        args.contains("--test-plan") -> {
            val pairIndex = args.indexOf("--pair-id")
            val pairId = if (pairIndex != -1 && pairIndex + 1 < args.size) args[pairIndex + 1] else DesktopConfigManager.getPairId()

            println("==================================================")
            println(" Dispatching Antigravity Plan Review to Mobile   ")
            println("==================================================")
            println("Target Pair ID : $pairId")
            println("Plan Title     : Full-Stack Auth & RBAC Security Overhaul")
            println("Connecting to Cloudflare Relay...")

            val keyStore = KeyStoreManager(File(System.getProperty("user.home"), ".handoff/keys"))
            val keyPair = keyStore.getOrGenerateKeyPair()
            val client = RelayClient(keyStoreManager = keyStore, privateKey = keyPair.private)
            val request = PermissionRequest(
                id = UUID.randomUUID().toString(),
                protocolVersion = "1.0",
                agent = AgentInfo(id = "antigravity", name = "Antigravity", version = "2.2.0"),
                session = SessionInfo(id = pairId, project = "HandOff", workspace = "handoff"),
                permission = PermissionInfo(type = "plan", description = "Implementation plan review"),
                risk = RiskInfo(level = "high", reasons = listOf("Database schema alterations", "Security infrastructure changes")),
                options = listOf("proceed_plan", "deny"),
                createdAt = Instant.now().toString(),
                expiresAt = Instant.now().plusSeconds(300).toString(),
                plan = com.ovi.handoff.shared.model.PlanPayload(
                    title = "Full-Stack Auth & RBAC Security Overhaul",
                    summary = "Scaffolds Argon2 password hashing, short-lived JWTs in HttpOnly cookies, and role-based route guards across mobile and web.",
                    userReviewRequired = listOf(
                        "Requires Redis server instance for revoked token blacklist",
                        "Existing user sessions will be invalidated on release"
                    )
                )
            )

            runBlocking {
                println("Waiting for plan approval on mobile phone...")
                val decision = client.sendRequestAndWaitForDecision(pairId, request)
                println()
                if (decision != null) {
                    println("🎉 Decision Received from Mobile Phone!")
                    println("Decision      : ${decision.decision}")
                    println("User Feedback : ${decision.feedback ?: "None"}")
                    println("Device ID     : ${decision.deviceId}")
                } else {
                    println("❌ Request timed out or was cancelled.")
                }
            }
        }
        args.contains("--test-codex") -> {
            val pairIndex = args.indexOf("--pair-id")
            val pairId = if (pairIndex != -1 && pairIndex + 1 < args.size) args[pairIndex + 1] else DesktopConfigManager.getPairId()

            println("==================================================")
            println(" Dispatching Codex Code Patch to Mobile          ")
            println("==================================================")
            println("Target Pair ID : $pairId")
            println("File Target    : src/main/kotlin/auth/AuthRepository.kt")
            println("Connecting to Cloudflare Relay...")

            val keyStore = KeyStoreManager(File(System.getProperty("user.home"), ".handoff/keys"))
            val keyPair = keyStore.getOrGenerateKeyPair()
            val client = RelayClient(keyStoreManager = keyStore, privateKey = keyPair.private)
            val diffSnippet = """
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

            val request = PermissionRequest(
                id = UUID.randomUUID().toString(),
                protocolVersion = "1.0",
                agent = AgentInfo(id = "codex", name = "Codex", version = "2026.1"),
                session = SessionInfo(id = pairId, project = "HandOff", workspace = "handoff"),
                permission = PermissionInfo(
                    type = "patch",
                    description = "Apply authentication security patch to AuthRepository.kt",
                    target = "src/main/kotlin/auth/AuthRepository.kt",
                    diff = diffSnippet
                ),
                risk = RiskInfo(
                    level = "high",
                    reasons = listOf(
                        "Modifies cryptographic password verification",
                        "Alters core security boundaries"
                    )
                ),
                options = listOf("approve", "deny"),
                createdAt = Instant.now().toString(),
                expiresAt = Instant.now().plusSeconds(300).toString()
            )

            runBlocking {
                println("Waiting for Codex diff approval on mobile phone...")
                val decision = client.sendRequestAndWaitForDecision(pairId, request)
                println()
                if (decision != null) {
                    println("🎉 Decision Received from Mobile Phone!")
                    println("Decision : ${decision.decision}")
                    println("Feedback : ${decision.feedback ?: "None"}")
                    println("Device   : ${decision.deviceId}")
                } else {
                    println("❌ Request timed out or was cancelled.")
                }
            }
        }
        args.contains("--test-cursor") -> {
            val pairIndex = args.indexOf("--pair-id")
            val pairId = if (pairIndex != -1 && pairIndex + 1 < args.size) args[pairIndex + 1] else DesktopConfigManager.getPairId()

            println("==================================================")
            println(" Dispatching Cursor Action to Mobile              ")
            println("==================================================")
            println("Target Pair ID : $pairId")
            println("Command        : npx prisma migrate dev --name add_rbac_tables")
            println("Connecting to Cloudflare Relay...")

            val keyStore = KeyStoreManager(File(System.getProperty("user.home"), ".handoff/keys"))
            val keyPair = keyStore.getOrGenerateKeyPair()
            val client = RelayClient(keyStoreManager = keyStore, privateKey = keyPair.private)
            val request = PermissionRequest(
                id = UUID.randomUUID().toString(),
                protocolVersion = "1.0",
                agent = AgentInfo(id = "cursor", name = "Cursor", version = "0.45.2"),
                session = SessionInfo(id = pairId, project = "HandOff", workspace = "handoff"),
                permission = PermissionInfo(
                    type = "terminal",
                    command = "npx prisma migrate dev --name add_rbac_tables",
                    description = "Execute production database schema migration",
                    cwd = "c:\\Users\\USERAS\\Desktop\\HandOff\\handoff"
                ),
                risk = RiskInfo(
                    level = "critical",
                    reasons = listOf(
                        "Modifies persistent database schema tables",
                        "Requires exclusive schema migration locks"
                    )
                ),
                options = listOf("approve", "deny"),
                createdAt = Instant.now().toString(),
                expiresAt = Instant.now().plusSeconds(300).toString()
            )

            runBlocking {
                println("Waiting for Cursor action decision on mobile phone...")
                val decision = client.sendRequestAndWaitForDecision(pairId, request)
                println()
                if (decision != null) {
                    println("🎉 Decision Received from Mobile Phone!")
                    println("Decision : ${decision.decision}")
                    println("Feedback : ${decision.feedback ?: "None"}")
                    println("Device   : ${decision.deviceId}")
                } else {
                    println("❌ Request timed out or was cancelled.")
                }
            }
        }
        args.contains("--test-request") || args.contains("--test-antigravity") -> {
            val pairIndex = args.indexOf("--pair-id")
            val pairId = if (pairIndex != -1 && pairIndex + 1 < args.size) {
                args[pairIndex + 1]
            } else {
                DesktopConfigManager.getPairId()
            }

            println("==================================================")
            println(" Dispatching Antigravity Dangerous Command       ")
            println("==================================================")
            println("Target Pair ID : $pairId")
            println("Command        : rm -rf / --no-preserve-root")
            println("Risk Level     : CRITICAL")
            println("Connecting to Cloudflare Relay...")

            val keyStore = KeyStoreManager(File(System.getProperty("user.home"), ".handoff/keys"))
            val keyPair = keyStore.getOrGenerateKeyPair()
            val client = RelayClient(keyStoreManager = keyStore, privateKey = keyPair.private)
            val request = PermissionRequest(
                id = UUID.randomUUID().toString(),
                protocolVersion = "1.0",
                agent = AgentInfo(
                    id = "antigravity",
                    name = "Antigravity",
                    version = "2.2.0"
                ),
                session = SessionInfo(
                    id = pairId,
                    project = "HandOff",
                    workspace = "handoff"
                ),
                permission = PermissionInfo(
                    type = "terminal",
                    command = "rm -rf / --no-preserve-root",
                    description = "Delete filesystem root recursively",
                    cwd = "c:\\Users\\USERAS\\Desktop\\HandOff\\handoff"
                ),
                risk = RiskInfo(
                    level = "critical",
                    reasons = listOf(
                        "Command target touches the system root path",
                        "Execution causes catastrophic and irreversible data destruction"
                    )
                ),
                options = listOf("approve", "deny"),
                createdAt = Instant.now().toString(),
                expiresAt = Instant.now().plusSeconds(300).toString()
            )

            runBlocking {
                println("Waiting for user decision on mobile phone...")
                val decision = client.sendRequestAndWaitForDecision(pairId, request)
                println()
                if (decision != null) {
                    println("🎉 Decision Received from Mobile Phone!")
                    println("Decision : ${decision.decision}")
                    println("Feedback : ${decision.feedback ?: "None"}")
                    println("Request  : ${decision.requestId}")
                    println("Device   : ${decision.deviceId}")
                    println("IssuedAt : ${decision.issuedAt}")
                } else {
                    println("❌ Request timed out or was cancelled.")
                }
            }
        }
        args.contains("--install") -> {
            println("==================================================")
            println("           Handoff MCP Auto-Installer             ")
            println("==================================================")
            com.ovi.handoff.core.McpAutoInstaller.install()
        }
        args.contains("--exec") -> {
            val execIndex = args.indexOf("--exec")
            if (execIndex + 1 < args.size) {
                val commandArgs = args.slice(execIndex + 1 until args.size)
                com.ovi.handoff.core.CommandWrapper.execute(commandArgs)
            } else {
                System.err.println("Error: Missing command after --exec")
            }
        }
        else -> {
            println("==================================================")
            println("               Handoff Desktop CLI                ")
            println("==================================================")
            println("Usage: handoff [OPTIONS]")
            println()
            println("Options:")
            println("  --mcp               Run the MCP Server on stdio (for AI Agents)")
            println("  --pair              Display pairing QR code and pair ID")
            println("  --install           Auto-install MCP config into IDEs (Claude/Cursor)")
            println("  --exec <cmd>        Intercept and authorize a terminal command")
            println("  --test-question     Send a test question to the paired phone")
            println("  --test-plan         Send a test implementation plan for review")
            println("  --test-codex        Send a test code patch for approval")
            println("  --test-cursor       Send a test terminal command for approval")
            println("  --test-request      Send a CRITICAL rm -rf terminal command")
            println("==================================================")
        }
    }
}