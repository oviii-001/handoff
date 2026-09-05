package com.ovi.handoff.mobile.feature.approval.viewmodel

import app.cash.turbine.test
import com.ovi.handoff.mobile.domain.repository.ConnectionState
import com.ovi.handoff.mobile.domain.repository.PairingInfo
import com.ovi.handoff.mobile.domain.repository.PairingRepository
import com.ovi.handoff.mobile.domain.repository.RelayRepository
import com.ovi.handoff.mobile.domain.settings.HandoffSettings
import com.ovi.handoff.mobile.domain.settings.SettingsRepository
import com.ovi.handoff.mobile.domain.usecase.UnpairDeviceUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val unpairDeviceUseCase = mockk<UnpairDeviceUseCase>()
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val pairingRepository = mockk<PairingRepository>()
    private val relayRepository = mockk<RelayRepository>()
    private val settingsFlow = MutableStateFlow(HandoffSettings())
    private val connectionStateFlow = MutableStateFlow(ConnectionState.CONNECTED)

    private val testPairId = "pair-device-test-123"
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { pairingRepository.getPairing() } returns PairingInfo(
            pairId = testPairId,
            relayHost = "relay.example.com",
            desktopPublicKey = "pk_test",
            pairSecret = "tok_test"
        )
        every { settingsRepository.observe() } returns settingsFlow
        every { relayRepository.connectionState } returns connectionStateFlow

        viewModel = SettingsViewModel(
            pairId = testPairId,
            unpairDeviceUseCase = unpairDeviceUseCase,
            settingsRepository = settingsRepository,
            pairingRepository = pairingRepository,
            relayRepository = relayRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial uiState contains correct pairId and settings`() = runTest {
        viewModel.uiState.test {
            advanceUntilIdle()
            val state = awaitItem()
            assertEquals(testPairId, state.pairId)
            assertTrue(state.settings.pushNotificationsEnabled)
            assertTrue(state.settings.notificationActionsEnabled)
            assertTrue(state.settings.vibrationEnabled)
            assertTrue(state.settings.biometricsForCritical)
            assertFalse(state.isUnpairing)
        }
    }

    @Test
    fun `toggle preferences update settingsRepository`() = runTest {
        viewModel.togglePushNotifications(false)
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsRepository.setPushNotificationsEnabled(false) }

        viewModel.toggleNotificationActions(false)
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsRepository.setNotificationActionsEnabled(false) }

        viewModel.toggleVibration(false)
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsRepository.setVibrationEnabled(false) }

        viewModel.toggleBiometricsForCritical(false)
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsRepository.setBiometricsForCritical(false) }

        viewModel.toggleBiometricsForShadeActions(false)
        advanceUntilIdle()
        coVerify(exactly = 1) { settingsRepository.setBiometricsForShadeActions(false) }
    }

    @Test
    fun `unpair calls unpairDeviceUseCase and triggers callback on success`() = runTest {
        coEvery { unpairDeviceUseCase() } returns Result.success(Unit)

        var callbackCalled = false
        viewModel.unpair {
            callbackCalled = true
        }

        advanceUntilIdle()
        assertTrue(callbackCalled)
        coVerify(exactly = 1) { unpairDeviceUseCase() }
        assertFalse(viewModel.uiState.value.isUnpairing)
    }

    @Test
    fun `unpair failure updates error message and does not trigger callback`() = runTest {
        coEvery { unpairDeviceUseCase() } returns Result.failure(Exception("Failed"))

        var callbackCalled = false
        viewModel.unpair {
            callbackCalled = true
        }

        advanceUntilIdle()
        assertFalse(callbackCalled)
        assertEquals("Could not unpair. Try again.", viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isUnpairing)
    }
}
