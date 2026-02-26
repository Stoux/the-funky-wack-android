package nl.stoux.tfw.core.common.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import nl.stoux.tfw.core.common.database.entity.EditionEntity
import nl.stoux.tfw.core.common.database.entity.LivesetEntity
import nl.stoux.tfw.core.common.database.entity.TrackEntity

/**
 * Relations used for loading the full catalog tree from Room.
 */
data class LivesetWithDetails(
    @Embedded val liveset: LivesetEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "livesetId",
        entity = TrackEntity::class
    )
    val tracks: List<TrackEntity>,


    @Relation(
        parentColumn = "editionId",
        entityColumn = "id",
        entity = EditionEntity::class,
    )
    val edition: EditionEntity,
)

data class EditionWithContent(
    @Embedded val edition: EditionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "editionId",
        entity = LivesetEntity::class
    )
    val livesets: List<LivesetWithDetails>
)

@Dao
interface EditionDao {

    // Reactive stream of the full catalog joined together
    @Transaction
    @Query("SELECT * FROM editions ORDER BY CAST(number AS INTEGER) DESC")
    fun getEditionsWithContent(): Flow<List<EditionWithContent>>

    @Transaction
    @Query("SELECT * FROM livesets WHERE id = :livesetId")
    fun getLivesetById(livesetId: Long): Flow<LivesetWithDetails?>

    @Transaction
    @Query(
        "SELECT ls.* FROM livesets ls " +
            "INNER JOIN editions e ON ls.editionId = e.id " +
            "ORDER BY CAST(e.number AS INTEGER) DESC, lineupOrder ASC, ls.id ASC " +
            "LIMIT :pageSize OFFSET (:page * :pageSize)"
    )
    fun getLivesets(page: Int, pageSize: Int): Flow<List<LivesetWithDetails>>

    @Transaction
    @Query("SELECT * FROM livesets WHERE editionId = :editionId ORDER BY lineupOrder ASC, id ASC LIMIT :pageSize OFFSET (:page * :pageSize)" )
    fun getEditionLivesets(editionId: Long, page: Int, pageSize: Int): Flow<List<LivesetWithDetails>>

    // Upserts
    @Upsert
    suspend fun upsertEditions(items: List<EditionEntity>)

    @Upsert
    suspend fun upsertLivesets(items: List<LivesetEntity>)

    @Upsert
    suspend fun upsertTracks(items: List<TrackEntity>)

    // Clears (order matters due to FKs)
    @Query("DELETE FROM tracks")
    suspend fun clearTracks()

    @Query("DELETE FROM livesets")
    suspend fun clearLivesets()

    @Query("DELETE FROM editions")
    suspend fun clearEditions()

    @Transaction
    suspend fun clearAll() {
        clearTracks()
        clearLivesets()
        clearEditions()
    }

    /**
     * Replace entire catalog in a single transaction. Use RoomDatabase.withTransaction
     * at the call site for atomicity across tables when calling this.
     */
    @Transaction
    suspend fun replaceAll(
        editions: List<EditionEntity>,
        livesets: List<LivesetEntity>,
        tracks: List<TrackEntity>
    ) {
        clearAll()
        if (editions.isNotEmpty()) upsertEditions(editions)
        if (livesets.isNotEmpty()) upsertLivesets(livesets)
        if (tracks.isNotEmpty()) upsertTracks(tracks)
    }

    // Queries for smart sync strategy
    @Query("SELECT id FROM editions")
    suspend fun getAllEditionIds(): List<Long>

    @Query("SELECT id FROM livesets")
    suspend fun getAllLivesetIds(): List<Long>

    @Query("SELECT id FROM tracks")
    suspend fun getAllTrackIds(): List<Long>

    // Selective deletes for sync
    @Query("DELETE FROM editions WHERE id IN (:ids)")
    suspend fun deleteEditionsByIds(ids: List<Long>)

    @Query("DELETE FROM livesets WHERE id IN (:ids)")
    suspend fun deleteLivesetsByIds(ids: List<Long>)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteTracksByIds(ids: List<Long>)

    @Query("DELETE FROM tracks WHERE livesetId IN (:livesetIds)")
    suspend fun deleteTracksByLivesetIds(livesetIds: List<Long>)
}
