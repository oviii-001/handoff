package com.ovi.handoff.mobile.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.handoff.mobile.core.theme.*

@Composable
public fun PlanApprovalCard(
    title: String,
    summary: String,
    userReviewRequired: List<String> = emptyList(),
    onProceed: () -> Unit,
    onRequestChanges: (feedback: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showChangesInput by remember { mutableStateOf(false) }
    var changesFeedback by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .border(1.dp, AntigravityVioletLight.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Tag
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AntigravityVioletDark)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "ANTIGRAVITY PLAN REVIEW",
                    color = AntigravityVioletLight,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonospaceFont
                )
            }
        }

        // Title
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 24.sp
        )

        // Summary box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurfaceVariant)
                .padding(12.dp)
        ) {
            Text(
                text = summary,
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }

        // Warnings / User Review Required
        if (userReviewRequired.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(RiskHighContainer)
                    .border(1.dp, RiskHighBorder, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "⚠️ User Review Required",
                    color = RiskHigh,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                userReviewRequired.forEach { item ->
                    Text(
                        text = "• $item",
                        color = TextPrimary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Changes Feedback Input (if toggled)
        if (showChangesInput) {
            OutlinedTextField(
                value = changesFeedback,
                onValueChange = { changesFeedback = it },
                placeholder = { Text("Describe changes needed for Antigravity...", fontSize = 13.sp, color = TextMuted) },
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
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!showChangesInput) {
                OutlinedButton(
                    onClick = { showChangesInput = true },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder))
                ) {
                    Text("Request Changes", fontSize = 13.sp)
                }
            } else {
                Button(
                    onClick = {
                        if (changesFeedback.isNotBlank()) {
                            onRequestChanges(changesFeedback)
                        }
                    },
                    enabled = changesFeedback.isNotBlank(),
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RiskHigh,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Send Feedback", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Button(
                onClick = onProceed,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AntigravityViolet,
                    contentColor = Color.White
                )
            ) {
                Text("Proceed", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}
