package com.ovi.handoff.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.text.HtmlCompat
import com.ovi.handoff.MainActivity
import com.ovi.handoff.R
import com.ovi.handoff.mobile.domain.notification.NotificationNotifier
import com.ovi.handoff.shared.model.PermissionRequest
import com.ovi.handoff.shared.model.cleanName
import com.ovi.handoff.shared.model.resolveProjectOrWorkspace
import timber.log.Timber

/**
 * Production-grade, Material 3 Expressive notification manager for HandOff.
 *
 * Dispatches risk-adaptive, beautifully formatted heads-up notifications with
 * monospace code/terminal blocks, agent branding badges, and contextual direct action
 * buttons tailored to commands, plans, and questions.
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
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 80, 200)
                enableLights(true)
                lightColor = Color.RED
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun postPermissionRequestNotification(request: PermissionRequest, pairId: String) {
        try {
            val agentName = request.agent.cleanName()
            val projectOrWorkspace = request.resolveProjectOrWorkspace()
            val riskLevel = request.risk.level.lowercase()
            val isCritical = riskLevel == "critical" || riskLevel == "high"
            val riskColor = getRiskColor(riskLevel)

            // Content intent to open the app directly to the request
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

            // Action: Approve Once
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

            // Action: Deny
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

            // Title styling based on type and risk
            val title = when {
                request.question != null -> context.getString(R.string.notif_title_question, agentName)
                request.plan != null -> context.getString(R.string.notif_title_plan, agentName)
                isCritical -> context.getString(R.string.notif_title_critical, request.permission.type.uppercase())
                else -> context.getString(R.string.notif_title_action, agentName, request.permission.type.uppercase())
            }

            val subText = if (!projectOrWorkspace.isNullOrBlank()) {
                context.getString(R.string.notif_subtext_format, projectOrWorkspace, agentName)
            } else {
                agentName
            }

            val summaryText = when {
                request.permission.command != null -> "$> ${request.permission.command}"
                request.permission.diff != null -> "Patch: ${request.permission.diff?.take(90)}..."
                request.question != null -> request.question?.question.orEmpty()
                request.plan != null -> request.plan?.summary.orEmpty()
                else -> request.permission.description ?: "Authorization Required"
            }

            // Rich HTML-formatted BigText for expanded notification
            val bigHtml = buildString {
                if (!projectOrWorkspace.isNullOrBlank()) {
                    append("<b><font color=\"#90CAF9\">📁 Workspace:</font></b> ")
                    append("<b>").append(projectOrWorkspace).append("</b><br/><br/>")
                }

                when {
                    request.permission.command != null -> {
                        append("<b><font color=\"#81C784\">Terminal Command:</font></b><br/>")
                        append("<tt><b>$&gt; ").append(request.permission.command).append("</b></tt><br/><br/>")
                    }
                    request.permission.diff != null -> {
                        append("<b><font color=\"#64B5F6\">Code Patch:</font></b><br/>")
                        val diffLines = request.permission.diff?.lines()?.take(5)?.joinToString("<br/>") { line ->
                            val clean = line.replace("<", "&lt;").replace(">", "&gt;")
                            when {
                                clean.startsWith("+") -> "<font color=\"#81C784\">$clean</font>"
                                clean.startsWith("-") -> "<font color=\"#E57373\">$clean</font>"
                                else -> clean
                            }
                        } ?: ""
                        append("<tt>").append(diffLines).append("</tt><br/><br/>")
                    }
                    request.question != null -> {
                        append("<b><font color=\"#FFB74D\">Question:</font></b><br/>")
                        append("<b>").append(request.question?.question).append("</b><br/><br/>")
                    }
                    request.plan != null -> {
                        append("<b><font color=\"#FFD54F\">Plan Summary:</font></b><br/>")
                        append(request.plan?.summary).append("<br/><br/>")
                    }
                }

                val riskHex = when (riskLevel) {
                    "critical" -> "#EF5350"
                    "high" -> "#FFA726"
                    "medium" -> "#42A5F5"
                    else -> "#66BB6A"
                }
                append("<font color=\"").append(riskHex).append("\"><b>● ").append(riskLevel.uppercase()).append(" RISK</b></font>")
                val reason = request.risk.reasons.firstOrNull()
                if (!reason.isNullOrBlank()) {
                    append(" — ").append(reason)
                }
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_shield)
                .setContentTitle(title)
                .setSubText(subText)
                .setContentText(summaryText)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(HtmlCompat.fromHtml(bigHtml, HtmlCompat.FROM_HTML_MODE_COMPACT))
                        .setSummaryText(subText)
                )
                .setColor(riskColor)
                .setColorized(isCritical)
                .setPriority(if (isCritical) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
                .setCategory(if (isCritical) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(contentPendingIntent)

            val ttl = (request.expiresAtEpochMs ?: 0L) - System.currentTimeMillis()
            if (ttl > 0) {
                builder.setTimeoutAfter(ttl)
            }

            // Contextual actions tailored to the request payload
            when {
                request.question != null -> {
                    builder.addAction(
                        R.drawable.ic_notif_answer,
                        context.getString(R.string.notif_action_answer),
                        contentPendingIntent
                    )
                    builder.addAction(
                        R.drawable.ic_notif_deny,
                        context.getString(R.string.notif_action_dismiss),
                        denyPendingIntent
                    )
                }
                request.plan != null -> {
                    builder.addAction(
                        R.drawable.ic_notif_open,
                        context.getString(R.string.notif_action_review_plan),
                        contentPendingIntent
                    )
                    builder.addAction(
                        R.drawable.ic_notif_deny,
                        context.getString(R.string.notif_action_reject),
                        denyPendingIntent
                    )
                }
                else -> {
                    builder.addAction(
                        R.drawable.ic_notif_approve,
                        context.getString(R.string.notif_action_approve_once),
                        approvePendingIntent
                    )
                    builder.addAction(
                        R.drawable.ic_notif_deny,
                        context.getString(R.string.notif_action_deny),
                        denyPendingIntent
                    )
                }
            }

            notificationManager.notify(request.id.hashCode(), builder.build())
            Timber.d("Posted enhanced authorization notification for request ${request.id}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to post enhanced notification for request ${request.id}")
        }
    }

    override fun dismissNotification(requestId: String) {
        notificationManager.cancel(requestId.hashCode())
    }


    private fun getRiskColor(riskLevel: String): Int = when (riskLevel.lowercase()) {
        "critical" -> Color.parseColor("#D32F2F")
        "high" -> Color.parseColor("#E65100")
        "medium" -> Color.parseColor("#0288D1")
        else -> Color.parseColor("#2E7D32")
    }

    public companion object {
        public const val CHANNEL_ID: String = "handoff_agent_authorizations"
    }
}
