package nl.stoux.tfw.auth

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import nl.stoux.tfw.core.common.network.dto.auth.PasskeyDto
import nl.stoux.tfw.feature.player.util.formatRelativeIso

@Composable
fun PasskeysSection(
    passkeyCredentials: PasskeyCredentials,
    viewModel: PasskeysViewModel = hiltViewModel(),
) {
    val passkeys by viewModel.passkeys.collectAsState()
    val hasPasskeys by viewModel.hasPasskeys.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val context = LocalContext.current
    var confirmRemove by remember { mutableStateOf<PasskeyDto?>(null) }
    var renaming by remember { mutableStateOf<PasskeyDto?>(null) }

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Text("Passkeys", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.height(8.dp))

    if (hasPasskeys == false) {
        // Suggest one after a password login, like the website does
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Sign in without a password next time: add a passkey, stored in your password manager.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    } else {
        Text(
            "Sign in with your fingerprint or screen lock instead of a password.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        passkeys.forEach { passkey ->
            PasskeyRow(
                passkey = passkey,
                onRename = { renaming = passkey },
                onRemove = { confirmRemove = passkey },
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Button(
        onClick = { viewModel.add(passkeyCredentials) },
        enabled = !isBusy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        } else {
            Text("Add a passkey")
        }
    }

    renaming?.let { passkey ->
        var name by remember(passkey.id) { mutableStateOf(passkey.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename passkey") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(255) },
                    singleLine = true,
                    label = { Text("Name") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.rename(passkey, name)
                        renaming = null
                    },
                    enabled = name.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text("Cancel") }
            },
        )
    }

    confirmRemove?.let { passkey ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove passkey?") },
            text = {
                Text("You can't sign in with \"${passkey.name}\" anymore. Also remove it from your password manager to clean it up there.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(passkey)
                    confirmRemove = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PasskeyRow(passkey: PasskeyDto, onRename: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Rename", onClick = onRename)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(passkey.name, style = MaterialTheme.typography.bodyLarge)
            val details = buildList {
                passkey.authenticator?.let { add(it) }
                val lastUsed = formatRelativeIso(passkey.lastUsedAt)
                val added = formatRelativeIso(passkey.createdAt)
                when {
                    lastUsed != null -> add("used $lastUsed")
                    added != null -> add("added $added")
                }
            }
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = "Remove passkey")
        }
    }
}
