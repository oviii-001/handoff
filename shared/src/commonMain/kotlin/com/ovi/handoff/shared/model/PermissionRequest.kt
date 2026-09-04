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
    val plan: PlanPayload? = null,
    val signature: String? = null
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

/**
 * Resolves a human-readable project or workspace name for display in notifications and UI headers.
 * Extracts folder basename if the project or workspace was transmitted as a full filesystem path.
 */
public fun PermissionRequest.resolveProjectOrWorkspace(): String? {
    if (!session.project.isNullOrBlank()) {
        val base = session.project.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')
        return base.ifBlank { session.project }
    }
    if (!session.workspace.isNullOrBlank()) {
        val base = session.workspace.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')
        return base.ifBlank { session.workspace }
    }
    if (!permission.cwd.isNullOrBlank()) {
        val base = permission.cwd.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')
        if (base.isNotBlank()) return base
    }
    return null
}

/**
 * Normalizes verbose IDE/agent names to clean, canonical identifiers.
 * E.g., "Antigravity Assistant" -> "Antigravity", "Cursor Composer" -> "Cursor", "Codex Agent" -> "Codex".
 */
public fun AgentInfo.cleanName(): String {
    val raw = name.ifBlank { id }.trim()
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
