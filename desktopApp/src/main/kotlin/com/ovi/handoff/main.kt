package com.ovi.handoff

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.ovi.handoff.adapter.McpServer
import com.ovi.handoff.core.RelayClient
import com.ovi.handoff.shared.model.AgentInfo
import com.ovi.handoff.shared.model.PermissionInfo
import com.ovi.handoff.shared.model.PermissionRequest
import com.ovi.handoff.shared.model.RiskInfo
import com.ovi.handoff.shared.model.SessionInfo
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.UUID

fun main(args: Array<String>) {
    when {
        args.contains("--cli") || args.contains("--mcp") -> {
            println("Starting AgentApprove MCP Server on stdio...")
            McpServer.run()
        }
        args.contains("--pair") -> {
            val pairId = "pair-" + UUID.randomUUID().toString().take(8)
            println("==================================================")
            println("       AgentApprove Desktop Pairing Mode          ")
            println("==================================================")
            println("Pair ID: $pairId")
            println("Relay  : agentapprove-relay.ismamhasanovi.workers.dev")
            println()
            println("Enter this code manually on your phone:")
            println(">>>  $pairId  <<<")
            println()
            println("Or copy this pairing URL:")
            println("agentapprove://pair?pairId=$pairId&host=agentapprove-relay.ismamhasanovi.workers.dev")
            println("==================================================")
            println("Keep this terminal open, or use this pair ID for requests.")
        }
        args.contains("--test-request") -> {
            val pairIndex = args.indexOf("--pair-id")
            val pairId = if (pairIndex != -1 && pairIndex + 1 < args.size) {
                args[pairIndex + 1]
            } else {
                "test-pair"
            }

            println("==================================================")
            println(" Dispatching Test Permission Request to Mobile   ")
            println("==================================================")
            println("Target Pair ID : $pairId")
            println("Command        : rm -rf / --no-preserve-root")
            println("Risk Level     : CRITICAL")
            println("Connecting to Cloudflare Relay...")

            val client = RelayClient()
            val request = PermissionRequest(
                id = UUID.randomUUID().toString(),
                protocolVersion = "1.0",
                agent = AgentInfo(
                    id = "antigravity",
                    name = "Antigravity Assistant",
                    version = "1.0.0"
                ),
                session = SessionInfo(
                    id = pairId,
                    project = "HandOff",
                    workspace = "c:\\Users\\USERAS\\Desktop\\HandOff\\handoff"
                ),
                permission = PermissionInfo(
                    type = "shell",
                    command = "rm -rf / --no-preserve-root",
                    description = "Dangerous operation requested by autonomous agent"
                ),
                risk = RiskInfo(
                    level = "critical",
                    reasons = listOf(
                        "Recursive deletion of filesystem root",
                        "High blast-radius terminal action"
                    )
                ),
                options = listOf("approve", "deny"),
                createdAt = Instant.now().toString(),
                expiresAt = Instant.now().plusSeconds(300).toString()
            )

            runBlocking {
                println("Sending request and waiting for mobile decision on your Pixel 9...")
                val decision = client.sendRequestAndWaitForDecision(pairId, request)
                println()
                if (decision != null) {
                    println("🎉 Decision Received from Mobile Phone!")
                    println("Decision : ${decision.decision}")
                    println("Request  : ${decision.requestId}")
                    println("Device   : ${decision.deviceId}")
                    println("IssuedAt : ${decision.issuedAt}")
                } else {
                    println("❌ Request timed out or was cancelled.")
                }
            }
        }
        else -> {
            application {
                Window(
                    onCloseRequest = ::exitApplication,
                    title = "Handoff",
                ) {
                    App()
                }
            }
        }
    }
}