package com.ovi.handoff.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PermissionRequestEntity::class], version = 2, exportSchema = false)
abstract class HandoffDatabase : RoomDatabase() {
    abstract fun requestDao(): RequestDao
}
