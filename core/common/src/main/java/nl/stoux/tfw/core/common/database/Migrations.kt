package nl.stoux.tfw.core.common.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migrations for TFW app.
 */
object Migrations {

    /**
     * Migration from version 4 to 5:
     * - Change LivesetDownloadEntity foreign key from CASCADE to NO_ACTION
     *
     * SQLite doesn't support ALTER TABLE to change foreign key constraints,
     * so we recreate the table with the correct constraint.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Create new table with NO_ACTION foreign key
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS liveset_downloads_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    livesetId INTEGER NOT NULL,
                    state TEXT NOT NULL,
                    progressPercent REAL NOT NULL DEFAULT 0,
                    bytesDownloaded INTEGER NOT NULL DEFAULT 0,
                    totalBytes INTEGER NOT NULL DEFAULT 0,
                    quality TEXT NOT NULL,
                    audioFilePath TEXT,
                    audioFileSize INTEGER NOT NULL DEFAULT 0,
                    waveformJson TEXT,
                    media3DownloadId TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    completedAt INTEGER,
                    failureReason TEXT,
                    FOREIGN KEY (livesetId) REFERENCES livesets(id) ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED
                )
            """.trimIndent())

            // Create indices
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_liveset_downloads_new_livesetId ON liveset_downloads_new (livesetId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_liveset_downloads_new_state ON liveset_downloads_new (state)")

            // Copy data from old table
            db.execSQL("""
                INSERT INTO liveset_downloads_new (
                    id, livesetId, state, progressPercent, bytesDownloaded, totalBytes,
                    quality, audioFilePath, audioFileSize, waveformJson, media3DownloadId,
                    createdAt, completedAt, failureReason
                )
                SELECT
                    id, livesetId, state, progressPercent, bytesDownloaded, totalBytes,
                    quality, audioFilePath, audioFileSize, waveformJson, media3DownloadId,
                    createdAt, completedAt, failureReason
                FROM liveset_downloads
            """.trimIndent())

            // Drop old table
            db.execSQL("DROP TABLE liveset_downloads")

            // Rename new table to original name
            db.execSQL("ALTER TABLE liveset_downloads_new RENAME TO liveset_downloads")

            // Recreate indices with correct names
            db.execSQL("DROP INDEX IF EXISTS index_liveset_downloads_new_livesetId")
            db.execSQL("DROP INDEX IF EXISTS index_liveset_downloads_new_state")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_liveset_downloads_livesetId ON liveset_downloads (livesetId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_liveset_downloads_state ON liveset_downloads (state)")
        }
    }

    val ALL_MIGRATIONS = arrayOf(MIGRATION_4_5)
}
