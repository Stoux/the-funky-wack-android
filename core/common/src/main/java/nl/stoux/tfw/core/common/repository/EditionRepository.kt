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

/**
 * Stale-While-Revalidate repository for Editions & associated content.
 */
interface EditionRepository {
    fun getEditions(): Flow<List<EditionWithContent>>

    fun findLiveset(id: Long): Flow<LivesetWithDetails?>

    fun getLivesets(page: Int = 0, pageSize: Int = Int.MAX_VALUE): Flow<List<LivesetWithDetails>>

    fun getLivesets(editionId: Long, page: Int = 0, pageSize: Int = Int.MAX_VALUE): Flow<List<LivesetWithDetails>>

    suspend fun refreshEditions()
}

class EditionRepositoryImpl @javax.inject.Inject constructor(
    private val api: ApiService,
    private val db: AppDatabase,
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

    override suspend fun refreshEditions() = withContext(Dispatchers.IO) {
        try {
            val dtos = api.getEditions()

            val editions = mutableListOf<EditionEntity>()
            val livesets = mutableListOf<LivesetEntity>()
            val tracks = mutableListOf<TrackEntity>()

            dtos.forEach { edDto ->
                editions += edDto.toEditionEntity()
                edDto.livesets.orEmpty().forEach { lsDto ->
                    livesets += lsDto.toLivesetEntity()
                    tracks += lsDto.tracks.orEmpty().map { it.toTrackEntity() }
                }
            }

            Log.d("TfwDao", "Editions ${editions.size} | Livesets ${livesets.size}")

            db.withTransaction {
                dao.replaceAll(editions, livesets, tracks)
            }

        } catch (_: Throwable) {
            // Intentionally swallow to keep UI working with stale data
        }
    }
}
