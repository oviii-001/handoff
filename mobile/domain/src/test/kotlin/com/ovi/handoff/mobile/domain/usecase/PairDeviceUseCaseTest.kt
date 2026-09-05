package com.ovi.handoff.mobile.domain.usecase

import com.ovi.handoff.mobile.domain.provider.PushTokenProvider
import com.ovi.handoff.mobile.domain.repository.PairingRepository
import com.ovi.handoff.mobile.domain.repository.RelayRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PairDeviceUseCaseTest {

    private val pairingRepository = mockk<PairingRepository>()
    private val relayRepository = mockk<RelayRepository>()
    private val pushTokenProvider = mockk<PushTokenProvider>()
    private val useCase = PairDeviceUseCase(pairingRepository, relayRepository, pushTokenProvider)

    private fun stubHappyPath() {
        coEvery { pairingRepository.pairDevice(any()) } returns Result.success(Unit)
        coEvery { relayRepository.awaitConnected(any(), any()) } returns Result.success(Unit)
        coEvery { relayRepository.announceIdentity(any()) } returns Result.success(Unit)
        coEvery { pushTokenProvider.getToken() } returns "mock_fcm_token"
        coEvery { relayRepository.registerPushToken(any(), any()) } returns Result.success(Unit)
    }

    @Test
    fun `when valid qr code scanned, repository pairs device successfully`() = runTest {
        val qrPayload = "handoff://pair?pairId=pair_12345&host=localhost&pubKey=abc&token=secret_tok_123"
        stubHappyPath()

        val result = useCase(qrPayload)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            pairingRepository.pairDevice(match { it.pairId == "pair_12345" && it.pairSecret == "secret_tok_123" })
        }
        coVerify(exactly = 1) { relayRepository.announceIdentity("pair_12345") }
        coVerify(exactly = 1) { pushTokenProvider.getToken() }
        coVerify(exactly = 1) { relayRepository.registerPushToken("pair_12345", "mock_fcm_token") }
    }

    @Test
    fun `when qr code contains pubKey, it is extracted and decoded`() = runTest {
        val base64PubKey = "YWJjZGVmZ2hpamtsbW5vcA"
        val qrPayload = "handoff://pair?pairId=pair_12345&host=localhost&pubKey=$base64PubKey&token=secret_tok_123"
        stubHappyPath()

        val result = useCase(qrPayload)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            pairingRepository.pairDevice(match { it.pairId == "pair_12345" && it.desktopPublicKey == base64PubKey })
        }
    }

    @Test
    fun `when qr code is empty, returns failure`() = runTest {
        val result = useCase("")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { pairingRepository.pairDevice(any()) }
    }

    /**
     * The regression this whole confirmation step exists for.
     *
     * Storing the pairing always succeeds, so success used to be reported before anything had been
     * verified. A user whose relay room was never claimed — the ordinary outcome of scanning without
     * `handoff --pair` left running — landed on the paired home screen and waited indefinitely for
     * requests that could not arrive.
     */
    @Test
    fun `when the relay refuses the socket, pairing fails and is rolled back`() = runTest {
        val qrPayload = "handoff://pair?pairId=pair_12345&host=localhost&pubKey=abc&token=secret_tok_123"
        val reason = "No computer has claimed this pairing code yet."

        coEvery { pairingRepository.pairDevice(any()) } returns Result.success(Unit)
        coEvery { relayRepository.awaitConnected(any(), any()) } returns
            Result.failure(IllegalStateException(reason))
        coEvery { pairingRepository.clearPairing() } returns Result.success(Unit)

        val result = useCase(qrPayload)

        assertTrue(result.isFailure)
        assertEquals(reason, result.exceptionOrNull()?.message)
        // Rolled back, so the app does not sit in a half-paired state it cannot recover from.
        coVerify(exactly = 1) { pairingRepository.clearPairing() }
        // And nothing was announced to a relay that will not have us.
        coVerify(exactly = 0) { relayRepository.announceIdentity(any()) }
        coVerify(exactly = 0) { relayRepository.registerPushToken(any(), any()) }
    }

    @Test
    fun `a bare pair id with no relay token is refused with an actionable message`() = runTest {
        val result = useCase("pair_12345")

        assertTrue(result.isFailure)
        // The message has to name the command, because a bare pair id is exactly what the old
        // onboarding text invited people to type.
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("handoff --pair"))
        coVerify(exactly = 0) { pairingRepository.pairDevice(any()) }
    }

    @Test
    fun `when 6-digit PIN is provided with spaces, it resolves via relay and pairs`() = runTest {
        val pinInfo = com.ovi.handoff.mobile.domain.repository.PairingInfo(
            pairId = "pair_pin_123",
            relayHost = "localhost",
            desktopPublicKey = "pub_key",
            pairSecret = "secret_tok_pin"
        )
        coEvery { relayRepository.resolvePin("842190") } returns Result.success(pinInfo)
        stubHappyPath()

        val result = useCase("842 190")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { relayRepository.resolvePin("842190") }
        coVerify(exactly = 1) { pairingRepository.pairDevice(pinInfo) }
        coVerify(exactly = 1) { relayRepository.awaitConnected("pair_pin_123", any()) }
    }

    @Test
    fun `when 6-digit PIN resolution fails, use case returns failure`() = runTest {
        coEvery { relayRepository.resolvePin("999999") } returns Result.failure(IllegalArgumentException("Code expired"))

        val result = useCase("999999")

        assertTrue(result.isFailure)
        assertEquals("Code expired", result.exceptionOrNull()?.message)
        coVerify(exactly = 0) { pairingRepository.pairDevice(any()) }
    }
}
