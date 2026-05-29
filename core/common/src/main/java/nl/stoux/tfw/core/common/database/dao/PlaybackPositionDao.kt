package nl.stoux.tfw.core.common.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import nl.stoux.tfw.core.common.database.entity.PlaybackPositionEntity

@Dao
interface PlaybackPositionDao {
    @Query("SELECT * FROM playback_positions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PlaybackPositionEntity>>

    @Query("SELECT * FROM playback_positions WHERE livesetId = :livesetId LIMIT 1")
    suspend fun get(livesetId: Long): PlaybackPositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: PlaybackPositionEntity)

    @Query("DELETE FROM playback_positions WHERE livesetId = :livesetId")
    suspend fun delete(livesetId: Long)

    @Query("DELETE FROM playback_positions")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(positions: List<PlaybackPositionEntity>) {
        clear()
        positions.forEach { upsert(it) }
    }
}
