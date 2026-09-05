package com.ovi.handoff.mobile.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.ovi.handoff.mobile.domain.repository.PairingInfo
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
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockPrefs.edit() } returns mockEditor
        
        val mockContext = mockk<Context>()
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs

        val repo = PairingRepositoryImpl(mockContext)
        
        val result = repo.pairDevice(
            PairingInfo(
                pairId = "test_pair_id",
                relayHost = "relay.test",
                desktopPublicKey = null,
                pairSecret = null
            )
        )
        
        assertTrue(result.isSuccess)
        assertEquals("test_pair_id", repo.getPairId())
    }
}
