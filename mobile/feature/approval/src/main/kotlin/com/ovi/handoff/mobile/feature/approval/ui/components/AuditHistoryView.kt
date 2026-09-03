package com.ovi.handoff.mobile.feature.approval.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.handoff.mobile.core.components.RiskBadge
import com.ovi.handoff.mobile.core.theme.*
import com.ovi.handoff.mobile.feature.approval.R
import com.ovi.handoff.shared.model.PermissionRequest

@Composable
public fun AuditHistoryView(
    requests: List<PermissionRequest>,
    searchQuery: String,
    filterRisk: String?,
    onSearchChanged: (String) -> Unit,
    onFilterRiskChanged: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current

    val filtered = requests.filter { req ->
        val matchesQuery = searchQuery.isBlank() ||
                (req.permission.command?.contains(searchQuery, ignoreCase = true) == true) ||
                (req.agent.name.contains(searchQuery, ignoreCase = true)) ||
                (req.permission.type.contains(searchQuery, ignoreCase = true))

        val matchesRisk = filterRisk == null || req.risk.level.equals(filterRisk, ignoreCase = true)

        matchesQuery && matchesRisk
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChanged,
            placeholder = { Text(stringResource(R.string.audit_search_hint), fontSize = 13.sp, color = TextMuted) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface,
                focusedBorderColor = AntigravityViolet,
                unfocusedBorderColor = DarkBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        // Risk Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) AntigravityViolet else DarkSurfaceVariant)
                        .clickable { onFilterRiskChanged(if (isSelected) null else riskValue) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) androidx.compose.ui.graphics.Color.White else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // History List
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.history_empty),
                    color = TextMuted,
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurface)
                            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                clipboardManager.setText(
                                    AnnotatedString(req.permission.command ?: req.permission.target ?: "")
                                )
                            }
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = req.agent.name,
                                color = AntigravityVioletLight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = MonospaceFont
                            )
                            RiskBadge(level = req.risk.level)
                        }

                        Text(
                            text = req.permission.command ?: req.permission.target ?: "Action",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontFamily = MonospaceFont,
                            maxLines = 2
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = req.createdAt.take(19).replace("T", " "),
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = MonospaceFont
                            )
                            Text(
                                text = "Resolved ✓",
                                color = TerminalGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = MonospaceFont
                            )
                        }
                    }
                }
            }
        }
    }
}
