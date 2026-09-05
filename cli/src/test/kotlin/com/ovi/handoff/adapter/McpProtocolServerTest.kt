package com.ovi.handoff.adapter

import com.ovi.handoff.core.ApprovalOutcome
import com.ovi.handoff.core.PolicyEngine
import com.ovi.handoff.shared.model.AgentInfo
import com.ovi.handoff.shared.model.PermissionRequest
import com.ovi.handoff.shared.model.SessionAnnouncement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Protocol-level tests for the MCP server.
 *
 * Nothing covered here was testable before: the server was an object wired directly to a relay
 * socket and an on-disk key store, so exercising the handshake meant having both. Every case below
 * is one an IDE would otherwise surface only as "the MCP server failed to start".
 */
class McpProtocolServerTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** A gateway that answers without a network, recording what it was asked. */
    private class FakeGateway(
        override val isPhonePaired: Boolean = true,
        private val outcome: ApprovalOutcome = ApprovalOutcome.NotPaired,
        private val suspendOnRequest: Boolean = false
    ) : ApprovalGateway {
        override val isRelayConnected: Boolean = false
        override val phoneOnline: Boolean? = null
        override val pairId: String = "pair-test"
        override val relayHost: String = "relay.test"

        val requested = mutableListOf<PermissionRequest>()
        val cancelled = mutableListOf<String>()
        var announcements = 0

        override suspend fun request(request: PermissionRequest): ApprovalOutcome {
            requested += request
            if (suspendOnRequest) {
                kotlinx.coroutines.awaitCancellation()
            }
            return outcome
        }

        override suspend fun announce(announcement: SessionAnnouncement) {
            announcements++
        }

        override suspend fun cancel(requestId: String) {
            cancelled += requestId
        }

        override fun close() = Unit
    }

    private class Harness(gateway: ApprovalGateway) {
        val sink = ByteArrayOutputStream()
        private val transport = JsonRpcTransport(sink)

        val server = McpProtocolServer(
            transport = transport,
            gateway = gateway,
            // Points at a path that does not exist, so the engine falls back to asking, which is the
            // behaviour every approval test here depends on.
            policyEngine = PolicyEngine(File(System.getProperty("java.io.tmpdir"), "handoff-absent-policy.yml")),
            // Unconfined so a launched approval runs to completion inline: the fake gateway never
            // actually suspends, so assertions can be made straight after handleLine returns.
            scope = CoroutineScope(Dispatchers.Unconfined),
            defaultWorkspace = File(System.getProperty("java.io.tmpdir"), "handoff-ws").absolutePath,
            initialAgent = AgentInfo(id = "test", name = "Test Agent", version = "1")
        )

        fun frames(): List<JsonObject> {
            transport.flush()
            return sink.toString(Charsets.UTF_8).lines()
                .filter { it.isNotBlank() }
                .map { Json.parseToJsonElement(it).jsonObject }
        }
    }

    private suspend fun McpProtocolServer.initialize(
        protocolVersion: String = McpProtocolServer.LATEST_PROTOCOL,
        capabilities: String = "{}"
    ) {
        handleLine(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"$protocolVersion",""" +
                """"capabilities":$capabilities,"clientInfo":{"name":"Cursor","version":"0.45"}}}"""
        )
        handleLine("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")
    }

    private fun List<JsonObject>.replyTo(id: Int): JsonObject? =
        firstOrNull { it["id"]?.jsonPrimitive?.contentOrNull == id.toString() }

    // ---------------------------------------------------------------------------------------
    // Handshake
    // ---------------------------------------------------------------------------------------

    @Test
    fun echoesTheProtocolVersionTheClientAskedFor() = runTest {
        // The server used to answer 2024-11-05 no matter what, which a client speaking a newer
        // revision is entitled to read as a refusal.
        for (version in McpProtocolServer.SUPPORTED_PROTOCOLS) {
            val harness = Harness(FakeGateway())
            harness.server.initialize(protocolVersion = version)

            val result = harness.frames().replyTo(1)?.get("result")?.jsonObject
            assertEquals(version, result?.get("protocolVersion")?.jsonPrimitive?.contentOrNull)
        }
    }

    @Test
    fun answersWithTheNewestSupportedVersionForAnUnknownRequest() = runTest {
        val harness = Harness(FakeGateway())
        harness.server.initialize(protocolVersion = "1999-01-01")

        val result = harness.frames().replyTo(1)?.get("result")?.jsonObject
        assertEquals(
            McpProtocolServer.LATEST_PROTOCOL,
            result?.get("protocolVersion")?.jsonPrimitive?.contentOrNull
        )
    }

    @Test
    fun advertisesItselfInServerInfo() = runTest {
        val harness = Harness(FakeGateway())
        harness.server.initialize()

        val info = harness.frames().replyTo(1)?.get("result")?.jsonObject?.get("serverInfo")?.jsonObject
        assertEquals("handoff", info?.get("name")?.jsonPrimitive?.contentOrNull)
    }

    // ---------------------------------------------------------------------------------------
    // Server-initiated requests
    // ---------------------------------------------------------------------------------------

    @Test
    fun doesNotAskForRootsWhenTheClientNeverDeclaredTheCapability() = runTest {
        val harness = Harness(FakeGateway())
        harness.server.initialize(capabilities = "{}")

        val rootsRequests = harness.frames().filter {
            it["method"]?.jsonPrimitive?.contentOrNull == "roots/list"
        }
        assertTrue(rootsRequests.isEmpty(), "roots/list must not be sent to a client without the capability")
    }

    @Test
    fun asksForRootsOnlyWhenDeclared() = runTest {
        val harness = Harness(FakeGateway())
        harness.server.initialize(capabilities = """{"roots":{"listChanged":true}}""")

        val rootsRequests = harness.frames().filter {
            it["method"]?.jsonPrimitive?.contentOrNull == "roots/list"
        }
        assertEquals(1, rootsRequests.size)
    }

    @Test
    fun givesEachServerRequestItsOwnId() = runTest {
        // JSON-RPC forbids reusing an id while an earlier request is outstanding; a fixed id meant
        // two roots/list requests were indistinguishable to the client.
        val harness = Harness(FakeGateway())
        harness.server.initialize(capabilities = """{"roots":{"listChanged":true}}""")
        harness.server.handleLine("""{"jsonrpc":"2.0","method":"notifications/roots/list_changed"}""")

        val ids = harness.frames()
            .filter { it["method"]?.jsonPrimitive?.contentOrNull == "roots/list" }
            .mapNotNull { it["id"]?.jsonPrimitive?.contentOrNull }

        assertEquals(2, ids.size)
        assertEquals(2, ids.toSet().size, "each outstanding server request needs a distinct id")
    }

    // ---------------------------------------------------------------------------------------
    // Tools
    // ---------------------------------------------------------------------------------------

    @Test
    fun listsAllFourTools() = runTest {
        val harness = Harness(FakeGateway())
        harness.server.initialize()
        harness.server.handleLine("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")

        val names = harness.frames().replyTo(2)
            ?.get("result")?.jsonObject?.get("tools")?.jsonArray
            ?.map { it.jsonObject["name"]!!.jsonPrimitive.content }
            .orEmpty()

        assertEquals(
            listOf(McpTools.APPROVE, McpTools.ASK_QUESTION, McpTools.REQUEST_PLAN, McpTools.STATUS).sorted(),
            names.sorted()
        )
    }

    @Test
    fun everyToolDeclaresAnObjectInputSchema() = runTest {
        val harness = Harness(FakeGateway())
        harness.server.initialize()
        harness.server.handleLine("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")

        val tools = harness.frames().replyTo(2)!!["result"]!!.jsonObject["tools"]!!.jsonArray
        for (tool in tools) {
            val schema = tool.jsonObject["inputSchema"]?.jsonObject
            assertNotNull(schema, "${tool.jsonObject["name"]} has no inputSchema")
            assertEquals("object", schema["type"]?.jsonPrimitive?.contentOrNull)
        }
    }

    @Test
    fun refusesToolCallsBeforeTheHandshakeCompletes() = runTest {
        val harness = Harness(FakeGateway())
        harness.server.handleLine(
            """{"jsonrpc":"2.0","id":9,"method":"tools/call","params":{"name":"handoff_status","arguments":{}}}"""
        )

        val error = harness.frames().replyTo(9)?.get("error")?.jsonObject
        assertNotNull(error)
        assertEquals(JsonRpcError.INVALID_REQUEST, error["code"]!!.jsonPrimitive.int)
    }

    @Test
    fun reportsAnUnknownMethodRatherThanIgnoringIt() = runTest {
        val harness = Harness(FakeGateway())
        harness.server.initialize()
        harness.server.handleLine("""{"jsonrpc":"2.0","id":3,"method":"does/not/exist"}""")

        val error = harness.frames().replyTo(3)?.get("error")?.jsonObject
        assertEquals(JsonRpcError.METHOD_NOT_FOUND, error?.get("code")?.jsonPrimitive?.int)
    }

    @Test
    fun reportsAnUnknownToolName() = runTest {
        val harness = Harness(FakeGateway())
        harness.server.initialize()
        harness.server.handleLine(
            """{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"nope","arguments":{}}}"""
        )

        val error = harness.frames().replyTo(4)?.get("error")?.jsonObject
        assertEquals(JsonRpcError.METHOD_NOT_FOUND, error?.get("code")?.jsonPrimitive?.int)
    }

    @Test
    fun ignoresANonJsonLineWithoutDying() = runTest {
        val harness = Harness(FakeGateway())
        harness.server.handleLine("this is not json")
        harness.server.handleLine("")
        harness.server.initialize()

        assertNotNull(harness.frames().replyTo(1), "the server must keep serving after junk input")
    }

    // ---------------------------------------------------------------------------------------
    // Status
    // ---------------------------------------------------------------------------------------

    @Test
    fun statusTellsAnUnpairedUserWhatToRun() = runTest {
        val harness = Harness(FakeGateway(isPhonePaired = false))
        harness.server.initialize()
        harness.server.handleLine(
            """{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"handoff_status","arguments":{}}}"""
        )

        val content = harness.frames().replyTo(5)!!["result"]!!.jsonObject["content"]!!.jsonArray
        val prose = content.first().jsonObject["text"]!!.jsonPrimitive.content

        assertTrue(prose.contains("NOT READY"), "an unpaired desktop is not ready")
        assertTrue(prose.contains("handoff --pair"), "the status must name the command that fixes it")
    }

    @Test
    fun statusReportsTheNegotiatedProtocolAndTheDetectedClient() = runTest {
        val harness = Harness(FakeGateway())
        harness.server.initialize(protocolVersion = "2024-11-05")
        harness.server.handleLine(
            """{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"handoff_status","arguments":{}}}"""
        )

        val content = harness.frames().replyTo(5)!!["result"]!!.jsonObject["content"]!!.jsonArray
        val machine = json.parseToJsonElement(content[1].jsonObject["text"]!!.jsonPrimitive.content).jsonObject

        assertEquals("2024-11-05", machine["mcpProtocolVersion"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Cursor", machine["connectedIde"]?.jsonPrimitive?.contentOrNull)
    }

    // ---------------------------------------------------------------------------------------
    // Approvals
    // ---------------------------------------------------------------------------------------

    @Test
    fun anUnpairedApprovalFailsImmediatelyAndSaysHowToFixIt() = runTest {
        val harness = Harness(FakeGateway(isPhonePaired = false, outcome = ApprovalOutcome.NotPaired))
        harness.server.initialize()
        harness.server.handleLine(
            """{"jsonrpc":"2.0","id":6,"method":"tools/call",""" +
                """"params":{"name":"handoff_approve","arguments":{"command":"rm -rf /"}}}"""
        )

        val result = harness.frames().replyTo(6)?.get("result")?.jsonObject
        assertNotNull(result, "an approval must always produce exactly one reply")
        assertEquals(true, result["isError"]?.jsonPrimitive?.booleanOrNull)

        val text = result["content"]!!.jsonArray.first().jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(text.contains("handoff --pair"))
        assertTrue(text.contains("nothing was authorized", ignoreCase = true))
    }

    @Test
    fun aDeniedApprovalIsReportedAsAnError() = runTest {
        val gateway = FakeGateway(outcome = ApprovalOutcome.PhoneUnreachable)
        val harness = Harness(gateway)
        harness.server.initialize()
        harness.server.handleLine(
            """{"jsonrpc":"2.0","id":7,"method":"tools/call",""" +
                """"params":{"name":"handoff_approve","arguments":{"command":"ls"}}}"""
        )

        val result = harness.frames().replyTo(7)!!["result"]!!.jsonObject
        assertEquals(true, result["isError"]?.jsonPrimitive?.booleanOrNull)
    }

    /**
     * The concurrency bug that made an approval card untrustworthy: `workspacePath` was a field
     * rewritten by every call, so a second call from another folder could relabel the first.
     */
    @Test
    fun eachCallUsesItsOwnWorkingDirectory() = runTest {
        val gateway = FakeGateway()
        val harness = Harness(gateway)
        harness.server.initialize()

        val first = File(System.getProperty("java.io.tmpdir"), "handoff-alpha").absolutePath
        val second = File(System.getProperty("java.io.tmpdir"), "handoff-beta").absolutePath

        harness.server.handleLine(
            """{"jsonrpc":"2.0","id":10,"method":"tools/call","params":{"name":"handoff_approve",""" +
                """"arguments":{"command":"a","cwd":${quote(first)}}}}"""
        )
        harness.server.handleLine(
            """{"jsonrpc":"2.0","id":11,"method":"tools/call","params":{"name":"handoff_approve",""" +
                """"arguments":{"command":"b","cwd":${quote(second)}}}}"""
        )

        assertEquals(2, gateway.requested.size)
        val byCommand = gateway.requested.associateBy { it.permission.command }
        assertEquals(first, byCommand["a"]?.permission?.cwd)
        assertEquals(second, byCommand["b"]?.permission?.cwd)
    }

    @Test
    fun cancellingACallTellsThePhoneToDropIt() = runTest {
        // Cancelling only locally left an approval card on the phone for a call the IDE had already
        // abandoned, and the relay kept replaying it.
        val gateway = FakeGateway(suspendOnRequest = true)
        val harness = Harness(gateway)
        harness.server.initialize()
        harness.server.handleLine(
            """{"jsonrpc":"2.0","id":12,"method":"tools/call",""" +
                """"params":{"name":"handoff_approve","arguments":{"command":"sleep"}}}"""
        )
        harness.server.handleLine(
            """{"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":12}}"""
        )

        val sentId = gateway.requested.firstOrNull()?.id
        assertNotNull(sentId)
        assertEquals(listOf(sentId), gateway.cancelled)
    }

    @Test
    fun aToolCallWithNoNameIsAnInvalidParams() = runTest {
        val harness = Harness(FakeGateway())
        harness.server.initialize()
        harness.server.handleLine("""{"jsonrpc":"2.0","id":13,"method":"tools/call","params":{}}""")

        val error = harness.frames().replyTo(13)?.get("error")?.jsonObject
        assertEquals(JsonRpcError.INVALID_PARAMS, error?.get("code")?.jsonPrimitive?.int)
    }

    @Test
    fun pingIsAnsweredWithoutTouchingTheRelay() = runTest {
        val gateway = FakeGateway()
        val harness = Harness(gateway)
        harness.server.initialize()
        val before = gateway.requested.size

        harness.server.handleLine("""{"jsonrpc":"2.0","id":14,"method":"ping"}""")

        assertNotNull(harness.frames().replyTo(14)?.get("result"))
        assertEquals(before, gateway.requested.size)
        assertNull(harness.frames().replyTo(14)?.get("error"))
    }

    private fun quote(value: String): String = JsonPrimitive(value).toString()
}
