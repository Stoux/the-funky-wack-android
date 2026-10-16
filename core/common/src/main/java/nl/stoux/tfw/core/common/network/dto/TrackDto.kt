package nl.stoux.tfw.core.common.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class LivesetTrackDto(
    val id: Long,
    val liveset_id: Long,
    val title: String,
    // Timestamp in seconds from start of liveset
    val timestamp: Int? = null,
    val order: Int,
    /** Canonical tracks this line links to: empty when unknown ("? - ID"), 2+ for a mashup. */
    val songs: List<TrackSong> = emptyList(),
)

/** A canonical track (artist + title + version; a remix is its own track). [slug] identifies it everywhere. */
@Serializable
data class TrackSong(
    val slug: String,
    val artist: String,
    val title: String,
    val version: String? = null,
)