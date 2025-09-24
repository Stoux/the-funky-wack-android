package nl.stoux.tfw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import nl.stoux.tfw.feature.browser.EditionListScreen
import nl.stoux.tfw.feature.browser.EditionListViewModel
import nl.stoux.tfw.feature.player.PlayerScreen
import nl.stoux.tfw.feature.player.PlayerViewModel
import nl.stoux.tfw.ui.theme.TheFunkyWackTheme
import androidx.compose.ui.unit.dp

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val listViewModel: EditionListViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TheFunkyWackTheme {
                val isPlaying by playerViewModel.isPlaying.collectAsState()
                val nowTitle by playerViewModel.nowPlayingTitle.collectAsState()
                val progress by playerViewModel.progress.collectAsState()

                val navController = rememberNavController()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        androidx.compose.material3.CenterAlignedTopAppBar(
                            title = {
                                androidx.compose.foundation.layout.Column(
                                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                                ) {
                                    Text(text = "The Funky Wack", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                                    Text(
                                        text = "Wacky beats, the recordings.",
                                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                    },
                    bottomBar = {
                        if (isPlaying || nowTitle != null) {
                            NowPlayingBar(
                                title = nowTitle ?: "Playing",
                                progress = progress,
                                onPlayPause = { playerViewModel.playPause() },
                                onOpenPlayer = { navController.navigate("player") }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(navController = navController, startDestination = "list", modifier = Modifier.padding(innerPadding)) {
                        composable("list") {
                            EditionListScreen(
                                viewModel = listViewModel,
                                onPlayClicked = { url, title, artist -> playerViewModel.playUrl(url, title, artist) },
                                onOpenPlayer = { navController.navigate("player") }
                            )
                        }
                        composable("player") {
                            PlayerScreen(viewModel = playerViewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingBar(title: String, progress: Float?, onPlayPause: () -> Unit, onOpenPlayer: () -> Unit) {
    androidx.compose.material3.Surface(
        modifier = Modifier
            .navigationBarsPadding(),
        color = androidx.compose.material3.MaterialTheme.colorScheme.surface
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
            // Show progress only when known; otherwise no loading bar to avoid endless spinner
            val p = progress
            if (p != null) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { p.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .clickable { onOpenPlayer() },
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                androidx.compose.material3.Text(text = title, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                IconButton(onClick = onPlayPause) { androidx.compose.material3.Text("⏯") }
            }
        }
    }
}