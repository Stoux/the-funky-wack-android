package nl.stoux.tfw.service.playback.service.queue

import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.stoux.tfw.core.common.database.dao.ManualQueueDao
import nl.stoux.tfw.core.common.database.entity.ManualQueueItemEntity
import nl.stoux.tfw.core.common.repository.EditionRepository
import nl.stoux.tfw.service.playback.download.DownloadRepository
import nl.stoux.tfw.service.playback.player.CastToLocalCallback
import nl.stoux.tfw.service.playback.player.LocalToCastCallback
import nl.stoux.tfw.service.playback.player.PlayerManager
import nl.stoux.tfw.service.playback.service.session.CustomMediaId
import nl.stoux.tfw.service.playback.service.session.PlayableMediaItemBuilder
import nl.stoux.tfw.service.playback.settings.PlaybackSettingsRepository
import nl.stoux.tfw.service.playback.settings.PlaybackSettingsRepository.AudioQuality
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
    private val playbackSettings: PlaybackSettingsRepository,
    private val downloadRepository: DownloadRepository,
) : Player.Listener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(QueueState())
    val state: StateFlow<QueueState> = _state

    // Current playback quality (per-session, not persisted)
    private val _currentQuality = MutableStateFlow<AudioQuality?>(null)
    val currentQuality: StateFlow<AudioQuality?> = _currentQuality

    // Actual quality being used (may differ from requested if requested isn't available)
    private val _actualQuality = MutableStateFlow<AudioQuality?>(null)
    val actualQuality: StateFlow<AudioQuality?> = _actualQuality

    // Flag to track if a user-initiated action has set the queue (prevents restore from overwriting)
    @Volatile
    private var userInitiatedPlayback = false

    // In-memory lanes
    private val manual = mutableListOf<QueueItem>()
    private val context = mutableListOf<QueueItem>()

    // Track the currently bound player for listener management
    private var boundPlayer: Player? = null

    init {
        // Register as the callback for Cast-to-local transitions
        playerManager.castToLocalCallback = CastToLocalCallback { livesetId, positionMs ->
            if (livesetId != null) {
                Log.d("QueueManager", "Rebuilding queue after Cast disconnect: liveset=$livesetId, position=${positionMs}ms")
                setContextFromLiveset(livesetId, startPositionMs = positionMs, autoplay = true)
            }
        }

        // Register as the callback for local-to-Cast transitions
        playerManager.localToCastCallback = LocalToCastCallback { livesetId, positionMs ->
            if (livesetId != null) {
                Log.d("QueueManager", "Rebuilding queue for Cast: liveset=$livesetId, position=${positionMs}ms")
                setContextFromLivesetForCast(livesetId, startPositionMs = positionMs)
            }
        }

        // Load default quality from settings
        scope.launch {
            val defaultQuality = playbackSettings.audioQuality().first()
            _currentQuality.value = defaultQuality
        }

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

        // Observe player callbacks and rebind when player changes (e.g., switching to/from CastPlayer)
        val initialPlayer = playerManager.currentPlayer()
        boundPlayer = initialPlayer
        initialPlayer.addListener(this)
        initialPlayer.repeatMode = Player.REPEAT_MODE_ALL

        scope.launch {
            playerManager.activePlayer.collect { newPlayer ->
                val oldPlayer = boundPlayer
                if (newPlayer != null && newPlayer !== oldPlayer) {
                    // Detach from old player
                    oldPlayer?.removeListener(this@QueueManager)

                    // Attach to new player
                    boundPlayer = newPlayer
                    newPlayer.addListener(this@QueueManager)
                    newPlayer.repeatMode = Player.REPEAT_MODE_ALL

                    Log.d("QueueManager", "Switched listener to new player: ${newPlayer.javaClass.simpleName}")
                }
            }
        }
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
     * Check if a user-initiated playback action has occurred.
     * Used by PlaybackResumeCoordinator to avoid overwriting user selections.
     */
    fun hasUserInitiatedPlayback(): Boolean = userInitiatedPlayback

    /**
     * Build and set the context queue based on the provided liveset.
     * Typically includes all livesets from the same edition, starting at the given liveset.
     * If repository lookup fails, falls back to a single-item context.
     * @param livesetId The clicked/restored liveset.
     * @param startPositionMs Optional start position for that liveset.
     * @param autoplay Whether to start playback immediately after applying.
     */
    fun setContextFromLiveset(livesetId: Long, startPositionMs: Long? = null, autoplay: Boolean = true) {
        // Mark that user initiated playback (prevents restore from overwriting)
        if (autoplay) {
            userInitiatedPlayback = true
        }
        scope.launch {
            // Reset to default quality from settings when starting new playback
            var defaultQuality = playbackSettings.audioQuality().first()
            // If default is lossless but lossless isn't allowed, fall back to HIGH
            if (defaultQuality == AudioQuality.LOSSLESS && !playbackSettings.allowLossless().first()) {
                defaultQuality = AudioQuality.HIGH
            }
            _currentQuality.value = defaultQuality
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

    /**
     * Build and set the context queue for Cast playback.
     * Uses the user's default quality setting. If that results in Opus (which Cast doesn't support),
     * falls back to a Cast-compatible alternative.
     */
    private fun setContextFromLivesetForCast(livesetId: Long, startPositionMs: Long? = null) {
        scope.launch {
            // Start with the user's default quality
            var castQuality = playbackSettings.audioQuality().first()
            // If default is lossless but not allowed, use HIGH
            if (castQuality == AudioQuality.LOSSLESS && !playbackSettings.allowLossless().first()) {
                castQuality = AudioQuality.HIGH
            }

            // Check if this quality results in Opus (which Cast doesn't support well)
            val lwd = withContext(Dispatchers.IO) { editionRepository.findLiveset(livesetId).first() }
            if (lwd != null) {
                val url = getUrlForQuality(lwd.liveset, castQuality)
                if (url != null && !isCastCompatibleFormat(url)) {
                    // Current quality is Opus, try to find a Cast-compatible alternative
                    val fallback = findCastCompatibleQuality(lwd.liveset)
                    if (fallback != null) {
                        Log.d("QueueManager", "Default quality ($castQuality) is Opus, falling back to $fallback for Cast")
                        castQuality = fallback
                    } else {
                        Log.w("QueueManager", "No Cast-compatible quality available, using $castQuality (may fail)")
                    }
                }
            }

            Log.d("QueueManager", "Cast quality: $castQuality")
            _currentQuality.value = castQuality

            // Resolve the list of context liveset IDs
            val contextLivesetIds: List<Long> = try {
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

            // Resolve all items with Cast quality
            val resolved = withContext(Dispatchers.IO) {
                contextLivesetIds.mapNotNull { id -> resolveQueueItem(id, manualEntryId = null, quality = castQuality) }
            }
            mutex.withLock {
                context.clear()
                context.addAll(resolved)
                rebuildEffectiveLocked(livesetId)
            }
            applyToPlayerSafely(startLivesetId = livesetId, startPositionMs = startPositionMs, autoplay = true)
        }
    }

    /**
     * Get the URL for a specific quality.
     */
    private fun getUrlForQuality(liveset: nl.stoux.tfw.core.common.database.entity.LivesetEntity, quality: AudioQuality): String? {
        return when (quality) {
            AudioQuality.LOSSLESS -> liveset.losslessUrl ?: liveset.hqUrl ?: liveset.lqUrl
            AudioQuality.HIGH -> liveset.hqUrl ?: liveset.losslessUrl ?: liveset.lqUrl
            AudioQuality.LOW -> liveset.lqUrl ?: liveset.hqUrl ?: liveset.losslessUrl
        }
    }

    /**
     * Find a Cast-compatible quality for a liveset.
     * Cast supports: WAV, MP3, AAC (m4a), FLAC. Does NOT reliably support Opus.
     * Returns null if no Cast-compatible format is available.
     */
    private fun findCastCompatibleQuality(liveset: nl.stoux.tfw.core.common.database.entity.LivesetEntity): AudioQuality? {
        // Check each quality in order of preference
        if (!liveset.losslessUrl.isNullOrBlank() && isCastCompatibleFormat(liveset.losslessUrl!!)) {
            return AudioQuality.LOSSLESS
        }
        if (!liveset.hqUrl.isNullOrBlank() && isCastCompatibleFormat(liveset.hqUrl!!)) {
            return AudioQuality.HIGH
        }
        if (!liveset.lqUrl.isNullOrBlank() && isCastCompatibleFormat(liveset.lqUrl!!)) {
            return AudioQuality.LOW
        }
        return null
    }

    /**
     * Check if a URL points to a Cast-compatible audio format.
     */
    private fun isCastCompatibleFormat(url: String): Boolean {
        val ext = url.substringAfterLast('.', "").lowercase()
        return ext in listOf("wav", "mp3", "m4a", "aac", "flac")
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

    /**
     * Change the audio quality for the current playback session.
     * This does NOT change the default quality setting, only the current playback.
     * The current item will be replaced with the new quality while preserving position.
     */
    fun setQuality(quality: AudioQuality) {
        scope.launch {
            val currentLivesetId = _state.value.currentLivesetId ?: return@launch
            val player = playerManager.currentPlayer()
            val currentPosition = player.currentPosition
            val wasPlaying = player.isPlaying

            _currentQuality.value = quality

            // Resolve the current liveset with the new quality
            val lwd = withContext(Dispatchers.IO) { editionRepository.findLiveset(currentLivesetId).first() }
                ?: return@launch

            // Update actual quality based on what's available
            val actualQuality = PlayableMediaItemBuilder.getActualQuality(lwd.liveset, quality)
            _actualQuality.value = actualQuality

            // Rebuild the media item with new quality
            val newMediaItem = PlayableMediaItemBuilder.build(lwd, quality = quality) ?: return@launch

            mutex.withLock {
                val currentIndex = player.currentMediaItemIndex
                val instanceId = player.currentMediaItem?.mediaMetadata?.extras?.getString(QueueExtrasKeys.INSTANCE_ID)
                    ?: return@withLock

                // Replace the current item with the new quality version
                val withExtras = newMediaItem.withQueueExtras(instanceId = instanceId, manualEntryId = null)

                // Update the item in our internal state
                val effectiveIndex = _state.value.effective.indexOfFirst { it.instanceId == instanceId }
                if (effectiveIndex >= 0) {
                    val oldItem = _state.value.effective[effectiveIndex]
                    // Update context or manual lane depending on where it came from
                    val contextIdx = context.indexOfFirst { it.instanceId == instanceId }
                    if (contextIdx >= 0) {
                        context[contextIdx] = oldItem.copy(mediaItem = withExtras)
                    }
                    val manualIdx = manual.indexOfFirst { it.instanceId == instanceId }
                    if (manualIdx >= 0) {
                        manual[manualIdx] = oldItem.copy(mediaItem = withExtras)
                    }
                    rebuildEffectiveLocked()
                }

                // Replace in player
                player.removeMediaItem(currentIndex)
                player.addMediaItem(currentIndex, withExtras)
                player.seekTo(currentIndex, currentPosition)
                player.prepare()
                if (wasPlaying) {
                    player.playWhenReady = true
                }
            }
        }
    }

    /**
     * Get the available qualities for the currently playing liveset.
     */
    fun getAvailableQualities(): Set<AudioQuality> {
        val currentLivesetId = _state.value.currentLivesetId ?: return emptySet()
        val effective = _state.value.effective
        val currentItem = effective.find { it.livesetId == currentLivesetId }
        // We need to look up the liveset data - for now return all as a reasonable default
        // The actual available qualities should be determined from the liveset entity
        return AudioQuality.entries.toSet()
    }

    /**
     * Build the context queue for resumption without applying to player.
     * Used by onPlaybackResumption to return items to Media3 while keeping QueueManager state in sync.
     * @return Triple of (mediaItems, startIndex, effectiveQueue) or null if liveset not found
     */
    suspend fun buildContextForResumption(livesetId: Long): Pair<List<MediaItem>, Int>? {
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

        // Resolve all items
        val resolved = withContext(Dispatchers.IO) {
            contextLivesetIds.mapNotNull { id -> resolveQueueItem(id, manualEntryId = null) }
        }
        if (resolved.isEmpty()) return null

        // Update internal state (context lane) without applying to player
        mutex.withLock {
            context.clear()
            context.addAll(resolved)
            rebuildEffectiveLocked(livesetId)
        }

        // Build the effective media items and find start index
        val effective = _state.value.effective
        val mediaItems = effective.map { it.mediaItem }
        val startIndex = effective.indexOfFirst { it.livesetId == livesetId }.coerceAtLeast(0)

        return Pair(mediaItems, startIndex)
    }
    
    private suspend fun resolveQueueItem(livesetId: Long, manualEntryId: Long? = null, quality: AudioQuality? = null): QueueItem? {
        val lwd = withContext(Dispatchers.IO) { editionRepository.findLiveset(livesetId).first() }
            ?: return null

        // Use the specified quality or fall back to current quality
        val effectiveQuality = quality ?: _currentQuality.value

        // Check if we should use downloaded file
        val preferDownloaded = playbackSettings.preferDownloadedQuality().first()
        val downloadedUri = if (preferDownloaded) {
            downloadRepository.getLocalUri(livesetId)
        } else {
            null
        }

        // Use centralized builder for consistent MediaItem construction (incl. MIME type)
        val baseMediaItem = PlayableMediaItemBuilder.build(
            lwd = lwd,
            quality = effectiveQuality,
            downloadedUri = downloadedUri,
        ) ?: return null

        val instanceId = java.util.UUID.randomUUID().toString()
        val withExtras = baseMediaItem.withQueueExtras(instanceId = instanceId, manualEntryId = manualEntryId)
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

                // Compute target index: prefer explicit start effective index, else explicit start id, else match by instance id, else by liveset id, else 0
                val explicitIndexOverride = startEffectiveIndex?.takeIf { it in effective.indices }
                val explicitIndex = startLivesetId?.let { id -> effective.indexOfFirst { it.livesetId == id }.takeIf { it >= 0 } }
                val instanceIndex = currentInstanceId?.let { iid -> effective.indexOfFirst { it.instanceId == iid }.takeIf { it >= 0 } }
                val livesetIndex = _state.value.currentLivesetId?.let { id -> effective.indexOfFirst { it.livesetId == id }.takeIf { it >= 0 } }
                val targetIndex = explicitIndexOverride ?: explicitIndex ?: instanceIndex ?: livesetIndex ?: 0

                val pos = startPositionMs ?: currentPosition
                val needsPositioning = targetIndex != player.currentMediaItemIndex || (startPositionMs != null) || (startEffectiveIndex != null)

                // For CastPlayer, use setMediaItems with built-in start position to avoid seek race condition
                // CastPlayer returns error 2001 if we try to seek before media is loaded
                if (needsPositioning) {
                    player.setMediaItems(mediaItems, targetIndex, pos)
                } else {
                    player.setMediaItems(mediaItems, /* resetPosition = */ false)
                }

                // Ensure repeat-all so MediaSession transport wraps on Next even at end
                player.repeatMode = Player.REPEAT_MODE_ALL

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
                val newLivesetId = mediaItem?.mediaId?.let { CustomMediaId.fromOrNull(it)?.getLivesetId() }
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

            // Update actual quality based on the new liveset
            updateActualQuality()
        }
    }

    /**
     * Update the actual quality being played based on the current liveset and requested quality.
     */
    private suspend fun updateActualQuality() {
        val currentLivesetId = _state.value.currentLivesetId ?: return
        val lwd = withContext(Dispatchers.IO) { editionRepository.findLiveset(currentLivesetId).first() }
            ?: return
        val requestedQuality = _currentQuality.value
        val actual = PlayableMediaItemBuilder.getActualQuality(lwd.liveset, requestedQuality)
        _actualQuality.value = actual
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
