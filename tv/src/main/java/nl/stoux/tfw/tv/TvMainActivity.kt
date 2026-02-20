package nl.stoux.tfw.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import nl.stoux.tfw.service.playback.service.queue.QueueManager
import nl.stoux.tfw.tv.navigation.TvNavHost
import nl.stoux.tfw.tv.ui.theme.TvTheme
import javax.inject.Inject

@AndroidEntryPoint
class TvMainActivity : ComponentActivity() {

    @Inject
    lateinit var queueManager: QueueManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on during playback to prevent TV from sleeping
        lifecycleScope.launch {
            queueManager.state.collectLatest { state ->
                if (state.isPlaying) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        setContent {
            TvTheme {
                TvNavHost()
            }
        }
    }
}
