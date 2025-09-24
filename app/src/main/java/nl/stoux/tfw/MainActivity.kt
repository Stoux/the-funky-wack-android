package nl.stoux.tfw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import nl.stoux.tfw.core.common.database.dao.EditionWithContent
import nl.stoux.tfw.ui.theme.TheFunkyWackTheme
import nl.stoux.tfw.ui.EditionListViewModel
import nl.stoux.tfw.ui.PlayerViewModel

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
                val editions by listViewModel.editions.collectAsState()
                val isPlaying by playerViewModel.isPlaying.collectAsState()
                val nowTitle by playerViewModel.nowPlayingTitle.collectAsState()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { TopAppBar(title = { Text("The Funky Wack") }) },
                    bottomBar = {
                        if (isPlaying || nowTitle != null) {
                            NowPlayingBar(
                                title = nowTitle ?: "Playing",
                                onPlayPause = { playerViewModel.playPause() }
                            )
                        }
                    }
                ) { innerPadding ->
                    EditionList(
                        editions = editions,
                        onPlayClicked = { url, title, artist -> playerViewModel.playUrl(url, title, artist) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
private fun EditionList(
    editions: List<EditionWithContent>,
    onPlayClicked: (url: String, title: String?, artist: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp)) {
        items(editions) { edition ->
            Text(text = "Edition #${edition.edition.number}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.padding(vertical = 4.dp))
            edition.livesets.forEach { lwd ->
                val ls = lwd.liveset
                val url = listOfNotNull(ls.losslessUrl, ls.hqUrl, ls.lqUrl).firstOrNull()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(text = ls.title, style = MaterialTheme.typography.bodyLarge)
                        Text(text = ls.artistName, style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(enabled = url != null, onClick = { if (url != null) onPlayClicked(url, ls.title, ls.artistName) }) {
                        Text("Play")
                    }
                }
            }
            Spacer(Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun NowPlayingBar(title: String, onPlayPause: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = onPlayPause) { Text("⏯") }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewList() {
    TheFunkyWackTheme {
        NowPlayingBar(title = "Sample Track", onPlayPause = {})
    }
}