package com.ovi.handoff.mobile.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.ovi.handoff.mobile.core.theme.DiffAddContainer
import com.ovi.handoff.mobile.core.theme.DiffAddOnContainer
import com.ovi.handoff.mobile.core.theme.DiffRemoveContainer
import com.ovi.handoff.mobile.core.theme.DiffRemoveOnContainer
import com.ovi.handoff.mobile.core.theme.MonospaceFont
import com.ovi.handoff.mobile.core.theme.ShapeExtraLarge
import com.ovi.handoff.mobile.core.theme.ShapeFull
import com.ovi.handoff.mobile.core.theme.ShapeMedium

@Composable
fun DiffViewerCard(
    filePath: String,
    diffContent: String,
    agentId: String,
    agentName: String? = null,
    projectOrWorkspace: String? = null,
    modifier: Modifier = Modifier
) {
    val lines = diffContent.lines()
    val additions = lines.count { it.startsWith("+") && !it.startsWith("+++") }
    val deletions = lines.count { it.startsWith("-") && !it.startsWith("---") }

    Card(
        shape = ShapeExtraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row with Agent Badge & File Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AgentBadge(agentId = agentId, agentName = agentName)

                    if (!projectOrWorkspace.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(ShapeFull)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = projectOrWorkspace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Stats Pills (+N / -N)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (additions > 0) {
                        Box(
                            modifier = Modifier
                                .clip(ShapeFull)
                                .background(DiffAddContainer)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.diff_lines_added, additions),
                                color = DiffAddOnContainer,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (deletions > 0) {
                        Box(
                            modifier = Modifier
                                .clip(ShapeFull)
                                .background(DiffRemoveContainer)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.diff_lines_removed, deletions),
                                color = DiffRemoveOnContainer,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // File Target Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ShapeMedium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = filePath,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = MonospaceFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
            }

            // Code Diff Inspector Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ShapeMedium)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(vertical = 8.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                lines.forEach { line ->
                    val (lineBg, lineText) = when {
                        line.startsWith("+") && !line.startsWith("+++") ->
                            DiffAddContainer.copy(alpha = 0.6f) to DiffAddOnContainer
                        line.startsWith("-") && !line.startsWith("---") ->
                            DiffRemoveContainer.copy(alpha = 0.6f) to DiffRemoveOnContainer
                        else ->
                            MaterialTheme.colorScheme.surfaceContainerLowest to MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(lineBg)
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = line,
                            fontFamily = MonospaceFont,
                            color = lineText,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}
