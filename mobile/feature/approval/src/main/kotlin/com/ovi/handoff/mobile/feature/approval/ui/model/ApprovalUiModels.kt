package com.ovi.handoff.mobile.feature.approval.ui.model

import androidx.compose.runtime.Immutable
import com.ovi.handoff.mobile.domain.repository.ConnectionState
import com.ovi.handoff.mobile.domain.repository.RequestRecord
import com.ovi.handoff.shared.model.DecisionType
import com.ovi.handoff.shared.model.PermissionRequest
import com.ovi.handoff.shared.model.RiskLevel
import com.ovi.handoff.shared.model.cleanName
import com.ovi.handoff.shared.model.resolveProjectOrWorkspace
import com.ovi.handoff.shared.model.shortWorkspaceName
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * UI models for the approval surfaces.
 *
 * Every model is `@Immutable` and every collection is an `ImmutableList`. That is not decoration: the
 * Compose compiler treats `kotlin.List` as unstable, so a state class holding one can never be
 * skipped. With plain lists, typing a single character into the audit search box recomposed the whole
 * home subtree along with it.
 */
@Immutable
public data class PermissionRequestUiModel(
    val id: String,
    val agentId: String,
    /** Display form, normalised here rather than at persistence time. */
    val agentName: String,
    val agentVersion: String?,
    val riskLevel: String,
    val riskWeight: Int,
    val isCritical: Boolean,
    val riskReasons: ImmutableList<String>,
    val permissionType: String,
    val command: String?,
    val description: String?,
    val diff: String?,
    val target: String?,
    val cwd: String?,
    val createdAt: String,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long?,
    val formattedTimestamp: String,
    val project: String?,
    val workspace: String?,
    /** Full path, for the details block. */
    val projectOrWorkspace: String?,
    /** Folder name only, for badges and app-bar labels. */
    val workspaceLabel: String?,
    val plan: PlanUiModel? = null,
    val question: QuestionUiModel? = null
) {
    /** Headline for compact rows: the command if there is one, else the human description. */
    val summaryLine: String
        get() = command ?: target ?: description ?: permissionType
}

@Immutable
public data class PlanUiModel(
    val title: String,
    val summary: String,
    val userReviewRequired: ImmutableList<String> = persistentListOf()
)

@Immutable
public data class QuestionUiModel(
    val question: String,
    val options: ImmutableList<String>,
    val isMultiSelect: Boolean = false
)

@Immutable
public data class ConnectedAgentUiModel(
    val id: String,
    val name: String,
    val version: String? = null
)

/** What happened to a request, for the audit list. */
public enum class AuditOutcome {
    PENDING,
    APPROVED,
    DENIED,
    EXPIRED,
    CANCELLED
}

/**
 * One row of the audit log.
 *
 * The outcome is explicit because the audit list previously drew a green tick and the word "RECORDED"
 * against every entry, so a denial and an approval were indistinguishable in the one screen whose
 * entire job is telling them apart.
 */
@Immutable
public data class AuditEntryUiModel(
    val request: PermissionRequestUiModel,
    val outcome: AuditOutcome,
    val decidedAtEpochMs: Long?
) {
    val id: String get() = request.id
}

public fun PermissionRequest.toUiModel(): PermissionRequestUiModel {
    val fullPath = resolveProjectOrWorkspace()
    return PermissionRequestUiModel(
        id = id,
        agentId = agent.id,
        agentName = agent.cleanName(),
        agentVersion = agent.version,
        riskLevel = risk.level,
        riskWeight = RiskLevel.weight(risk.level),
        isCritical = RiskLevel.isCritical(risk.level),
        riskReasons = risk.reasons.toImmutableList(),
        permissionType = permission.type,
        command = permission.command,
        description = permission.description,
        diff = permission.diff,
        target = permission.target,
        cwd = permission.cwd,
        createdAt = createdAt,
        createdAtEpochMs = createdAtEpochMs ?: 0L,
        expiresAtEpochMs = expiresAtEpochMs,
        formattedTimestamp = createdAt.take(19).replace('T', ' '),
        project = session.project,
        workspace = session.workspace,
        projectOrWorkspace = fullPath,
        workspaceLabel = shortWorkspaceName(fullPath),
        plan = plan?.let { p ->
            PlanUiModel(
                title = p.title,
                summary = p.summary,
                userReviewRequired = p.userReviewRequired.toImmutableList()
            )
        },
        question = question?.let { q ->
            QuestionUiModel(
                question = q.question,
                options = q.options.toImmutableList(),
                isMultiSelect = q.isMultiSelect
            )
        }
    )
}

public fun RequestRecord.toAuditEntry(): AuditEntryUiModel {
    val dec = decision
    return AuditEntryUiModel(
        request = request.toUiModel(),
        outcome = when {
            isPending -> AuditOutcome.PENDING
            dec == null -> AuditOutcome.PENDING
            dec == DecisionType.EXPIRED -> AuditOutcome.EXPIRED
            dec == DecisionType.CANCEL -> AuditOutcome.CANCELLED
            DecisionType.isApproval(dec) -> AuditOutcome.APPROVED
            else -> AuditOutcome.DENIED
        },
        decidedAtEpochMs = decidedAtEpochMs
    )
}

/** Connection banner text, derived once rather than hardcoded to "connected". */
@Immutable
public data class ConnectionUiModel(
    val state: ConnectionState
) {
    val isConnected: Boolean get() = state == ConnectionState.CONNECTED
}
