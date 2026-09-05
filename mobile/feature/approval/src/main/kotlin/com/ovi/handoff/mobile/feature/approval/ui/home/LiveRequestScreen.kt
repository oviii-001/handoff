package com.ovi.handoff.mobile.feature.approval.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.ovi.handoff.mobile.core.components.AgentBadge
import com.ovi.handoff.mobile.core.components.DiffViewerSnippet
import com.ovi.handoff.mobile.core.components.PlanApprovalCard
import com.ovi.handoff.mobile.core.components.QuestionModal
import com.ovi.handoff.mobile.core.components.RiskBadge
import com.ovi.handoff.mobile.core.components.TerminalSnippet
import com.ovi.handoff.mobile.core.security.BiometricAuthHelper
import com.ovi.handoff.mobile.core.theme.MonospaceFont
import com.ovi.handoff.mobile.core.theme.RiskCriticalColor
import com.ovi.handoff.mobile.core.theme.ShapeExtraLarge
import com.ovi.handoff.mobile.core.theme.ShapeFull
import com.ovi.handoff.mobile.core.theme.ShapeLarge
import com.ovi.handoff.mobile.core.theme.ShapeMedium
import com.ovi.handoff.mobile.domain.repository.ConnectionState
import com.ovi.handoff.mobile.feature.approval.R
import com.ovi.handoff.mobile.feature.approval.ui.model.PermissionRequestUiModel
import kotlinx.coroutines.delay

/**
 * Clean, production-grade Live Request Screen.
 *
 * Presents authorization requests with zero nested cards and zero duplicate badges.
 * Terminal commands and diffs are directly embedded in the single unified request container.
 */
@Composable
fun LiveRequestScreen(
    request: PermissionRequestUiModel,
    queueIndex: Int,
    queueSize: Int,
    isSending: Boolean,
    connectionState: ConnectionState,
    connectionError: String? = null,
    requireBiometricsForCritical: Boolean,
    onApprove: () -> Unit,
    onReject: (feedback: String?) -> Unit,
    onSubmitQuestion: (selectedOptions: List<String>, writeIn: String?) -> Unit,
    onProceedPlan: () -> Unit,
    onRequestPlanChanges: (feedback: String) -> Unit,
    onShowPrevious: () -> Unit,
    onShowNext: () -> Unit,
    onBlocked: (String) -> Unit,
    onExtendDeadline: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var feedbackText by remember(request.id) { mutableStateOf("") }

    val gate: (() -> Unit) -> Unit = remember(request.id, requireBiometricsForCritical) {
        { action ->
            if (!requireBiometricsForCritical || !request.isCritical) {
                action()
            } else {
                val activity = context as? FragmentActivity
                if (activity == null) {
                    onBlocked(context.getString(R.string.biometric_auth_subtitle))
                } else {
                    BiometricAuthHelper.authenticate(
                        activity = activity,
                        title = context.getString(R.string.biometric_auth_title),
                        subtitle = context.getString(R.string.biometric_auth_subtitle),
                        onSuccess = action,
                        onFailure = { message -> if (message.isNotBlank()) onBlocked(message) },
                        onUnavailable = { message ->
                            onBlocked(message)
                        }
                    )
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (connectionState != ConnectionState.CONNECTED) {
            ConnectionBanner(connectionState, connectionError)
        }

        QueueHeader(
            queueIndex = queueIndex,
            queueSize = queueSize,
            expiresAtEpochMs = request.expiresAtEpochMs,
            onExtendDeadline = onExtendDeadline,
            onShowPrevious = onShowPrevious,
            onShowNext = onShowNext
        )

        when {
            request.question != null -> QuestionModal(
                question = request.question.question,
                options = request.question.options,
                isMultiSelect = request.question.isMultiSelect,
                agentId = request.agentId,
                agentName = request.agentName,
                projectOrWorkspace = request.workspaceLabel,
                onSubmit = onSubmitQuestion
            )

            request.plan != null -> PlanApprovalCard(
                title = request.plan.title,
                summary = request.plan.summary,
                userReviewRequired = request.plan.userReviewRequired,
                agentId = request.agentId,
                agentName = request.agentName,
                projectOrWorkspace = request.workspaceLabel,
                onProceed = { gate(onProceedPlan) },
                onRequestChanges = onRequestPlanChanges
            )

            else -> {
                // Single unified request surface: no nested cards, no duplicate badges
                UnifiedRequestCard(request = request)

                ActionButtons(
                    isCritical = request.isCritical,
                    requiresBiometrics = requireBiometricsForCritical && request.isCritical,
                    isSending = isSending,
                    onApprove = { gate(onApprove) },
                    onDenyWithNote = { showFeedbackDialog = true },
                    onDeny = { onReject(null) }
                )
            }
        }
    }

    if (showFeedbackDialog) {
        FeedbackDialog(
            value = feedbackText,
            onValueChange = { feedbackText = it },
            onDismiss = { showFeedbackDialog = false },
            onSend = {
                val note = feedbackText.trim().ifEmpty { null }
                showFeedbackDialog = false
                onReject(note)
            }
        )
    }
}

/**
 * Unified Request Card.
 * Combines agent metadata, operation title, risk callout, and embedded payload viewer
 * into ONE sleek Material 3 surface.
 */
@Composable
private fun UnifiedRequestCard(
    request: PermissionRequestUiModel,
    modifier: Modifier = Modifier
) {
    Card(
        shape = ShapeExtraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Agent Badge & Workspace on Left, Risk Badge on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.label_ide_tag),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        AgentBadge(
                            agentId = request.agentId,
                            agentName = request.agentName,
                            version = request.agentVersion
                        )
                    }
                    request.workspaceLabel?.takeIf { it.isNotBlank() }?.let { label ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.label_working_tag),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(ShapeFull)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(horizontal = 9.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = label,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                RiskBadge(
                    level = request.riskLevel,
                    modifier = Modifier.semantics {
                        contentDescription = "Risk level: ${request.riskLevel}"
                    }
                )
            }

            // Operation Title / Intent
            Text(
                text = request.description ?: request.permissionType,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                lineHeight = 22.sp
            )

            // Risk Justifications (rendered as a sleek alert callout)
            if (request.riskReasons.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShapeMedium)
                        .background(
                            if (request.isCritical) RiskCriticalColor.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    request.riskReasons.forEach { reason ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = if (request.isCritical) RiskCriticalColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(14.dp)
                            )
                            Text(
                                text = reason,
                                color = if (request.isCritical) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }

            // Embedded Payload Viewport (No separate nested cards)
            if (!request.diff.isNullOrBlank()) {
                DiffViewerSnippet(
                    filePath = request.target ?: stringResource(R.string.code_changes_default),
                    diffContent = request.diff
                )
            } else if (!request.command.isNullOrBlank()) {
                TerminalSnippet(
                    command = request.command,
                    cwd = request.cwd ?: request.target
                )
            } else {
                // Secondary details (e.g. file or directory targets)
                val details = remember(request.id) {
                    buildList {
                        request.workspace?.takeIf { it.isNotBlank() }?.let { add(R.string.label_workspace to it) }
                        request.cwd?.takeIf { it.isNotBlank() && it != request.workspace }?.let { add(R.string.label_cwd to it) }
                    }
                }
                if (details.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ShapeMedium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        details.forEach { (labelRes, value) ->
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    text = stringResource(labelRes),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = value,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontFamily = MonospaceFont
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionBanner(state: ConnectionState, reason: String? = null) {
    // A specific reason always beats the generic banner: "no computer has claimed this pairing code"
    // tells the user what to do, where "You are offline" leaves them guessing.
    val message = reason?.takeIf { it.isNotBlank() }
        ?: if (state == ConnectionState.CONNECTING) {
            stringResource(R.string.connection_connecting_banner)
        } else {
            stringResource(R.string.connection_offline_banner)
        }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeMedium)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.WifiOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun QueueHeader(
    queueIndex: Int,
    queueSize: Int,
    expiresAtEpochMs: Long?,
    onExtendDeadline: () -> Unit,
    onShowPrevious: () -> Unit,
    onShowNext: () -> Unit
) {
    var remainingMs by remember(expiresAtEpochMs) {
        mutableLongStateOf(expiresAtEpochMs?.minus(System.currentTimeMillis())?.coerceAtLeast(0L) ?: -1L)
    }

    LaunchedEffect(expiresAtEpochMs) {
        if (expiresAtEpochMs == null) return@LaunchedEffect
        while (true) {
            remainingMs = (expiresAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
            if (remainingMs <= 0L) break
            delay(1_000)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.queue_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (remainingMs >= 0L) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = ShapeFull,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (remainingMs <= 0L) {
                                        stringResource(R.string.expires_soon)
                                    } else {
                                        stringResource(R.string.expires_in, formatRemaining(remainingMs))
                                    },
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontFamily = MonospaceFont
                                )
                            }
                        }

                        AssistChip(
                            onClick = onExtendDeadline,
                            label = {
                                Text(
                                    text = stringResource(R.string.btn_extend_time),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp)
                                )
                            },
                            shape = ShapeFull,
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                labelColor = MaterialTheme.colorScheme.primary
                            ),
                            border = null,
                            modifier = Modifier.height(26.dp)
                        )
                    }
                }
            }

            if (queueSize > 1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onShowPrevious, enabled = queueIndex > 0) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.queue_previous),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.queue_position, queueIndex + 1, queueSize),
                        fontSize = 12.sp,
                        fontFamily = MonospaceFont,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = onShowNext, enabled = queueIndex < queueSize - 1) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.queue_next),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButtons(
    isCritical: Boolean,
    requiresBiometrics: Boolean,
    isSending: Boolean,
    onApprove: () -> Unit,
    onDenyWithNote: () -> Unit,
    onDeny: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onApprove,
            enabled = !isSending,
            shape = ShapeFull,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isCritical) RiskCriticalColor else MaterialTheme.colorScheme.primary,
                contentColor = if (isCritical) Color.White else MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .semantics { contentDescription = "Approve this request" }
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                if (requiresBiometrics) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(R.string.btn_approve),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = onDenyWithNote,
                enabled = !isSending,
                shape = ShapeFull,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .weight(1.4f)
                    .height(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.EditNote,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.btn_deny_feedback),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            FilledTonalButton(
                onClick = onDeny,
                enabled = !isSending,
                shape = ShapeFull,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .semantics { contentDescription = "Deny this request" }
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.btn_deny),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun FeedbackDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSend: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.feedback_dialog_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.feedback_guidance_prompt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.feedback_dialog_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    },
                    shape = ShapeLarge,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSend,
                shape = ShapeFull,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(text = stringResource(R.string.send_feedback), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = ShapeExtraLarge
    )
}

private const val URGENT_THRESHOLD_MS = 30_000L
private const val TOTAL_WINDOW_MS = 300_000f

private fun formatRemaining(millis: Long): String {
    val totalSeconds = millis / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}
