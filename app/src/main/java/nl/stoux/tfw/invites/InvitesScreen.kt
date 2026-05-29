package nl.stoux.tfw.invites

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import nl.stoux.tfw.core.common.network.dto.auth.InviteDto

@Composable
fun InvitesScreen(
    viewModel: InvitesViewModel = hiltViewModel(),
) {
    val invites by viewModel.invites.collectAsState()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Invite codes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = { viewModel.createInvite() }) { Text("New") }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(invites, key = { it.id }) { invite ->
                InviteCard(invite = invite, onCopy = { copyToClipboard(context, it) }, onShare = { share(context, it) })
            }
        }
    }
}

@Composable
private fun InviteCard(invite: InviteDto, onCopy: (String) -> Unit, onShare: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(invite.code, style = MaterialTheme.typography.titleMedium)
            invite.usedBy?.let {
                Text("Used by ${it.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } ?: Text("Unused", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { onCopy(invite.code) }) { Text("Copy") }
                if (invite.usedBy == null) {
                    TextButton(onClick = { onShare(invite.code) }) { Text("Share") }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("TFW invite", code))
}

private fun share(context: Context, code: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Join The Funky Wack with this invite code: $code")
    }
    context.startActivity(Intent.createChooser(intent, "Share invite code"))
}
