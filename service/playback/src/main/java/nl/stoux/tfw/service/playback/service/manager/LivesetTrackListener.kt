package nl.stoux.tfw.service.playback.service.manager

import nl.stoux.tfw.core.common.database.dao.LivesetWithDetails
import nl.stoux.tfw.core.common.database.entity.TrackEntity

/**
 * Listener interface for the {@link LivesetTrackManager}
 */
interface LivesetTrackListener {

    fun onLivesetChanged(liveset: LivesetWithDetails?) {}

    fun onTrackChanged(track: TrackEntity?) {}

    fun onNextPrevTrackStatusChanged(hasPreviousTrack: Boolean, hasNextTrack: Boolean) {}

    fun onTimeProgress(position: Long?, duration: Long?) {}

}