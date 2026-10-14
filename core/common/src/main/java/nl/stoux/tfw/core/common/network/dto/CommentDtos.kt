package nl.stoux.tfw.core.common.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CommentUserDto(
    val id: Long,
    val name: String,
)

/**
 * A liveset comment. Top-level comments carry their [replies] (one level deep,
 * created_at asc). Soft-deleted comments that still have replies come back as a
 * placeholder: [isDeleted] true and [body] null. Deleted replies are returned as
 * placeholders too.
 */
@Serializable
data class CommentDto(
    val id: Long,
    val user: CommentUserDto? = null,
    val body: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("timestamp_seconds") val timestampSeconds: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_own") val isOwn: Boolean = false,
    val replies: List<CommentDto>? = null,
)

@Serializable
data class CommentsListResponse(
    val comments: List<CommentDto> = emptyList(),
)

@Serializable
data class CommentWrapper(
    val comment: CommentDto,
)

@Serializable
data class CreateCommentRequest(
    val body: String,
    /** Required by the server even for replies; it's overridden with the parent's timestamp there. */
    @SerialName("timestamp_seconds") val timestampSeconds: Int,
    @SerialName("parent_id") val parentId: Long? = null,
)

@Serializable
data class UpdateCommentRequest(
    val body: String,
)

@Serializable
data class RecentCommentLivesetDto(
    val id: Long,
    val title: String,
    @SerialName("artist_name") val artistName: String? = null,
)

@Serializable
data class RecentCommentDto(
    val id: Long,
    val user: CommentUserDto? = null,
    val liveset: RecentCommentLivesetDto? = null,
    val body: String? = null,
    @SerialName("timestamp_seconds") val timestampSeconds: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class RecentCommentsResponse(
    val comments: List<RecentCommentDto> = emptyList(),
)

@Serializable
data class MessageResponse(
    val message: String? = null,
)
