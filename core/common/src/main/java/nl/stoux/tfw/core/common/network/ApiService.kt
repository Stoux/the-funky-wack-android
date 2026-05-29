package nl.stoux.tfw.core.common.network

import nl.stoux.tfw.core.common.network.dto.EditionDto
import nl.stoux.tfw.core.common.network.dto.auth.AddPlaylistItemRequest
import nl.stoux.tfw.core.common.network.dto.auth.AuthResponse
import nl.stoux.tfw.core.common.network.dto.auth.BroadcastAuthRequest
import nl.stoux.tfw.core.common.network.dto.auth.BroadcastAuthResponse
import nl.stoux.tfw.core.common.network.dto.auth.CreatePlaylistRequest
import nl.stoux.tfw.core.common.network.dto.auth.DeviceCodePollResponse
import nl.stoux.tfw.core.common.network.dto.auth.DeviceCodeRequest
import nl.stoux.tfw.core.common.network.dto.auth.DeviceCodeResponse
import nl.stoux.tfw.core.common.network.dto.auth.FavoriteResponse
import nl.stoux.tfw.core.common.network.dto.auth.FavoritesListResponse
import nl.stoux.tfw.core.common.network.dto.auth.JoinSessionRequest
import nl.stoux.tfw.core.common.network.dto.auth.JoinSessionResponse
import nl.stoux.tfw.core.common.network.dto.auth.LiveSessionDto
import nl.stoux.tfw.core.common.network.dto.auth.LoginRequest
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
    suspend fun pollDeviceCode(@Path("code") code: String): DeviceCodePollResponse

    @POST("auth/device-code/{code}/authorize")
    suspend fun authorizeDeviceCode(@Path("code") code: String)

    // --- Playback Tracking ---

    @POST("playback/track")
    suspend fun startPlaybackSession(@Body request: PlaybackStartRequest): PlaybackStartResponse

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
    suspend fun getLiveSessions(): List<LiveSessionDto>

    @POST("live/sessions/{token}/join")
    suspend fun joinLiveSession(
        @Path("token") token: String,
        @Body request: JoinSessionRequest,
    ): JoinSessionResponse

    @POST("live/sessions/{token}/leave")
    suspend fun leaveLiveSession(@Path("token") token: String)

    // --- Broadcast Auth (for Pusher) ---

    @POST("broadcast/auth")
    suspend fun broadcastAuth(@Body request: BroadcastAuthRequest): BroadcastAuthResponse

    // --- Settings ---

    @PUT("settings/visibility")
    suspend fun updateVisibility(@Body request: VisibilitySettingRequest)
}
