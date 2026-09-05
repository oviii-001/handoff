package com.ovi.handoff.adapter

import com.ovi.handoff.core.Log
import com.ovi.handoff.core.PolicyEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.text.Charsets

/**
 * Process entry point for `handoff --mcp`.
 *
 * Deliberately thin: everything worth testing lives in [McpProtocolServer], and this file exists
 * only to wire the real transport, relay and policy engine to it and to own process lifetime.
 */
public object McpServer {

    public fun run(): Unit = runBlocking {
        // Claimed before anything else can write: stdout is the protocol, and one stray line from
        // any library on the classpath corrupts every frame after it.
        val protocolStream = StdioGuard.claimStdout()
        Log.enableFileLogging()

        val transport = JsonRpcTransport(protocolStream)
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val gateway = RelayApprovalGateway()

        val server = McpProtocolServer(
            transport = transport,
            gateway = gateway,
            policyEngine = PolicyEngine(File(System.getProperty("user.home"), ".handoff/policy.yml")),
            scope = scope,
            defaultWorkspace = McpProtocolServer.defaultWorkspace(),
            initialAgent = McpProtocolServer.detectAgentFromEnvironment()
        )

        val reader = BufferedReader(InputStreamReader(System.`in`, Charsets.UTF_8))
        try {
            server.serve(reader)
        } finally {
            // Ordered: stop accepting work, drop the socket, then flush whatever was already
            // written, so a reply produced during shutdown still reaches the client.
            scope.cancel()
            gateway.close()
            transport.flush()
        }
    }
}
