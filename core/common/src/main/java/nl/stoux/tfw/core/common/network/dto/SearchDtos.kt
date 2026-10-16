package nl.stoux.tfw.core.common.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- GET /api/search ---

@Serializable
data class SearchResponse(
    val livesets: List<SearchLivesetDto> = emptyList(),
    val tracks: List<SearchTrackDto> = emptyList(),
    /** Raw tracklist lines without a linked track (LIKE match), so IDs are still findable. */
    @SerialName("unlinked_lines") val unlinkedLines: List<UnlinkedLineDto> = emptyList(),
)

@Serializable
data class SearchLivesetDto(
    val id: Long,
    val title: String,
    @SerialName("artist_name") val artistName: String,
    val genre: String? = null,
    val edition: SearchEditionDto? = null,
)

@Serializable
data class SearchEditionDto(
    val id: Long,
    val number: String,
    @SerialName("tag_line") val tagLine: String? = null,
)

@Serializable
data class SearchTrackDto(
    val slug: String,
    val artist: String,
    val title: String,
    val version: String? = null,
    val appearances: List<TrackAppearanceDto> = emptyList(),
)

@Serializable
data class UnlinkedLineDto(
    val line: String,
    @SerialName("liveset_id") val livesetId: Long,
    @SerialName("liveset_title") val livesetTitle: String,
    @SerialName("artist_name") val artistName: String,
    @SerialName("edition_number") val editionNumber: String? = null,
    /** Seconds into the set, null when the line has no time. */
    val timestamp: Int? = null,
)

/**
 * Where a track was played: newest edition first, playing order within a set.
 * Playing it = start the liveset at [timestamp] (0 when null).
 */
@Serializable
data class TrackAppearanceDto(
    @SerialName("liveset_id") val livesetId: Long,
    @SerialName("liveset_title") val livesetTitle: String,
    @SerialName("artist_name") val artistName: String,
    @SerialName("edition_number") val editionNumber: String? = null,
    /** e.g. "16 May 2025" */
    @SerialName("edition_date") val editionDate: String? = null,
    val timestamp: Int? = null,
    /** Raw tracklist text */
    val line: String,
)

// --- GET /api/tracks/{slug} ---

@Serializable
data class TrackDetailResponse(
    val track: TrackDetailDto,
    val appearances: List<TrackAppearanceDto> = emptyList(),
    @SerialName("other_versions") val otherVersions: List<TrackSong> = emptyList(),
)

@Serializable
data class TrackDetailDto(
    val slug: String,
    val artist: String,
    val title: String,
    val version: String? = null,
    /** Always false for guests. */
    @SerialName("is_liked") val isLiked: Boolean = false,
)

// --- Liked tracks ---

@Serializable
data class TrackLikesResponse(
    val tracks: List<LikedTrackDto> = emptyList(),
)

@Serializable
data class LikedTrackDto(
    val slug: String,
    val artist: String,
    val title: String,
    val version: String? = null,
    @SerialName("liked_at") val likedAt: String? = null,
    val appearances: List<TrackAppearanceDto> = emptyList(),
)

@Serializable
data class TrackLikeResponse(
    val liked: Boolean,
)

/** "Artist - Title (Version)" */
fun TrackSong.displayName(): String = trackDisplayName(artist, title, version)

fun trackDisplayName(artist: String, title: String, version: String?): String =
    buildString {
        append(artist).append(" - ").append(title)
        if (!version.isNullOrBlank()) append(" (").append(version).append(")")
    }
