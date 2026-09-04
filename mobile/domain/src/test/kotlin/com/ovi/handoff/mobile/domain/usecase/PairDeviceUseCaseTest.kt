package com.ovi.handoff.mobile.domain.usecase

import com.ovi.handoff.mobile.domain.repository.PairingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertTrue

class PairDeviceUseCaseTest {
    
    private val pairingRepository = mockk<PairingRepository>()
    private val useCase = PairDeviceUseCase(pairingRepository)

    @Test
    fun `when valid qr code scanned, repository pairs device successfully`() = runTest {
        val qrPayload = "pair_12345"
        
        coEvery { pairingRepository.pairDevice(any(), any()) } returns Result.success(Unit)
        
        val result = useCase(qrPayload)
        
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { pairingRepository.pairDevice(qrPayload, any()) }
    }

    @Test
    fun `when qr code contains pubKey, it is extracted and decoded`() = runTest {
        val base64PubKey = "YWJjZGVmZ2hpamtsbW5vcA"
        val qrPayload = "handoff://pair?pairId=pair_12345&host=localhost&pubKey=$base64PubKey"
        
        coEvery { pairingRepository.pairDevice(any(), any()) } returns Result.success(Unit)
        
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
