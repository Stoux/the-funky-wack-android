package nl.stoux.tfw.core.common.repository

import android.util.Log
import nl.stoux.tfw.core.common.database.dao.EditionDao
import nl.stoux.tfw.core.common.network.ApiService
import nl.stoux.tfw.core.common.network.dto.CommentDto
import nl.stoux.tfw.core.common.network.dto.CreateCommentRequest
import nl.stoux.tfw.core.common.network.dto.RecentCommentDto
import nl.stoux.tfw.core.common.network.dto.UpdateCommentRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network-backed liveset comments. No Room cache; callers fetch on screen open.
 *
 * The one local side effect: after fetching a liveset's comments we write the
 * fresh count back to `livesets.commentsCount`, so rows/player badges don't
 * wait for the next editions refresh.
 */
@Singleton
class CommentsRepository @Inject constructor(
    private val api: ApiService,
    private val editionDao: EditionDao,
) {

    enum class Sort(val apiValue: String?) {
        /** timestamp_seconds asc (server default) */
        TIMESTAMP(null),
        /** created_at asc */
        DATE("date"),
    }

    suspend fun getComments(livesetId: Long, sort: Sort = Sort.TIMESTAMP): Result<List<CommentDto>> =
        runCatching { api.getComments(livesetId, sort.apiValue).comments }
            .onSuccess { comments -> syncLocalCount(livesetId, comments) }
            .onFailure { Log.w(TAG, "Failed to fetch comments for $livesetId", it) }

    suspend fun getRecent(): Result<List<RecentCommentDto>> =
        runCatching { api.getRecentComments().comments }
            .onFailure { Log.w(TAG, "Failed to fetch recent comments", it) }

    /**
     * Post a new comment, or a reply when [parentId] is set. For replies the server
     * ignores [timestampSeconds] and uses the parent's, but still validates it.
     */
    suspend fun create(
        livesetId: Long,
        body: String,
        timestampSeconds: Int,
        parentId: Long? = null,
    ): Result<CommentDto> = runCatching {
        api.createComment(
            livesetId,
            CreateCommentRequest(
                body = body,
                timestampSeconds = timestampSeconds.coerceAtLeast(0),
                parentId = parentId,
            ),
        ).comment
    }.onFailure { Log.w(TAG, "Failed to create comment on $livesetId", it) }

    suspend fun update(commentId: Long, body: String): Result<CommentDto> =
        runCatching { api.updateComment(commentId, UpdateCommentRequest(body)).comment }
            .onFailure { Log.w(TAG, "Failed to update comment $commentId", it) }

    suspend fun delete(commentId: Long): Result<Unit> =
        runCatching { api.deleteComment(commentId); Unit }
            .onFailure { Log.w(TAG, "Failed to delete comment $commentId", it) }

    private suspend fun syncLocalCount(livesetId: Long, comments: List<CommentDto>) {
        // Matches the server's withCount: non-deleted comments incl. replies.
        val count = comments.sumOf { top ->
            (if (top.isDeleted) 0 else 1) + top.replies.orEmpty().count { !it.isDeleted }
        }
        runCatching { editionDao.updateCommentsCount(livesetId, count) }
            .onFailure { Log.w(TAG, "Failed to store comments count for $livesetId", it) }
    }

    companion object {
        private const val TAG = "CommentsRepository"
        const val MAX_BODY_LENGTH = 2000
    }
}
