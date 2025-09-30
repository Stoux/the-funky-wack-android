package nl.stoux.tfw.service.playback.service.manager

import nl.stoux.tfw.core.common.database.dao.LivesetWithDetails
import nl.stoux.tfw.core.common.database.entity.TrackEntity

class ListenersUpdater {

    // Management of the list
    private val listeners = mutableListOf<LivesetTrackListener>()

    fun addListener(listener: LivesetTrackListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: LivesetTrackListener) {
        listeners.remove(listener)
    }

    fun isEmpty(): Boolean {
        return listeners.isEmpty()
    }

    fun onLivesetChanged(liveset: LivesetWithDetails?, currentTrack: TrackEntity? = null) {
        listeners.forEach { listener ->
            listener.onLivesetChanged(liveset)
            listener.onTrackChanged(currentTrack)
            listener.onTimeProgress(null, null)
            listener.onNextPrevTrackStatusChanged(hasPreviousTrack = false, hasNextTrack = false)
        }
    }

    fun onTrackChanged(trackSection: LivesetTrackManager.TrackSection) {
        val hasPrev = trackSection.prev != null
        val hasNext = trackSection.next != null

        listeners.forEach { listener ->
            listener.onTrackChanged(trackSection.track)
            listener.onNextPrevTrackStatusChanged(hasPrev, hasNext)
        }

    }

    fun onTrackChanged(currentTrack: TrackEntity?) {
        listeners.forEach { listener ->
            listener.onTrackChanged(currentTrack)
        }
    }

    fun onNextPrevTrackStatusChanged(hasPreviousTrack: Boolean, hasNextTrack: Boolean) {
        listeners.forEach { listener ->
            listener.onNextPrevTrackStatusChanged(hasPreviousTrack, hasNextTrack)
        }
    }

    fun onTimeProgress(position: Long?, duration: Long?) {
        listeners.forEach { listener ->
            listener.onTimeProgress(position, duration)
        }
    }

}