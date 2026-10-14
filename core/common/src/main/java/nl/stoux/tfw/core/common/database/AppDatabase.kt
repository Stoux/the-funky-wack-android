package nl.stoux.tfw.core.common.database

import androidx.room.Database
import androidx.room.RoomDatabase
import nl.stoux.tfw.core.common.database.dao.EditionDao
import nl.stoux.tfw.core.common.database.dao.FavoriteDao
import nl.stoux.tfw.core.common.database.dao.LivesetDownloadDao
import nl.stoux.tfw.core.common.database.dao.ManualQueueDao
import nl.stoux.tfw.core.common.database.dao.PlaybackPositionDao
import nl.stoux.tfw.core.common.database.dao.PlaylistDao
import nl.stoux.tfw.core.common.database.entity.EditionEntity
import nl.stoux.tfw.core.common.database.entity.FavoriteEntity
import nl.stoux.tfw.core.common.database.entity.LivesetDownloadEntity
import nl.stoux.tfw.core.common.database.entity.LivesetEntity
import nl.stoux.tfw.core.common.database.entity.ManualQueueItemEntity
import nl.stoux.tfw.core.common.database.entity.PlaybackPositionEntity
import nl.stoux.tfw.core.common.database.entity.PlaylistEntity
import nl.stoux.tfw.core.common.database.entity.PlaylistItemEntity
import nl.stoux.tfw.core.common.database.entity.TrackEntity

@Database(
    entities = [
        EditionEntity::class,
        LivesetEntity::class,
        TrackEntity::class,
        ManualQueueItemEntity::class,
        LivesetDownloadEntity::class,
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        PlaybackPositionEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun editionDao(): EditionDao
    abstract fun manualQueueDao(): ManualQueueDao
    abstract fun livesetDownloadDao(): LivesetDownloadDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playbackPositionDao(): PlaybackPositionDao
}
