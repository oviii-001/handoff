package com.ovi.handoff.shared.crypto

import com.ovi.handoff.shared.model.PermissionDecision
import com.ovi.handoff.shared.model.PermissionRequest

/**
 * Canonical, unambiguous byte encoding for the payloads that get cryptographically signed.
 *
 * Both the desktop signer and the phone verifier (and vice versa for decisions) call into this
 * one implementation. Deriving the signed bytes from `Json.encodeToString` instead would make the
 * signature depend on serializer field ordering and default-value handling, so any future field
 * addition would silently invalidate signatures on one side only.
 *
 * Encoding rules: every field is written as `name=<byteLength>:<utf8 value>;`, and a null value is
 * written as `name=~;`. The explicit length prefix means no value can be crafted to look like a
 * field boundary, so two different payloads can never produce the same canonical bytes.
 */
public object Canonical {

    /** Bytes the desktop signs for a permission request. Never includes `signature` itself. */
    public fun requestBytes(request: PermissionRequest): ByteArray {
        val w = Writer()
        w.field("v", request.protocolVersion)
        w.field("id", request.id)
        w.field("agentId", request.agent.id)
        w.field("agentName", request.agent.name)
        w.field("agentVersion", request.agent.version)
        w.field("sessionId", request.session.id)
        w.field("project", request.session.project)
        w.field("workspace", request.session.workspace)
        w.field("permType", request.permission.type)
        w.field("permCommand", request.permission.command)
        w.field("permTarget", request.permission.target)
        w.field("permDescription", request.permission.description)
        w.field("permCwd", request.permission.cwd)
        w.field("permDiff", request.permission.diff)
        w.field("riskLevel", request.risk.level)
        w.list("riskReasons", request.risk.reasons)
        w.list("options", request.options)
        w.field("createdAt", request.createdAt)
        w.field("expiresAt", request.expiresAt)
        // The epoch mirrors are signed too. They are what expiry logic actually reads, so leaving
        // them out would let a tampered frame move a deadline without breaking the signature.
        w.field("createdAtEpochMs", request.createdAtEpochMs?.toString())
        w.field("expiresAtEpochMs", request.expiresAtEpochMs?.toString())
        w.field("questionText", request.question?.question)
        w.list("questionOptions", request.question?.options)
        w.field("questionMulti", request.question?.isMultiSelect?.toString())
        w.field("planTitle", request.plan?.title)
        w.field("planSummary", request.plan?.summary)
        w.list("planReview", request.plan?.userReviewRequired)
        return w.build()
    }

    /**
     * Hex SHA-256 over [requestBytes]. The phone echoes this back inside its decision so a
     * captured decision cannot be replayed against a different request.
     */
    public fun requestHash(request: PermissionRequest): String = Sha256.hashHex(requestBytes(request))

    /** Bytes the phone signs for a decision. Never includes `signature` itself. */
    public fun decisionBytes(decision: PermissionDecision): ByteArray {
        val w = Writer()
        w.field("requestId", decision.requestId)
        w.field("requestHash", decision.requestHash)
        w.field("decision", decision.decision)
        w.field("issuedAt", decision.issuedAt)
        w.field("nonce", decision.nonce)
        w.field("deviceId", decision.deviceId)
        w.field("feedback", decision.feedback)
        w.list("selectedOptions", decision.selectedOptions)
        return w.build()
    }

    private class Writer {
        private val sb = StringBuilder(512)

        fun field(name: String, value: String?) {
            sb.append(name).append('=')
            if (value == null) {
                sb.append('~')
            } else {
                sb.append(value.encodeToByteArray().size).append(':').append(value)
            }
            sb.append(';')
        }

        /** A list is encoded as its element count followed by each element as its own field. */
        fun list(name: String, values: List<String>?) {
            if (values == null) {
                field(name, null)
                return
            }
            sb.append(name).append('#').append(values.size).append(';')
            for ((index, value) in values.withIndex()) {
                field("$name[$index]", value)
            }
        }

        fun build(): ByteArray = sb.toString().encodeToByteArray()
    }
}
