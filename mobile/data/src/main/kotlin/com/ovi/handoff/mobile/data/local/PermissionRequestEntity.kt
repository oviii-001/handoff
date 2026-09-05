package com.ovi.handoff.mobile.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ovi.handoff.shared.model.AgentInfo
import com.ovi.handoff.shared.model.PermissionInfo
import com.ovi.handoff.shared.model.PermissionRequest
import com.ovi.handoff.shared.model.PlanPayload
import com.ovi.handoff.shared.model.QuestionPayload
import com.ovi.handoff.shared.model.RiskInfo
import com.ovi.handoff.shared.model.SessionInfo
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Stored form of a permission request, plus its outcome.
 *
 * Three things about the previous mapping were wrong in ways that mattered:
 *
 *  - **It lost fields.** `session.project`, `session.workspace`, `agent.version` and the request's
 *    own signature were never persisted. A request read back from the database was therefore not the
 *    request that arrived, so its canonical hash no longer matched and a decision signed over it
 *    would be rejected by the desktop.
 *  - **It stored the display name.** `agentName` held `agent.cleanName()`, so the raw name the
 *    desktop signed over was gone. Display normalisation belongs in the UI layer, not in storage.
 *  - **It joined lists with commas.** A risk reason containing a comma split into two on read.
 *    Lists are JSON now.
 */
@Entity(
    tableName = "permission_requests",
    // The queue and the audit list are both ordered by time and filtered by pending state.
    indices = [Index(value = ["isPending", "createdAtEpochMs"])]
)
public data class PermissionRequestEntity(
    @PrimaryKey val id: String,
    val protocolVersion: String,
    val agentId: String,
    /** The raw name as sent, not the display form: the signature is computed over this. */
    val agentName: String,
    val agentVersion: String?,
    val sessionId: String,
    val sessionProject: String?,
    val sessionWorkspace: String?,
    val permissionType: String,
    val permissionCommand: String?,
    val permissionTarget: String?,
    val permissionDescription: String?,
    val permissionCwd: String?,
    val permissionDiff: String?,
    val riskLevel: String,
    /** JSON array. */
    val riskReasons: String,
    /** JSON array. */
    val options: String,
    val createdAt: String,
    val expiresAt: String,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long?,
    val requestSignature: String?,
    val isPending: Boolean,
    /** The decision that resolved this request, or null while pending. */
    val decision: String?,
    val decidedAtEpochMs: Long?,
    val questionPrompt: String? = null,
    /** JSON array. */
    val questionOptions: String? = null,
    val questionIsMultiSelect: Boolean = false,
    val planTitle: String? = null,
    val planSummary: String? = null,
    /** JSON array. */
    val planReviewRequired: String? = null
)

private val listJson = Json { ignoreUnknownKeys = true }
private val stringListSerializer = ListSerializer(String.serializer())

internal fun encodeList(values: List<String>?): String? =
    values?.let { listJson.encodeToString(stringListSerializer, it) }

/**
 * Decodes a JSON string list, falling back to comma splitting.
 *
 * The fallback exists for rows written by the pre-migration schema whose values the migration copies
 * across verbatim; without it, older history would render as one run-together reason.
 */
internal fun decodeList(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching { listJson.decodeFromString(stringListSerializer, raw) }
        .getOrElse { raw.split(',').map { it.trim() }.filter { it.isNotEmpty() } }
}

public fun PermissionRequest.toEntity(): PermissionRequestEntity = PermissionRequestEntity(
    id = id,
    protocolVersion = protocolVersion,
    agentId = agent.id,
    agentName = agent.name,
    agentVersion = agent.version,
    sessionId = session.id,
    sessionProject = session.project,
    sessionWorkspace = session.workspace,
    permissionType = permission.type,
    permissionCommand = permission.command,
    permissionTarget = permission.target,
    permissionDescription = permission.description,
    permissionCwd = permission.cwd,
    permissionDiff = permission.diff,
    riskLevel = risk.level,
    riskReasons = encodeList(risk.reasons).orEmpty(),
    options = encodeList(options).orEmpty(),
    createdAt = createdAt,
    expiresAt = expiresAt,
    // Falls back to arrival time so ordering still works for a request from an older desktop.
    createdAtEpochMs = createdAtEpochMs ?: System.currentTimeMillis(),
    expiresAtEpochMs = expiresAtEpochMs,
    requestSignature = signature,
    isPending = true,
    decision = null,
    decidedAtEpochMs = null,
    questionPrompt = question?.question,
    questionOptions = encodeList(question?.options),
    questionIsMultiSelect = question?.isMultiSelect ?: false,
    planTitle = plan?.title,
    planSummary = plan?.summary,
    planReviewRequired = encodeList(plan?.userReviewRequired)
)

/**
 * Rebuilds the exact request that arrived.
 *
 * Field-for-field fidelity is a correctness requirement, not tidiness: the phone signs a hash of
 * these canonical bytes, and the desktop recomputes the same hash from the request it sent. Any field
 * that does not survive the round trip makes every decision unverifiable.
 */
public fun PermissionRequestEntity.toDomain(): PermissionRequest = PermissionRequest(
    id = id,
    protocolVersion = protocolVersion,
    agent = AgentInfo(id = agentId, name = agentName, version = agentVersion),
    session = SessionInfo(id = sessionId, project = sessionProject, workspace = sessionWorkspace),
    permission = PermissionInfo(
        type = permissionType,
        command = permissionCommand,
        target = permissionTarget,
        description = permissionDescription,
        cwd = permissionCwd,
        diff = permissionDiff
    ),
    risk = RiskInfo(level = riskLevel, reasons = decodeList(riskReasons)),
    options = decodeList(options),
    createdAt = createdAt,
    expiresAt = expiresAt,
    createdAtEpochMs = createdAtEpochMs,
    expiresAtEpochMs = expiresAtEpochMs,
    question = questionPrompt?.let {
        QuestionPayload(
            question = it,
            options = decodeList(questionOptions),
            isMultiSelect = questionIsMultiSelect
        )
    },
    plan = planTitle?.let { title ->
        PlanPayload(
            title = title,
            summary = planSummary.orEmpty(),
            userReviewRequired = decodeList(planReviewRequired)
        )
    },
    signature = requestSignature
)
