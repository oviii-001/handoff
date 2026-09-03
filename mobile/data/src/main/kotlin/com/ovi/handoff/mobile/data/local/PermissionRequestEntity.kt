package com.ovi.handoff.mobile.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ovi.handoff.shared.model.*

@Entity(tableName = "permission_requests")
data class PermissionRequestEntity(
    @PrimaryKey val id: String,
    val protocolVersion: String,
    val agentId: String,
    val agentName: String,
    val sessionId: String,
    val permissionType: String,
    val permissionCommand: String?,
    val permissionTarget: String?,
    val permissionDescription: String?,
    val permissionCwd: String?,
    val permissionDiff: String?,
    val riskLevel: String,
    val riskReasons: String,
    val options: String,
    val createdAt: String,
    val expiresAt: String,
    val isPending: Boolean,
    val questionPrompt: String? = null,
    val questionOptions: String? = null,
    val questionIsMultiSelect: Boolean = false,
    val planTitle: String? = null,
    val planSummary: String? = null,
    val planReviewRequired: String? = null
)

fun PermissionRequest.toEntity() = PermissionRequestEntity(
    id = id,
    protocolVersion = protocolVersion,
    agentId = agent.id,
    agentName = agent.name,
    sessionId = session.id,
    permissionType = permission.type,
    permissionCommand = permission.command,
    permissionTarget = permission.target,
    permissionDescription = permission.description,
    permissionCwd = permission.cwd,
    permissionDiff = permission.diff,
    riskLevel = risk.level,
    riskReasons = risk.reasons.joinToString(","),
    options = options.joinToString(","),
    createdAt = createdAt,
    expiresAt = expiresAt,
    isPending = true,
    questionPrompt = question?.question,
    questionOptions = question?.options?.joinToString("|||"),
    questionIsMultiSelect = question?.isMultiSelect ?: false,
    planTitle = plan?.title,
    planSummary = plan?.summary,
    planReviewRequired = plan?.userReviewRequired?.joinToString("|||")
)

fun PermissionRequestEntity.toDomain() = PermissionRequest(
    id = id,
    protocolVersion = protocolVersion,
    agent = AgentInfo(id = agentId, name = agentName),
    session = SessionInfo(id = sessionId),
    permission = PermissionInfo(
        type = permissionType,
        command = permissionCommand,
        target = permissionTarget,
        description = permissionDescription,
        cwd = permissionCwd,
        diff = permissionDiff
    ),
    risk = RiskInfo(level = riskLevel, reasons = riskReasons.split(",").filter { it.isNotEmpty() }),
    options = options.split(",").filter { it.isNotEmpty() },
    createdAt = createdAt,
    expiresAt = expiresAt,
    question = if (questionPrompt != null) {
        QuestionPayload(
            question = questionPrompt,
            options = questionOptions?.split("|||")?.filter { it.isNotEmpty() } ?: emptyList(),
            isMultiSelect = questionIsMultiSelect
        )
    } else null,
    plan = if (planTitle != null && planSummary != null) {
        PlanPayload(
            title = planTitle,
            summary = planSummary,
            userReviewRequired = planReviewRequired?.split("|||")?.filter { it.isNotEmpty() } ?: emptyList()
        )
    } else null
)

