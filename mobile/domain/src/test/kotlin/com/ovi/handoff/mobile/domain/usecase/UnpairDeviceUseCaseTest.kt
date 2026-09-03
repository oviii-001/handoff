package com.ovi.handoff.mobile.domain.usecase

import com.ovi.handoff.mobile.domain.repository.PairingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertTrue

class UnpairDeviceUseCaseTest {

    private val repository = mockk<PairingRepository>()
    private val useCase = UnpairDeviceUseCase(repository)

    @Test
    fun `unpairs device via pairing repository`() = runTest {
        coEvery { repository.clearPairing() } returns Result.success(Unit)

        val result = useCase()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.clearPairing() }
    }
}
