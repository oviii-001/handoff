package com.ovi.handoff.mobile.feature.approval.ui.model


import com.ovi.handoff.shared.model.PermissionRequest
import com.ovi.handoff.shared.model.cleanName
import com.ovi.handoff.shared.model.resolveProjectOrWorkspace

/**
 * Immutable UI data model representing an authorization request rendered by Composables.
 * Strictly decoupled from Room entities, DTOs, and raw domain entities.
 */
data class PermissionRequestUiModel(
    val id: String,
    val agentId: String,
    val agentName: String,
    val agentVersion: String?,
    val riskLevel: String,
    val riskReasons: List<String>,
    val permissionType: String,
    val command: String?,
    val description: String?,
    val diff: String?,
    val target: String?,
    val cwd: String?,
    val createdAt: String,
    val formattedTimestamp: String,
    val project: String?,
    val workspace: String?,
    val projectOrWorkspace: String?,
    val plan: PlanUiModel? = null,
    val question: QuestionUiModel? = null
)

data class PlanUiModel(
    val title: String,
    val summary: String,
    val userReviewRequired: List<String> = emptyList()
)

data class QuestionUiModel(
    val question: String,
    val options: List<String>,
    val isMultiSelect: Boolean = false
)

data class ConnectedAgentUiModel(
    val id: String,
    val name: String,
    val version: String? = null
)

/**
 * Maps domain [PermissionRequest] to immutable [PermissionRequestUiModel].
 */
fun PermissionRequest.toUiModel(): PermissionRequestUiModel {
    val projOrWs = resolveProjectOrWorkspace()
    return PermissionRequestUiModel(
        id = id,
        agentId = agent.id,
        agentName = agent.cleanName(),
        agentVersion = agent.version,
        riskLevel = risk.level,
        riskReasons = risk.reasons,
        permissionType = permission.type,
        command = permission.command,
        description = permission.description,
        diff = permission.diff,
        target = permission.target,
        cwd = permission.cwd,
        createdAt = createdAt,
        formattedTimestamp = createdAt.take(19).replace("T", " "),
        project = session.project,
        workspace = session.workspace,
        projectOrWorkspace = projOrWs,
        plan = plan?.let {
            PlanUiModel(
                title = it.title,
                summary = it.summary,
                userReviewRequired = it.userReviewRequired
            )
        },
        question = question?.let {
            QuestionUiModel(
                question = it.question,
                options = it.options,
                isMultiSelect = it.isMultiSelect
            )
        }
    )
}

