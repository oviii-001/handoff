package com.ovi.handoff.mobile.data.repository

import com.ovi.handoff.mobile.data.local.RequestDao
import com.ovi.handoff.mobile.domain.notification.NotificationNotifier
import com.ovi.handoff.shared.model.PermissionDecision
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RelayRepositoryImplTest {

    @Test
    fun `test initialization with notification notifier`() = runTest {
        val mockDao = mockk<RequestDao>(relaxed = true)
        val mockNotifier = mockk<NotificationNotifier>(relaxed = true)
        val repo = RelayRepositoryImpl(
            requestDao = mockDao,
            relayHost = "localhost",
            notificationNotifier = mockNotifier
        )
        assertNotNull(repo)
    }

    @Test
    fun `sendDecision handles failure gracefully when unreachable`() = runTest {
        val mockDao = mockk<RequestDao>(relaxed = true)
        val mockNotifier = mockk<NotificationNotifier>(relaxed = true)
        val repo = RelayRepositoryImpl(
            requestDao = mockDao,
            relayHost = "invalid-unreachable-host.local",
            notificationNotifier = mockNotifier
        )

        val decision = PermissionDecision(
            requestId = "req-1",
            decision = "once",
            issuedAt = Instant.now().toString(),
            nonce = UUID.randomUUID().toString(),
            deviceId = "test-device",
            signature = ""
        )

        val result = repo.sendDecision("pair-1", decision)
        // Since host is unreachable, result should fail gracefully without unhandled crash
        assertTrue(result.isFailure)
    }
}
