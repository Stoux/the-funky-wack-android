package nl.stoux.tfw.core.common.repository

import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import nl.stoux.tfw.core.common.database.AppDatabase
import nl.stoux.tfw.core.common.database.dao.EditionDao
import nl.stoux.tfw.core.common.database.dao.EditionWithContent
import nl.stoux.tfw.core.common.database.dao.LivesetWithDetails
import nl.stoux.tfw.core.common.database.entity.EditionEntity
import nl.stoux.tfw.core.common.database.entity.LivesetEntity
import nl.stoux.tfw.core.common.database.entity.TrackEntity
import nl.stoux.tfw.core.common.mappers.toEditionEntity
import nl.stoux.tfw.core.common.mappers.toLivesetEntity
import nl.stoux.tfw.core.common.mappers.toTrackEntity
import nl.stoux.tfw.core.common.network.ApiService
import javax.inject.Inject

/**
 * Stale-While-Revalidate repository for Editions & associated content.
 */
interface EditionRepository {
    fun getEditions(): Flow<List<EditionWithContent>>

    fun findLiveset(id: Long): Flow<LivesetWithDetails?>

    fun getLivesets(page: Int = 0, pageSize: Int = Int.MAX_VALUE): Flow<List<LivesetWithDetails>>

    fun getLivesets(editionId: Long, page: Int = 0, pageSize: Int = Int.MAX_VALUE): Flow<List<LivesetWithDetails>>

    fun searchLivesets(query: String, limit: Int = 20): Flow<List<LivesetWithDetails>>

    fun searchEditions(query: String, limit: Int = 10): Flow<List<EditionWithContent>>

    suspend fun refreshEditions()
}

class EditionRepositoryImpl @Inject constructor(
    private val api: ApiService,
    private val db: AppDatabase,
    private val cleanupCallbackHolder: LivesetCleanupCallbackHolder,
) : EditionRepository {

    private val dao: EditionDao get() = db.editionDao()

    override fun getEditions(): Flow<List<EditionWithContent>> =
        dao.getEditionsWithContent().map { editions ->
            // Ensure editions are sorted by numeric number DESC
            val sortedEditions = editions.sortedWith(
                compareByDescending<EditionWithContent> { it.edition.number.toLongOrNull() ?: Long.MIN_VALUE }
                    .thenBy { it.edition.id }
            )
            // For each edition, sort livesets by lineup_order (nulls last) then id ASC
            sortedEditions.map { ed ->
                val sortedLivesets = ed.livesets.sortedWith(
                    compareBy<nl.stoux.tfw.core.common.database.dao.LivesetWithDetails> { it.liveset.lineupOrder == null }
                        .thenBy { it.liveset.lineupOrder ?: Int.MAX_VALUE }
                        .thenBy { it.liveset.id }
                )
                ed.copy(livesets = sortedLivesets)
            }
        }

    override fun findLiveset(id: Long): Flow<LivesetWithDetails?> = dao.getLivesetById(id)

    override fun getLivesets(page: Int, pageSize: Int): Flow<List<LivesetWithDetails>> = dao.getLivesets(page, pageSize)

    override fun getLivesets(editionId: Long, page: Int, pageSize: Int): Flow<List<LivesetWithDetails>> = dao.getEditionLivesets(editionId, page, pageSize)

    override fun searchLivesets(query: String, limit: Int): Flow<List<LivesetWithDetails>> = dao.searchLivesets(query, limit)

    override fun searchEditions(query: String, limit: Int): Flow<List<EditionWithContent>> = dao.searchEditions(query, limit)

    override suspend fun refreshEditions(): Unit = withContext(Dispatchers.IO) {
        try {
            val dtos = api.getEditions()

            val newEditions = mutableListOf<EditionEntity>()
            val newLivesets = mutableListOf<LivesetEntity>()
            val newTracks = mutableListOf<TrackEntity>()

            dtos.forEach { edDto ->
                newEditions += edDto.toEditionEntity()
                edDto.livesets.orEmpty().forEach { lsDto ->
                    newLivesets += lsDto.toLivesetEntity()
                    newTracks += lsDto.tracks.orEmpty().map { it.toTrackEntity() }
                }
            }

            Log.d("TfwDao", "Editions ${newEditions.size} | Livesets ${newLivesets.size}")

            // Get current IDs from database
            val existingEditionIds = dao.getAllEditionIds().toSet()
            val existingLivesetIds = dao.getAllLivesetIds().toSet()
            val existingTrackIds = dao.getAllTrackIds().toSet()

            // Calculate IDs from API response
            val apiEditionIds = newEditions.map { it.id }.toSet()
            val apiLivesetIds = newLivesets.map { it.id }.toSet()
            val apiTrackIds = newTracks.map { it.id }.toSet()

            // Find items to delete (exist in DB but not in API)
            val editionsToDelete = existingEditionIds - apiEditionIds
            val livesetsToDelete = existingLivesetIds - apiLivesetIds
            val tracksToDelete = existingTrackIds - apiTrackIds

            // Clean up downloads before deleting livesets
            if (livesetsToDelete.isNotEmpty()) {
                cleanupCallbackHolder.callback?.onLivesetsRemoving(livesetsToDelete)
            }

            db.withTransaction {
                // Delete removed items (order matters for FKs: tracks -> livesets -> editions)
                if (tracksToDelete.isNotEmpty()) {
                    dao.deleteTracksByIds(tracksToDelete.toList())
                }
                if (livesetsToDelete.isNotEmpty()) {
                    dao.deleteLivesetsByIds(livesetsToDelete.toList())
                }
                if (editionsToDelete.isNotEmpty()) {
                    dao.deleteEditionsByIds(editionsToDelete.toList())
                }

                // Upsert new/updated items
                if (newEditions.isNotEmpty()) dao.upsertEditions(newEditions)
                if (newLivesets.isNotEmpty()) dao.upsertLivesets(newLivesets)
                if (newTracks.isNotEmpty()) dao.upsertTracks(newTracks)
            }

        } catch (e: Throwable) {
            Log.w("TfwDao", "Failed to refresh editions", e)
            // Intentionally swallow to keep UI working with stale data
        }
    }
}
