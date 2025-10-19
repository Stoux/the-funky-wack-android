package nl.stoux.tfw.service.playback.service.queue

import android.util.Log
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import nl.stoux.tfw.core.common.database.dao.ManualQueueDao
import nl.stoux.tfw.core.common.database.entity.ManualQueueItemEntity
import nl.stoux.tfw.core.common.repository.EditionRepository
import nl.stoux.tfw.service.playback.player.PlayerManager
import nl.stoux.tfw.service.playback.service.session.CustomMediaId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central queue manager holding manual + context lanes.
 * Minimal scaffolding to start integrating. More features will follow.
 */
@Singleton
class QueueManager @Inject constructor(
    private val manualQueueDao: ManualQueueDao,
    private val playerManager: PlayerManager,
    private val editionRepository: EditionRepository,
) : Player.Listener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(QueueState())
    val state: StateFlow<QueueState> = _state

    // In-memory lanes
    private val manual = mutableListOf<QueueItem>()
    private val context = mutableListOf<QueueItem>()

    init {
        // Load manual queue from DB
        ioScope.launch {
            val items = manualQueueDao.loadAll()
            val resolved = items.mapNotNull { entity: ManualQueueItemEntity ->
                resolveQueueItem(entity.livesetId, manualEntryId = entity.id)
            }
            mutex.withLock {
                manual.clear()
                manual.addAll(resolved)
                rebuildEffectiveLocked()
            }
            applyToPlayerSafely()
        }

        // Observe player callbacks minimally
        playerManager.currentPlayer().addListener(this)
        // Ensure player repeat is ALL so MediaSession transport (e.g., notification) wraps on Next/Prev
        playerManager.currentPlayer().repeatMode = Player.REPEAT_MODE_ALL
    }

    // region Public API (minimal)

    fun setContextQueue(contextLivesets: List<Long>, startLivesetId: Long? = null, startPositionMs: Long? = null) {
        scope.launch {
            // Resolve all media items with URIs on IO
            val resolved = withContext(Dispatchers.IO) {
                contextLivesets.mapNotNull { id -> resolveQueueItem(id, manualEntryId = null) }
            }
            mutex.withLock {
                context.clear()
                context.addAll(resolved)
                rebuildEffectiveLocked(startLivesetId)
            }
            val autoplay = (startLivesetId != null || startPositionMs != null)
            applyToPlayerSafely(startLivesetId = startLivesetId, startPositionMs = startPositionMs, autoplay = autoplay)
        }
    }


    fun enqueueNext(livesetId: Long) {
        scope.launch {
            val now = System.currentTimeMillis()
            // Compute order index and persist on IO; capture row id
            val rowId: Long = withContext(Dispatchers.IO) {
                val idx = computeTailOrderIndex()
                manualQueueDao.insertWithOrderIndex(
                    ManualQueueItemEntity(
                        livesetId = livesetId,
                        orderIndex = idx,
                        addedAtMs = now,
                    )
                )
            }
            // Resolve media for this liveset, mark as manual with DB id
            val item = resolveQueueItem(livesetId, manualEntryId = rowId)
            if (item != null) {
                var insertIndex = -1
                mutex.withLock {
                    // Append to the end of the manual lane
                    manual.add(item)
                    rebuildEffectiveLocked()
                    // Determine the effective index of the newly added item
                    insertIndex = _state.value.effective.indexOfFirst { it.instanceId == item.instanceId }
                }
                val player = playerManager.currentPlayer()
                if (insertIndex >= 0) {
                    // Incremental update: insert just the new item at its effective position to avoid stutter
                    val boundedIndex = insertIndex.coerceIn(0, player.mediaItemCount)
                    player.addMediaItem(boundedIndex, item.mediaItem)
                    // Do not call prepare/seek here; keep playback state intact
                } else {
                    // Fallback: if index not found, re-apply fully
                    applyToPlayerSafely()
                }
            } else {
                Log.w("QueueManager", "enqueueNext: No playable URL for liveset=$livesetId; skipping add")
            }
        }
    }

    fun clearManual() {
        scope.launch {
            ioScope.launch { manualQueueDao.clear() }
            mutex.withLock {
                manual.clear()
                rebuildEffectiveLocked()
            }
            applyToPlayerSafely()
        }
    }

    fun removeAtEffective(index: Int) {
        scope.launch {
            val instanceId = mutex.withLock {
                _state.value.effective.getOrNull(index)?.instanceId
            } ?: return@launch
            removeByInstanceId(instanceId)
        }
    }

    fun removeByInstanceId(instanceId: String) {
        scope.launch {
            var removedWasCurrent = false
            var targetIndexAfterRemoval: Int? = null
            var removedPlayerIndex: Int = -1
            mutex.withLock {
                val player = playerManager.currentPlayer()
                val beforeEffective = _state.value.effective
                val prevIndex = beforeEffective.indexOfFirst { it.instanceId == instanceId }
                // Determine if we are removing the currently playing item
                val currentIid = safeCurrentInstanceId()
                removedWasCurrent = (currentIid == instanceId)

                // Try manual lane first
                val manualIdx = manual.indexOfFirst { it.instanceId == instanceId }
                if (manualIdx >= 0) {
                    val removed = manual.removeAt(manualIdx)
                    removed.manualEntryId?.let { id -> ioScope.launch { manualQueueDao.deleteById(id) } }
                } else {
                    // Then context lane
                    val ctxIdx = context.indexOfFirst { it.instanceId == instanceId }
                    if (ctxIdx >= 0) context.removeAt(ctxIdx)
                }
                // Rebuild effective to reflect removal
                rebuildEffectiveLocked()

                // Compute the target index if the removed item was the current one
                if (removedWasCurrent) {
                    val newSize = _state.value.effective.size
                    if (newSize > 0) {
                        targetIndexAfterRemoval = prevIndex.coerceAtMost(newSize - 1)
                    } else {
                        targetIndexAfterRemoval = null
                    }
                } else {
                    // For non-current removal, attempt incremental player playlist removal
                    removedPlayerIndex = findPlayerIndexByInstanceId(player, instanceId)
                }
            }

            val player = playerManager.currentPlayer()
            if (removedWasCurrent) {
                // Apply full playlist and jump to the same index (now next item) at 0:00, autoplay
                applyToPlayerSafely(
                    startLivesetId = null,
                    startPositionMs = 0L,
                    autoplay = true,
                    startEffectiveIndex = targetIndexAfterRemoval
                )
            } else {
                // Non-current: remove incrementally from player if possible to avoid stutter
                if (player != null && removedPlayerIndex >= 0) {
                    player.removeMediaItem(removedPlayerIndex)
                    // No need to re-apply fully
                } else {
                    applyToPlayerSafely()
                }
            }
        }
    }

    // endregion

    /**
     * Build and set the context queue based on the provided liveset.
     * Typically includes all livesets from the same edition, starting at the given liveset.
     * If repository lookup fails, falls back to a single-item context.
     * @param livesetId The clicked/restored liveset.
     * @param startPositionMs Optional start position for that liveset.
     * @param autoplay Whether to start playback immediately after applying.
     */
    fun setContextFromLiveset(livesetId: Long, startPositionMs: Long? = null, autoplay: Boolean = true) {
        scope.launch {
            // Resolve the list of context liveset IDs
            val contextLivesetIds: List<Long> = try {
                val lwd = withContext(Dispatchers.IO) { editionRepository.findLiveset(livesetId).first() }
                val editionId = lwd?.liveset?.editionId
                if (editionId != null) {
                    val livesets = withContext(Dispatchers.IO) { editionRepository.getLivesets(editionId = editionId).first() }
                    livesets.map { it.liveset.id }
                } else {
                    listOf(livesetId)
                }
            } catch (_: Throwable) {
                listOf(livesetId)
            }
            // Delegate to setContextQueue and control autoplay explicitly
            // Note: applyToPlayerSafely will only autoplay when requested via the flag below.
            // Resolve all items, update effective, and apply
            val resolved = withContext(Dispatchers.IO) { contextLivesetIds.mapNotNull { id -> resolveQueueItem(id, manualEntryId = null) } }
            mutex.withLock {
                context.clear()
                context.addAll(resolved)
                rebuildEffectiveLocked(livesetId)
            }
            applyToPlayerSafely(startLivesetId = livesetId, startPositionMs = startPositionMs, autoplay = autoplay)
        }
    }

    fun skipToEffective(index: Int) {
        scope.launch {
            mutex.withLock {
                val player = playerManager.currentPlayer()
                val effective = _state.value.effective
                if (index !in effective.indices) return@withLock
                // Seek to the item at the given effective index and start playing
                player.seekTo(index, /* positionMs = */ 0L)
                player.playWhenReady = true
            }
        }
    }

    /**
     * Skip to next item. If currently at the last effective item, wrap according to policy:
     * - If context lane is non-empty, jump to the first context item (repeat list behavior).
     * - Otherwise, wrap to the first item (index 0) to ensure the Next button always does something.
     */
    fun skipToNext() {
        scope.launch {
            mutex.withLock {
                val player = playerManager.currentPlayer() ?: return@withLock
                // With REPEAT_MODE_ALL, ExoPlayer handles wrapping natively.
                player.seekToNext()
                player.playWhenReady = true
            }
        }
    }
    
    private suspend fun resolveQueueItem(livesetId: Long, manualEntryId: Long? = null): QueueItem? {
        val lwd = withContext(Dispatchers.IO) { editionRepository.findLiveset(livesetId).first() }
        val ls = lwd?.liveset ?: return null
        val url = ls.losslessUrl ?: ls.hqUrl ?: ls.lqUrl
        if (url.isNullOrBlank()) return null
        val mediaId = CustomMediaId.forEntity(ls).original
        val instanceId = java.util.UUID.randomUUID().toString()
        val base = MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(url)
            .build()
        val withExtras = base.withQueueExtras(instanceId = instanceId, manualEntryId = manualEntryId)
        return QueueItem(livesetId = livesetId, mediaItem = withExtras, manualEntryId = manualEntryId, instanceId = instanceId)
    }

    /**
     * Safely read the current playing queue instance id from Player only when on the main thread.
     * Returns null if not on main or if player/item is unavailable.
     */
    private fun safeCurrentInstanceId(): String? {
        val player = playerManager.currentPlayer() ?: return null
        return if (Looper.myLooper() == Looper.getMainLooper()) {
            player.currentMediaItem?.mediaMetadata?.extras?.getString(QueueExtrasKeys.INSTANCE_ID)
        } else null
    }

    private fun rebuildEffectiveLocked(anchorLivesetId: Long? = _state.value.currentLivesetId) {
        // Build effective such that manual items are shown/played right after the currently playing context item.
        val contextBeforeAfter = if (anchorLivesetId != null) {
            val ctxIdx = context.indexOfFirst { it.livesetId == anchorLivesetId }
            if (ctxIdx >= 0) ctxIdx else -1
        } else -1
        val effective = buildList {
            if (contextBeforeAfter >= 0) {
                // Context up to and including the anchor
                addAll(context.subList(0, contextBeforeAfter + 1))
                // Then all manual items
                addAll(manual)
                // Then remaining context
                if (contextBeforeAfter + 1 < context.size) addAll(context.subList(contextBeforeAfter + 1, context.size))
            } else {
                // Fallback: manual first then full context
                addAll(manual)
                addAll(context)
            }
        }
        // Recompute currentEffectiveIndex by matching instanceId of the current player item when possible.
        // Access player only on main thread; otherwise fall back to livesetId mapping.
        val currentInstanceId = safeCurrentInstanceId()
        val newCurrentIndex = currentInstanceId?.let { iid ->
            effective.indexOfFirst { it.instanceId == iid }.takeIf { it >= 0 }
        } ?: effective.indexOfFirst { it.livesetId == _state.value.currentLivesetId }.takeIf { it >= 0 } ?: 0
        _state.update {
            it.copy(
                manual = manual.toList(),
                context = context.toList(),
                effective = effective,
                currentEffectiveIndex = newCurrentIndex,
            )
        }
    }

    private suspend fun computeTailOrderIndex(): Double {
        val all = manualQueueDao.loadAll()
        val max = all.maxByOrNull { it.orderIndex }?.orderIndex
        return (max ?: 0.0) + 1.0
    }

    private fun applyToPlayerSafely(startLivesetId: Long? = null, startPositionMs: Long? = null, autoplay: Boolean = false, startEffectiveIndex: Int? = null) {
        scope.launch {
            mutex.withLock {
                val player = playerManager.currentPlayer()
                val effective = _state.value.effective
                val mediaItems = effective.map { it.mediaItem }

                // Capture current identity before rebuild to preserve during playlist updates
                val currentItem = player.currentMediaItem
                val currentInstanceId = currentItem?.mediaMetadata?.extras?.getString(QueueExtrasKeys.INSTANCE_ID)
                val currentPosition = player.currentPosition

                player.setMediaItems(mediaItems, /* resetPosition = */ false)

                // Ensure repeat-all so MediaSession transport wraps on Next even at end
                player.repeatMode = Player.REPEAT_MODE_ALL

                // Compute target index: prefer explicit start effective index, else explicit start id, else match by instance id, else by liveset id, else 0
                val explicitIndexOverride = startEffectiveIndex?.takeIf { it in effective.indices }
                val explicitIndex = startLivesetId?.let { id -> effective.indexOfFirst { it.livesetId == id }.takeIf { it >= 0 } }
                val instanceIndex = currentInstanceId?.let { iid -> effective.indexOfFirst { it.instanceId == iid }.takeIf { it >= 0 } }
                val livesetIndex = _state.value.currentLivesetId?.let { id -> effective.indexOfFirst { it.livesetId == id }.takeIf { it >= 0 } }
                val targetIndex = explicitIndexOverride ?: explicitIndex ?: instanceIndex ?: livesetIndex ?: 0

                val pos = startPositionMs ?: currentPosition
                if (targetIndex != player.currentMediaItemIndex || (startPositionMs != null) || (startEffectiveIndex != null)) {
                    player.seekTo(targetIndex, pos)
                }
                // Prepare the new playlist; only auto-play when explicitly requested
                player.prepare()
                if (autoplay) {
                    player.playWhenReady = true
                }
                // Update state currentEffectiveIndex after applying
                _state.update { it.copy(currentEffectiveIndex = targetIndex) }
            }
        }
    }

    // region Player.Listener

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        scope.launch {
            mutex.withLock {
                val player = playerManager.currentPlayer()
                // Identify the previously playing item using currentEffectiveIndex
                val prevIndex = _state.value.currentEffectiveIndex
                val prevItem = _state.value.effective.getOrNull(prevIndex)
                if (prevItem?.manualEntryId != null) {
                    // Remove consumed manual entry (played item) from in-memory + DB
                    val manualIdx = manual.indexOfFirst { it.instanceId == prevItem.instanceId }
                    if (manualIdx >= 0) {
                        val removed = manual.removeAt(manualIdx)
                        ioScope.launch { manualQueueDao.deleteById(removed.manualEntryId!!) }
                    }
                    // Also remove the corresponding media item from the Player playlist to keep it in sync
                    val instanceId = prevItem.instanceId
                    if (player != null) {
                        val toRemoveIndex = findPlayerIndexByInstanceId(player, instanceId)
                        val currentIdx = player.currentMediaItemIndex
                        if (toRemoveIndex >= 0 && toRemoveIndex != currentIdx) {
                            player.removeMediaItem(toRemoveIndex)
                        }
                    }
                }
                // Update current liveset/index based on the new media item's instance id
                val newLivesetId = mediaItem?.mediaId?.let { CustomMediaId.from(it).getLivesetId() }
                val newInstanceId = mediaItem?.mediaMetadata?.extras?.getString(QueueExtrasKeys.INSTANCE_ID)

                // Rebuild effective around the new anchor if it is a context item
                rebuildEffectiveLocked(anchorLivesetId = newLivesetId)

                // Compute the new current index by instance match first
                val newIndex = newInstanceId?.let { iid -> _state.value.effective.indexOfFirst { it.instanceId == iid } }
                    ?.takeIf { it >= 0 }
                    ?: _state.value.effective.indexOfFirst { it.livesetId == newLivesetId }
                        .takeIf { it >= 0 } ?: 0

                _state.update { it.copy(currentLivesetId = newLivesetId, currentEffectiveIndex = newIndex) }
            }
            // Avoid rebuilding the entire playlist here; Player already advanced. No full re-apply.
        }
    }

    /**
     * Find the player's media item index that matches the given queue instance id in MediaMetadata.extras.
     * Must be called on the main thread (listener callbacks execute on main).
     */
    private fun findPlayerIndexByInstanceId(player: Player, instanceId: String): Int {
        val count = player.mediaItemCount
        for (i in 0 until count) {
            val iid = player.getMediaItemAt(i).mediaMetadata.extras?.getString(QueueExtrasKeys.INSTANCE_ID)
            if (iid == instanceId) return i
        }
        return -1
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _state.update { it.copy(isPlaying = isPlaying) }
    }


    // endregion
}
