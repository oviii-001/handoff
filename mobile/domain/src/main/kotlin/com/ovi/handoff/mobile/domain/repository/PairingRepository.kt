package com.ovi.handoff.mobile.domain.repository

import kotlinx.coroutines.flow.Flow

/** Which IDE and workspace the desktop last reported. */
public data class ConnectedSession(
    val ideName: String,
    val workspaceName: String?,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Everything the pairing QR code carries.
 *
 * [pairSecret] is new and is what authenticates this phone to the relay. Previously the pair id
 * alone was enough to join a room, so anyone who learned or guessed it could approve commands.
 */
public data class PairingInfo(
    val pairId: String,
    val relayHost: String?,
    /** Base64url X.509 key the desktop signs requests with. */
    val desktopPublicKey: String?,
    val pairSecret: String?
)

public interface PairingRepository {
    public suspend fun pairDevice(info: PairingInfo): Result<Unit>
    public suspend fun getPairId(): String?
    public suspend fun getPairing(): PairingInfo?
    public suspend fun clearPairing(): Result<Unit>
    public suspend fun saveConnectedSession(ideName: String, workspaceName: String?): Result<Unit>
    public fun observeConnectedSession(): Flow<ConnectedSession?>
}
