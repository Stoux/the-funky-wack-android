package nl.stoux.tfw.service.playback.service.session

import nl.stoux.tfw.core.common.database.entity.EditionEntity
import nl.stoux.tfw.core.common.database.entity.LivesetEntity
import nl.stoux.tfw.core.common.database.entity.TrackEntity

data class CustomMediaId(
    val original: String,
    val type: String = original,
    val id: Long? = null,
    val timestamp: Int? = null,
) {

    fun isEdition(): Boolean {
        return type == TYPE_EDITION && id != null
    }

    fun isLiveset(): Boolean {
        return type == TYPE_LIVESET && id != null
    }

    fun getEditionId() : Long? {
        return if (isEdition()) id else null
    }

    fun getLivesetId() : Long? {
        return if (isLiveset()) id else null
    }

    companion object {

        // Root menu
        private const val MENU_ID_ROOT = "root"
        // => Top level options
        private const val MENU_ID_EDITIONS = "root.editions"
        private const val MENU_ID_LIVESETS = "root.livesets"
        private const val MENU_ID_SETTINGS = "root.settings"
        // => Actions
        private const val ACTION_REFRESH_LIVESETS = "settings.refresh"

        // Item types
        private const val TYPE_EDITION = "edition"
        private const val TYPE_LIVESET = "liveset"

        // Static root items for quick comparisons
        val ROOT = CustomMediaId(MENU_ID_ROOT)
        val ROOT_EDITIONS = CustomMediaId(MENU_ID_EDITIONS)
        val ROOT_LIVESETS = CustomMediaId(MENU_ID_LIVESETS)
        val ROOT_SETTINGS = CustomMediaId(MENU_ID_SETTINGS)
        val SETTINGS_REFRESH = CustomMediaId(ACTION_REFRESH_LIVESETS)

        /**
         * Parse the Media ID into a [CustomMediaId] object, or null if invalid/empty.
         */
        @JvmStatic
        fun fromOrNull(mediaId: String?): CustomMediaId? {
            if (mediaId.isNullOrBlank()) return null
            return runCatching { from(mediaId) }.getOrNull()
        }

        /**
         * Parse the Media ID into a [CustomMediaId] object.
         */
        @JvmStatic
        fun from(mediaId: String): CustomMediaId {
            // Root values
            if (mediaId == MENU_ID_ROOT) return ROOT
            if (mediaId == MENU_ID_EDITIONS) return ROOT_EDITIONS
            if (mediaId == MENU_ID_LIVESETS) return ROOT_LIVESETS
            if (mediaId == MENU_ID_SETTINGS) return ROOT_SETTINGS
            if (mediaId == ACTION_REFRESH_LIVESETS) return SETTINGS_REFRESH

            // Check if we're a ID'd type
            val typeAndIds = mediaId.split(':', limit = 2)
            if (typeAndIds.size == 2) {
                // Check if we're a supported type
                val type = when {
                    typeAndIds[0] == TYPE_EDITION -> TYPE_EDITION
                    typeAndIds[0] == TYPE_LIVESET -> TYPE_LIVESET
                    else -> throw IllegalArgumentException("Unsupported MediaID type: ${typeAndIds[0]}")
                }

                // Check if we have an additional data thing
                val id = typeAndIds[1].substringBefore("@", typeAndIds[1]).toLongOrNull() ?: throw IllegalArgumentException("Invalid MediaID for type $type: $mediaId")
                val timestamp = typeAndIds[1].substringAfter("@").toIntOrNull()

                return CustomMediaId(mediaId, type, id, timestamp)
            }

            // Any other actions?
            throw IllegalArgumentException("Unsupported Media ID: $mediaId")
        }

        @JvmStatic
        fun forEntity(edition: EditionEntity) : CustomMediaId {
            return CustomMediaId(
                "$TYPE_EDITION:${edition.id}",
                TYPE_EDITION,
                edition.id,
            )
        }


        @JvmStatic
        fun forEntity(liveset: LivesetEntity) : CustomMediaId {
            return CustomMediaId(
                "$TYPE_LIVESET:${liveset.id}",
                TYPE_LIVESET,
                liveset.id,
            )
        }

        @JvmStatic
        fun forEntity(track: TrackEntity) : CustomMediaId {
            return CustomMediaId(
                "$TYPE_LIVESET:${track.livesetId}",
                TYPE_LIVESET,
                track.livesetId,
                track.timestampSec
            )
        }

    }

}