package com.ovi.handoff.mobile.data.repository

import android.content.Context
import com.ovi.handoff.mobile.domain.repository.ConnectedSession
import com.ovi.handoff.mobile.domain.repository.PairingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Base64

public class PairingRepositoryImpl(
    private val context: Context
) : PairingRepository {
    private val prefs by lazy {
        context.getSharedPreferences("handoff_pairing_prefs", Context.MODE_PRIVATE)
    }

    private val sessionFlow = MutableStateFlow<ConnectedSession?>(null)

    init {
        sessionFlow.value = readSessionFromPrefs()
    }

    override suspend fun pairDevice(pairId: String, publicKey: ByteArray): Result<Unit> {
        return try {
            val encodedKey = Base64.getEncoder().encodeToString(publicKey)
            prefs.edit()
                .putString(KEY_PAIR_ID, pairId)
                .putString(KEY_PUBLIC_KEY, encodedKey)
                .commit()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPairId(): String? {
        return prefs.getString(KEY_PAIR_ID, null)
    }

    override suspend fun clearPairing(): Result<Unit> {
        return try {
            prefs.edit().clear().commit()
            sessionFlow.value = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveConnectedSession(ideName: String, workspaceName: String?): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            prefs.edit()
                .putString(KEY_LAST_IDE, ideName)
                .putString(KEY_LAST_WORKSPACE, workspaceName)
                .putLong(KEY_LAST_UPDATED, now)
                .commit()

            val session = ConnectedSession(
                ideName = ideName,
                workspaceName = workspaceName,
                lastUpdated = now
            )
            sessionFlow.value = session
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeConnectedSession(): Flow<ConnectedSession?> {
        return sessionFlow.asStateFlow()
    }

    private fun readSessionFromPrefs(): ConnectedSession? {
        val ide = prefs.getString(KEY_LAST_IDE, null) ?: return null
        val ws = prefs.getString(KEY_LAST_WORKSPACE, null)
        val time = prefs.getLong(KEY_LAST_UPDATED, 0L)
        return ConnectedSession(ideName = ide, workspaceName = ws, lastUpdated = time)
    }

    private companion object {
        private const val KEY_PAIR_ID = "paired_session_id"
        private const val KEY_PUBLIC_KEY = "paired_desktop_public_key"
        private const val KEY_LAST_IDE = "last_connected_ide"
        private const val KEY_LAST_WORKSPACE = "last_connected_workspace"
        private const val KEY_LAST_UPDATED = "last_connected_timestamp"
    }
}
