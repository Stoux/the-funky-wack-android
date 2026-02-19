package nl.stoux.tfw.service.playback.service.session

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import nl.stoux.tfw.core.common.database.dao.LivesetWithDetails
import nl.stoux.tfw.core.common.database.entity.LivesetEntity
import nl.stoux.tfw.core.common.database.entity.artworkUrl

/**
 * Centralized builder for playable MediaItems.
 * Ensures consistent MIME type detection and metadata for both QueueManager and LibraryManager.
 */
object PlayableMediaItemBuilder {

    /**
     * Build a playable MediaItem for a liveset.
     * Returns null if no playable URL is available.
     */
    fun build(
        lwd: LivesetWithDetails,
        showEditionInfo: Boolean = false,
    ): MediaItem? {
        val liveset = lwd.liveset
        val edition = lwd.edition

        // Find the best playable URL (prefer lossless > hq > lq)
        val playableUrl = liveset.losslessUrl ?: liveset.hqUrl ?: liveset.lqUrl
        if (playableUrl.isNullOrBlank()) return null

        val playableUri = playableUrl.toUri()
        val mimeType = detectMimeType(playableUrl)
        val mediaId = CustomMediaId.forEntity(liveset).original
        val artworkUri = edition.artworkUrl?.toUri()

        // Build title/artist (optionally with edition info)
        var title = liveset.title
        var artist = liveset.artistName
        if (showEditionInfo) {
            title = "#${liveset.lineupOrder ?: "?"} $title"
            artist = "TFW #${edition.number} - $artist"
        }

        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(playableUri)
            .setMimeType(mimeType)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setTrackNumber(liveset.lineupOrder)
                    .setAlbumTitle("TFW #${edition.number}: ${edition.tagLine}")
                    .setArtworkUri(artworkUri)
                    .setIsBrowsable(lwd.tracks.any { it.timestampSec != null })
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }

    /**
     * Detect MIME type from URL file extension.
     */
    fun detectMimeType(url: String?): String? {
        if (url == null) return null
        return when (url.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a", "mp4" -> "audio/mp4"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "ogg", "oga" -> "audio/ogg"
            "opus" -> "audio/webm"
            else -> null
        }
    }
}
