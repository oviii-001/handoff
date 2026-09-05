package com.ovi.handoff.mobile.domain.repository

import com.ovi.handoff.shared.model.PermissionDecision
import com.ovi.handoff.shared.model.PermissionRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Live state of the relay socket.
 *
 * Surfaced because the connection indicator was previously hardcoded to `isConnected = true`: the
 * app claimed "RELAY CONNECTED" while the socket was down, which is exactly the moment the user
 * needs to know it is not.
 */
public enum class ConnectionState {
    OFFLINE,
    CONNECTING,
    CONNECTED
}

/**
 * A request plus what happened to it.
 *
 * The audit log used to render every entry with a green tick and the label "RECORDED", whether the
 * user had approved it, denied it, or let it expire. An audit trail that cannot distinguish those is
 * not an audit trail, so the outcome is now part of the model.
 */
public data class RequestRecord(
    val request: PermissionRequest,
    val isPending: Boolean,
    /** Null while still pending. One of `com.ovi.handoff.shared.model.DecisionType`. */
    val decision: String?,
    val decidedAtEpochMs: Long?
)

public interface RelayRepository {

    /** Connection state of the long-lived relay socket. */
    public val connectionState: StateFlow<ConnectionState>

    /**
     * Why the socket is not up, in words a user can act on, or null when there is no problem.
     *
     * Kept separate from [ConnectionState] because the two answer different questions. The state
     * drives the connection pill; this drives the sentence underneath it. Previously the reason was
     * discarded entirely — the socket loop swallowed its exception — so a phone refused by the relay
     * for a nameable reason ("no desktop has claimed this pair yet") looked exactly like a phone
     * with no signal.
     */
    public val connectionError: StateFlow<String?>

    /**
     * Every request still awaiting a decision, oldest first.
     *
     * Returns the whole queue rather than one request: the DAO had no `ORDER BY` and the repository
     * took `firstOrNull()`, so with two pending requests the second was unreachable forever.
     */
    public fun observePendingRequests(pairId: String): Flow<List<PermissionRequest>>

    /** Full audit history, newest first, including the recorded outcome. */
    public fun observeHistory(): Flow<List<RequestRecord>>

    /** Returns the request with the given id from the local database, if present. */
    public suspend fun getRequest(id: String): PermissionRequest?

    /** Ensures the socket is running. Safe to call repeatedly. */
    public suspend fun connect(pairId: String): Result<Unit>

    /**
     * Connects and suspends until the relay actually accepts this device, or explains why it will not.
     *
     * Exists because pairing used to be declared successful the moment the code parsed. The relay
     * can refuse the socket — the pair room may be unclaimed, or claimed with a different secret —
     * and the app would still store the pairing, navigate to the paired home screen and wait
     * forever for requests that could never arrive. Confirming the socket is what makes "Paired"
     * mean something.
     */
    public suspend fun awaitConnected(pairId: String, timeoutMs: Long = DEFAULT_CONNECT_TIMEOUT_MS): Result<Unit>

    /**
     * Queues a decision for delivery and resolves it locally only once the relay acknowledges it.
     *
     * The previous implementation emitted into a `MutableSharedFlow` with no replay, so a decision
     * produced before the socket existed was dropped on the floor while the UI reported success and
     * dismissed the notification.
     */
    public suspend fun sendDecision(pairId: String, decision: PermissionDecision): Result<Unit>

    /** Announces this phone's signing key so the desktop can verify decisions. */
    public suspend fun announceIdentity(pairId: String): Result<Unit>

    public suspend fun abortSession(pairId: String): Result<Unit>

    public suspend fun registerPushToken(pairId: String, token: String): Result<Unit>

    public suspend fun clearHistory(): Result<Unit>

    /** Resolves an ephemeral 6-digit PIN into pairing info via the relay. */
    public suspend fun resolvePin(pin: String): Result<PairingInfo>

    /**
     * Marks requests past their deadline as expired.
     *
     * Returns how many were swept. Without this a request whose deadline passed while the phone was
     * asleep would sit in the queue forever, inviting the user to approve something the agent has
     * already given up on.
     */
    public suspend fun expireOverdueRequests(nowEpochMs: Long = System.currentTimeMillis()): Result<Int>

    public companion object {
        /**
         * How long pairing waits for the socket.
         *
         * Long enough for a cold TLS handshake on mobile data, short enough that a user staring at a
         * spinner gets an answer rather than an indefinite wait.
         */
        public const val DEFAULT_CONNECT_TIMEOUT_MS: Long = 15_000
    }
}
