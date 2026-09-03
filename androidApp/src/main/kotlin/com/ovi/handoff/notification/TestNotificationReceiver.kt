package com.ovi.handoff.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ovi.handoff.mobile.domain.notification.NotificationNotifier
import com.ovi.handoff.shared.model.AgentInfo
import com.ovi.handoff.shared.model.PermissionInfo
import com.ovi.handoff.shared.model.PermissionRequest
import com.ovi.handoff.shared.model.RiskInfo
import com.ovi.handoff.shared.model.SessionInfo
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.time.Instant
import java.util.UUID

/**
 * Diagnostic and test receiver allowing automated testing of status-bar notifications
 * and shade actions via intent broadcast.
 */
public class TestNotificationReceiver : BroadcastReceiver(), KoinComponent {

    private val notificationNotifier: NotificationNotifier by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val agent = intent.getStringExtra(EXTRA_AGENT) ?: "antigravity"
        val command = intent.getStringExtra(EXTRA_COMMAND) ?: "git push origin main --force"
        val risk = intent.getStringExtra(EXTRA_RISK) ?: "critical"
        val pairId = intent.getStringExtra(EXTRA_PAIR_ID) ?: "session-test"
        val reqId = intent.getStringExtra(EXTRA_REQ_ID) ?: ("test-" + UUID.randomUUID().toString().take(8))

        Timber.d("Posting test notification: reqId=$reqId, agent=$agent, command=$command, risk=$risk")

        val request = PermissionRequest(
            id = reqId,
            protocolVersion = "1.0",
            agent = AgentInfo(
                id = agent,
                name = when (agent.lowercase()) {
                    "cursor" -> "Cursor"
                    "codex" -> "Codex"
                    else -> "Antigravity"
                }
            ),
            session = SessionInfo(id = pairId, project = "HandOff", workspace = "handoff"),
            permission = PermissionInfo(
                type = "shell",
                command = command,
                description = "Agent requested terminal execution permission"
            ),
            risk = RiskInfo(level = risk, reasons = listOf("Command execution: $command")),
            options = listOf("once", "session", "deny"),
            createdAt = Instant.now().toString(),
            expiresAt = Instant.now().plusSeconds(900).toString()
        )

        notificationNotifier.postPermissionRequestNotification(request, pairId)
    }

    public companion object {
        public const val ACTION_TRIGGER_TEST_NOTIFICATION: String = "com.ovi.handoff.ACTION_TRIGGER_TEST_NOTIFICATION"
        public const val EXTRA_AGENT: String = "agent"
        public const val EXTRA_COMMAND: String = "command"
        public const val EXTRA_RISK: String = "risk"
        public const val EXTRA_PAIR_ID: String = "pairId"
        public const val EXTRA_REQ_ID: String = "reqId"
    }
}
