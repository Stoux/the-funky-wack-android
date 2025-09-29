package nl.stoux.tfw.service.playback.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.C.WAKE_MODE_LOCAL
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    @UnstableApi
    fun provideExoPlayer(@ApplicationContext context: Context): ExoPlayer {
        // Correctly take audio priority over other apps when playing starts
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(context)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .setWakeMode(WAKE_MODE_LOCAL)  /* Prevent the service from being killed during playback */
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(audioAttributes, true)
            .build()

        player.repeatMode = Player.REPEAT_MODE_ALL;

        return player;
    }

}
