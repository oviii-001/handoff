package com.ovi.handoff.mobile.core.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.handoff.mobile.core.theme.ShapeFull

public data class AgentFilterOption(
    val id: String?,
    val displayName: String
)

private data class AgentFilterItem(
    val id: String?,
    val label: String,
    val icon: ImageVector? = null,
    val selectedContainerColor: Color,
    val selectedContentColor: Color
)

@Composable
public fun AgentFilterRow(
    availableAgents: List<AgentFilterOption>,
    selectedAgentId: String?,
    onSelectAgent: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (availableAgents.size <= 1) {
        return
    }

    val colorScheme = MaterialTheme.colorScheme

    val filterItems = remember(availableAgents, colorScheme) {
        listOf(
            AgentFilterItem(
                id = null,
                label = "All",
                icon = Icons.Outlined.Hub,
                selectedContainerColor = colorScheme.primaryContainer,
                selectedContentColor = colorScheme.onPrimaryContainer
            )
        ) + availableAgents.map { option ->
            val visuals = resolveAgentVisuals(option.id ?: "", colorScheme)
            AgentFilterItem(
                id = option.id,
                label = option.displayName,
                icon = visuals.icon, // null for antigravity, codex, cursor
                selectedContainerColor = visuals.containerColor,
                selectedContentColor = visuals.contentColor
            )
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        filterItems.forEach { filter ->
            val isSelected = (filter.id == selectedAgentId)

            val containerColor by animateColorAsState(
                targetValue = if (isSelected) filter.selectedContainerColor else colorScheme.surfaceContainerHigh,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "agentFilterContainer"
            )

            val contentColor by animateColorAsState(
                targetValue = if (isSelected) filter.selectedContentColor else colorScheme.onSurfaceVariant,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "agentFilterContent"
            )

            Row(
                modifier = Modifier
                    .clip(ShapeFull)
                    .background(containerColor)
                    .clickable { onSelectAgent(filter.id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (filter.icon != null) {
                    Icon(
                        imageVector = filter.icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = filter.label,
                    color = contentColor,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
