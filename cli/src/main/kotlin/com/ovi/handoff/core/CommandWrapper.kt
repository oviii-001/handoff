package com.ovi.handoff.core

import com.ovi.handoff.shared.model.AgentInfo
import com.ovi.handoff.shared.model.PermissionInfo
import com.ovi.handoff.shared.model.PermissionRequest
import com.ovi.handoff.shared.model.RiskInfo
import com.ovi.handoff.shared.model.SessionInfo
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.system.exitProcess

object CommandWrapper {
    
    fun execute(commandArgs: List<String>) {
        if (commandArgs.isEmpty()) {
            System.err.println("Error: No command provided to --exec")
            exitProcess(1)
        }
        
        val commandString = commandArgs.joinToString(" ")
        val pairId = DesktopConfigManager.getPairId()
        
        println("==================================================")
        println(" Handoff Terminal Authorization (Intercepted)     ")
        println("==================================================")
        println("Command : $commandString")
        println("Pair ID : $pairId")
        println("Status  : Waiting for authorization on your phone...")
        
        val keyStore = KeyStoreManager(File(System.getProperty("user.home"), ".handoff/keys"))
        val keyPair = keyStore.getOrGenerateKeyPair()
        val client = RelayClient(keyStoreManager = keyStore, privateKey = keyPair.private)
        
        // Construct Permission Request
        val request = PermissionRequest(
            id = UUID.randomUUID().toString(),
            protocolVersion = "1.0",
            agent = AgentInfo(
                id = "terminal-wrapper",
                name = "Terminal User",
                version = "1.0.0"
            ),
            session = SessionInfo(
                id = pairId,
                project = "Local Shell",
                workspace = System.getProperty("user.dir")
            ),
            permission = PermissionInfo(
                type = "terminal",
                command = commandString,
                description = "Execute local terminal command",
                cwd = System.getProperty("user.dir")
            ),
            risk = RiskInfo(
                level = determineRiskLevel(commandString),
                reasons = listOf("Manual terminal execution intercept")
            ),
            options = listOf("approve", "deny"),
            createdAt = Instant.now().toString(),
            expiresAt = Instant.now().plusSeconds(300).toString()
        )
        
        val decision = runBlocking {
            client.sendRequestAndWaitForDecision(pairId, request)
        }
        
        if (decision == null) {
            System.err.println("❌ Request timed out or was cancelled.")
            exitProcess(1)
        }
        
        if (decision.decision == "approve") {
            println("✅ Request APPROVED. Executing command...")
            println("==================================================")
            executeLocally(commandArgs)
        } else {
            System.err.println("❌ Request DENIED by user.")
            exitProcess(1)
        }
    }
    
    private fun executeLocally(commandArgs: List<String>) {
        try {
            // Need to wrap in bash or cmd depending on OS for shell builtins
            val os = System.getProperty("os.name").lowercase()
            val finalArgs = if (os.contains("win")) {
                listOf("cmd.exe", "/c") + commandArgs
            } else {
                listOf("bash", "-c", commandArgs.joinToString(" "))
            }
            
            val processBuilder = ProcessBuilder(finalArgs)
            processBuilder.inheritIO() // Stream stdout/stderr directly to terminal
            val process = processBuilder.start()
            val exitCode = process.waitFor()
            exitProcess(exitCode)
        } catch (e: Exception) {
            System.err.println("Error executing command: ${e.message}")
            exitProcess(1)
        }
    }
    
    private fun determineRiskLevel(cmd: String): String {
        val criticalPatterns = listOf("rm -rf", "mkfs", "dd", "> /dev/sda", "drop table", "shutdown")
        val highPatterns = listOf("rm ", "del ", "chmod", "chown", "docker run")
        
        return when {
            criticalPatterns.any { cmd.contains(it, ignoreCase = true) } -> "critical"
            highPatterns.any { cmd.contains(it, ignoreCase = true) } -> "high"
            else -> "medium"
        }
    }
}
