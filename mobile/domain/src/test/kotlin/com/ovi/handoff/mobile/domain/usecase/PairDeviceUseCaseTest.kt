package com.ovi.handoff.mobile.domain.usecase

import com.ovi.handoff.mobile.domain.repository.PairingRepository
import com.ovi.handoff.mobile.domain.repository.RelayRepository
import com.ovi.handoff.mobile.domain.provider.PushTokenProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertTrue

class PairDeviceUseCaseTest {
    
    private val pairingRepository = mockk<PairingRepository>()
    private val relayRepository = mockk<RelayRepository>()
    private val pushTokenProvider = mockk<PushTokenProvider>()
    private val useCase = PairDeviceUseCase(pairingRepository, relayRepository, pushTokenProvider)

    @Test
    fun `when valid qr code scanned, repository pairs device successfully`() = runTest {
        val qrPayload = "pair_12345"
        
        coEvery { pairingRepository.pairDevice(any(), any()) } returns Result.success(Unit)
        coEvery { pushTokenProvider.getToken() } returns "mock_fcm_token"
        coEvery { relayRepository.registerPushToken(any(), any()) } returns Result.success(Unit)
        
        val result = useCase(qrPayload)
        
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { pairingRepository.pairDevice(qrPayload, any()) }
        coVerify(exactly = 1) { pushTokenProvider.getToken() }
        coVerify(exactly = 1) { relayRepository.registerPushToken(qrPayload, "mock_fcm_token") }
    }

    @Test
    fun `when qr code contains pubKey, it is extracted and decoded`() = runTest {
        val base64PubKey = "YWJjZGVmZ2hpamtsbW5vcA"
        val qrPayload = "handoff://pair?pairId=pair_12345&host=localhost&pubKey=$base64PubKey"
        
        coEvery { pairingRepository.pairDevice(any(), any()) } returns Result.success(Unit)
        coEvery { pushTokenProvider.getToken() } returns "mock_fcm_token"
        coEvery { relayRepository.registerPushToken(any(), any()) } returns Result.success(Unit)
        
        val result = useCase(qrPayload)
        
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { pairingRepository.pairDevice("pair_12345", any()) }
    }

    @Test
    fun `when qr code is empty, returns failure`() = runTest {
        val qrPayload = ""
        
        val result = useCase(qrPayload)
        
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { pairingRepository.pairDevice(any(), any()) }
    }
}
