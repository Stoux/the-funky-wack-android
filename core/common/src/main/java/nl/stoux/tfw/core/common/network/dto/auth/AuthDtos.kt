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
    @SerialName("is_admin") val isAdmin: Boolean = false,
    @SerialName("listening_visibility") val listeningVisibility: String = "everyone",
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
    val platform: String? = null,
    @SerialName("client_id") val clientId: String? = null,
)

@Serializable
data class PlaybackStartResponse(
    @SerialName("play_history_id") val playHistoryId: Long,
    @SerialName("session_channel") val sessionChannel: String? = null,
)

/**
 * Generic event body for all non-start playback events sent to /api/playback/track.
 * Fields are nullable because each event uses a different subset — see API §3.
 */
@Serializable
data class PlaybackEventRequest(
    val event: String,
    @SerialName("play_history_id") val playHistoryId: Long,
    val position: Long? = null,
    @SerialName("from_position") val fromPosition: Long? = null,
    @SerialName("duration_listened") val durationListened: Long? = null,
    val quality: String? = null,
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
data class PlaybackPositionsListResponse(
    val positions: List<PlaybackPositionDto>,
)

@Serializable
data class PlaybackPositionDto(
    @SerialName("liveset_id") val livesetId: Long,
    val position: Long, // in seconds
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val device: PositionDeviceDto? = null,
)

@Serializable
data class PositionDeviceDto(
    @SerialName("client_id") val clientId: String? = null,
    @SerialName("device_type") val deviceType: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
data class SavePositionRequest(
    val position: Long,
)

// --- Listen Along ---

@Serializable
data class LiveSessionsListResponse(
    val sessions: List<LiveSessionDto>,
)

@Serializable
data class LiveSessionDto(
    @SerialName("channel_token") val channelToken: String,
    @SerialName("started_at") val startedAt: String,
    val host: LiveSessionHostDto,
    val liveset: LiveSessionLivesetDto? = null,
    val position: Long = 0,
    @SerialName("position_updated_at") val positionUpdatedAt: String? = null,
    val quality: String? = null,
    @SerialName("listeners_count") val listenersCount: Int = 0,
)

@Serializable
data class LiveSessionHostDto(
    val name: String,
    @SerialName("user_id") val userId: Long? = null,
)

@Serializable
data class LiveSessionLivesetDto(
    val id: Long,
    val title: String,
    @SerialName("artist_name") val artistName: String? = null,
    @SerialName("edition_number") val editionNumber: String? = null,
    @SerialName("duration_in_seconds") val durationInSeconds: Long? = null,
)

@Serializable
data class JoinSessionRequest(
    val mode: String, // "synced", "independent"
)

@Serializable
data class JoinSessionResponse(
    @SerialName("member_id") val memberId: Long,
    val mode: String,
)

@Serializable
data class DetachResponse(
    val message: String,
    @SerialName("new_room_token") val newRoomToken: String? = null,
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
    @SerialName("listening_visibility") val listeningVisibility: String, // "everyone", "authenticated", "nobody"
)

// --- Devices ---

@Serializable
data class DevicesListResponse(
    val devices: List<DeviceDto>,
)

@Serializable
data class DeviceDto(
    val id: Long,
    @SerialName("client_id") val clientId: String,
    @SerialName("device_type") val deviceType: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("device_nickname") val deviceNickname: String? = null,
    @SerialName("display_name") val displayName: String,
    @SerialName("is_hidden") val isHidden: Boolean = false,
    @SerialName("is_stale") val isStale: Boolean = false,
    @SerialName("is_current") val isCurrent: Boolean = false,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class DeviceUpdateRequest(
    @SerialName("device_nickname") val deviceNickname: String,
)

// --- Invite Codes ---

@Serializable
data class InvitesListResponse(
    val invites: List<InviteDto>,
)

@Serializable
data class InviteDto(
    val id: Long,
    val code: String,
    @SerialName("used_by") val usedBy: InviteUsedByDto? = null,
    @SerialName("used_at") val usedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class InviteUsedByDto(
    val id: Long,
    val name: String,
)

@Serializable
data class InviteResponse(
    val invite: InviteDto,
)
