package nl.stoux.tfw.service.playback.service.session

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionSettings @Inject constructor() {

    /** Changes whether all livesets will be set in the queue or just the ones from the edition */
    var queueEditionLivesetsOnly = false

}