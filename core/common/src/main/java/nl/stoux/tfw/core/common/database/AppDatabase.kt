package nl.stoux.tfw.core.common.database

import androidx.room.Database
import androidx.room.RoomDatabase
import nl.stoux.tfw.core.common.database.dao.EditionDao
import nl.stoux.tfw.core.common.database.entity.EditionEntity
import nl.stoux.tfw.core.common.database.entity.LivesetEntity
import nl.stoux.tfw.core.common.database.entity.TrackEntity

@Database(
    entities = [
        EditionEntity::class,
        LivesetEntity::class,
        TrackEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun editionDao(): EditionDao
}
