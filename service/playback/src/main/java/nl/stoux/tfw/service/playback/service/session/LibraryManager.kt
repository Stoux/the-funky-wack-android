package nl.stoux.tfw.service.playback.service.session

import android.annotation.SuppressLint
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import nl.stoux.tfw.core.common.database.dao.EditionWithContent
import nl.stoux.tfw.core.common.database.dao.LivesetWithDetails
import nl.stoux.tfw.core.common.repository.EditionRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryManager @Inject constructor(
    private val editionRepository: EditionRepository,
) {

    suspend fun init() {
        editionRepository.refreshEditions()
    }

    /**
     * Get the root MediaItem used to register the app in the Media Library
     */
    fun getRoot(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(CustomMediaId.ROOT.original)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("The Funky Wack")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    /**
     * Get the visible (paginated) items for the given media ID
     * Allows for nested navigation based on the parent ID.
     */
    fun getChildren(
        parentId: String,
        page: Int,
        pageSize: Int,
    ): List<MediaItem> {
        Log.d("LibraryManager", "Getting children for ${parentId}")
        val mediaId = CustomMediaId.from(parentId)


        return when {
            // Menus
            mediaId == CustomMediaId.ROOT -> getRootChildren()
            mediaId == CustomMediaId.ROOT_EDITIONS -> getEditions(page, pageSize)
            mediaId == CustomMediaId.ROOT_LIVESETS -> getAllLivesets(page, pageSize)

            // Show items inside another item
            mediaId.isEdition() -> getEditionLivesets(mediaId, page, pageSize)
            mediaId.isLiveset() -> getLivesetTracks(mediaId, page, pageSize)

            else -> emptyList()
        }
    }

    private fun getRootChildren(): List<MediaItem> {
        return listOf(
            browsableMediaItem(CustomMediaId.ROOT_EDITIONS, "Editions"),
            browsableMediaItem(CustomMediaId.ROOT_LIVESETS, "All livesets"),
        )
    }

    private fun getEditions(page: Int, pageSize: Int): List<MediaItem> {
        val editions = paginate(loadEditions(), page, pageSize)

        return editions.map { ed ->
            MediaItem.Builder()
                .setMediaId("edition:${ed.edition.id}")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("TFW #${ed.edition.number}: ${ed.edition.tagLine}")
                        .setArtist("${ed.edition.date} - ${ed.livesets.size} liveset${if (ed.livesets.size == 1)  "" else "s"}")
                        .setArtworkUri(ed.edition.posterUrl?.toUri())
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
        }
    }

    private fun getAllLivesets(page: Int, pageSize: Int): List<MediaItem> {
        val livesets = runBlocking { editionRepository.getLivesets(page, pageSize).first() }
        return livesets.map { liveset -> livesetMediaItem(liveset) }
    }

    private fun getEditionLivesets(mediaId: CustomMediaId, page: Int, pageSize: Int): List<MediaItem> {
        val editionId = mediaId.getEditionId() ?: throw IllegalArgumentException("Invalid Edition ID Media ID passed")
        val livesets = runBlocking { editionRepository.getLivesets(editionId, page, pageSize).first() }
        return livesets.map { liveset -> livesetMediaItem(liveset) }
    }

    @SuppressLint("DefaultLocale")
    private fun getLivesetTracks(mediaId: CustomMediaId, page: Int, pageSize: Int): List<MediaItem> {
        val livesetId = mediaId.getLivesetId() ?: throw IllegalArgumentException("Invalid Liveset ID Media ID passed")
        val liveset = runBlocking { editionRepository.findLiveset(livesetId).first() } ?: throw IllegalArgumentException("Liveset not found")

        return liveset.tracks.map { track ->
            val trackMediaId = CustomMediaId.forEntity(track)
            val timestampSec = track.timestampSec
            val timestamp = when {
                timestampSec != null -> {
                    val hours = timestampSec / 3600
                    val minutes = (timestampSec % 3600) / 60
                    val seconds = timestampSec % 60
                    if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
                    else String.format("%02d:%02d", minutes, seconds)
                } else -> "N/A"
            }

            MediaItem.Builder()
                .setMediaId(trackMediaId.original)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("#${track.orderInSet}: ${track.title}")
                        .setArtist("Timestamp: $timestamp")
                        .setIsBrowsable(false)
                        .setIsPlayable(track.timestampSec != null)
                        .build()
                )
                .build()
        }
    }


    private fun browsableMediaItem(
        mediaId: CustomMediaId,
        title: String,
        additionalMetadata: (MediaMetadata.Builder.() -> Unit)? = null
    ): MediaItem {
        return MediaItem.Builder()
            .setMediaId(mediaId.original)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .apply { additionalMetadata?.invoke(this) }
                    .build()
            )
            .build()
    }

    fun livesetMediaItem(
        id: Long,
    ) : MediaItem {
        val liveset = runBlocking { editionRepository.findLiveset(id).first() }
        if (liveset == null) throw IllegalArgumentException("Unknown liveset: $id")

        return livesetMediaItem(liveset)
    }

    private fun livesetMediaItem(
        lwd: LivesetWithDetails,
    ) : MediaItem {
        val mediaId = CustomMediaId.forEntity(lwd.liveset)

        val playableUri = listOfNotNull(lwd.liveset.hqUrl, lwd.liveset.lqUrl, lwd.liveset.losslessUrl).firstOrNull()
        return MediaItem.Builder()
            .setMediaId(mediaId.original)
            .setUri(playableUri?.toUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(lwd.liveset.title)
                    .setArtist(lwd.liveset.artistName)
                    .setTrackNumber(lwd.liveset.lineupOrder)
                    .setAlbumTitle("TFW #${lwd.edition.number}: ${lwd.edition.tagLine}")
                    .setIsBrowsable(lwd.tracks.firstOrNull{ it.timestampSec != null } != null)
                    .setIsPlayable(playableUri != null)
                    .build()
            )
            .build()
    }


    private fun loadEditions(): List<EditionWithContent> =
        runBlocking { editionRepository.getEditions().first() }



    private fun <T> paginate(list: List<T>, page: Int, pageSize: Int): List<T> {
        val startIndex = page * pageSize
        if (startIndex >= list.size) {
            return emptyList()
        }
        val endIndex = (startIndex + pageSize).coerceAtMost(list.size)
        return list.subList(startIndex, endIndex)
    }


}