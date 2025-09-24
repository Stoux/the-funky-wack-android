package nl.stoux.tfw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

                val navController = rememberNavController()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { TopAppBar(title = { Text("The Funky Wack") }) },
                    bottomBar = {
                        if (isPlaying || nowTitle != null) {
                            NowPlayingBar(
                                title = nowTitle ?: "Playing",
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
private fun NowPlayingBar(title: String, onPlayPause: () -> Unit, onOpenPlayer: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        // Title doubles as a button to open the full player
        androidx.compose.material3.Text(text = title, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
        IconButton(onClick = onPlayPause) { androidx.compose.material3.Text("⏯") }
    }
}