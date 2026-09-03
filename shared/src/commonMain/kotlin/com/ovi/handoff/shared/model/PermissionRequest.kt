package com.ovi.handoff.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class PermissionRequest(
    val id: String,
    val protocolVersion: String,
    val agent: AgentInfo,
    val session: SessionInfo,
    val permission: PermissionInfo,
    val risk: RiskInfo,
    val options: List<String>,
    val createdAt: String,
    val expiresAt: String,
    val question: QuestionPayload? = null,
    val plan: PlanPayload? = null
)

@Serializable
data class AgentInfo(
    val id: String,
    val name: String,
    val version: String? = null
)

@Serializable
data class SessionInfo(
    val id: String,
    val project: String? = null,
    val workspace: String? = null
)

@Serializable
data class PermissionInfo(
    val type: String, // 'shell', 'file_read', 'file_write', 'network', 'mcp', 'question', 'plan', 'other'
    val command: String? = null,
    val target: String? = null,
    val description: String? = null,
    val cwd: String? = null,
    val diff: String? = null
)

@Serializable
data class RiskInfo(
    val level: String, // 'low', 'medium', 'high', 'critical'
    val reasons: List<String>
)

@Serializable
data class QuestionPayload(
    val question: String,
    val options: List<String>,
    val isMultiSelect: Boolean = false
)

@Serializable
data class PlanPayload(
    val title: String,
    val summary: String,
    val userReviewRequired: List<String> = emptyList()
)
