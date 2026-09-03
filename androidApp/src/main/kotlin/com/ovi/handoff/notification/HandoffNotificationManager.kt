package com.ovi.handoff.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ovi.handoff.MainActivity
import com.ovi.handoff.R
import com.ovi.handoff.mobile.domain.notification.NotificationNotifier
import com.ovi.handoff.shared.model.PermissionRequest
import com.ovi.handoff.shared.model.resolveProjectOrWorkspace
import com.ovi.handoff.shared.model.cleanName
import timber.log.Timber

/**
 * Production-grade notification manager for HandOff.
 * Dispatches high-priority heads-up notifications with direct action buttons
 * ("Approve Once" and "Deny") so developers can authorize coding agents directly from the notification shade.
 */
public class HandoffNotificationManager(
    private val context: Context
) : NotificationNotifier {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Agent Authorizations",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority notifications for agent permission and tool requests"
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun postPermissionRequestNotification(request: PermissionRequest, pairId: String) {
        try {
            // Intent to open app directly onto the request
            val contentIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("requestId", request.id)
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                request.id.hashCode(),
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Direct "Approve Once" Action Intent
            val approveIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_APPROVE
                putExtra(NotificationActionReceiver.EXTRA_REQUEST_ID, request.id)
                putExtra(NotificationActionReceiver.EXTRA_PAIR_ID, pairId)
                putExtra(NotificationActionReceiver.EXTRA_DECISION_TYPE, "approve_once")
            }
            val approvePendingIntent = PendingIntent.getBroadcast(
                context,
                (request.id + "_approve").hashCode(),
                approveIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Direct "Deny" Action Intent
            val denyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_DENY
                putExtra(NotificationActionReceiver.EXTRA_REQUEST_ID, request.id)
                putExtra(NotificationActionReceiver.EXTRA_PAIR_ID, pairId)
                putExtra(NotificationActionReceiver.EXTRA_DECISION_TYPE, "deny")
            }
            val denyPendingIntent = PendingIntent.getBroadcast(
                context,
                (request.id + "_deny").hashCode(),
                denyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val detailText = when {
                request.permission.command != null -> "$> ${request.permission.command}"
                request.permission.diff != null -> "Patch: ${request.permission.diff?.take(120)}..."
                request.question != null -> "Question: ${request.question?.question}"
                request.plan != null -> "Plan: ${request.plan?.summary}"
                else -> request.permission.description ?: "Authorization Required"
            }

            val projectOrWorkspace = request.resolveProjectOrWorkspace()
            val agentName = request.agent.cleanName()
            val title = if (!projectOrWorkspace.isNullOrBlank()) {
                "[$projectOrWorkspace] $agentName: ${request.permission.type.uppercase()}"
            } else {
                "$agentName: ${request.permission.type.uppercase()}"
            }

            val projectHeader = if (!projectOrWorkspace.isNullOrBlank()) {
                "Workspace: $projectOrWorkspace\n"
            } else ""

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_shield)
                .setContentTitle(title)
                .apply {
                    if (!projectOrWorkspace.isNullOrBlank()) {
                        setSubText(projectOrWorkspace)
                    }
                }
                .setContentText(detailText)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("${projectHeader}$detailText\n\nRisk: ${request.risk.level.uppercase()} • Tap an action below to respond immediately.")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(contentPendingIntent)
                .addAction(
                    android.R.drawable.ic_media_play,
                    "Approve Once",
                    approvePendingIntent
                )
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Deny",
                    denyPendingIntent
                )

            notificationManager.notify(request.id.hashCode(), builder.build())
            Timber.d("Posted authorization notification for request ${request.id}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to post notification for request ${request.id}")
        }
    }

    override fun dismissNotification(requestId: String) {
        notificationManager.cancel(requestId.hashCode())
    }

    public companion object {
        public const val CHANNEL_ID: String = "handoff_agent_authorizations"
    }
}
