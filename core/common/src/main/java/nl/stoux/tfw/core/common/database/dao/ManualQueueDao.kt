package nl.stoux.tfw.core.common.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import nl.stoux.tfw.core.common.database.entity.ManualQueueItemEntity

@Dao
interface ManualQueueDao {

    @Query("SELECT * FROM manual_queue_item ORDER BY orderIndex ASC")
    suspend fun loadAll(): List<ManualQueueItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ManualQueueItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<ManualQueueItemEntity>): List<Long>

    @Query("DELETE FROM manual_queue_item WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM manual_queue_item")
    suspend fun clear()

    /**
     * Insert an item next in line using an orderIndex midpoint strategy.
     * Caller provides the computed target orderIndex; this helper just writes it.
     */
    @Transaction
    suspend fun insertWithOrderIndex(item: ManualQueueItemEntity): Long = upsert(item)
}
