package nl.stoux.tfw.core.common.repository

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.stoux.tfw.core.common.auth.AuthRepository
import nl.stoux.tfw.core.common.network.ApiService
import nl.stoux.tfw.core.common.network.dto.LikedTrackDto
import nl.stoux.tfw.core.common.network.dto.TrackDetailResponse
import nl.stoux.tfw.core.common.network.dto.TrackSong
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Canonical tracks and the user's liked tracks. Network-backed; likes are kept in memory,
 * refreshed on login and cleared on logout. Toggling is optimistic.
 *
 * Slugs are stable unless an admin merges tracks: a merged-away slug 404s and its likes
 * move to the kept track, so a 404 triggers a likes refresh.
 */
@Singleton
class TrackRepository @Inject constructor(
    private val api: ApiService,
    private val authRepository: AuthRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val likesMutex = Mutex()

    private val _likedTracks = MutableStateFlow<List<LikedTrackDto>>(emptyList())
    /** Last liked first. */
    val likedTracks: StateFlow<List<LikedTrackDto>> = _likedTracks.asStateFlow()

    /** Slugs currently liked, for hearts in tracklists and the player. */
    private val _likedSlugs = MutableStateFlow<Set<String>>(emptySet())
    val likedSlugs: StateFlow<Set<String>> = _likedSlugs.asStateFlow()

    private val _isLoadingLikes = MutableStateFlow(false)
    val isLoadingLikes: StateFlow<Boolean> = _isLoadingLikes.asStateFlow()

    init {
        scope.launch {
            authRepository.isLoggedIn.collect { loggedIn ->
                if (loggedIn) refreshLikes() else clearLikes()
            }
        }
    }

    suspend fun getTrack(slug: String): Result<TrackDetailResponse> =
        runCatching { api.getTrack(slug) }
            .onSuccess { response ->
                // Keep hearts in step with what the server says for this track
                val liked = response.track.isLiked
                if (authRepository.isLoggedIn.value) {
                    _likedSlugs.update { if (liked) it + slug else it - slug }
                }
            }
            .onFailure { e ->
                Log.w(TAG, "Failed to load track $slug", e)
                if ((e as? HttpException)?.code() == 404) scope.launch { refreshLikes() }
            }

    suspend fun refreshLikes(): Result<Unit> {
        if (!authRepository.isLoggedIn.value) return Result.success(Unit)
        return likesMutex.withLock {
            _isLoadingLikes.value = true
            try {
                runCatching { api.getTrackLikes().tracks }
                    .onSuccess { tracks ->
                        _likedTracks.value = tracks
                        _likedSlugs.value = tracks.mapTo(HashSet()) { it.slug }
                    }
                    .onFailure { Log.w(TAG, "Failed to refresh liked tracks", it) }
                    .map { }
            } finally {
                _isLoadingLikes.value = false
            }
        }
    }

    /**
     * Like or unlike [song] (idempotent on the server). Updates hearts right away and rolls
     * back on failure; a 404 means the slug was merged away, so likes get refreshed.
     */
    suspend fun setLiked(song: TrackSong, liked: Boolean): Result<Unit> {
        val slug = song.slug
        val beforeSlugs = _likedSlugs.value
        val beforeTracks = _likedTracks.value
        _likedSlugs.update { if (liked) it + slug else it - slug }
        _likedTracks.update { tracks ->
            if (liked) {
                if (tracks.any { it.slug == slug }) tracks
                else listOf(LikedTrackDto(slug, song.artist, song.title, song.version)) + tracks
            } else {
                tracks.filterNot { it.slug == slug }
            }
        }

        return runCatching {
            if (liked) api.likeTrack(slug) else api.unlikeTrack(slug)
            Unit
        }.onSuccess {
            // Fill in liked_at and appearances for a fresh like
            if (liked) scope.launch { refreshLikes() }
        }.onFailure { e ->
            Log.w(TAG, "Failed to ${if (liked) "like" else "unlike"} $slug", e)
            _likedSlugs.value = beforeSlugs
            _likedTracks.value = beforeTracks
            if ((e as? HttpException)?.code() == 404) scope.launch { refreshLikes() }
        }
    }

    fun isLiked(slug: String): StateFlow<Boolean> = _likedSlugs
        .map { slug in it }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), slug in _likedSlugs.value)

    private fun clearLikes() {
        _likedTracks.value = emptyList()
        _likedSlugs.value = emptySet()
    }

    companion object { private const val TAG = "TrackRepository" }
}
