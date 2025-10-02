package nl.stoux.tfw.automotive.car

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import nl.stoux.tfw.service.playback.service.MediaPlaybackService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lazily provides a MediaController connected to the shared MediaPlaybackService.
 */
@Singleton
class ControllerHolder @Inject constructor() {

    @Volatile
    private var controller: MediaController? = null

    fun init(context: Context) {
        if (controller != null) {
            return
        }

        // Create an async MediaController, add listener to set it as the controller
        val token = SessionToken(context.applicationContext, ComponentName(context, MediaPlaybackService::class.java))
        val future = MediaController.Builder(context.applicationContext, token).buildAsync()
        future.addListener({
                controller = future.get()
            }, { runnable -> runnable.run() })
        return
    }

    fun get(): MediaController? {
        return controller
    }

}
