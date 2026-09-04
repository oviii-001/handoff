package com.ovi.handoff.mobile.feature.approval.ui.audit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.handoff.mobile.core.components.AgentBadge
import com.ovi.handoff.mobile.core.components.RiskBadge
import com.ovi.handoff.mobile.core.theme.MonospaceFont
import com.ovi.handoff.mobile.core.theme.ShapeFull
import com.ovi.handoff.mobile.core.theme.ShapeLarge
import com.ovi.handoff.mobile.core.theme.ShapeMedium
import com.ovi.handoff.mobile.feature.approval.R
import com.ovi.handoff.mobile.feature.approval.ui.model.PermissionRequestUiModel

@Composable
fun AuditScreen(
    modifier: Modifier = Modifier,
    requests: List<PermissionRequestUiModel>,
    searchQuery: String,
    filterRisk: String?,
    selectedAgentId: String? = null,
    onSearchChanged: (String) -> Unit,
    onFilterRiskChanged: (String?) -> Unit
) {
    val filtered = requests.filter { req ->
        val matchesQuery = searchQuery.isBlank() ||
                (req.command?.contains(searchQuery, ignoreCase = true) == true) ||
                (req.agentName.contains(searchQuery, ignoreCase = true)) ||
                (req.permissionType.contains(searchQuery, ignoreCase = true)) ||
                (req.description?.contains(searchQuery, ignoreCase = true) == true)

        val matchesRisk = filterRisk == null || req.riskLevel.equals(filterRisk, ignoreCase = true)

        val matchesAgent = selectedAgentId == null || (req.agentId.equals(selectedAgentId, ignoreCase = true))

        matchesQuery && matchesRisk && matchesAgent
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Search Bar with zero border
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChanged,
            placeholder = {
                Text(
                    text = stringResource(R.string.audit_search_hint),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            shape = ShapeLarge,
            singleLine = true
        )

        // Risk Filter Chips (stadium pills)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val chips = listOf(
                null to stringResource(R.string.filter_all),
                "critical" to stringResource(R.string.filter_critical),
                "high" to stringResource(R.string.filter_high),
                "medium" to stringResource(R.string.filter_medium),
                "low" to stringResource(R.string.filter_low)
            )

            chips.forEach { (riskValue, label) ->
                val isSelected = filterRisk == riskValue
                Box(
                    modifier = Modifier
                        .clip(ShapeFull)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        .clickable { onFilterRiskChanged(if (isSelected) null else riskValue) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        // List or Empty View
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.history_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { req ->
                    Card(
                        shape = ShapeLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    AgentBadge(
                                        agentId = req.agentId,
                                        agentName = req.agentName,
                                        version = req.agentVersion
                                    )
                                    val project = req.projectOrWorkspace
                                    if (!project.isNullOrBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .clip(ShapeFull)
                                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Folder,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Text(
                                                    text = project,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                                RiskBadge(level = req.riskLevel)
                            }

                            Text(
                                text = req.description ?: req.permissionType,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )

                            if (!req.command.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(ShapeMedium)
                                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                        .padding(8.dp)
                                    ) {
                                    Text(
                                        text = req.command!!,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = MonospaceFont,
                                        fontSize = 11.sp,
                                        maxLines = 2
                                    )
                                }
                            } else if (!req.diff.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(ShapeMedium)
                                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                        .padding(8.dp)
                                    ) {
                                    Text(
                                        text = stringResource(
                                            R.string.diff_patch_file,
                                            req.target ?: stringResource(R.string.diff_patch_default)
                                        ),
                                        color = MaterialTheme.colorScheme.tertiary,
                                        fontFamily = MonospaceFont,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            val projectOrWorkspace = req.projectOrWorkspace ?: ""
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = projectOrWorkspace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontFamily = MonospaceFont
                                )
                                Text(
                                    text = req.formattedTimestamp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontFamily = MonospaceFont
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
