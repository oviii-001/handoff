package com.ovi.handoff.mobile.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.handoff.mobile.core.theme.ShapeFull

data class AgentBadgeVisuals(
    val containerColor: Color,
    val contentColor: Color,
    val icon: ImageVector? = null
)

fun resolveAgentVisuals(agentId: String, colorScheme: ColorScheme): AgentBadgeVisuals {
    val normalized = agentId.lowercase()
    return when {
        normalized.contains("antigravity") -> AgentBadgeVisuals(
            containerColor = colorScheme.primaryContainer,
            contentColor = colorScheme.onPrimaryContainer,
            icon = null
        )
        normalized.contains("cursor") -> AgentBadgeVisuals(
            containerColor = colorScheme.secondaryContainer,
            contentColor = colorScheme.onSecondaryContainer,
            icon = null
        )
        normalized.contains("codex") -> AgentBadgeVisuals(
            containerColor = colorScheme.tertiaryContainer,
            contentColor = colorScheme.onTertiaryContainer,
            icon = null
        )
        else -> AgentBadgeVisuals(
            containerColor = colorScheme.surfaceContainerHighest,
            contentColor = colorScheme.onSurface,
            icon = null
        )
    }
}

fun formatAgentDisplayName(agentName: String?, agentId: String?): String {
    val raw = (agentName?.ifBlank { null } ?: agentId ?: "Agent").trim()
    return when {
        raw.contains("antigravity", ignoreCase = true) -> "Antigravity"
        raw.contains("cursor", ignoreCase = true) -> "Cursor"
        raw.contains("codex", ignoreCase = true) -> "Codex"
        raw.contains("claude", ignoreCase = true) -> "Claude"
        raw.contains("windsurf", ignoreCase = true) -> "Windsurf"
        raw.contains("copilot", ignoreCase = true) -> "Copilot"
        else -> raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

@Composable
fun AgentBadge(
    modifier: Modifier = Modifier,
    agentId: String,
    agentName: String? = null,
    version: String? = null
) {
    val visuals = resolveAgentVisuals(agentId, MaterialTheme.colorScheme)
    val displayName = formatAgentDisplayName(agentName, agentId)

    Row(
        modifier = modifier
            .clip(ShapeFull)
            .background(visuals.containerColor)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (visuals.icon != null) {
            Icon(
                imageVector = visuals.icon,
                contentDescription = null,
                tint = visuals.contentColor,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = displayName,
            color = visuals.contentColor,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
        if (!version.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .clip(ShapeFull)
                    .background(visuals.contentColor.copy(alpha = 0.15f))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "v$version",
                    color = visuals.contentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
