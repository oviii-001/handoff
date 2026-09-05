package com.ovi.handoff.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Every write is `suspend` and every read is ordered.
 *
 * The original DAO exposed blocking functions (Room would throw if one ever reached the main thread)
 * and its pending query had no `ORDER BY`, leaving SQLite free to return rows in any order. Since the
 * repository then took only the first row, which request the user saw was effectively arbitrary.
 */
@Dao
@JvmSuppressWildcards
public abstract class RequestDao {

    /** The approval queue: oldest first, so the request closest to expiring is handled first. */
    @Query("SELECT * FROM permission_requests WHERE isPending = 1 ORDER BY createdAtEpochMs ASC")
    public abstract fun observePendingRequests(): Flow<List<PermissionRequestEntity>>

    @Query("SELECT * FROM permission_requests ORDER BY createdAtEpochMs DESC LIMIT :limit")
    public abstract fun observeHistory(limit: Int): Flow<List<PermissionRequestEntity>>

    @Query("SELECT * FROM permission_requests WHERE id = :id LIMIT 1")
    public abstract suspend fun findById(id: String): PermissionRequestEntity?

    @Query("SELECT * FROM permission_requests WHERE isPending = 1 ORDER BY createdAtEpochMs ASC")
    public abstract suspend fun pendingRequests(): List<PermissionRequestEntity>

    /**
     * Inserts a request without clobbering one that has already been decided.
     *
     * The relay replays undelivered requests whenever the phone reconnects, and a plain REPLACE would
     * resurrect an answered request as pending again the moment a replay raced an in-flight decision.
     */
    public open suspend fun upsertPending(request: PermissionRequestEntity) {
        val existing = findById(request.id)
        if (existing != null && !existing.isPending) return
        insert(request)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract suspend fun insert(request: PermissionRequestEntity): Long

    /** Records the outcome. Returns the number of rows changed, so callers can detect a no-op. */
    @Query(
        "UPDATE permission_requests SET isPending = 0, decision = :decision, decidedAtEpochMs = :decidedAtEpochMs " +
            "WHERE id = :id AND isPending = 1"
    )
    public abstract suspend fun resolve(id: String, decision: String, decidedAtEpochMs: Long): Int

    @Query(
        "UPDATE permission_requests SET isPending = 0, decision = :decision, decidedAtEpochMs = :nowEpochMs " +
            "WHERE isPending = 1 AND expiresAtEpochMs IS NOT NULL AND expiresAtEpochMs <= :nowEpochMs"
    )
    public abstract suspend fun expireOverdue(nowEpochMs: Long, decision: String): Int

    @Query("DELETE FROM permission_requests WHERE isPending = 0")
    public abstract suspend fun clearHistory(): Int

    /** Caps stored history so the audit table cannot grow without bound. */
    @Query(
        "DELETE FROM permission_requests WHERE isPending = 0 AND id NOT IN (" +
            "SELECT id FROM permission_requests WHERE isPending = 0 ORDER BY createdAtEpochMs DESC LIMIT :keep" +
            ")"
    )
    public abstract suspend fun trimHistory(keep: Int): Int
}
