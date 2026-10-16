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

    /**
     * Migration from version 5 to 6:
     * - Add social/stats columns to livesets table
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE livesets ADD COLUMN playsCount INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE livesets ADD COLUMN favoritesCount INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE livesets ADD COLUMN isFavorited INTEGER DEFAULT NULL")
        }
    }

    /**
     * Migration from version 6 to 7:
     * - Add Room-backed caches for favorites, playlists (with items), and playback positions
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS favorites (" +
                    "livesetId INTEGER PRIMARY KEY NOT NULL, " +
                    "favoritedAt TEXT)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS playlists (" +
                    "id INTEGER PRIMARY KEY NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "description TEXT, " +
                    "visibility TEXT NOT NULL, " +
                    "shareCode TEXT NOT NULL, " +
                    "itemsCount INTEGER NOT NULL, " +
                    "isOwn INTEGER NOT NULL, " +
                    "ownerName TEXT)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS playlist_items (" +
                    "playlistId INTEGER NOT NULL, " +
                    "livesetId INTEGER NOT NULL, " +
                    "position INTEGER NOT NULL, " +
                    "PRIMARY KEY (playlistId, livesetId))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_items_playlistId ON playlist_items (playlistId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_items_livesetId ON playlist_items (livesetId)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS playback_positions (" +
                    "livesetId INTEGER PRIMARY KEY NOT NULL, " +
                    "positionSeconds INTEGER NOT NULL, " +
                    "updatedAt TEXT)"
            )
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playback_positions ADD COLUMN clientId TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playback_positions ADD COLUMN deviceLabel TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE playback_positions ADD COLUMN deviceType TEXT DEFAULT NULL")
        }
    }

    /**
     * Migration from version 9 to 10:
     * - Add comments count to livesets (filled on the next editions refresh)
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE livesets ADD COLUMN commentsCount INTEGER DEFAULT NULL")
        }
    }

    /**
     * Migration from version 10 to 11:
     * - Add linked canonical tracks (songs JSON) to tracklist lines (filled on the next editions refresh)
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tracks ADD COLUMN songs TEXT DEFAULT NULL")
        }
    }

    val ALL_MIGRATIONS = arrayOf(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
}
