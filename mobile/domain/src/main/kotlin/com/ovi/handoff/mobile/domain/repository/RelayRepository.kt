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

    /**
     * Marks requests past their deadline as expired.
     *
     * Returns how many were swept. Without this a request whose deadline passed while the phone was
     * asleep would sit in the queue forever, inviting the user to approve something the agent has
     * already given up on.
     */
    public suspend fun expireOverdueRequests(nowEpochMs: Long = System.currentTimeMillis()): Result<Int>
}
