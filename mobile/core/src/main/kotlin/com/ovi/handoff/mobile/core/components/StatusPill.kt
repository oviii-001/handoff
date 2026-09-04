package com.ovi.handoff.mobile.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.handoff.mobile.core.R
import com.ovi.handoff.mobile.core.theme.MonospaceFont
import com.ovi.handoff.mobile.core.theme.RiskCriticalColor
import com.ovi.handoff.mobile.core.theme.RiskCriticalContainerColor
import com.ovi.handoff.mobile.core.theme.RiskLowColor
import com.ovi.handoff.mobile.core.theme.RiskLowContainerColor
import com.ovi.handoff.mobile.core.theme.ShapeFull

@Composable
fun StatusPill(
    isConnected: Boolean,
    latencyMs: Int? = null,
    modifier: Modifier = Modifier
) {
    val dotColor = if (isConnected) RiskLowColor else RiskCriticalColor
    val labelColor = if (isConnected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val containerColor = if (isConnected) RiskLowContainerColor else RiskCriticalContainerColor

    Row(
        modifier = modifier
            .clip(ShapeFull)
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = if (isConnected) stringResource(R.string.status_relay_connected) else stringResource(R.string.status_reconnecting),
            color = labelColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = MonospaceFont
        )
        if (isConnected && latencyMs != null) {
            Text(
                text = "${latencyMs}ms",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontFamily = MonospaceFont
            )
        }
    }
}
