package nl.stoux.tfw.service.playback.service.manager

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import nl.stoux.tfw.core.common.database.dao.LivesetWithDetails
import nl.stoux.tfw.core.common.database.entity.TrackEntity
import nl.stoux.tfw.core.common.repository.EditionRepository
import nl.stoux.tfw.service.playback.player.PlayerManager
import nl.stoux.tfw.service.playback.service.session.CustomMediaId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager that provides utility methods for skipping tracks inside the current playlist (if supported)
 */
@Singleton
class LivesetTrackManager @Inject constructor(
    private val playerManager: PlayerManager,
    private val editionRepository: EditionRepository,

) : Player.Listener {

    private val serviceIOScope = CoroutineScope(Dispatchers.IO)
    private val serviceMainScope = CoroutineScope(Dispatchers.Main)

    private var currentLiveset: LivesetWithDetails? = null
    private var currentLivesetId: Long? = null

    private var currentTrackSection: TrackSection? = null

    private val listenersUpdater = ListenersUpdater()

    private var progressReporter = PlayingProgressReporter(serviceMainScope) {
        updateTrackInformation()
    }

    /**
     * Bind the track manager to the current player & service
     *
     * @return The callback to unbind
     */
    fun bind(
        listener: LivesetTrackListener,
        // TODO: Preferred refresh interval
    ): UnbindCallback {
        val player = playerManager.currentPlayer()

        // Register the listener if we're the first to hook into this manager
        if (listenersUpdater.isEmpty()) {
            player.addListener(this);
        }

        listenersUpdater.addListener(listener)


        // Instantly fire all events (by using a temporary updater with just that listener)
        val updater = ListenersUpdater()
        updater.addListener(listener)

        val trackSection = currentTrackSection
        updater.onLivesetChanged(liveset = currentLiveset)
        if (trackSection != null) {
            updater.onTrackChanged(trackSection)
        }
        updater.onTimeProgress(player.currentPosition, player.duration)

        return {
            listenersUpdater.removeListener(listener)
            unbindIfNoListeners()
        }
    }

    private fun unbindIfNoListeners() {
        // Unregister the listener if there are no more callbacks to update
        if (listenersUpdater.isEmpty()) {
            progressReporter.stop()
            playerManager.currentPlayer().removeListener(this);
        }
    }

    fun toPreviousTrack() {
        val previousTrack = currentTrackSection?.prev
        if (previousTrack == null) return
        playerManager.currentPlayer().seekTo(previousTrack.startAt)
    }

    fun toNextTrack() {
        val nextTrack = currentTrackSection?.next
        if (nextTrack == null) return
        playerManager.currentPlayer().seekTo(nextTrack.startAt)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // Parse the media item & check if it's a valid liveset
        val mediaId = if (mediaItem == null) null else CustomMediaId.Companion.from(mediaItem.mediaId)
        val livesetId = mediaId?.getLivesetId()
        if (livesetId == null) {
            currentLiveset = null
            currentLivesetId = null
            currentTrackSection = null
            return
        }

        // Abort if already on that liveset
        if (currentLivesetId == livesetId) {
            return
        }

        // Set the current live 
        currentLivesetId = livesetId
        currentLiveset = null
        currentTrackSection = null
        
        // Find that liveset (async)
        serviceIOScope.launch {
            val liveset = runBlocking { editionRepository.findLiveset(livesetId).first() }
            val foundLivesetId = liveset?.liveset?.id
            if (foundLivesetId == null || foundLivesetId != currentLivesetId) {
                return@launch
            }

            // Build a linked list progressing the timestamps
            val trackLinkedList = TrackSection(-1L, null)
            var lastSection = trackLinkedList
            liveset.tracks.filter { it.timestampSec != null }.sortedBy { it.timestampSec }.forEach { track ->
                val nextSection = TrackSection(track.timestampSec?.times(1000L) ?: 0, track, lastSection)
                lastSection.next = nextSection
                lastSection = nextSection
            }

            // Everything is resolved. Update the status & player
            serviceMainScope.launch {
                // Quick sanity check
                if (foundLivesetId == currentLivesetId) {
                    currentLiveset = liveset
                    currentTrackSection = trackLinkedList
                    listenersUpdater.onLivesetChanged(liveset, trackLinkedList.track)
                    updateTrackInformation()
                }
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            progressReporter.start()
        } else {
            progressReporter.stop()
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        updateTrackInformation()
    }

    fun updateTrackInformation() {
        // Update the timer
        // TODO

        // Early bail if we're missing stuff
        val currentTrackSection = this.currentTrackSection
        if (currentTrackSection == null) {
            return;
        }

        // Resolve the current position
        val position = playerManager.currentPlayer().currentPosition
        val playingSection = currentTrackSection.findPlayingSection(position);
        if (playingSection == currentTrackSection) {
            // We're already showing the correct item
            return;
        } else if (playingSection == null) {
            // Nothing playing?
            listenersUpdater.onTrackChanged(null)
            listenersUpdater.onNextPrevTrackStatusChanged(hasPreviousTrack = false, hasNextTrack = false)
            return;
        }

        // We've hit a different section! Make sure the underlying track has also changed
        this.currentTrackSection = playingSection
        listenersUpdater.onTrackChanged(playingSection)
        if (currentTrackSection.track?.id == playingSection.track?.id) {
            // Same track somehow. No need to update the player. Might be a different next/prev?
            return;
        }

        // Sanity checks: we need liveset info
        val liveset = currentLiveset
        if (liveset == null) {
            return;
        }

        // Update the media player!
        // TODO: Make this an option
        val player = playerManager.currentPlayer()
        val mediaItem = player.currentMediaItem;
        if (mediaItem == null) {
            return;
        }

        // Default title should just be like the normal media item
        val track = playingSection.track
        var title = liveset.liveset.title
        var artist = liveset.liveset.artistName
        // But if we're showing a track, we move those to the artist & push the track title/artist/text into the title
        if (track != null) {
            artist = title
            title = track.title
        }

        // Swap the state in the player
        val updatedMediaItem = mediaItem.buildUpon()
            .setMediaMetadata(
                mediaItem.mediaMetadata.buildUpon()
                    .setTitle(title)
                    .setArtist(artist)
                    .build()
            )
            .build()

        player.replaceMediaItem(player.currentMediaItemIndex, updatedMediaItem)
    }

    private inner class PlayingProgressReporter(
        private val scope: CoroutineScope,
        private val report: (currentPositionInMs: Long) -> Unit,
    ) {
        private var job: Job? = null

        fun start() {
            // Make sure the previous one is stopped if for some reason start gets called multiple times
            stop()

            // Start the job
            job = scope.launch {
                while(true) {
                    report(playerManager.currentPlayer().currentPosition);
                    delay(1000)
                }
            }
        }

        fun stop() {
            job?.cancel();
            job = null
        }
    }

    class TrackSection(
        val startAt: Long,
        val track: TrackEntity?,
        var prev: TrackSection? = null,
        var next: TrackSection? = null,
    ) {

        fun isPlaying(position: Long): Boolean {
            val nextStart = next?.startAt ?: Long.MAX_VALUE
            return position >= startAt && position < nextStart
        }

        fun findPlayingSection(position: Long): TrackSection? {
            if (isPlaying(position)) {
                return this
            }

            if (startAt > position) {
                return prev?.findPlayingSection(position)
            } else {
                return next?.findPlayingSection(position)
            }
        }

    }

    private data class RegisteredCallback(
        val hashCode: Int,
        val callback: (hasPreviousTrack: Boolean, hasNextTrack: Boolean) -> Unit,
    )



}

typealias UnbindCallback = () -> Unit