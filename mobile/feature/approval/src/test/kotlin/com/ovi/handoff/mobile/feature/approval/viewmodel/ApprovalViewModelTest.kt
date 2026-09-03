package com.ovi.handoff.mobile.feature.approval.viewmodel

import app.cash.turbine.test
import com.ovi.handoff.mobile.domain.repository.PairingRepository
import com.ovi.handoff.mobile.domain.usecase.AbortSessionUseCase
import com.ovi.handoff.mobile.domain.usecase.ClearRequestHistoryUseCase
import com.ovi.handoff.mobile.domain.usecase.GetRequestHistoryUseCase
import com.ovi.handoff.mobile.domain.usecase.ObserveRequestsUseCase
import com.ovi.handoff.mobile.domain.usecase.PairDeviceUseCase
import com.ovi.handoff.mobile.domain.usecase.SendDecisionUseCase
import com.ovi.handoff.mobile.domain.usecase.UnpairDeviceUseCase
import com.ovi.handoff.mobile.feature.approval.ui.model.toUiModel
import com.ovi.handoff.shared.model.AgentInfo
import com.ovi.handoff.shared.model.PermissionDecision
import com.ovi.handoff.shared.model.PermissionInfo
import com.ovi.handoff.shared.model.PermissionRequest
import com.ovi.handoff.shared.model.RiskInfo
import com.ovi.handoff.shared.model.SessionInfo
import io.mockk.coEvery
import io.mockk.coVerify
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ApprovalViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var observeRequestsUseCase: ObserveRequestsUseCase
    private lateinit var sendDecisionUseCase: SendDecisionUseCase
    private lateinit var getRequestHistoryUseCase: GetRequestHistoryUseCase
    private lateinit var clearRequestHistoryUseCase: ClearRequestHistoryUseCase
    private lateinit var abortSessionUseCase: AbortSessionUseCase
    private lateinit var pairDeviceUseCase: PairDeviceUseCase
    private lateinit var unpairDeviceUseCase: UnpairDeviceUseCase
    private lateinit var pairingRepository: PairingRepository
    private lateinit var viewModel: ApprovalViewModel

    private val testPairId = "pair-123"

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        observeRequestsUseCase = mockk()
        sendDecisionUseCase = mockk()
        getRequestHistoryUseCase = mockk()
        clearRequestHistoryUseCase = mockk()
        abortSessionUseCase = mockk()
        pairDeviceUseCase = mockk()
        unpairDeviceUseCase = mockk()
        pairingRepository = mockk()

        every { observeRequestsUseCase(any()) } returns flowOf(null)
        every { getRequestHistoryUseCase() } returns flowOf(emptyList())
        coEvery { clearRequestHistoryUseCase() } returns Result.success(Unit)
        coEvery { pairingRepository.getPairId() } returns testPairId
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createDummyRequest(id: String = "req-1"): PermissionRequest = PermissionRequest(
        id = id,
        protocolVersion = "1.0",
        agent = AgentInfo("agent-1", "My Agent"),
        session = SessionInfo("session-1"),
        permission = PermissionInfo("file_read", target = "target"),
        risk = RiskInfo("low", emptyList()),
        options = emptyList(),
        createdAt = "",
        expiresAt = ""
    )

    private fun buildViewModel(initialPairId: String? = testPairId): ApprovalViewModel {
        return ApprovalViewModel(
            initialPairId = initialPairId,
            observeRequestsUseCase = observeRequestsUseCase,
            sendDecisionUseCase = sendDecisionUseCase,
            getRequestHistoryUseCase = getRequestHistoryUseCase,
            clearRequestHistoryUseCase = clearRequestHistoryUseCase,
            abortSessionUseCase = abortSessionUseCase,
            pairDeviceUseCase = pairDeviceUseCase,
            unpairDeviceUseCase = unpairDeviceUseCase,
            pairingRepository = pairingRepository
        )
    }

    @Test
    fun `observes requests on init when paired`() = runTest {
        val request = createDummyRequest()
        every { observeRequestsUseCase(testPairId) } returns flowOf(request)

        viewModel = buildViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            if (state.currentRequest == null) {
                val updatedState = awaitItem()
                assertEquals(request.toUiModel(), updatedState.currentRequest)
            } else {
                assertEquals(request.toUiModel(), state.currentRequest)
            }
        }
    }

    @Test
    fun `onApprove sends decision and clears request on success`() = runTest {
        val request = createDummyRequest()
        every { observeRequestsUseCase(testPairId) } returns flowOf(request)
        coEvery { sendDecisionUseCase(testPairId, match { it.decision == "approve_once" }) } returns Result.success(Unit)

        viewModel = buildViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.currentRequest == null) {
                state = awaitItem()
            }
            assertEquals(request.toUiModel(), state.currentRequest)

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

        viewModel = buildViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            if (state.currentRequest == null) {
                state = awaitItem()
            }
            assertEquals(request.toUiModel(), state.currentRequest)

            viewModel.onReject()

            val sendingState = awaitItem()
            assertTrue(sendingState.isSendingDecision)

            val finalState = awaitItem()
            assertFalse(finalState.isSendingDecision)
            assertEquals("Network error", finalState.error)
            assertEquals(request.toUiModel(), finalState.currentRequest)
        }
    }

    @Test
    fun `switchTab changes active tab across HOME, AUDIT, SETTINGS`() = runTest {
        every { observeRequestsUseCase(testPairId) } returns flowOf(null)

        viewModel = buildViewModel()

        assertEquals(ApprovalTab.HOME, viewModel.uiState.value.selectedTab)
        viewModel.switchTab(ApprovalTab.AUDIT)
        assertEquals(ApprovalTab.AUDIT, viewModel.uiState.value.selectedTab)
        viewModel.switchTab(ApprovalTab.SETTINGS)
        assertEquals(ApprovalTab.SETTINGS, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun `pairWithCode successfully pairs device and starts observing`() = runTest {
        coEvery { pairingRepository.getPairId() } returnsMany listOf(null, testPairId)
        coEvery { pairDeviceUseCase(any()) } returns Result.success(Unit)
        every { observeRequestsUseCase(testPairId) } returns flowOf(null)

        viewModel = buildViewModel(initialPairId = null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isPaired)

        viewModel.pairWithCode("handoff://pair?pair_id=pair-123&code=123456")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isPaired)
        assertEquals(testPairId, viewModel.uiState.value.pairId)
        assertNull(viewModel.uiState.value.pairingError)
    }

    @Test
    fun `unpair resets pairing state and calls unpairDeviceUseCase`() = runTest {
        every { observeRequestsUseCase(testPairId) } returns flowOf(null)
        coEvery { unpairDeviceUseCase() } returns Result.success(Unit)

        viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isPaired)

        viewModel.unpair()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isPaired)
        assertNull(viewModel.uiState.value.pairId)
        coVerify { unpairDeviceUseCase() }
    }

    @Test
    fun `recentActivity returns top 3 recent history requests`() = runTest {
        val req1 = createDummyRequest("req-1")
        val req2 = createDummyRequest("req-2")
        val req3 = createDummyRequest("req-3")
        val req4 = createDummyRequest("req-4")

        every { observeRequestsUseCase(testPairId) } returns flowOf(null)
        every { getRequestHistoryUseCase() } returns flowOf(listOf(req1, req2, req3, req4))

        viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(4, viewModel.uiState.value.historyRequests.size)
        assertEquals(3, viewModel.uiState.value.recentActivity.size)
        assertEquals("req-1", viewModel.uiState.value.recentActivity[0].id)
        assertEquals("req-3", viewModel.uiState.value.recentActivity[2].id)
    }

    @Test
    fun `onAbortSession triggers abortSessionUseCase`() = runTest {
        every { observeRequestsUseCase(testPairId) } returns flowOf(null)
        coEvery { abortSessionUseCase(testPairId) } returns Result.success(Unit)

        viewModel = buildViewModel()

        viewModel.onAbortSession()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Agent session aborted", viewModel.uiState.value.notificationMessage)
    }

    @Test
    fun `clearAuditHistory calls clearRequestHistoryUseCase and sets notification message`() = runTest {
        coEvery { clearRequestHistoryUseCase() } returns Result.success(Unit)

        viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.clearAuditHistory()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Audit log cleared", viewModel.uiState.value.notificationMessage)
        coVerify { clearRequestHistoryUseCase() }
    }
}
