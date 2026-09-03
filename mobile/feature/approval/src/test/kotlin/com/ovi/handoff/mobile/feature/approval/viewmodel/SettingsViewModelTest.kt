package com.ovi.handoff.mobile.feature.approval.viewmodel

import app.cash.turbine.test
import com.ovi.handoff.mobile.domain.usecase.UnpairDeviceUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val testPairId = "pair-device-test-123"
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = SettingsViewModel(
            pairId = testPairId,
            unpairDeviceUseCase = unpairDeviceUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial uiState contains correct pairId and defaults`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(testPairId, state.pairId)
            assertTrue(state.pushNotificationsEnabled)
            assertTrue(state.directActionsEnabled)
            assertTrue(state.vibrationEnabled)
            assertFalse(state.biometricsRequiredForCritical)
            assertFalse(state.isUnpairing)
        }
    }

    @Test
    fun `toggle preferences update uiState properly`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            viewModel.togglePushNotifications(false)
            assertEquals(false, awaitItem().pushNotificationsEnabled)

            viewModel.toggleDirectActions(false)
            assertEquals(false, awaitItem().directActionsEnabled)

            viewModel.toggleVibration(false)
            assertEquals(false, awaitItem().vibrationEnabled)

            viewModel.toggleBiometrics(true)
            assertEquals(true, awaitItem().biometricsRequiredForCritical)
        }
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
}
