package com.ovi.handoff.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PermissionRequestEntity::class], version = 3, exportSchema = true)
public abstract class HandoffDatabase : RoomDatabase() {
    public abstract fun requestDao(): RequestDao
}

/**
 * Migrations for the audit table.
 *
 * These exist because the database was previously built with `fallbackToDestructiveMigration()`. For
 * a product whose whole purpose is a tamper-evident record of what an agent was allowed to do,
 * dropping that record on every schema bump is a data-loss bug, not a convenience.
 */
public object HandoffMigrations {

    /**
     * v2 to v3: adds the fields needed to reproduce a request exactly and to record its outcome.
     *
     * Existing rows are carried over rather than discarded. They keep their comma-joined list values,
     * which `decodeList` still reads, and are marked resolved with an unknown outcome, because the old
     * schema had no way to record what the user actually chose.
     */
    public val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS permission_requests_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    protocolVersion TEXT NOT NULL,
                    agentId TEXT NOT NULL,
                    agentName TEXT NOT NULL,
                    agentVersion TEXT,
                    sessionId TEXT NOT NULL,
                    sessionProject TEXT,
                    sessionWorkspace TEXT,
                    permissionType TEXT NOT NULL,
                    permissionCommand TEXT,
                    permissionTarget TEXT,
                    permissionDescription TEXT,
                    permissionCwd TEXT,
                    permissionDiff TEXT,
                    riskLevel TEXT NOT NULL,
                    riskReasons TEXT NOT NULL,
                    options TEXT NOT NULL,
                    createdAt TEXT NOT NULL,
                    expiresAt TEXT NOT NULL,
                    createdAtEpochMs INTEGER NOT NULL,
                    expiresAtEpochMs INTEGER,
                    requestSignature TEXT,
                    isPending INTEGER NOT NULL,
                    decision TEXT,
                    decidedAtEpochMs INTEGER,
                    questionPrompt TEXT,
                    questionOptions TEXT,
                    questionIsMultiSelect INTEGER NOT NULL,
                    planTitle TEXT,
                    planSummary TEXT,
                    planReviewRequired TEXT
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO permission_requests_new (
                    id, protocolVersion, agentId, agentName, agentVersion,
                    sessionId, sessionProject, sessionWorkspace,
                    permissionType, permissionCommand, permissionTarget, permissionDescription,
                    permissionCwd, permissionDiff, riskLevel, riskReasons, options,
                    createdAt, expiresAt, createdAtEpochMs, expiresAtEpochMs, requestSignature,
                    isPending, decision, decidedAtEpochMs,
                    questionPrompt, questionOptions, questionIsMultiSelect,
                    planTitle, planSummary, planReviewRequired
                )
                SELECT
                    id, protocolVersion, agentId, agentName, NULL,
                    sessionId, NULL, permissionCwd,
                    permissionType, permissionCommand, permissionTarget, permissionDescription,
                    permissionCwd, permissionDiff, riskLevel, riskReasons, options,
                    createdAt, expiresAt, 0, NULL, NULL,
                    0, NULL, NULL,
                    questionPrompt, questionOptions, questionIsMultiSelect,
                    planTitle, planSummary, planReviewRequired
                FROM permission_requests
                """.trimIndent()
            )

            // Old rows have no epoch timestamp. Ordering by 0 would collapse the whole existing
            // history into one bucket, so derive it from the ISO string SQLite can already parse.
            db.execSQL(
                """
                UPDATE permission_requests_new
                SET createdAtEpochMs = COALESCE(CAST(strftime('%s', createdAt) AS INTEGER) * 1000, 0)
                """.trimIndent()
            )

            db.execSQL("DROP TABLE permission_requests")
            db.execSQL("ALTER TABLE permission_requests_new RENAME TO permission_requests")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_permission_requests_isPending_createdAtEpochMs " +
                    "ON permission_requests (isPending, createdAtEpochMs)"
            )
        }
    }

    /** v1 to v2 predates this work; the columns it added are already present in v2 rows. */
    public val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            for (column in listOf("questionPrompt TEXT", "questionOptions TEXT", "planTitle TEXT", "planSummary TEXT", "planReviewRequired TEXT")) {
                runCatching { db.execSQL("ALTER TABLE permission_requests ADD COLUMN $column") }
            }
            runCatching {
                db.execSQL("ALTER TABLE permission_requests ADD COLUMN questionIsMultiSelect INTEGER NOT NULL DEFAULT 0")
            }
        }
    }

    public val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
