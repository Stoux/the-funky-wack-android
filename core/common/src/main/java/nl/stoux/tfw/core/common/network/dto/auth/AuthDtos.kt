package nl.stoux.tfw.core.common.network.dto.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Login ---

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    @SerialName("device_name") val deviceName: String,
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: UserDto,
)

@Serializable
data class UserDto(
    val id: Long,
    val name: String,
    val email: String,
)

// --- Device Code Flow ---

@Serializable
data class DeviceCodeRequest(
    @SerialName("device_name") val deviceName: String,
    @SerialName("client_id") val clientId: String,
)

@Serializable
data class DeviceCodeResponse(
    val code: String,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
data class DeviceCodePollResponse(
    val status: String, // "pending", "authorized"
    val token: String? = null,
    val user: UserDto? = null,
)

// --- Playback Tracking ---

@Serializable
data class PlaybackStartRequest(
    @SerialName("liveset_id") val livesetId: Long,
    val event: String = "start",
    val quality: String? = null,
    val position: Long? = null,
)

@Serializable
data class PlaybackStartResponse(
    @SerialName("play_history_id") val playHistoryId: Long,
    @SerialName("session_channel") val sessionChannel: String? = null,
)

// --- Favorites ---

@Serializable
data class FavoriteResponse(
    val message: String,
    @SerialName("favorites_count") val favoritesCount: Int,
)

@Serializable
data class FavoritesListResponse(
    val favorites: List<FavoriteLivesetDto>,
)

@Serializable
data class FavoriteLivesetDto(
    val id: Long,
    val title: String? = null,
    @SerialName("artist_name") val artistName: String? = null,
    val edition: FavoriteLivesetEditionDto? = null,
    @SerialName("duration_in_seconds") val durationInSeconds: Long? = null,
    @SerialName("favorited_at") val favoritedAt: String? = null,
)

@Serializable
data class FavoriteLivesetEditionDto(
    val id: Long,
    val number: String? = null,
    @SerialName("tag_line") val tagLine: String? = null,
)

// --- Playlists ---

@Serializable
data class PlaylistsListResponse(
    val playlists: List<PlaylistDto> = emptyList(),
    val publicPlaylists: List<PlaylistDto> = emptyList(),
)

@Serializable
data class PlaylistWrapper(
    val playlist: PlaylistDto,
)

@Serializable
data class PlaylistDto(
    val id: Long,
    val name: String,
    val description: String? = null,
    val visibility: String = "private", // private, public
    @SerialName("share_code") val shareCode: String,
    @SerialName("items_count") val itemsCount: Int = 0,
    @SerialName("is_owner") val isOwn: Boolean = false,
    val user: PlaylistUserDto? = null,
    val items: List<PlaylistItemDto>? = null,
)

@Serializable
data class PlaylistUserDto(
    val id: Long,
    val name: String,
)

@Serializable
data class PlaylistItemDto(
    val id: Long,
    @SerialName("liveset_id") val livesetId: Long,
    val position: Int = 0,
    val liveset: PlaylistItemLivesetDto? = null,
)

@Serializable
data class PlaylistItemLivesetDto(
    val id: Long,
    val title: String? = null,
    @SerialName("artist_name") val artistName: String? = null,
    @SerialName("duration_in_seconds") val durationInSeconds: Long? = null,
)

@Serializable
data class CreatePlaylistRequest(
    val name: String,
    val description: String? = null,
    val visibility: String = "private",
)

@Serializable
data class UpdatePlaylistRequest(
    val name: String? = null,
    val description: String? = null,
    val visibility: String? = null,
)

@Serializable
data class AddPlaylistItemRequest(
    @SerialName("liveset_id") val livesetId: Long,
)

@Serializable
data class ReorderPlaylistItemsRequest(
    val items: List<ReorderItem>,
)

@Serializable
data class ReorderItem(
    val id: Long,
    val position: Int,
)

// --- Playback Positions ---

@Serializable
data class PlaybackPositionDto(
    @SerialName("liveset_id") val livesetId: Long,
    val position: Long, // in seconds
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class SavePositionRequest(
    val position: Long,
)

// --- Listen Along ---

@Serializable
data class LiveSessionDto(
    val id: Long,
    val token: String,
    @SerialName("host_name") val hostName: String,
    @SerialName("liveset_id") val livesetId: Long,
    val liveset: nl.stoux.tfw.core.common.network.dto.LivesetDto? = null,
    @SerialName("listener_count") val listenerCount: Int = 0,
)

@Serializable
data class JoinSessionRequest(
    val mode: String, // "synced", "independent"
)

@Serializable
data class JoinSessionResponse(
    val channel: String,
    val mode: String,
)

// --- Broadcast Auth ---

@Serializable
data class BroadcastAuthRequest(
    @SerialName("socket_id") val socketId: String,
    @SerialName("channel_name") val channelName: String,
)

@Serializable
data class BroadcastAuthResponse(
    val auth: String,
    @SerialName("channel_data") val channelData: String? = null,
)

// --- Settings ---

@Serializable
data class VisibilitySettingRequest(
    val visibility: String, // "everyone", "authenticated", "nobody"
)
