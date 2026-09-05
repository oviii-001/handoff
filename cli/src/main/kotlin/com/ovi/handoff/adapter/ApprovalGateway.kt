package com.ovi.handoff.adapter

import com.ovi.handoff.core.ApprovalOutcome
import com.ovi.handoff.core.DesktopConfigManager
import com.ovi.handoff.core.KeyStoreManager
import com.ovi.handoff.core.Log
import com.ovi.handoff.core.RelayClient
import com.ovi.handoff.shared.model.PermissionRequest
import com.ovi.handoff.shared.model.SessionAnnouncement
import java.io.File

/**
 * Everything the MCP layer needs from the world beyond it.
 *
 * The protocol server used to reach straight into `RelayClient`, `KeyStoreManager` and
 * `DesktopConfigManager` singletons, which meant a test of "does this server negotiate the protocol
 * version correctly" could not run without a key pair on disk and a live WebSocket. Nothing that
 * touches the network was testable, so nothing about the protocol was tested. This seam is the
 * minimum needed to change that.
 */
internal interface ApprovalGateway {

    /** Whether a phone has announced a signing key, so a request could be answered at all. */
    val isPhonePaired: Boolean

    /** Whether the relay socket is currently up. */
    val isRelayConnected: Boolean

    /** Whether the relay reports a phone attached, or null when it has not said. */
    val phoneOnline: Boolean?

    val pairId: String

    val relayHost: String

    suspend fun request(request: PermissionRequest): ApprovalOutcome

    suspend fun announce(announcement: SessionAnnouncement)

    /** Abandons a request so the phone stops offering it. */
    suspend fun cancel(requestId: String)

    fun close()
}

/**
 * The real gateway, backed by the relay socket.
 *
 * Construction of the key store and the relay client is deferred, because this object is created
 * during process start-up while the MCP handshake is in flight: generating an Ed25519 key pair and
 * reading `~/.handoff` on that path would put disk I/O between the client's `initialize` and our
 * reply, and a failure there would fail the handshake rather than the approval it belongs to.
 */
internal class RelayApprovalGateway(
    private val onAbort: () -> Unit = {}
) : ApprovalGateway {

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
            privateKey = keyPair.private,
            onAbort = onAbort
        )
    }

    /** Whether [relayClient] has been touched, so status never *creates* a connection to report on. */
    @Volatile
    private var started = false

    override val isPhonePaired: Boolean
        get() = DesktopConfigManager.isPhonePaired()

    override val isRelayConnected: Boolean
        get() = started && relayClient.isConnected

    override val phoneOnline: Boolean?
        get() = if (started) relayClient.phoneOnline else null

    override val pairId: String
        get() = DesktopConfigManager.getPairId()

    override val relayHost: String
        get() = DesktopConfigManager.getRelayHost()

    override suspend fun request(request: PermissionRequest): ApprovalOutcome {
        started = true
        return relayClient.sendRequestAndWaitForDecision(request)
    }

    override suspend fun announce(announcement: SessionAnnouncement) {
        started = true
        relayClient.announceSession(announcement)
    }

    override suspend fun cancel(requestId: String) {
        if (!started) return
        relayClient.cancelRequest(requestId)
    }

    override fun close() {
        if (!started) return
        runCatching { relayClient.close() }.onFailure { Log.warn("Closing the relay client failed: ${it.message}") }
    }
}

/**
 * A gateway that reaches nothing, for exercising the protocol without touching the network.
 *
 * Used by `handoff --doctor` to run a real handshake in process. Reporting [ApprovalOutcome.NotPaired]
 * rather than throwing keeps the self-test on the same code path a real unpaired desktop takes.
 */
internal class OfflineApprovalGateway(
    override val pairId: String = DesktopConfigManager.getPairId(),
    override val relayHost: String = DesktopConfigManager.getRelayHost()
) : ApprovalGateway {

    override val isPhonePaired: Boolean = false
    override val isRelayConnected: Boolean = false
    override val phoneOnline: Boolean? = null

    override suspend fun request(request: PermissionRequest): ApprovalOutcome = ApprovalOutcome.NotPaired

    override suspend fun announce(announcement: SessionAnnouncement): Unit = Unit

    override suspend fun cancel(requestId: String): Unit = Unit

    override fun close(): Unit = Unit
}
