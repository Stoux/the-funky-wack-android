package nl.stoux.tfw.service.playback.service.queue

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.util.UUID

object QueueExtrasKeys {
    const val INSTANCE_ID = "queue_instance_id"
    const val MANUAL_ENTRY_ID = "manual_entry_id"
}

/**
 * Minimal queue item used by QueueManager.
 * For now we only support livesets; the stable id is the livesetId.
 */
data class QueueItem(
    val livesetId: Long,
    val mediaItem: MediaItem, // mediaId encodes the livesetId via CustomMediaId
    val manualEntryId: Long? = null, // present when this entry comes from the manual queue
    val instanceId: String = UUID.randomUUID().toString(), // unique per entry to disambiguate duplicates
)

/**
 * Public state exposed by QueueManager for UI/consumers.
 */
data class QueueState(
    val manual: List<QueueItem> = emptyList(),
    val context: List<QueueItem> = emptyList(),
    val effective: List<QueueItem> = emptyList(),
    val currentEffectiveIndex: Int = 0,
    val currentLivesetId: Long? = null,
    val isPlaying: Boolean = false,
)

fun MediaItem.withQueueExtras(instanceId: String, manualEntryId: Long?): MediaItem {
    val currentExtras: Bundle = this.mediaMetadata.extras ?: Bundle()
    currentExtras.putString(QueueExtrasKeys.INSTANCE_ID, instanceId)
    if (manualEntryId != null) currentExtras.putLong(QueueExtrasKeys.MANUAL_ENTRY_ID, manualEntryId)

    // Preserve all existing metadata and just add the extras
    val meta = this.mediaMetadata.buildUpon()
        .setExtras(currentExtras)
        .build()

    return this.buildUpon()
        .setMediaMetadata(meta)
        .build()
}
