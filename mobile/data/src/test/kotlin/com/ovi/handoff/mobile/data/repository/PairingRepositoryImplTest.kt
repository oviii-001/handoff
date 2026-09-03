package com.ovi.handoff.mobile.data.repository

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PairingRepositoryImplTest {
    
    @Test
    fun `pairDevice saves pairId and returns success`() = runTest {
        val mockEditor = mockk<SharedPreferences.Editor>(relaxed = true)
        val mockPrefs = mockk<SharedPreferences>(relaxed = true)
        every { mockEditor.commit() } returns true
        every { mockPrefs.edit() } returns mockEditor
        every { mockPrefs.getString("paired_session_id", null) } returns "test_pair_id"
        
        val mockContext = mockk<Context>()
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs

        val repo = PairingRepositoryImpl(mockContext)
        
        val result = repo.pairDevice("test_pair_id", ByteArray(0))
        
        assertTrue(result.isSuccess)
        assertEquals("test_pair_id", repo.getPairId())
    }
}
