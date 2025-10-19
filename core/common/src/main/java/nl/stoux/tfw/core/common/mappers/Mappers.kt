package nl.stoux.tfw.core.common.mappers

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import nl.stoux.tfw.core.common.database.entity.EditionEntity
import nl.stoux.tfw.core.common.database.entity.LivesetEntity
import nl.stoux.tfw.core.common.database.entity.TrackEntity
import nl.stoux.tfw.core.common.network.dto.EditionDto
import nl.stoux.tfw.core.common.network.dto.LivesetDto
import nl.stoux.tfw.core.common.network.dto.LivesetTrackDto
import nl.stoux.tfw.core.common.network.dto.SrcsetDto

/**
 * Mapping functions from network DTOs to Room entities.
 */

fun EditionDto.toEditionEntity(): EditionEntity = EditionEntity(
    id = id,
    number = number,
    tagLine = tag_line,
    date = date,
    notes = notes,
    emptyNote = empty_note,
    timetablerMode = timetabler_mode,
    posterUrl = poster_url,
    posterOptimizedUrl = selectOptimizedPosterUrl(),
)

fun LivesetDto.toLivesetEntity(): LivesetEntity = LivesetEntity(
    id = id,
    editionId = edition_id,
    title = title,
    artistName = artist_name,
    description = description,
    bpm = bpm,
    genre = genre,
    durationSeconds = duration_in_seconds,
    startedAt = started_at,
    lineupOrder = lineup_order,
    timeslotRaw = timeslotToRaw(timeslot),
    soundcloudUrl = soundcloud_url,
    audioWaveformPath = audio_waveform_path,
    audioWaveformUrl = audio_waveform_url,
    lqUrl = files?.lq,
    hqUrl = files?.hq,
    losslessUrl = files?.lossless,
)

fun LivesetTrackDto.toTrackEntity(): TrackEntity = TrackEntity(
    id = id,
    livesetId = liveset_id,
    title = title,
    timestampSec = timestamp,
    orderInSet = order,
)

private fun EditionDto.selectOptimizedPosterUrl(): String? {
    val set = poster_srcset_urls
    if (!set.isNullOrEmpty()) {
        // Prefer width 1500 if available, else take the largest available
        val preferred = set.firstOrNull { it.width == 1500 } ?: set.maxByOrNull(SrcsetDto::width)
        preferred?.url?.let { return it }
    }
    // Fallback to original poster_url if no srcset
    return poster_url
}

private fun timeslotToRaw(timeslot: JsonElement?): String? = when (timeslot) {
    null -> null
    is JsonPrimitive -> when {
        timeslot.isString -> timeslot.jsonPrimitive.content
        timeslot.booleanOrNull == false -> "INVALID"
        else -> timeslot.toString()
    }
    else -> timeslot.toString()
}
