package com.ovi.handoff.mobile.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.ovi.handoff.mobile.data.security.AndroidDecisionSigner
import com.ovi.handoff.mobile.data.security.SecretVault
import com.ovi.handoff.mobile.domain.repository.ConnectedSession
import com.ovi.handoff.mobile.domain.repository.PairingInfo
import com.ovi.handoff.mobile.domain.repository.PairingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Stores the pairing and the last session the desktop reported.
 *
 * Two fixes beyond the new fields. Every read and write now happens on [Dispatchers.IO]: these are
 * `suspend` functions that were doing blocking disk work on whatever thread called them, and
 * `getPairId()` in particular was called from a `LaunchedEffect` on the main thread during startup.
 * And writes use `apply()` rather than `commit()`, so the caller is not blocked on an fsync.
 *
 * The pairing secret is encrypted through [SecretVault] instead of sitting in plain text.
 */
class PairingRepositoryImpl(
    private val context: Context,
    private val signer: AndroidDecisionSigner? = null
) : PairingRepository {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("handoff_pairing_prefs", Context.MODE_PRIVATE)
    }

    private val sessionFlow = MutableStateFlow<ConnectedSession?>(null)

    @Volatile
    private var cachedPairing: PairingInfo? = null

    @Volatile
    private var pairingLoaded = false

    override suspend fun pairDevice(info: PairingInfo): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val encryptedSecret = info.pairSecret?.let { SecretVault.encrypt(it) }
            if (info.pairSecret != null && encryptedSecret == null) {
                error("This device could not securely store the pairing secret.")
            }

            prefs.edit()
                .putString(KEY_PAIR_ID, info.pairId)
                .putString(KEY_RELAY_HOST, info.relayHost)
                .putString(KEY_DESKTOP_PUBLIC_KEY, info.desktopPublicKey)
                .putString(KEY_PAIR_SECRET, encryptedSecret)
                .apply()

            cachedPairing = info
            pairingLoaded = true
        }
    }

    override suspend fun getPairId(): String? = getPairing()?.pairId

    override suspend fun getPairing(): PairingInfo? {
        cachedPairing?.let { return it }
        if (pairingLoaded) return null

        return withContext(Dispatchers.IO) {
            val pairId = prefs.getString(KEY_PAIR_ID, null)
            pairingLoaded = true
            if (pairId.isNullOrBlank()) {
                null
            } else {
                PairingInfo(
                    pairId = pairId,
                    relayHost = prefs.getString(KEY_RELAY_HOST, null),
                    desktopPublicKey = prefs.getString(KEY_DESKTOP_PUBLIC_KEY, null),
                    pairSecret = SecretVault.decrypt(prefs.getString(KEY_PAIR_SECRET, null))
                ).also { cachedPairing = it }
            }
        }
    }

    override suspend fun clearPairing(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            prefs.edit().clear().apply()
            cachedPairing = null
            pairingLoaded = true
            sessionFlow.value = null
            // Retire the signing key too. Leaving it in place would let a later re-pair present the
            // same device identity to a different desktop.
            signer?.reset()
            Unit
        }
    }

    override suspend fun saveConnectedSession(ideName: String, workspaceName: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis()
                val session = ConnectedSession(
                    ideName = ideName,
                    workspaceName = workspaceName,
                    lastUpdated = now
                )
                // Skip the write when nothing changed: the desktop re-announces on every reconnect and
                // before every request, so this would otherwise be a disk write per request.
                val existing = sessionFlow.value
                if (existing?.ideName == ideName && existing.workspaceName == workspaceName) {
                    return@runCatching
                }

                prefs.edit()
                    .putString(KEY_LAST_IDE, ideName)
                    .putString(KEY_LAST_WORKSPACE, workspaceName)
                    .putLong(KEY_LAST_UPDATED, now)
                    .apply()

                sessionFlow.value = session
            }
        }

    override fun observeConnectedSession(): Flow<ConnectedSession?> = sessionFlow.asStateFlow()

    /**
     * Loads the persisted session into the flow.
     *
     * Called explicitly rather than from `init`, because the constructor previously read from disk,
     * making object construction, and therefore dependency-graph creation, a blocking I/O operation.
     */
    suspend fun warmUp() {
        if (sessionFlow.value != null) return
        withContext(Dispatchers.IO) {
            val ide = prefs.getString(KEY_LAST_IDE, null) ?: return@withContext
            sessionFlow.value = ConnectedSession(
                ideName = ide,
                workspaceName = prefs.getString(KEY_LAST_WORKSPACE, null),
                lastUpdated = prefs.getLong(KEY_LAST_UPDATED, 0L)
            )
        }
    }

    private companion object {
        const val KEY_PAIR_ID = "paired_session_id"
        const val KEY_RELAY_HOST = "paired_relay_host"
        const val KEY_DESKTOP_PUBLIC_KEY = "paired_desktop_public_key"
        const val KEY_PAIR_SECRET = "paired_relay_secret"
        const val KEY_LAST_IDE = "last_connected_ide"
        const val KEY_LAST_WORKSPACE = "last_connected_workspace"
        const val KEY_LAST_UPDATED = "last_connected_timestamp"
    }
}
