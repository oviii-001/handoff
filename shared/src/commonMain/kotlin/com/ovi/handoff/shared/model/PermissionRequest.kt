package com.ovi.handoff.shared.model

import com.ovi.handoff.shared.protocol.Protocol
import kotlinx.serialization.Serializable

@Serializable
public data class PermissionRequest(
    val id: String,
    val protocolVersion: String = Protocol.VERSION,
    val agent: AgentInfo,
    val session: SessionInfo,
    val permission: PermissionInfo,
    val risk: RiskInfo,
    val options: List<String>,
    val createdAt: String,
    val expiresAt: String,
    /**
     * Epoch millis mirror of [createdAt] / [expiresAt].
     *
     * The ISO strings are kept for display, but all ordering and expiry arithmetic uses these.
     * `Instant.toString()` emits variable fractional-second precision, so comparing the ISO strings
     * lexicographically silently misorders timestamps that differ only in that precision.
     */
    val createdAtEpochMs: Long? = null,
    val expiresAtEpochMs: Long? = null,
    val question: QuestionPayload? = null,
    val plan: PlanPayload? = null,
    val signature: String? = null
)

@Serializable
public data class AgentInfo(
    val id: String,
    val name: String,
    val version: String? = null
)

@Serializable
public data class SessionInfo(
    val id: String,
    /** Human-readable workspace name, normally the folder basename. */
    val project: String? = null,
    /** Absolute path of the workspace root. */
    val workspace: String? = null
)

@Serializable
public data class PermissionInfo(
    val type: String,
    val command: String? = null,
    val target: String? = null,
    val description: String? = null,
    val cwd: String? = null,
    val diff: String? = null
)

@Serializable
public data class RiskInfo(
    val level: String,
    val reasons: List<String>
)

@Serializable
public data class QuestionPayload(
    val question: String,
    val options: List<String>,
    val isMultiSelect: Boolean = false
)

@Serializable
public data class PlanPayload(
    val title: String,
    val summary: String,
    val userReviewRequired: List<String> = emptyList()
)

@Serializable
public data class SessionAnnouncement(
    val type: String = "session_info",
    val pairId: String,
    val agent: AgentInfo,
    val session: SessionInfo,
    val timestamp: String
)

/** Values used in [PermissionInfo.type]. */
public object PermissionType {
    public const val SHELL: String = "shell"
    public const val TERMINAL: String = "terminal"
    public const val FILE_READ: String = "file_read"
    public const val FILE_WRITE: String = "file_write"
    public const val PATCH: String = "patch"
    public const val NETWORK: String = "network"
    public const val MCP: String = "mcp"
    public const val QUESTION: String = "question"
    public const val PLAN: String = "plan"
    public const val OTHER: String = "other"
}

/** Values used in [RiskInfo.level]. */
public object RiskLevel {
    public const val LOW: String = "low"
    public const val MEDIUM: String = "medium"
    public const val HIGH: String = "high"
    public const val CRITICAL: String = "critical"

    public fun isCritical(level: String): Boolean = level.trim().equals(CRITICAL, ignoreCase = true)

    /** Higher is more dangerous. Used to sort a queue of pending requests. */
    public fun weight(level: String): Int = when (level.trim().lowercase()) {
        CRITICAL -> 3
        HIGH -> 2
        MEDIUM -> 1
        else -> 0
    }
}

/**
 * True when the request's deadline has passed. Requests with no epoch deadline never expire, so an
 * older desktop that omits the field cannot have its requests silently dropped.
 */
public fun PermissionRequest.isExpiredAt(nowEpochMs: Long): Boolean {
    val deadline = expiresAtEpochMs ?: return false
    return nowEpochMs >= deadline
}

/** Milliseconds left before expiry, or null when the request carries no epoch deadline. */
public fun PermissionRequest.remainingMs(nowEpochMs: Long): Long? {
    val deadline = expiresAtEpochMs ?: return null
    return (deadline - nowEpochMs).coerceAtLeast(0L)
}

/** True when a decision on this request needs no risk gate beyond a tap. */
public fun PermissionRequest.requiresStrongAuth(): Boolean = RiskLevel.isCritical(risk.level)

/**
 * Collapses runs of backslashes that Windows paths pick up when they travel through JSON.
 *
 * Hoisted to a top-level `val` because this used to be constructed inside
 * [resolveProjectOrWorkspace], which runs for every notification and every UI model built.
 */
private val REDUNDANT_BACKSLASHES = Regex("""(?<!^)\\{2,}""")

private val GENERIC_IDE_NAMES = setOf(
    "antigravity ide", "antigravity", "cursor", "claude", "vscode",
    "workspace", "workspace session", "active workspace"
)

private val IDE_INSTALL_DIR_MARKERS = listOf(
    "/appdata/local/programs/",
    "/program files/"
)

private val IDE_INSTALL_DIR_SUFFIXES = listOf(
    "/antigravity ide",
    "/cursor",
    "/vscode"
)

/**
 * Resolves a project or workspace identifier for display, preferring a real filesystem path over
 * an IDE product name. Returns the full path; call [shortWorkspaceName] for the label form.
 */
public fun PermissionRequest.resolveProjectOrWorkspace(): String? =
    resolveProjectOrWorkspace(session.project, session.workspace, permission.cwd)

public fun resolveProjectOrWorkspace(project: String?, workspace: String?, cwd: String? = null): String? {
    val cleanCwd = cleanPath(cwd)
    val cleanWs = cleanPath(workspace)
    val cleanProj = cleanPath(project)

    // 1. Prefer an actual filesystem path that is not just the IDE's own install directory.
    val pathCandidate = listOfNotNull(cleanCwd, cleanWs, cleanProj).firstOrNull {
        (it.contains('/') || it.contains('\\')) && !isGenericIdeName(it)
    }
    if (pathCandidate != null) return pathCandidate

    // 2. Otherwise take the first non-generic name, falling back to whatever we were given.
    return listOfNotNull(cleanProj, cleanWs, cleanCwd).firstOrNull { !isGenericIdeName(it) }
        ?: cleanProj ?: cleanWs ?: cleanCwd
}

/**
 * The label form of [resolveProjectOrWorkspace]: the trailing folder name only.
 *
 * Notification titles and the home app bar previously rendered the full absolute path, because the
 * MCP server set `session.project` to the same absolute path as `session.workspace`.
 */
public fun PermissionRequest.shortWorkspaceName(): String? =
    shortWorkspaceName(resolveProjectOrWorkspace())

public fun shortWorkspaceName(pathOrName: String?): String? {
    val cleaned = cleanPath(pathOrName) ?: return null
    val basename = cleaned.substringAfterLast('/').substringAfterLast('\\')
    return basename.ifBlank { cleaned }
}

private fun cleanPath(path: String?): String? {
    if (path.isNullOrBlank()) return null
    var s = path.trim()
    if (s.startsWith("file://", ignoreCase = true)) {
        s = s.removePrefix("file:///").removePrefix("file://")
    }
    s = s.replace(REDUNDANT_BACKSLASHES, """\""")
    s = s.trimEnd('/', '\\')
    return s.ifBlank { null }
}

private fun isGenericIdeName(s: String?): Boolean {
    if (s.isNullOrBlank()) return true
    val lower = s.lowercase().trim().replace('\\', '/')
    if (lower in GENERIC_IDE_NAMES) return true
    if (IDE_INSTALL_DIR_MARKERS.any { lower.contains(it) }) return true
    return IDE_INSTALL_DIR_SUFFIXES.any { lower.endsWith(it) }
}

/**
 * Normalizes verbose IDE/agent names to canonical identifiers.
 * "Antigravity Assistant" becomes "Antigravity", "Cursor Composer" becomes "Cursor".
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
