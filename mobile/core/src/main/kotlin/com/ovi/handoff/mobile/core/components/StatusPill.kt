package com.ovi.handoff.mobile.core.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.handoff.mobile.core.R
import com.ovi.handoff.mobile.core.theme.MonospaceFont
import com.ovi.handoff.mobile.core.theme.RiskCriticalColor
import com.ovi.handoff.mobile.core.theme.RiskCriticalContainerColor
import com.ovi.handoff.mobile.core.theme.RiskLowColor
import com.ovi.handoff.mobile.core.theme.RiskLowContainerColor
import com.ovi.handoff.mobile.core.theme.RiskMediumColor
import com.ovi.handoff.mobile.core.theme.ShapeFull

/** Relay link states the pill can show. Mirrors the domain enum without depending on it. */
public enum class LinkState {
    CONNECTED,
    CONNECTING,
    OFFLINE
}

/**
 * Relay connection indicator.
 *
 * Takes a real state. Callers used to pass `isConnected = true` unconditionally, so the app displayed
 * "Relay Connected" while the socket was down, which is precisely when the user needs to know that a
 * decision will not reach their desktop.
 */
@Composable
public fun StatusPill(
    state: LinkState,
    latencyMs: Int? = null,
    modifier: Modifier = Modifier
) {
    val dotColor = when (state) {
        LinkState.CONNECTED -> RiskLowColor
        LinkState.CONNECTING -> RiskMediumColor
        LinkState.OFFLINE -> RiskCriticalColor
    }
    val containerColor = when (state) {
        LinkState.CONNECTED -> RiskLowContainerColor
        LinkState.CONNECTING -> MaterialTheme.colorScheme.surfaceContainerHighest
        LinkState.OFFLINE -> RiskCriticalContainerColor
    }
    val label = when (state) {
        LinkState.CONNECTED -> stringResource(R.string.status_relay_connected)
        LinkState.CONNECTING -> stringResource(R.string.status_reconnecting)
        LinkState.OFFLINE -> stringResource(R.string.status_relay_offline)
    }

    // Pulses only while connecting, so "trying" is distinguishable from "connected" at a glance
    // without the user having to read the label.
    val pulse = if (state == LinkState.CONNECTING) {
        val transition = rememberInfiniteTransition(label = "relay_pulse")
        val value by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "relay_pulse_alpha"
        )
        value
    } else {
        1f
    }

    Row(
        modifier = modifier
            .clip(ShapeFull)
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .alpha(pulse)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = MonospaceFont
        )
        if (state == LinkState.CONNECTED && latencyMs != null) {
            Text(
                text = "${latencyMs}ms",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontFamily = MonospaceFont
            )
        }
    }
}
