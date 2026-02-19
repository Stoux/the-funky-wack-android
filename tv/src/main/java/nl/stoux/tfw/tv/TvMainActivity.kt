package nl.stoux.tfw.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import nl.stoux.tfw.tv.navigation.TvNavHost
import nl.stoux.tfw.tv.ui.theme.TvTheme

@AndroidEntryPoint
class TvMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TvTheme {
                TvNavHost()
            }
        }
    }
}
