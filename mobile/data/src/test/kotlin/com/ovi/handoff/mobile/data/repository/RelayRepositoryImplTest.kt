package com.ovi.handoff.mobile.data.repository

import com.ovi.handoff.mobile.data.local.RequestDao
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertTrue

class RelayRepositoryImplTest {

    @Test
    fun `test initialization`() = runTest {
        val mockDao = mockk<RequestDao>(relaxed = true)
        val repo = RelayRepositoryImpl(mockDao, "localhost")
        assertTrue(true)
    }
}
