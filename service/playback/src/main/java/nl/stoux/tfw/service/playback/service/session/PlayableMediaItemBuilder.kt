package nl.stoux.tfw.service.playback.service.session

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import nl.stoux.tfw.core.common.database.dao.LivesetWithDetails
import nl.stoux.tfw.core.common.database.entity.LivesetEntity
import nl.stoux.tfw.core.common.database.entity.artworkUrl
import nl.stoux.tfw.service.playback.settings.PlaybackSettingsRepository.AudioQuality

/**
 * Centralized builder for playable MediaItems.
 * Ensures consistent MIME type detection and metadata for both QueueManager and LibraryManager.
 */
object PlayableMediaItemBuilder {

    /**
     * Build a playable MediaItem for a liveset.
     * Returns null if no playable URL is available.
     * @param quality Preferred quality. Falls back to best available if requested quality is unavailable.
     */
    fun build(
        lwd: LivesetWithDetails,
        showEditionInfo: Boolean = false,
        quality: AudioQuality? = null,
    ): MediaItem? {
        val liveset = lwd.liveset
        val edition = lwd.edition

        // Find the playable URL based on requested quality with fallbacks
        val playableUrl = getUrlForQuality(liveset, quality)
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

    /**
     * Get the URL for the requested quality with fallbacks.
     * If no specific quality is requested or the requested quality is unavailable,
     * falls back to best available (lossless > hq > lq).
     */
    private fun getUrlForQuality(liveset: nl.stoux.tfw.core.common.database.entity.LivesetEntity, quality: AudioQuality?): String? {
        return when (quality) {
            AudioQuality.LOSSLESS -> liveset.losslessUrl ?: liveset.hqUrl ?: liveset.lqUrl
            AudioQuality.HIGH -> liveset.hqUrl ?: liveset.losslessUrl ?: liveset.lqUrl
            AudioQuality.LOW -> liveset.lqUrl ?: liveset.hqUrl ?: liveset.losslessUrl
            null -> liveset.losslessUrl ?: liveset.hqUrl ?: liveset.lqUrl
        }
    }

    /**
     * Determine the actual quality being used for a liveset given a requested quality.
     */
    fun getActualQuality(liveset: nl.stoux.tfw.core.common.database.entity.LivesetEntity, requestedQuality: AudioQuality?): AudioQuality? {
        val url = getUrlForQuality(liveset, requestedQuality) ?: return null
        return when (url) {
            liveset.losslessUrl -> AudioQuality.LOSSLESS
            liveset.hqUrl -> AudioQuality.HIGH
            liveset.lqUrl -> AudioQuality.LOW
            else -> null
        }
    }

    /**
     * Get the set of available qualities for a liveset.
     */
    fun getAvailableQualities(liveset: nl.stoux.tfw.core.common.database.entity.LivesetEntity): Set<AudioQuality> {
        val available = mutableSetOf<AudioQuality>()
        if (!liveset.lqUrl.isNullOrBlank()) available.add(AudioQuality.LOW)
        if (!liveset.hqUrl.isNullOrBlank()) available.add(AudioQuality.HIGH)
        if (!liveset.losslessUrl.isNullOrBlank()) available.add(AudioQuality.LOSSLESS)
        return available
    }
}
