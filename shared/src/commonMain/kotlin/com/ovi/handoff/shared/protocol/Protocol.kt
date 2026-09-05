package com.ovi.handoff.shared.protocol

import com.ovi.handoff.shared.model.PermissionDecision
import com.ovi.handoff.shared.model.PermissionRequest
import com.ovi.handoff.shared.model.SessionAnnouncement
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Wire protocol constants shared by the desktop daemon, the relay and the phone. */
public object Protocol {
    /** Current protocol. v2 adds signed decisions, request/decision binding and acks. */
    public const val VERSION: String = "2.0"

    /** Pre-hardening protocol: unsigned decisions, bare frames with no envelope. */
    public const val VERSION_LEGACY: String = "1.0"
}

/** `type` discriminator carried by every [Envelope]. */
public object FrameType {
    /** Desktop to phone: a permission request awaiting a decision. */
    public const val REQUEST: String = "request"

    /** Phone to desktop: a signed decision for exactly one request. */
    public const val DECISION: String = "decision"

    /** Desktop to phone: which IDE and workspace is currently attached. */
    public const val SESSION_INFO: String = "session_info"

    /** Phone to relay: register or refresh the FCM token for this pair. */
    public const val FCM_REGISTER: String = "fcm_register"

    /** Phone to relay to desktop: the phone's device id and signing public key. */
    public const val PAIR_HELLO: String = "pair_hello"

    /** Phone to desktop: stop the agent session immediately. */
    public const val ABORT: String = "abort"

    /** Relay to sender: the frame was durably accepted. Gates local "resolved" state. */
    public const val ACK: String = "ack"

    /** Relay to desktop: nobody decided before `expiresAt`, so stop waiting. */
    public const val EXPIRED: String = "expired"

    /**
     * Desktop to phone: the agent gave up on a request, so drop its card.
     *
     * Without this the phone keeps showing an approval for a tool call the IDE already cancelled,
     * inviting the user to authorize something nobody is waiting on.
     */
    public const val CANCEL: String = "cancel"

    /** Relay to either side: whether the peer on the other side currently holds a socket. */
    public const val PRESENCE: String = "presence"
}

/**
 * Every v2 frame on the wire is an envelope. Keeping the discriminator and the request id in the
 * outer object lets the relay route, store and expire frames without deserializing the payload,
 * which matters because a payload can carry a large diff.
 */
@Serializable
public data class Envelope(
    val v: String = Protocol.VERSION,
    val type: String,
    val requestId: String? = null,
    val payload: JsonElement? = null
)

/**
 * Signature algorithms a peer may announce.
 *
 * The algorithm is on the wire rather than assumed, because the two ends cannot use the same one.
 * The desktop signs with Ed25519, but Android's hardware-backed keystore has no Ed25519 at this
 * app's `minSdk` of 29, so the phone signs with a hardware-backed EC P-256 key instead. Hardcoding
 * one algorithm would have forced the phone's key into software to match.
 */
public object SignatureAlgorithm {
    public const val ED25519: String = "Ed25519"
    public const val ECDSA_P256_SHA256: String = "SHA256withECDSA"
}

/** Phone identity announced once per pairing, so the desktop can verify later decisions. */
@Serializable
public data class PairHello(
    val deviceId: String,
    val publicKey: String,
    val algorithm: String = SignatureAlgorithm.ECDSA_P256_SHA256,
    val appVersion: String? = null
)

/**
 * Relay acknowledgement. `status` is `stored` for requests and `delivered` for decisions.
 *
 * The nullable fields were added after the desktop was found waiting a request's full five-minute
 * TTL for a phone that was never going to answer. The relay already knows, at the moment it accepts
 * a request, whether a phone socket was attached and whether a push could be sent; passing that
 * back releases the agent in seconds instead of minutes.
 *
 * They are nullable rather than defaulted to false because "an older relay did not say" and "the
 * relay said no" must behave differently: the first has to preserve the old full-TTL wait, only the
 * second may shorten it.
 */
@Serializable
public data class AckPayload(
    val requestId: String,
    val status: String,
    /** Whether the frame reached a live socket on the other side. */
    val delivered: Boolean? = null,
    /** Whether a push notification was dispatched to wake a sleeping phone. */
    val pushQueued: Boolean? = null,
    /** Whether a phone currently holds a socket on this pair. */
    val phoneOnline: Boolean? = null,
    /** Whether a desktop currently holds a socket on this pair. */
    val desktopOnline: Boolean? = null
)

/** Relay-to-peer notice that the other side attached or dropped its socket. */
@Serializable
public data class PresencePayload(
    val phoneOnline: Boolean? = null,
    val desktopOnline: Boolean? = null
)

/** Desktop-to-phone notice that a request no longer needs an answer. */
@Serializable
public data class CancelPayload(
    val requestId: String,
    val reason: String = "cancelled_by_agent"
)

/** Relay-side expiry notice, so a request abandoned on the phone still unblocks the agent. */
@Serializable
public data class ExpiredPayload(
    val requestId: String,
    val reason: String = "expired"
)

/**
 * Encodes and decodes [Envelope] frames.
 *
 * [decode] also accepts bare v1 frames (a raw `PermissionRequest` or `PermissionDecision` with no
 * envelope) by sniffing their shape, so a desktop and a phone on different builds degrade to a
 * readable frame instead of a silent parse failure during rollout.
 */
public object EnvelopeCodec {

    public val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    public fun encode(type: String, payload: JsonElement?, requestId: String? = null): String =
        json.encodeToString(Envelope.serializer(), Envelope(type = type, requestId = requestId, payload = payload))

    public fun encodeRequest(request: PermissionRequest): String =
        encode(
            type = FrameType.REQUEST,
            payload = json.encodeToJsonElement(PermissionRequest.serializer(), request),
            requestId = request.id
        )

    public fun encodeDecision(decision: PermissionDecision): String =
        encode(
            type = FrameType.DECISION,
            payload = json.encodeToJsonElement(PermissionDecision.serializer(), decision),
            requestId = decision.requestId
        )

    public fun encodeSessionInfo(announcement: SessionAnnouncement): String =
        encode(
            type = FrameType.SESSION_INFO,
            payload = json.encodeToJsonElement(SessionAnnouncement.serializer(), announcement)
        )

    public fun encodePairHello(hello: PairHello): String =
        encode(type = FrameType.PAIR_HELLO, payload = json.encodeToJsonElement(PairHello.serializer(), hello))

    public fun encodeCancel(cancel: CancelPayload): String =
        encode(
            type = FrameType.CANCEL,
            payload = json.encodeToJsonElement(CancelPayload.serializer(), cancel),
            requestId = cancel.requestId
        )

    /** Returns null when [text] is not JSON at all, rather than throwing on every stray frame. */
    public fun decode(text: String): Envelope? {
        val element = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null

        val declaredType = (element["type"] as? JsonPrimitive)?.contentOrNull
        val declaredVersion = (element["v"] as? JsonPrimitive)?.contentOrNull

        // A top-level `v` is the envelope marker. Bare v1 payloads never carry one: a request has
        // `protocolVersion`, and a session announcement has only `type`.
        if (declaredType != null && declaredVersion != null) {
            return runCatching { json.decodeFromJsonElement(Envelope.serializer(), element) }.getOrNull()
        }

        // Legacy v1: the payload object was sent bare. Infer the frame type from its shape.
        val inferredType = when {
            declaredType != null -> declaredType
            element.containsKey("permission") -> FrameType.REQUEST
            element.containsKey("decision") -> FrameType.DECISION
            else -> return null
        }
        return Envelope(
            v = Protocol.VERSION_LEGACY,
            type = inferredType,
            requestId = (element["id"] ?: element["requestId"])?.jsonPrimitive?.contentOrNull,
            payload = element
        )
    }

    public fun asRequest(envelope: Envelope): PermissionRequest? {
        val payload = envelope.payload ?: return null
        return runCatching { json.decodeFromJsonElement(PermissionRequest.serializer(), payload) }.getOrNull()
    }

    public fun asDecision(envelope: Envelope): PermissionDecision? {
        val payload = envelope.payload ?: return null
        return runCatching { json.decodeFromJsonElement(PermissionDecision.serializer(), payload) }.getOrNull()
    }

    public fun asSessionAnnouncement(envelope: Envelope): SessionAnnouncement? {
        val payload = envelope.payload ?: return null
        return runCatching { json.decodeFromJsonElement(SessionAnnouncement.serializer(), payload) }.getOrNull()
    }

    public fun asPairHello(envelope: Envelope): PairHello? {
        val payload = envelope.payload ?: return null
        return runCatching { json.decodeFromJsonElement(PairHello.serializer(), payload) }.getOrNull()
    }

    public fun asAck(envelope: Envelope): AckPayload? {
        val payload = envelope.payload ?: return null
        return runCatching { json.decodeFromJsonElement(AckPayload.serializer(), payload) }.getOrNull()
    }

    public fun asPresence(envelope: Envelope): PresencePayload? {
        val payload = envelope.payload ?: return null
        return runCatching { json.decodeFromJsonElement(PresencePayload.serializer(), payload) }.getOrNull()
    }

    public fun asCancel(envelope: Envelope): CancelPayload? {
        val payload = envelope.payload ?: return null
        return runCatching { json.decodeFromJsonElement(CancelPayload.serializer(), payload) }.getOrNull()
    }
}
