package com.ovi.handoff.mobile.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.handoff.mobile.core.theme.*

@Composable
public fun RiskBadge(
    level: String,
    modifier: Modifier = Modifier
) {
    val (color, containerColor, borderColor) = when (level.lowercase()) {
        "critical" -> Triple(RiskCritical, RiskCriticalContainer, RiskCriticalBorder)
        "high" -> Triple(RiskHigh, RiskHighContainer, RiskHighBorder)
        "medium" -> Triple(RiskMedium, RiskMediumContainer, RiskMediumBorder)
        else -> Triple(RiskLow, RiskLowContainer, RiskLowBorder)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Text(
            text = level.uppercase(),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = MonospaceFont
        )
    }
}
