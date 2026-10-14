package nl.stoux.tfw.core.common.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * In-app (database) notification. [type] mirrors `data.type`:
 * [TYPE_COMMENT_REPLY] or [TYPE_NEW_COMMENT].
 */
@Serializable
data class AppNotificationDto(
    val id: String,
    val type: String? = null,
    val data: AppNotificationDataDto = AppNotificationDataDto(),
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val isRead: Boolean get() = readAt != null

    companion object {
        const val TYPE_COMMENT_REPLY = "comment_reply"
        const val TYPE_NEW_COMMENT = "new_comment"
    }
}

/**
 * Union of the `comment_reply` and `new_comment` payloads. [replierName] is only
 * set for replies, [commenterName] only for new comments, [parentId] only for replies.
 */
@Serializable
data class AppNotificationDataDto(
    val type: String? = null,
    @SerialName("comment_id") val commentId: Long? = null,
    @SerialName("parent_id") val parentId: Long? = null,
    @SerialName("liveset_id") val livesetId: Long? = null,
    @SerialName("liveset_title") val livesetTitle: String? = null,
    @SerialName("replier_name") val replierName: String? = null,
    @SerialName("commenter_name") val commenterName: String? = null,
    @SerialName("body_preview") val bodyPreview: String? = null,
    @SerialName("timestamp_seconds") val timestampSeconds: Int? = null,
)

@Serializable
data class NotificationsListResponse(
    val notifications: List<AppNotificationDto> = emptyList(),
    @SerialName("unread_count") val unreadCount: Int = 0,
)
