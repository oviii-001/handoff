package com.ovi.handoff.mobile.feature.pairing.viewmodel

import app.cash.turbine.test
import com.ovi.handoff.mobile.domain.usecase.PairDeviceUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class PairingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var useCase: PairDeviceUseCase
    private lateinit var viewModel: PairingViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        useCase = mockk()
        viewModel = PairingViewModel(useCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is scanning`() = runTest {
        val state = viewModel.uiState.value
        assertTrue(state.isScanning)
        assertFalse(state.isPairing)
        assertEquals(null, state.error)
    }

    @Test
    fun `onQrCodeScanned transitions to pairing and emits success on successful use case`() = runTest {
        coEvery { useCase("payload") } returns Result.success(Unit)

        viewModel.events.test {
            viewModel.onQrCodeScanned("payload")
            
            // Check intermediate state
            var state = viewModel.uiState.value
            assertTrue(state.isPairing)
            assertFalse(state.isScanning)

            val event = awaitItem()
            assertEquals(PairingEvent.PairingSuccess, event)
            
            // Check final state
            state = viewModel.uiState.value
            assertFalse(state.isPairing)
        }
    }

    @Test
    fun `onQrCodeScanned transitions to error and emits error on failed use case`() = runTest {
        coEvery { useCase("payload") } returns Result.failure(Exception("Pairing failed"))

        viewModel.events.test {
            viewModel.onQrCodeScanned("payload")

            val event = awaitItem()
            assertEquals(PairingEvent.PairingError("Pairing failed"), event)

            val state = viewModel.uiState.value
            assertFalse(state.isPairing)
            assertTrue(state.isScanning)
            assertEquals("Pairing failed", state.error)
        }
    }
}
