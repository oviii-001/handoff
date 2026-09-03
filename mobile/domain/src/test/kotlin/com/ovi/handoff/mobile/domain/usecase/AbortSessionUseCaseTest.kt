package com.ovi.handoff.mobile.domain.usecase

import com.ovi.handoff.mobile.domain.repository.RelayRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertTrue

class AbortSessionUseCaseTest {

    private val repository = mockk<RelayRepository>()
    private val useCase = AbortSessionUseCase(repository)

    @Test
    fun `aborts session via repository`() = runTest {
        val pairId = "pair-99"
        coEvery { repository.abortSession(pairId) } returns Result.success(Unit)

        val result = useCase(pairId)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.abortSession(pairId) }
    }
}
