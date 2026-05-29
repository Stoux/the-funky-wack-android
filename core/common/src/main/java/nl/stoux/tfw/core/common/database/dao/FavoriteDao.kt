package nl.stoux.tfw.core.common.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import nl.stoux.tfw.core.common.database.entity.FavoriteEntity

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY favoritedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT livesetId FROM favorites")
    fun observeIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE livesetId = :livesetId")
    suspend fun delete(livesetId: Long)

    @Query("DELETE FROM favorites")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<FavoriteEntity>) {
        clear()
        items.forEach { upsert(it) }
    }
}
