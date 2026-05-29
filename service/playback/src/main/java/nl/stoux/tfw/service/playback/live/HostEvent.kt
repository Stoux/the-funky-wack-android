package nl.stoux.tfw.service.playback.live

/**
 * Events broadcast by a listen-along host to synced listeners over the
 * `presence-listen-along.{channelToken}` Pusher channel.
 *
 * Pusher event names use a leading dot per Laravel's broadcast convention
 * (`.host.seek`, `.host.pause`, etc.). The dot is stripped before we get here.
 */
sealed interface HostEvent {
    data class Seek(val position: Long) : HostEvent
    data class Pause(val position: Long) : HostEvent
    data class Resume(val position: Long) : HostEvent
    data class TrackChange(val livesetId: Long, val position: Long, val quality: String?) : HostEvent
    object Stop : HostEvent
}

/**
 * Events fired by the host's room for the host themselves (and surfaced in the
 * lobby UI): someone joined/left their session.
 */
sealed interface ListenerEvent {
    data class Joined(val name: String, val count: Int) : ListenerEvent
    data class Left(val count: Int) : ListenerEvent
}
