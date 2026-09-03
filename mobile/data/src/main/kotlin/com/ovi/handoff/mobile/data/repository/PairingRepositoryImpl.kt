package com.ovi.handoff.mobile.data.repository

import android.content.Context
import java.util.Base64
import com.ovi.handoff.mobile.domain.repository.PairingRepository

public class PairingRepositoryImpl(
    private val context: Context
) : PairingRepository {
    private val prefs by lazy {
        context.getSharedPreferences("handoff_pairing_prefs", Context.MODE_PRIVATE)
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
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        private const val KEY_PAIR_ID = "paired_session_id"
        private const val KEY_PUBLIC_KEY = "paired_desktop_public_key"
    }
}
