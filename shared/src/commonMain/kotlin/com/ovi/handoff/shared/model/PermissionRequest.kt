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

@Serializable
data class SessionAnnouncement(
    val type: String = "session_info",
    val pairId: String,
    val agent: AgentInfo,
    val session: SessionInfo,
    val timestamp: String
)

/**
 * Resolves a human-readable project or workspace name for display in notifications and UI headers.
 * Extracts folder basename if the project or workspace was transmitted as a full filesystem path or file URI.
 */
public fun PermissionRequest.resolveProjectOrWorkspace(): String? {
    return resolveProjectOrWorkspace(session.project, session.workspace, permission.cwd)
}

public fun resolveProjectOrWorkspace(project: String?, workspace: String?, cwd: String? = null): String? {
    fun clean(path: String?): String? {
        if (path.isNullOrBlank()) return null
        var s = path.trim()
        if (s.startsWith("file://", ignoreCase = true)) {
            s = s.substring(7)
        }
        s = s.trimEnd('/', '\\')
        val base = s.substringAfterLast('/').substringAfterLast('\\')
        return base.ifBlank { s }
    }

    return clean(project) ?: clean(workspace) ?: clean(cwd)
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
        raw.contains("vscode", ignoreCase = true) -> "VSCode"
        raw.contains("intellij", ignoreCase = true) -> "IntelliJ"
        raw.contains("android studio", ignoreCase = true) -> "Android Studio"
        raw.contains("gemini", ignoreCase = true) -> "Gemini"
        else -> raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

