package nl.stoux.tfw.core.common.repository

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import nl.stoux.tfw.core.common.database.AppDatabase
import nl.stoux.tfw.core.common.database.dao.EditionDao
import nl.stoux.tfw.core.common.database.dao.EditionWithContent
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
    suspend fun refreshEditions()
}

class EditionRepositoryImpl(
    private val api: ApiService,
    private val db: AppDatabase,
) : EditionRepository {

    private val dao: EditionDao get() = db.editionDao()

    override fun getEditions(): Flow<List<EditionWithContent>> = dao.getEditionsWithContent()

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

            db.withTransaction {
                dao.replaceAll(editions, livesets, tracks)
            }
        } catch (_: Throwable) {
            // Intentionally swallow to keep UI working with stale data
        }
    }
}
