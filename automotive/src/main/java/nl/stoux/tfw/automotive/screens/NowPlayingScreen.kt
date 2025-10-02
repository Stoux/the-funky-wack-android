package nl.stoux.tfw.automotive.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarText
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Template
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import nl.stoux.tfw.automotive.car.ControllerHolder
import nl.stoux.tfw.service.playback.service.MediaPlaybackService

/**
 * A "Now Playing" screen implemented with Car App's MediaTemplate.
 * - Shows artwork, title/artist from Media3 MediaController
 * - Provides main controls: previous, play/pause, next
 * - Provides secondary custom actions: previous/next track within liveset via custom commands
 * - Displays a dynamic progress bar including indeterminate buffering
 */
class NowPlayingScreen @AssistedInject constructor(
    @Assisted private val carContext: CarContext,
    private val controllerHolder: ControllerHolder,
) : Screen(carContext), DefaultLifecycleObserver {

    private var controller: MediaController? = null

    // Playback state cache for building template
    private var currentItem: MediaItem? = null
    private var isPlaying: Boolean = false
    private var playbackState: Int = Player.STATE_IDLE
    private var shuffleEnabled: Boolean = false
    private var repeatMode: Int = Player.REPEAT_MODE_OFF

    // Artwork handling
    private var artworkIcon: CarIcon? = null
    private var artworkUri: Uri? = null

    // Progress handling
    private var positionMs: Long = 0L
    private var durationMs: Long = 0L
    private var progressJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentItem = mediaItem
            // Reset artwork and try to update
            artworkIcon = null
            artworkUri = null
            lifecycleScope.launch { prepareArtworkIcon(mediaItem?.mediaMetadata) }
            invalidate()
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@NowPlayingScreen.isPlaying = isPlaying
            maybeStartOrStopProgress()
            invalidate()
        }
        override fun onPlaybackStateChanged(state: Int) {
            playbackState = state
            maybeStartOrStopProgress()
            invalidate()
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            shuffleEnabled = shuffleModeEnabled
            invalidate()
        }
        override fun onRepeatModeChanged(repeatMode: Int) {
            this@NowPlayingScreen.repeatMode = repeatMode
            invalidate()
        }
    }

    init {
        lifecycle.addObserver(this)
        // Attach controller
        controller = controllerHolder.get()
        controller?.addListener(listener)
        // Prime current state
        currentItem = controller?.currentMediaItem
        isPlaying = controller?.isPlaying == true || controller?.playWhenReady == true
        playbackState = controller?.playbackState ?: Player.STATE_IDLE
        shuffleEnabled = controller?.shuffleModeEnabled ?: false
        repeatMode = controller?.repeatMode ?: Player.REPEAT_MODE_OFF
        lifecycleScope.launch { prepareArtworkIcon(currentItem?.mediaMetadata) }
        maybeStartOrStopProgress()
    }

    override fun onGetTemplate(): Template {
        val ctrl = controller
        val metadata = currentItem?.mediaMetadata
        val title = metadata?.title?.toString()?.ifBlank { "Now Playing" } ?: "Now Playing"
        val artist = metadata?.artist?.toString() ?: ""
        val timeInfo = buildString {
            val pos = positionMs / 1000
            val dur = (durationMs.takeIf { it > 0 } ?: ctrl?.duration ?: 0L) / 1000
            append(String.format("%d:%02d", pos / 60, pos % 60))
            if (dur > 0) {
                append(" / ")
                append(String.format("%d:%02d", dur / 60, dur % 60))
            }
            if (playbackState == Player.STATE_BUFFERING) append("  •  Buffering…")
        }

        // Main actions
        fun carIcon(resId: Int): CarIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, resId)
        ).build()

        val prevAction = Action.Builder()
            .setIcon(carIcon(android.R.drawable.ic_media_previous))
            .setOnClickListener { ctrl?.seekToPreviousMediaItem() }
            .build()

        val playPauseAction = Action.Builder()
            .setIcon(carIcon(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play))
            .setOnClickListener {
                val c = controller ?: return@setOnClickListener
                if (c.isPlaying) c.pause() else c.play()
            }
            .build()

        val nextAction = Action.Builder()
            .setIcon(carIcon(android.R.drawable.ic_media_next))
            .setOnClickListener { ctrl?.seekToNextMediaItem() }
            .build()

        // Optional top action strip with secondary actions (e.g., only essential actions here)
        val primaryStrip = ActionStrip.Builder()
            .addAction(prevAction)
            .addAction(playPauseAction)
//            .addAction(nextAction)
            .build()

        // Build the pane content (artwork, title/artist, time, and big playback actions)
        val paneBuilder = Pane.Builder()
            .addRow(androidx.car.app.model.Row.Builder().setTitle(title).addText(artist).build())
            .addRow(androidx.car.app.model.Row.Builder().setTitle(timeInfo).build())

        // Show large artwork if available
        artworkIcon?.let { paneBuilder.setImage(it) }

        // Show large primary controls within the pane to mimic a Now Playing screen
        paneBuilder
            .addAction(Action.Builder()
                .setIcon(carIcon(android.R.drawable.ic_media_previous))
                .setOnClickListener { ctrl?.seekToPreviousMediaItem() }
                .build())
            .addAction(playPauseAction)
            .addAction(nextAction)
            // Keep pane actions within the allowed max of 2. Next is available in the ActionStrip above.

        val pane = paneBuilder.build()

        return PaneTemplate.Builder(pane)
            .setTitle("Now Playing")
            .setHeaderAction(Action.BACK)
            .setActionStrip(primaryStrip)
            .build()
    }

    private fun maybeStartOrStopProgress() {
        val c = controller
        if (c == null) {
            stopProgress()
            return
        }
        if (isPlaying || playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
            if (progressJob?.isActive == true) return

            progressJob = lifecycleScope.launch {
                while (true) {
                    positionMs = c.currentPosition
                    durationMs = c.duration
                    invalidate()
                    delay(1000)
                }
            }
        } else {
            stopProgress()
        }
    }

    private fun stopProgress() {
        progressJob?.cancel()
        progressJob = null
    }

    override fun onDestroy(owner: LifecycleOwner) {
        try {
            controller?.removeListener(listener)
        } catch (_: Throwable) {}
        stopProgress()
    }

    /**
     * Prepare a CarIcon for artwork based on MediaMetadata.artworkUri.
     * Downloads https image into cache and exposes a content:// URI via FileProvider.
     */
    private suspend fun prepareArtworkIcon(metadata: MediaMetadata?) {
        val uri = metadata?.artworkUri ?: return
        if (uri.scheme != "https") return
        try {
            val file = downloadToCache(uri.toString()) ?: return
            val contentUri = FileProvider.getUriForFile(
                carContext,
                carContext.packageName + ".fileprovider",
                file
            )
            artworkUri = contentUri
            artworkIcon = CarIcon.Builder(IconCompat.createWithContentUri(contentUri)).build()
        } catch (t: Throwable) {
            Log.w("NowPlaying", "Artwork load failed: ${t.message}")
        }
    }

    private fun downloadToCache(urlString: String): File? {
        val cacheFile = File(carContext.cacheDir, "artwork_${urlString.hashCode()}.img")
        if (cacheFile.exists() && cacheFile.length() > 0) return cacheFile
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 5000; readTimeout = 5000 }
            conn.inputStream.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            // Quick sanity check
            BitmapFactory.decodeFile(cacheFile.absolutePath) ?: return null
            cacheFile
        } catch (t: Throwable) {
            Log.w("NowPlaying", "Failed to download artwork: ${t.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }
}
