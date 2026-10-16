package nl.stoux.tfw.core.common.database

import androidx.room.TypeConverter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import nl.stoux.tfw.core.common.network.dto.TrackSong

/** Stores a tracklist line's linked songs as a JSON array in a single TEXT column. */
class SongListConverter {
    // Explicit types: KSP (Room) can't resolve types inferred from plugin-generated serializers
    private val json: Json = Json { ignoreUnknownKeys = true }
    private val serializer: KSerializer<List<TrackSong>> = ListSerializer(TrackSong.serializer())

    @TypeConverter
    fun fromJson(value: String?): List<TrackSong>? =
        value?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }

    @TypeConverter
    fun toJson(songs: List<TrackSong>?): String? =
        songs?.let { json.encodeToString(serializer, it) }
}
