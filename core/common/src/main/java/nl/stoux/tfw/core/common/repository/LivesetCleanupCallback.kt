package nl.stoux.tfw.core.common.repository

/**
 * Callback interface for cleaning up associated data when livesets are removed.
 * Implemented by DownloadRepository to clean up downloaded files before liveset deletion.
 */
interface LivesetCleanupCallback {
    /**
     * Called before livesets are deleted from the database.
     * Implementation should clean up any associated data (downloads, cache, etc).
     *
     * @param livesetIds IDs of livesets about to be deleted
     */
    suspend fun onLivesetsRemoving(livesetIds: Set<Long>)
}
