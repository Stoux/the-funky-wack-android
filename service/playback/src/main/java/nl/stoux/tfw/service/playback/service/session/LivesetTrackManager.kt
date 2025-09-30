package nl.stoux.tfw.service.playback.service.session

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

    private var onUpdatedCallbacks: MutableList<RegisteredCallback> = mutableListOf()

    private var progressReporter = PlayingProgressReporter(serviceMainScope) {
        updateTrackInformation()
    }

    /**
     * Bind the track manager to the current player & service
     */
    fun bind(
        hashCode: Int,
        onTracksUpdated: (hasPreviousTrack: Boolean, hasNextTrack: Boolean) -> Unit,
    ) {
        // Register the listener if we're the first to hook into this manager
        if (onUpdatedCallbacks.isEmpty()) {
            playerManager.currentPlayer().addListener(this);
        }

        this.onUpdatedCallbacks.add(RegisteredCallback(hashCode, onTracksUpdated))
    }

    fun unbind(
        hashCode: Int,
    ) {
        this.onUpdatedCallbacks = this.onUpdatedCallbacks.filter { it.hashCode != hashCode }.toMutableList()

        // Unregister the listener if there are no more callbacks to update
        if (onUpdatedCallbacks.isEmpty()) {
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
        val mediaId = if (mediaItem == null) null else CustomMediaId.from(mediaItem.mediaId)
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
                    notifyCallbacks(false, trackLinkedList.next != null)
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

    fun updateTrackInformation() {
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
            notifyCallbacks(false, false)
            return;
        }

        // We've hit a different section! Make sure the underlying track has also changed
        this.currentTrackSection = playingSection
        if (currentTrackSection.track?.id == playingSection.track?.id) {
            return; // Same track somehow. No need to update the player
        }

        // Update the buttons
        notifyCallbacks(playingSection.prev != null, playingSection.next != null)

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

    private fun notifyCallbacks(hasPreviousTrack: Boolean, hasNextTrack: Boolean) {
        onUpdatedCallbacks.forEach { it.callback.invoke(hasPreviousTrack, hasNextTrack) }
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

    private class TrackSection(
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