package com.ovi.handoff.mobile.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.handoff.mobile.core.theme.*

@Composable
public fun StatusPill(
    isConnected: Boolean,
    latencyMs: Int? = null,
    modifier: Modifier = Modifier
) {
    val dotColor = if (isConnected) TerminalGreen else RiskCritical
    val labelColor = if (isConnected) TextPrimary else TextSecondary
    val bgColor = if (isConnected) RiskLowContainer else RiskCriticalContainer
    val borderColor = if (isConnected) RiskLowBorder else RiskCriticalBorder

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = if (isConnected) "Relay Connected" else "Reconnecting...",
            color = labelColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = MonospaceFont
        )
        if (isConnected && latencyMs != null) {
            Text(
                text = "${latencyMs}ms",
                color = TextMuted,
                fontSize = 11.sp,
                fontFamily = MonospaceFont
            )
        }
    }
}
