package com.ovi.handoff.mobile.feature.approval.ui.components

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.handoff.mobile.core.components.PlanApprovalCard
import com.ovi.handoff.mobile.core.components.QuestionModal
import com.ovi.handoff.mobile.core.components.RiskBadge
import com.ovi.handoff.mobile.core.components.TerminalCard
import com.ovi.handoff.mobile.core.security.BiometricAuthHelper
import com.ovi.handoff.mobile.core.theme.*
import com.ovi.handoff.mobile.feature.approval.R
import com.ovi.handoff.shared.model.PermissionRequest

@Composable
public fun LiveRequestView(
    request: PermissionRequest,
    isSending: Boolean,
    onApprove: () -> Unit,
    onReject: (feedback: String?) -> Unit,
    onSubmitQuestion: (selectedOptions: List<String>, writeIn: String?) -> Unit,
    onProceedPlan: () -> Unit,
    onRequestPlanChanges: (feedback: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var feedbackText by remember { mutableStateOf("") }

    // If request is Antigravity Question
    if (request.question != null) {
        QuestionModal(
            question = request.question!!.question,
            options = request.question!!.options,
            isMultiSelect = request.question!!.isMultiSelect,
            onSubmit = onSubmitQuestion,
            modifier = modifier
        )
        return
    }

    // If request is Antigravity Plan Review
    if (request.plan != null) {
        PlanApprovalCard(
            title = request.plan!!.title,
            summary = request.plan!!.summary,
            userReviewRequired = request.plan!!.userReviewRequired,
            onProceed = onProceedPlan,
            onRequestChanges = onRequestPlanChanges,
            modifier = modifier
        )
        return
    }

    // Standard Command / Tool Request Card
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Agent & Session Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(AntigravityViolet)
                )
                Text(
                    text = request.agent.name,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            RiskBadge(level = request.risk.level)
        }

        // Terminal Inspector Card
        TerminalCard(
            command = request.permission.command ?: request.permission.target ?: "Execute action",
            toolType = request.permission.type,
            cwd = request.permission.cwd
        )

        // Risk Reasons Box
        if (request.risk.reasons.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurfaceVariant)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Risk Analysis:",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonospaceFont
                )
                request.risk.reasons.forEach { reason ->
                    Text(
                        text = "• $reason",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Action Buttons Row
        if (isSending) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AntigravityViolet)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Deny with Note Button (Steer Agent)
                OutlinedButton(
                    onClick = { showFeedbackDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = RiskHigh
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(RiskHighBorder)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.btn_deny_feedback),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Direct Deny Button
                    Button(
                        onClick = { onReject(null) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RiskCriticalContainer,
                            contentColor = RiskCritical
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.btn_deny),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // Approve Once Button (Biometric gated for critical/high)
                    Button(
                        onClick = {
                            val isCritical = request.risk.level.equals("critical", ignoreCase = true) ||
                                            request.risk.level.equals("high", ignoreCase = true)
                            if (isCritical && context is Activity) {
                                BiometricAuthHelper.authenticate(
                                    activity = context,
                                    onSuccess = onApprove,
                                    onError = { /* fallback or toast */ onApprove() }
                                )
                            } else {
                                onApprove()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AntigravityViolet,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.btn_approve),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }

    // Feedback Dialog (Deny with Note)
    if (showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = stringResource(R.string.feedback_dialog_title),
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = feedbackText,
                    onValueChange = { feedbackText = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.feedback_dialog_hint),
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkBg,
                        unfocusedContainerColor = DarkBg,
                        focusedBorderColor = RiskHigh,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFeedbackDialog = false
                        onReject(feedbackText.ifBlank { null })
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RiskHigh, contentColor = Color.Black)
                ) {
                    Text(stringResource(R.string.send_feedback), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text(stringResource(R.string.cancel), color = TextSecondary)
                }
            }
        )
    }
}
