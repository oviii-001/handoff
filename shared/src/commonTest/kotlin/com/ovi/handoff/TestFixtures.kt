package com.ovi.handoff

import com.ovi.handoff.shared.model.AgentInfo
import com.ovi.handoff.shared.model.DecisionType
import com.ovi.handoff.shared.model.PermissionDecision
import com.ovi.handoff.shared.model.PermissionInfo
import com.ovi.handoff.shared.model.PermissionRequest
import com.ovi.handoff.shared.model.RiskInfo
import com.ovi.handoff.shared.model.SessionInfo

/** Shared builders so protocol tests do not each restate the full object graph. */
internal object TestFixtures {

    const val CREATED_AT_MS: Long = 1_788_000_000_000L
    const val EXPIRES_AT_MS: Long = CREATED_AT_MS + 300_000L

    fun request(
        id: String = "req-1",
        command: String? = "ls",
        riskLevel: String = "medium",
        project: String? = "handoff",
        workspace: String? = "/home/dev/handoff"
    ): PermissionRequest = PermissionRequest(
        id = id,
        agent = AgentInfo(id = "antigravity", name = "Antigravity", version = "2.2.0"),
        session = SessionInfo(id = "pair-abc", project = project, workspace = workspace),
        permission = PermissionInfo(
            type = "terminal",
            command = command,
            description = "Run a build",
            cwd = workspace
        ),
        risk = RiskInfo(level = riskLevel, reasons = listOf("Touches the build output")),
        options = listOf("approve", "deny"),
        createdAt = "2026-09-04T12:00:00Z",
        expiresAt = "2026-09-04T12:05:00Z",
        createdAtEpochMs = CREATED_AT_MS,
        expiresAtEpochMs = EXPIRES_AT_MS
    )

    fun decision(
        requestId: String = "req-1",
        decision: String = DecisionType.APPROVE_ONCE,
        requestHash: String = "0".repeat(64),
        signature: String = "signature",
        feedback: String? = null,
        selectedOptions: List<String>? = null
    ): PermissionDecision = PermissionDecision(
        requestId = requestId,
        decision = decision,
        issuedAt = "2026-09-04T12:01:00Z",
        nonce = "nonce-1",
        deviceId = "device-1",
        requestHash = requestHash,
        signature = signature,
        feedback = feedback,
        selectedOptions = selectedOptions
    )
}
