package nl.stoux.tfw.core.common.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class LivesetFilesByQualityDto(
    val lq: String? = null,
    val hq: String? = null,
    val lossless: String? = null,
)

@Serializable
data class LivesetDto(
    val id: Long,
    val edition_id: Long,
    val edition: EditionRefDto? = null,
    val title: String,
    val artist_name: String,
    val description: String? = null,
    val bpm: String? = null,
    val genre: String? = null,
    val duration_in_seconds: Int? = null,
    val started_at: String? = null,
    val lineup_order: Int? = null,
    // null | false | string
    val timeslot: JsonElement? = null,
    val soundcloud_url: String? = null,
    val audio_waveform_path: String? = null,
    val audio_waveform_url: String? = null,
    val tracks: List<LivesetTrackDto>? = null,
    val files: LivesetFilesByQualityDto? = null,
)

@Serializable
data class EditionRefDto(
    val id: Long,
    val number: String,
)