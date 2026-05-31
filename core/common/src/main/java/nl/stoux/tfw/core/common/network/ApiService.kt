package nl.stoux.tfw.core.common.network

import nl.stoux.tfw.core.common.network.dto.EditionDto
import nl.stoux.tfw.core.common.network.dto.auth.AddPlaylistItemRequest
import nl.stoux.tfw.core.common.network.dto.auth.AuthResponse
import nl.stoux.tfw.core.common.network.dto.auth.BroadcastAuthRequest
import nl.stoux.tfw.core.common.network.dto.auth.BroadcastAuthResponse
import nl.stoux.tfw.core.common.network.dto.auth.CreatePlaylistRequest
import nl.stoux.tfw.core.common.network.dto.auth.DetachResponse
import nl.stoux.tfw.core.common.network.dto.auth.DeviceCodePollResponse
import nl.stoux.tfw.core.common.network.dto.auth.DeviceRegisterRequest
import nl.stoux.tfw.core.common.network.dto.auth.DeviceUpdateRequest
import nl.stoux.tfw.core.common.network.dto.auth.DevicesListResponse
import nl.stoux.tfw.core.common.network.dto.auth.InviteResponse
import nl.stoux.tfw.core.common.network.dto.auth.InvitesListResponse
import nl.stoux.tfw.core.common.network.dto.auth.DeviceCodeRequest
import nl.stoux.tfw.core.common.network.dto.auth.DeviceCodeResponse
import nl.stoux.tfw.core.common.network.dto.auth.FavoriteResponse
import nl.stoux.tfw.core.common.network.dto.auth.FavoritesListResponse
import nl.stoux.tfw.core.common.network.dto.auth.JoinSessionRequest
import nl.stoux.tfw.core.common.network.dto.auth.JoinSessionResponse
import nl.stoux.tfw.core.common.network.dto.auth.LiveSessionDto
import nl.stoux.tfw.core.common.network.dto.auth.LiveSessionsListResponse
import nl.stoux.tfw.core.common.network.dto.auth.LoginRequest
import nl.stoux.tfw.core.common.network.dto.auth.PlaybackEventRequest
import nl.stoux.tfw.core.common.network.dto.auth.PlaybackPositionsListResponse
import nl.stoux.tfw.core.common.network.dto.auth.PlaybackStartRequest
import nl.stoux.tfw.core.common.network.dto.auth.PlaybackStartResponse
import nl.stoux.tfw.core.common.network.dto.auth.PlaylistDto
import nl.stoux.tfw.core.common.network.dto.auth.PlaylistWrapper
import nl.stoux.tfw.core.common.network.dto.auth.PlaylistsListResponse
import nl.stoux.tfw.core.common.network.dto.auth.ReorderPlaylistItemsRequest
import nl.stoux.tfw.core.common.network.dto.auth.SavePositionRequest
import nl.stoux.tfw.core.common.network.dto.auth.UpdatePlaylistRequest
import nl.stoux.tfw.core.common.network.dto.auth.UserDto
import nl.stoux.tfw.core.common.network.dto.auth.VisibilitySettingRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ApiService {
    /**
     * Fetch the entire catalog tree in a single call.
     */
    @GET("editions")
    suspend fun getEditions(): List<EditionDto>

    // --- Auth ---

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("auth/logout")
    suspend fun logout()

    @GET("auth/user")
    suspend fun getUser(): UserDto

    @POST("auth/device-code")
    suspend fun requestDeviceCode(@Body request: DeviceCodeRequest): DeviceCodeResponse

    @GET("auth/device-code/{code}/poll")
    suspend fun pollDeviceCode(
        @Path("code") code: String,
        @retrofit2.http.Query("client_id") clientId: String,
    ): DeviceCodePollResponse

    @POST("auth/device-code/{code}/authorize")
    suspend fun authorizeDeviceCode(@Path("code") code: String)

    // --- Playback Tracking ---

    @POST("playback/track")
    suspend fun startPlaybackSession(@Body request: PlaybackStartRequest): PlaybackStartResponse

    @POST("playback/track")
    suspend fun trackPlaybackEvent(@Body request: PlaybackEventRequest)

    // --- Favorites ---

    @GET("favorites")
    suspend fun getFavorites(): FavoritesListResponse

    @POST("favorites/{livesetId}")
    suspend fun addFavorite(@Path("livesetId") id: Long): FavoriteResponse

    @DELETE("favorites/{livesetId}")
    suspend fun removeFavorite(@Path("livesetId") id: Long): FavoriteResponse

    // --- Playlists ---

    @GET("playlists")
    suspend fun getPlaylists(): PlaylistsListResponse

    @POST("playlists")
    suspend fun createPlaylist(@Body request: CreatePlaylistRequest): PlaylistWrapper

    @GET("playlists/{shareCode}")
    suspend fun getPlaylist(@Path("shareCode") shareCode: String): PlaylistWrapper

    @PUT("playlists/{shareCode}")
    suspend fun updatePlaylist(
        @Path("shareCode") shareCode: String,
        @Body request: UpdatePlaylistRequest,
    ): PlaylistWrapper

    @DELETE("playlists/{shareCode}")
    suspend fun deletePlaylist(@Path("shareCode") shareCode: String)

    @POST("playlists/{shareCode}/items")
    suspend fun addPlaylistItem(
        @Path("shareCode") shareCode: String,
        @Body request: AddPlaylistItemRequest,
    )

    @DELETE("playlists/{shareCode}/items/{livesetId}")
    suspend fun removePlaylistItem(
        @Path("shareCode") shareCode: String,
        @Path("livesetId") livesetId: Long,
    )

    @PUT("playlists/{shareCode}/items")
    suspend fun reorderPlaylistItems(
        @Path("shareCode") shareCode: String,
        @Body request: ReorderPlaylistItemsRequest,
    )

    // --- Playback Positions ---

    @GET("playback/positions")
    suspend fun getPlaybackPositions(): PlaybackPositionsListResponse

    @PUT("playback/positions/{livesetId}")
    suspend fun savePlaybackPosition(
        @Path("livesetId") livesetId: Long,
        @Body request: SavePositionRequest,
    )

    @DELETE("playback/positions/{livesetId}")
    suspend fun clearPlaybackPosition(@Path("livesetId") livesetId: Long)

    // --- Listen Along ---

    @GET("live/sessions")
    suspend fun getLiveSessions(): LiveSessionsListResponse

    @POST("live/rooms/{token}/join")
    suspend fun joinLiveSession(
        @Path("token") token: String,
        @Body request: JoinSessionRequest,
    ): JoinSessionResponse

    @POST("live/rooms/{token}/leave")
    suspend fun leaveLiveSession(@Path("token") token: String)

    @GET("live/rooms/{token}/state")
    suspend fun getLiveRoomState(@Path("token") token: String): LiveSessionDto

    @POST("live/rooms/{token}/detach")
    suspend fun detachLiveSession(@Path("token") token: String): DetachResponse

    @POST("live/rooms/{token}/pause-sync")
    suspend fun pauseSync(@Path("token") token: String)

    @POST("live/rooms/{token}/resume-sync")
    suspend fun resumeSync(@Path("token") token: String)

    // --- Broadcast Auth (for Pusher) ---

    @POST("broadcast/auth")
    suspend fun broadcastAuth(@Body request: BroadcastAuthRequest): BroadcastAuthResponse

    // --- Settings ---

    @PUT("settings/visibility")
    suspend fun updateVisibility(@Body request: VisibilitySettingRequest)

    // --- Devices ---

    @GET("devices")
    suspend fun getDevices(): DevicesListResponse

    @POST("devices/register")
    suspend fun registerDevice(@Body request: DeviceRegisterRequest)

    @PUT("devices/{id}")
    suspend fun updateDevice(
        @Path("id") id: Long,
        @Body request: DeviceUpdateRequest,
    )

    @PUT("devices/{id}/hide")
    suspend fun hideDevice(@Path("id") id: Long)

    @PUT("devices/{id}/show")
    suspend fun showDevice(@Path("id") id: Long)

    @DELETE("devices/{id}")
    suspend fun deleteDevice(@Path("id") id: Long)

    // --- Invite Codes ---

    @GET("invites")
    suspend fun getInvites(): InvitesListResponse

    @POST("invites")
    suspend fun createInvite(): InviteResponse
}
