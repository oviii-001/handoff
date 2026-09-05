package com.ovi.handoff.shared.model

import kotlinx.serialization.Serializable

/**
 * A user's answer to exactly one [PermissionRequest].
 *
 * [requestHash] binds the decision to the canonical bytes of the request it answers, and
 * [signature] covers both. Without that binding a captured "approve" frame could be replayed
 * against a different, more dangerous request, because the desktop only ever sees the request id.
 */
@Serializable
public data class PermissionDecision(
    val requestId: String,
    val decision: String,
    val issuedAt: String,
    val nonce: String,
    val deviceId: String,
    /** Hex SHA-256 of the request's canonical bytes, from `Canonical.requestHash`. */
    val requestHash: String,
    /** Base64url Ed25519 signature over `Canonical.decisionBytes`. */
    val signature: String,
    val feedback: String? = null,
    val selectedOptions: List<String>? = null
)

/**
 * The decision strings carried in [PermissionDecision.decision].
 *
 * These exist as constants because each consumer previously hardcoded its own subset: the phone
 * sends `approve_once` while `CommandWrapper` only recognised `approve`, so every approval was
 * being executed as a denial.
 */
public object DecisionType {
    public const val APPROVE_ONCE: String = "approve_once"
    public const val APPROVE_ALWAYS: String = "approve_always"

    /** Legacy alias still emitted by pre-v2 phones and by the `--test-*` fixtures. */
    public const val APPROVE: String = "approve"

    public const val DENY: String = "deny"
    public const val CANCEL: String = "cancel"
    public const val ANSWER_QUESTION: String = "answer_question"
    public const val PROCEED_PLAN: String = "proceed_plan"
    public const val EXPIRED: String = "expired"

    private val APPROVING: Set<String> = setOf(
        APPROVE, APPROVE_ONCE, APPROVE_ALWAYS, ANSWER_QUESTION, PROCEED_PLAN
    )

    /** True when the decision authorizes the agent to continue. */
    public fun isApproval(decision: String): Boolean = decision.trim().lowercase() in APPROVING
}

/** True when this decision authorizes the agent to continue. */
public fun PermissionDecision.isApproval(): Boolean = DecisionType.isApproval(decision)
