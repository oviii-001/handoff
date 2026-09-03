package com.ovi.handoff.mobile.feature.approval.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.handoff.mobile.core.components.RiskBadge
import com.ovi.handoff.mobile.core.components.StatusPill
import com.ovi.handoff.mobile.core.theme.*
import com.ovi.handoff.mobile.feature.approval.R
import com.ovi.handoff.shared.model.PermissionRequest

@Composable
public fun ActiveSessionDashboard(
    pairId: String,
    historyCount: Int,
    recentRequests: List<PermissionRequest>,
    onHaltAgent: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Standby Status Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusPill(isConnected = true, latencyMs = 42)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "ID: ${pairId.take(12)}...",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = MonospaceFont
                    )
                }
            }

            Text(
                text = stringResource(R.string.idle_title),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = stringResource(R.string.idle_subtitle),
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            HorizontalDivider(color = DarkBorder, thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Resolved Today",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Text(
                    text = "$historyCount actions",
                    color = TerminalGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonospaceFont
                )
            }
        }

        // Emergency Halt Agent Button
        Button(
            onClick = onHaltAgent,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = RiskCriticalContainer,
                contentColor = RiskCritical
            )
        ) {
            Text(
                text = stringResource(R.string.halt_agent),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        // Recent Activity Preview
        if (recentRequests.isNotEmpty()) {
            Text(
                text = "Recent Authorizations",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            recentRequests.take(3).forEach { req ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurface)
                        .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = req.permission.command ?: req.permission.target ?: "Action",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontFamily = MonospaceFont,
                            maxLines = 1
                        )
                        Text(
                            text = req.agent.name,
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    RiskBadge(level = req.risk.level)
                }
            }
        }
    }
}
