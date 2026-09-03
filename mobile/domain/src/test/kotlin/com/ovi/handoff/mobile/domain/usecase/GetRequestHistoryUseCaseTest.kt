package com.ovi.handoff.mobile.domain.usecase

import app.cash.turbine.test
import com.ovi.handoff.mobile.domain.repository.RelayRepository
import com.ovi.handoff.shared.model.AgentInfo
import com.ovi.handoff.shared.model.PermissionInfo
import com.ovi.handoff.shared.model.PermissionRequest
import com.ovi.handoff.shared.model.RiskInfo
import com.ovi.handoff.shared.model.SessionInfo
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class GetRequestHistoryUseCaseTest {

    private val repository = mockk<RelayRepository>()
    private val useCase = GetRequestHistoryUseCase(repository)

    @Test
    fun `returns history flow from repository`() = runTest {
        val dummyRequest = PermissionRequest(
            id = "req-1",
            protocolVersion = "1.0",
            agent = AgentInfo("agent-1", "Antigravity"),
            session = SessionInfo("session-1"),
            permission = PermissionInfo("shell", command = "ls -la"),
            risk = RiskInfo("low", emptyList()),
            options = emptyList(),
            createdAt = "",
            expiresAt = ""
        )

        every { repository.observeHistory() } returns flowOf(listOf(dummyRequest))

        useCase().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("req-1", list[0].id)
            awaitComplete()
        }
    }
}
