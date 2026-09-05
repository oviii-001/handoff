package com.ovi.handoff.mobile.feature.approval.viewmodel

import app.cash.turbine.test
import com.ovi.handoff.mobile.domain.repository.ConnectedSession
import com.ovi.handoff.mobile.domain.repository.ConnectionState
import com.ovi.handoff.mobile.domain.repository.PairingInfo
import com.ovi.handoff.mobile.domain.repository.PairingRepository
import com.ovi.handoff.mobile.domain.repository.RelayRepository
import com.ovi.handoff.mobile.domain.repository.RequestRecord
import com.ovi.handoff.mobile.domain.settings.HandoffSettings
import com.ovi.handoff.mobile.domain.settings.SettingsRepository
import com.ovi.handoff.mobile.domain.usecase.AbortSessionUseCase
import com.ovi.handoff.mobile.domain.usecase.ClearRequestHistoryUseCase
import com.ovi.handoff.mobile.domain.usecase.ExpireOverdueRequestsUseCase
import com.ovi.handoff.mobile.domain.usecase.GetRequestHistoryUseCase
import com.ovi.handoff.mobile.domain.usecase.ObserveRequestsUseCase
import com.ovi.handoff.mobile.domain.usecase.PairDeviceUseCase
import com.ovi.handoff.mobile.domain.usecase.SubmitDecisionUseCase
import com.ovi.handoff.mobile.domain.usecase.UnpairDeviceUseCase
import com.ovi.handoff.mobile.feature.approval.ui.model.toUiModel
import com.ovi.handoff.shared.model.AgentInfo
import com.ovi.handoff.shared.model.DecisionType
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ApprovalViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var relayRepository: RelayRepository
    private lateinit var observeRequestsUseCase: ObserveRequestsUseCase
    private lateinit var submitDecisionUseCase: SubmitDecisionUseCase
    private lateinit var getRequestHistoryUseCase: GetRequestHistoryUseCase
    private lateinit var clearRequestHistoryUseCase: ClearRequestHistoryUseCase
    private lateinit var expireOverdueRequestsUseCase: ExpireOverdueRequestsUseCase
    private lateinit var abortSessionUseCase: AbortSessionUseCase
    private lateinit var pairDeviceUseCase: PairDeviceUseCase
    private lateinit var unpairDeviceUseCase: UnpairDeviceUseCase
    private lateinit var pairingRepository: PairingRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewModel: ApprovalViewModel

    private val testPairId = "pair-123"
    private val connectionStateFlow = MutableStateFlow(ConnectionState.CONNECTED)
    private val settingsFlow = MutableStateFlow(HandoffSettings())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        relayRepository = mockk(relaxed = true)
        observeRequestsUseCase = mockk()
        submitDecisionUseCase = mockk()
        getRequestHistoryUseCase = mockk()
        clearRequestHistoryUseCase = mockk()
        expireOverdueRequestsUseCase = mockk(relaxed = true)
        abortSessionUseCase = mockk()
        pairDeviceUseCase = mockk()
        unpairDeviceUseCase = mockk()
        pairingRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)

        every { observeRequestsUseCase(any()) } returns flowOf(emptyList())
        every { getRequestHistoryUseCase() } returns flowOf(emptyList())
        coEvery { clearRequestHistoryUseCase() } returns Result.success(Unit)
        coEvery { pairingRepository.getPairId() } returns testPairId
        every { pairingRepository.observeConnectedSession() } returns flowOf(null)
        every { relayRepository.connectionState } returns connectionStateFlow
        every { settingsRepository.observe() } returns settingsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createDummyRequest(id: String = "req-1"): PermissionRequest = PermissionRequest(
        id = id,
        protocolVersion = "2.0",
        agent = AgentInfo("agent-1", "My Agent"),
        session = SessionInfo("session-1", "handoff", "/home/dev/handoff"),
        permission = PermissionInfo("terminal", command = "ls -la"),
        risk = RiskInfo("low", emptyList()),
        options = listOf("approve", "deny"),
        createdAt = "",
        expiresAt = ""
    )

    private fun buildViewModel(initialPairId: String? = testPairId): ApprovalViewModel {
        return ApprovalViewModel(
            initialPairId = initialPairId,
            relayRepository = relayRepository,
            observeRequestsUseCase = observeRequestsUseCase,
            submitDecisionUseCase = submitDecisionUseCase,
            getRequestHistoryUseCase = getRequestHistoryUseCase,
            clearRequestHistoryUseCase = clearRequestHistoryUseCase,
            expireOverdueRequestsUseCase = expireOverdueRequestsUseCase,
            abortSessionUseCase = abortSessionUseCase,
            pairDeviceUseCase = pairDeviceUseCase,
            unpairDeviceUseCase = unpairDeviceUseCase,
            pairingRepository = pairingRepository,
            settingsRepository = settingsRepository
        )
    }

    @Test
    fun `observes requests on init when paired`() = runTest {
        val request = createDummyRequest()
        every { observeRequestsUseCase(testPairId) } returns flowOf(listOf(request))

        viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.pendingRequests.size)
        assertEquals(request.toUiModel(), viewModel.uiState.value.displayedRequest)
    }

    @Test
    fun `onApprove submits decision on success`() = runTest {
        val request = createDummyRequest()
        every { observeRequestsUseCase(testPairId) } returns flowOf(listOf(request))
        coEvery {
            submitDecisionUseCase.invoke(
                pairId = any(),
                request = any(),
                verdict = any(),
                feedback = any(),
                selectedOptions = any()
            )
        } returns Result.success(Unit)

        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onApprove()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            submitDecisionUseCase.invoke(
                pairId = testPairId,
                request = any(),
                verdict = DecisionType.APPROVE_ONCE,
                feedback = any(),
                selectedOptions = any()
            )
        }
    }

    @Test
    fun `onReject submits decision on success`() = runTest {
        val request = createDummyRequest()
        every { observeRequestsUseCase(testPairId) } returns flowOf(listOf(request))
        coEvery {
            submitDecisionUseCase.invoke(
                pairId = any(),
                request = any(),
                verdict = any(),
                feedback = any(),
                selectedOptions = any()
            )
        } returns Result.success(Unit)

        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onReject()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            submitDecisionUseCase.invoke(
                pairId = testPairId,
                request = any(),
                verdict = DecisionType.DENY,
                feedback = any(),
                selectedOptions = any()
            )
        }
    }

    @Test
    fun `switchTab changes active tab across HOME, AUDIT, SETTINGS`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()

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
        coEvery { pairingRepository.getPairing() } returns PairingInfo(testPairId, "relay.test", "pk", "tok")

        viewModel = buildViewModel(initialPairId = null)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isPaired)

        viewModel.pairWithCode("handoff://pair?pair_id=pair-123&code=123456")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isPaired)
        assertEquals(testPairId, viewModel.uiState.value.pairId)
        assertNull(viewModel.uiState.value.pairingError)
    }

    @Test
    fun `unpair resets pairing state and calls unpairDeviceUseCase`() = runTest {
        coEvery { unpairDeviceUseCase() } returns Result.success(Unit)

        viewModel = buildViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isPaired)

        viewModel.unpair()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isPaired)
        assertNull(viewModel.uiState.value.pairId)
        coVerify { unpairDeviceUseCase() }
    }

    @Test
    fun `recentActivity returns top 3 recent history requests`() = runTest {
        val rec1 = RequestRecord(createDummyRequest("req-1"), isPending = false, decision = "APPROVED", decidedAtEpochMs = 1000L)
        val rec2 = RequestRecord(createDummyRequest("req-2"), isPending = false, decision = "APPROVED", decidedAtEpochMs = 2000L)
        val rec3 = RequestRecord(createDummyRequest("req-3"), isPending = false, decision = "DENIED", decidedAtEpochMs = 3000L)
        val rec4 = RequestRecord(createDummyRequest("req-4"), isPending = false, decision = "EXPIRED", decidedAtEpochMs = 4000L)

        every { getRequestHistoryUseCase() } returns flowOf(listOf(rec1, rec2, rec3, rec4))

        viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals(4, viewModel.uiState.value.auditEntries.size)
        assertEquals(3, viewModel.uiState.value.recentActivity.size)
        assertEquals("req-1", viewModel.uiState.value.recentActivity[0].id)
        assertEquals("req-3", viewModel.uiState.value.recentActivity[2].id)
    }

    @Test
    fun `onEmergencyHalt triggers abortSessionUseCase`() = runTest {
        coEvery { abortSessionUseCase(testPairId) } returns Result.success(Unit)

        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.onEmergencyHalt()
        advanceUntilIdle()

        assertEquals("Halt sent to your agent", viewModel.uiState.value.notificationMessage)
        coVerify(exactly = 1) { abortSessionUseCase(testPairId) }
    }

    @Test
    fun `clearAuditHistory calls clearRequestHistoryUseCase and sets notification message`() = runTest {
        coEvery { clearRequestHistoryUseCase() } returns Result.success(Unit)

        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.clearAuditHistory()
        advanceUntilIdle()

        assertEquals("Audit log cleared", viewModel.uiState.value.notificationMessage)
        coVerify { clearRequestHistoryUseCase() }
    }

    @Test
    fun `observes connected session and updates state`() = runTest {
        val testSession = ConnectedSession(
            ideName = "Antigravity",
            workspaceName = "handoff"
        )
        every { pairingRepository.observeConnectedSession() } returns flowOf(testSession)

        viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals("Antigravity", viewModel.uiState.value.connectedAgent?.name)
        assertEquals("handoff", viewModel.uiState.value.activeWorkspaceLabel)
    }
}
