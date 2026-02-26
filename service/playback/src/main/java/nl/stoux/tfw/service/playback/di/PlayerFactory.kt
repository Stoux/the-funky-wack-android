package nl.stoux.tfw.service.playback.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import dagger.hilt.android.qualifiers.ApplicationContext
import nl.stoux.tfw.service.playback.settings.PlaybackSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerFactory @Inject constructor(
    @ApplicationContext private val context: Context
) {

    @UnstableApi
    fun create(bufferMinutes: Int = PlaybackSettingsRepository.DEFAULT_BUFFER_MINUTES): Player {
        // Correctly take audio priority over other apps when playing starts
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Configurable forward buffer for offline playback (tunnels, spotty coverage)
        val bufferMs = bufferMinutes * 60 * 1000
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs */ bufferMs,
                /* maxBufferMs */ bufferMs,
                /* bufferForPlaybackMs */ DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                /* bufferForPlaybackAfterRebufferMs */ DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .setWakeMode(C.WAKE_MODE_NETWORK)  /* Keep CPU + WiFi awake during streaming playback */
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(audioAttributes, true)
            .build()

        player.repeatMode = Player.REPEAT_MODE_ALL

        return player
    }



}