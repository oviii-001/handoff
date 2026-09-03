package com.ovi.handoff.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RequestDao {
    @Query("SELECT * FROM permission_requests WHERE isPending = 1")
    fun observePendingRequests(): Flow<List<PermissionRequestEntity>>

    @Query("SELECT * FROM permission_requests ORDER BY createdAt DESC")
    fun observeAllRequests(): Flow<List<PermissionRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(request: PermissionRequestEntity)

    @Query("UPDATE permission_requests SET isPending = 0 WHERE id = :id")
    fun markAsResolved(id: String)
}
