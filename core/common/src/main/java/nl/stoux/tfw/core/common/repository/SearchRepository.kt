package nl.stoux.tfw.core.common.repository

import android.util.Log
import nl.stoux.tfw.core.common.network.ApiService
import nl.stoux.tfw.core.common.network.dto.SearchResponse
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backend search (Meilisearch, shared with the website): livesets, canonical tracks with
 * where they were played, and tracklist lines that aren't linked to a track yet.
 */
@Singleton
class SearchRepository @Inject constructor(
    private val api: ApiService,
) {
    /** Empty result for queries under [MIN_QUERY_LENGTH]; the server would answer 422. */
    suspend fun search(query: String, limit: Int = DEFAULT_LIMIT): Result<SearchResponse> {
        val q = query.trim()
        if (q.length < MIN_QUERY_LENGTH) return Result.success(SearchResponse())
        return runCatching { api.search(q.take(MAX_QUERY_LENGTH), limit.coerceIn(1, MAX_LIMIT)) }
            .onFailure { Log.w(TAG, "Search failed for '$q'", it) }
    }

    companion object {
        private const val TAG = "SearchRepository"
        const val MIN_QUERY_LENGTH = 2
        const val MAX_QUERY_LENGTH = 100
        const val DEFAULT_LIMIT = 10
        const val MAX_LIMIT = 25
    }
}
