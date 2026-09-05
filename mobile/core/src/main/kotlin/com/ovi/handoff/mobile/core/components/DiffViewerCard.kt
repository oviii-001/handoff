package com.ovi.handoff.mobile.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

private const val INITIAL_LINE_BUDGET = 200
private const val MAX_BODY_HEIGHT_DP = 420

private enum class DiffLineKind { ADDED, REMOVED, HEADER, CONTEXT }

private data class DiffLine(val index: Int, val text: String, val kind: DiffLineKind)

private data class ParsedDiff(
    val lines: List<DiffLine>,
    val additions: Int,
    val deletions: Int
)

/**
 * Embeddable Diff Viewer Snippet.
 * Renders file header, addition/deletion stats, and virtualized diff rows without outer Card
 * or duplicate agent badges.
 */
@Composable
fun DiffViewerSnippet(
    filePath: String,
    diffContent: String,
    modifier: Modifier = Modifier
) {
    val parsed = remember(diffContent) { parseDiff(diffContent) }
    var showAll by remember(diffContent) { mutableStateOf(parsed.lines.size <= INITIAL_LINE_BUDGET) }
    val visible = remember(parsed, showAll) {
        if (showAll) parsed.lines else parsed.lines.take(INITIAL_LINE_BUDGET)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // File path and addition/deletion stats header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ShapeMedium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
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
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (parsed.additions > 0) {
                    StatPill(
                        text = stringResource(R.string.diff_lines_added, parsed.additions),
                        container = DiffAddContainer,
                        content = DiffAddOnContainer
                    )
                }
                if (parsed.deletions > 0) {
                    StatPill(
                        text = stringResource(R.string.diff_lines_removed, parsed.deletions),
                        container = DiffRemoveContainer,
                        content = DiffRemoveOnContainer
                    )
                }
            }
        }

        val horizontalScroll = rememberScrollState()
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = MAX_BODY_HEIGHT_DP.dp)
                .clip(ShapeMedium)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(vertical = 8.dp)
        ) {
            items(visible, key = { it.index }) { line ->
                DiffLineRow(line = line, horizontalScroll = horizontalScroll)
            }
        }

        if (!showAll) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.diff_truncated, visible.size, parsed.lines.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                TextButton(onClick = { showAll = true }) {
                    Text(
                        text = stringResource(R.string.diff_show_all, parsed.lines.size),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Standalone Diff Viewer Card.
 */
@Composable
fun DiffViewerCard(
    filePath: String,
    diffContent: String,
    agentId: String,
    agentName: String? = null,
    projectOrWorkspace: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        shape = ShapeExtraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
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
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            DiffViewerSnippet(
                filePath = filePath,
                diffContent = diffContent
            )
        }
    }
}

@Composable
private fun DiffLineRow(line: DiffLine, horizontalScroll: androidx.compose.foundation.ScrollState) {
    val background = when (line.kind) {
        DiffLineKind.ADDED -> DiffAddContainer.copy(alpha = 0.6f)
        DiffLineKind.REMOVED -> DiffRemoveContainer.copy(alpha = 0.6f)
        else -> Color.Transparent
    }
    val textColor = when (line.kind) {
        DiffLineKind.ADDED -> DiffAddOnContainer
        DiffLineKind.REMOVED -> DiffRemoveOnContainer
        DiffLineKind.HEADER -> MaterialTheme.colorScheme.tertiary
        DiffLineKind.CONTEXT -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .horizontalScroll(horizontalScroll)
            .padding(horizontal = 12.dp, vertical = 2.dp)
    ) {
        Text(
            text = line.text,
            fontFamily = MonospaceFont,
            color = textColor,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun StatPill(text: String, container: Color, content: Color) {
    Box(
        modifier = Modifier
            .clip(ShapeFull)
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = text, color = content, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

private fun parseDiff(content: String): ParsedDiff {
    val raw = content.lines()
    val lines = ArrayList<DiffLine>(raw.size)
    var additions = 0
    var deletions = 0

    raw.forEachIndexed { index, text ->
        val kind = when {
            text.startsWith("+++") || text.startsWith("---") || text.startsWith("@@") -> DiffLineKind.HEADER
            text.startsWith("+") -> DiffLineKind.ADDED
            text.startsWith("-") -> DiffLineKind.REMOVED
            else -> DiffLineKind.CONTEXT
        }
        if (kind == DiffLineKind.ADDED) additions++
        if (kind == DiffLineKind.REMOVED) deletions++
        lines += DiffLine(index = index, text = text, kind = kind)
    }

    return ParsedDiff(lines = lines, additions = additions, deletions = deletions)
}
