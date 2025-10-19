package nl.stoux.tfw.core.common.database

import androidx.room.Database
import androidx.room.RoomDatabase
import nl.stoux.tfw.core.common.database.dao.EditionDao
import nl.stoux.tfw.core.common.database.dao.ManualQueueDao
import nl.stoux.tfw.core.common.database.entity.EditionEntity
import nl.stoux.tfw.core.common.database.entity.LivesetEntity
import nl.stoux.tfw.core.common.database.entity.ManualQueueItemEntity
import nl.stoux.tfw.core.common.database.entity.TrackEntity

@Database(
    entities = [
        EditionEntity::class,
        LivesetEntity::class,
        TrackEntity::class,
        ManualQueueItemEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun editionDao(): EditionDao
    abstract fun manualQueueDao(): ManualQueueDao
}
