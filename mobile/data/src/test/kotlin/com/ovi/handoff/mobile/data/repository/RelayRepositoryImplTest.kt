package com.ovi.handoff.mobile.data.repository

import com.ovi.handoff.mobile.data.local.RequestDao
import com.ovi.handoff.mobile.domain.notification.NotificationNotifier
import com.ovi.handoff.mobile.domain.repository.ConnectionState
import com.ovi.handoff.mobile.domain.repository.PairingInfo
import com.ovi.handoff.mobile.domain.repository.PairingRepository
import com.ovi.handoff.mobile.domain.security.DecisionSigner
import com.ovi.handoff.shared.model.PermissionDecision
import com.ovi.handoff.shared.protocol.SignatureAlgorithm
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * These exercise the paths that run when the relay is *not* reachable, which is the case that used
 * to be reported to the user as success.
 */
class RelayRepositoryImplTest {

    /** A host that cannot resolve, so the socket fails fast instead of waiting on a real network. */
    private val unreachableHost = "handoff-test-unreachable.invalid"

    private fun repository(
        host: String = unreachableHost,
        pairing: PairingInfo? = PairingInfo(
            pairId = "pair-1",
            relayHost = host,
            desktopPublicKey = null,
            pairSecret = "s".repeat(43)
        )
    ): RelayRepositoryImpl {
        val pairingRepository = mockk<PairingRepository>(relaxed = true)
        coEvery { pairingRepository.getPairing() } returns pairing

        val signer = mockk<DecisionSigner>(relaxed = true)
        every { signer.publicKeyBase64() } returns "cHVibGlj"
        every { signer.deviceId() } returns "device-1"
        every { signer.algorithm() } returns SignatureAlgorithm.ECDSA_P256_SHA256

        return RelayRepositoryImpl(
            requestDao = mockk<RequestDao>(relaxed = true),
            pairingRepository = pairingRepository,
            signer = signer,
            defaultRelayHost = host,
            notificationNotifier = mockk<NotificationNotifier>(relaxed = true)
        )
    }

    @Test
    fun `starts offline with no error`() = runTest {
        val repo = repository()
        assertNotNull(repo)
        assertEquals(ConnectionState.OFFLINE, repo.connectionState.value)
        assertEquals(null, repo.connectionError.value)
    }

    @Test
    fun `awaitConnected fails with an explanation when the relay cannot be reached`() = runTest {
        val repo = repository()

        val result = repo.awaitConnected("pair-1", timeoutMs = 1_500)

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message.isNotBlank(), "the failure must carry a reason the user can act on")
        // And the same reason is published for the UI, not just thrown away as it used to be.
        assertEquals(message, repo.connectionError.value)
    }

    @Test
    fun `a pairing with no relay token is refused without attempting a socket`() = runTest {
        val repo = repository(
            pairing = PairingInfo(
                pairId = "pair-1",
                relayHost = unreachableHost,
                desktopPublicKey = null,
                pairSecret = null
            )
        )

        val result = repo.awaitConnected("pair-1", timeoutMs = 1_500)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("handoff --pair"))
    }

    @Test
    fun `sendDecision reports failure rather than claiming delivery when unreachable`() = runTest {
        val repo = repository()

        val decision = PermissionDecision(
            requestId = "req-1",
            decision = "approve_once",
            issuedAt = Instant.now().toString(),
            nonce = UUID.randomUUID().toString(),
            deviceId = "test-device",
            requestHash = "0".repeat(64),
            signature = ""
        )

        val result = repo.sendDecision("pair-1", decision)

        // Reported as a failure so the request stays in the queue. Telling the user their approval
        // was delivered while it sits unsent is the one outcome this class exists to prevent.
        assertTrue(result.isFailure)
    }
}
