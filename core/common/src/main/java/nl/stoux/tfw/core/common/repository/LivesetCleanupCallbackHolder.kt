package nl.stoux.tfw.core.common.repository

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holder for the liveset cleanup callback.
 * Allows DownloadRepository to register itself after construction
 * to avoid circular dependency issues.
 */
@Singleton
class LivesetCleanupCallbackHolder @Inject constructor() {
    @Volatile
    var callback: LivesetCleanupCallback? = null
}
