package com.ovi.handoff.mobile.feature.approval.viewmodel

import app.cash.turbine.test
import com.ovi.handoff.mobile.domain.usecase.ObserveRequestsUseCase
import com.ovi.handoff.mobile.domain.usecase.SendDecisionUseCase
import com.ovi.handoff.shared.model.AgentInfo
import com.ovi.handoff.shared.model.PermissionDecision
import com.ovi.handoff.shared.model.PermissionInfo
import com.ovi.handoff.shared.model.PermissionRequest
import com.ovi.handoff.shared.model.RiskInfo
import com.ovi.handoff.shared.model.SessionInfo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ApprovalViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var observeRequestsUseCase: ObserveRequestsUseCase
    private lateinit var sendDecisionUseCase: SendDecisionUseCase
    private lateinit var viewModel: ApprovalViewModel

    private val testPairId = "pair-123"

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        observeRequestsUseCase = mockk()
        sendDecisionUseCase = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createDummyRequest(): PermissionRequest = PermissionRequest(
        id = "req-1",
        protocolVersion = "1.0",
        agent = AgentInfo("agent-1", "My Agent"),
        session = SessionInfo("session-1"),
        permission = PermissionInfo("file_read", target = "target"),
        risk = RiskInfo("low", emptyList()),
        options = emptyList(),
        createdAt = "",
        expiresAt = ""
    )

    private fun createDummyDecision(approved: Boolean): PermissionDecision = PermissionDecision(
        requestId = "req-1",
        decision = if (approved) "approve_once" else "deny",
        issuedAt = "",
        nonce = "",
        deviceId = testPairId,
        signature = ""
    )

    @Test
    fun `observes requests on init`() = runTest {
        val request = createDummyRequest()
        every { observeRequestsUseCase(testPairId) } returns flowOf(request)

        viewModel = ApprovalViewModel(testPairId, observeRequestsUseCase, sendDecisionUseCase)

        viewModel.uiState.test {
            // Initial state might be emitted before collect
            val state = awaitItem()
            if (state.currentRequest == null) {
                // If initial state was empty, wait for the update
                val updatedState = awaitItem()
                assertEquals(request, updatedState.currentRequest)
            } else {
                assertEquals(request, state.currentRequest)
            }
        }
    }

    @Test
    fun `onApprove sends decision and clears request on success`() = runTest {
        val request = createDummyRequest()
        every { observeRequestsUseCase(testPairId) } returns flowOf(request)
        coEvery { sendDecisionUseCase(testPairId, match { it.decision == "approve_once" }) } returns Result.success(Unit)

        viewModel = ApprovalViewModel(testPairId, observeRequestsUseCase, sendDecisionUseCase)

        viewModel.uiState.test {
            // Wait for initial state and the loaded request
            var state = awaitItem()
            if (state.currentRequest == null) {
                state = awaitItem()
            }
            assertEquals(request, state.currentRequest)

            viewModel.onApprove()

            val sendingState = awaitItem()
            assertTrue(sendingState.isSendingDecision)

            val finalState = awaitItem()
            assertFalse(finalState.isSendingDecision)
            assertEquals(null, finalState.currentRequest)
            assertEquals(null, finalState.error)
        }
    }

    @Test
    fun `onReject sends decision and sets error on failure`() = runTest {
        val request = createDummyRequest()
        every { observeRequestsUseCase(testPairId) } returns flowOf(request)
        coEvery { sendDecisionUseCase(testPairId, match { it.decision == "deny" }) } returns Result.failure(Exception("Network error"))

        viewModel = ApprovalViewModel(testPairId, observeRequestsUseCase, sendDecisionUseCase)

        viewModel.uiState.test {
            // Wait for initial state and the loaded request
            var state = awaitItem()
            if (state.currentRequest == null) {
                state = awaitItem()
            }
            assertEquals(request, state.currentRequest)

            viewModel.onReject()

            val sendingState = awaitItem()
            assertTrue(sendingState.isSendingDecision)

            val finalState = awaitItem()
            assertFalse(finalState.isSendingDecision)
            assertEquals("Network error", finalState.error)
            assertEquals(request, finalState.currentRequest)
        }
    }
}
