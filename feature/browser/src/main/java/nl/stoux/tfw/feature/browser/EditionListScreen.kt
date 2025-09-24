package nl.stoux.tfw.feature.browser

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.stoux.tfw.core.common.database.dao.EditionWithContent

@Composable
fun EditionListScreen(
    viewModel: EditionListViewModel,
    modifier: Modifier = Modifier,
    onPlayClicked: (url: String, title: String?, artist: String?) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val editions by viewModel.editions.collectAsState()
    EditionList(
        editions = editions,
        onPlayClicked = onPlayClicked,
        modifier = modifier
    )
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
