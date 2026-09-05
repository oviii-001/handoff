package com.ovi.handoff.core

import com.ovi.handoff.shared.model.AgentInfo
import com.ovi.handoff.shared.model.PermissionInfo
import com.ovi.handoff.shared.model.PermissionRequest
import com.ovi.handoff.shared.model.PlanPayload
import com.ovi.handoff.shared.model.QuestionPayload
import com.ovi.handoff.shared.model.RiskInfo
import com.ovi.handoff.shared.model.SessionInfo
import com.ovi.handoff.shared.model.shortWorkspaceName
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * Builds [PermissionRequest] instances.
 *
 * Every entry point (the MCP tools, the terminal wrapper, the `--test-*` fixtures) previously
 * assembled its own request inline, which is how the epoch timestamp fields and consistent
 * project/workspace naming would have drifted apart. Timestamps in particular need to be produced in
 * exactly one place: the ISO string is for display and the epoch millis are what expiry logic reads.
 */
public object RequestFactory {

    public const val DEFAULT_TTL_SECONDS: Long = 300

    public fun build(
        pairId: String,
        agent: AgentInfo,
        permission: PermissionInfo,
        risk: RiskInfo,
        options: List<String>,
        workspacePath: String?,
        ttlSeconds: Long = DEFAULT_TTL_SECONDS,
        question: QuestionPayload? = null,
        plan: PlanPayload? = null,
        id: String = UUID.randomUUID().toString()
    ): PermissionRequest {
        val createdAt = Instant.now()
        val expiresAt = createdAt.plusSeconds(ttlSeconds)

        return PermissionRequest(
            id = id,
            agent = agent,
            session = SessionInfo(
                id = pairId,
                // The phone renders `project` as a label and `workspace` as the detail line. The MCP
                // server used to set both to the same absolute path, so notification titles showed
                // a full Windows path instead of the folder name.
                project = shortWorkspaceName(workspacePath),
                workspace = workspacePath
            ),
            permission = permission,
            risk = risk,
            options = options,
            createdAt = createdAt.toString(),
            expiresAt = expiresAt.toString(),
            createdAtEpochMs = createdAt.toEpochMilli(),
            expiresAtEpochMs = expiresAt.toEpochMilli(),
            question = question,
            plan = plan
        )
    }

    /** Canonicalises a path or `file://` URI to an absolute path, or null when unusable. */
    public fun resolveWorkspacePath(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        var cleaned = raw.trim()
        if (cleaned.startsWith("file://", ignoreCase = true)) {
            // Strip only the scheme and authority, keeping the path's leading slash: dropping
            // `file:///` wholesale turns the POSIX path `file:///tmp` into the relative `tmp`.
            cleaned = cleaned.substring("file://".length)
            // A Windows drive letter arrives as `/C:/Users/...`; that one leading slash is spurious.
            if (cleaned.length > 2 && cleaned[0] == '/' && cleaned[2] == ':') {
                cleaned = cleaned.substring(1)
            }
        }
        if (cleaned.isBlank()) return null

        return runCatching { File(cleaned).canonicalFile.absolutePath }
            .getOrElse { runCatching { File(cleaned).absolutePath }.getOrNull() }
    }
}
