package com.ovi.handoff.mobile.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.handoff.mobile.core.theme.MonospaceFont
import com.ovi.handoff.mobile.core.theme.RiskCriticalColor
import com.ovi.handoff.mobile.core.theme.RiskCriticalContainerColor
import com.ovi.handoff.mobile.core.theme.RiskHighColor
import com.ovi.handoff.mobile.core.theme.RiskHighContainerColor
import com.ovi.handoff.mobile.core.theme.RiskLowColor
import com.ovi.handoff.mobile.core.theme.RiskLowContainerColor
import com.ovi.handoff.mobile.core.theme.RiskMediumColor
import com.ovi.handoff.mobile.core.theme.RiskMediumContainerColor
import com.ovi.handoff.mobile.core.theme.ShapeFull

@Composable
fun RiskBadge(
    level: String,
    modifier: Modifier = Modifier
) {
    val (color, containerColor) = when (level.lowercase()) {
        "critical" -> RiskCriticalColor to RiskCriticalContainerColor
        "high" -> RiskHighColor to RiskHighContainerColor
        "medium" -> RiskMediumColor to RiskMediumContainerColor
        else -> RiskLowColor to RiskLowContainerColor
    }

    Row(
        modifier = modifier
            .clip(ShapeFull)
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(ShapeFull)
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
