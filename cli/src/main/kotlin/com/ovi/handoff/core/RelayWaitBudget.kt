package com.ovi.handoff.core

import com.ovi.handoff.shared.protocol.AckPayload

/**
 * How long to wait for a decision, and when to stop waiting early.
 *
 * Pulled out of [RelayClient] as pure arithmetic so the interesting case — deciding that nobody is
 * ever going to answer — can be tested without a relay, a socket or a clock.
 */
public object RelayWaitBudget {

    /** Used when a request carries no epoch deadline at all. */
    public const val DEFAULT_WAIT_MS: Long = 300_000

    /** Floor, so a request that arrives already near its deadline still gets a usable window. */
    public const val MIN_WAIT_MS: Long = 5_000

    /** Ceiling, so a malformed far-future deadline cannot pin a tool call open indefinitely. */
    public const val MAX_WAIT_MS: Long = 900_000

    /**
     * How long to keep waiting after the relay reports that nothing can reach the phone.
     *
     * Not zero: the phone may be reconnecting at that exact moment, and the relay replays stored
     * requests on attach, so a short window converts a large fraction of these into real answers.
     * Not minutes: the whole point is that the agent stops blocking on an unreachable device.
     */
    public const val UNREACHABLE_GRACE_MS: Long = 20_000

    /** The wait implied by a request's own deadline, clamped to something sane. */
    public fun forDeadline(expiresAtEpochMs: Long?, nowEpochMs: Long): Long {
        val deadline = expiresAtEpochMs ?: return DEFAULT_WAIT_MS
        return (deadline - nowEpochMs).coerceIn(MIN_WAIT_MS, MAX_WAIT_MS)
    }

    /**
     * Grace period to apply after an ack, or null to keep the full deadline.
     *
     * Returns a grace period only when the relay positively reports that the request reached no
     * socket *and* no push was sent. A relay too old to report either leaves both fields null, and
     * null must preserve the previous full-length wait: treating "did not say" as "no" would cut
     * every approval short against an older relay.
     */
    public fun graceAfterAck(ack: AckPayload): Long? {
        val delivered = ack.delivered
        val pushQueued = ack.pushQueued
        if (delivered == null && pushQueued == null) return null
        if (delivered == true || pushQueued == true) return null
        return UNREACHABLE_GRACE_MS
    }
}
