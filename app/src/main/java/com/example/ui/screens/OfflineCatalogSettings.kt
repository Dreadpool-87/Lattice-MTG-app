package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.text.format.Formatter
import com.example.data.repository.OfflineCatalogPack
import com.example.data.repository.OfflineCatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun OfflineCatalogSettings(repository: OfflineCatalogRepository, offlineOnly: Boolean) {
    val context = LocalContext.current
    val manifest by repository.manifest.collectAsStateWithLifecycle()
    val installed by repository.installed.collectAsStateWithLifecycle(initialValue = emptyMap())
    val downloading by repository.downloading.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var showSets by rememberSaveable { mutableStateOf(false) }
    var confirmFullCatalog by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    fun download(pack: OfflineCatalogPack) {
        showSets = false
        message = null
        scope.launch {
            try {
                repository.download(pack)
                message = "${pack.name} is available offline."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message = "Download failed. Check for card data updates and try again."
            }
        }
    }

    SettingsSectionCard("Offline card data", Icons.Filled.CloudSync) {
        val available = manifest
        val full = available?.packs?.first()
        Text(
            when {
                offlineOnly -> "Turn off offline-only mode to check or download."
                available == null -> "Download card data from Lattice to browse offline."
                available.packs.any { it.id in installed && installed[it.id] != it.sha256 } ->
                    "New card data available · ${available.snapshot}"
                else -> "Lattice catalog · ${available.snapshot}"
            },
            style = MaterialTheme.typography.bodyMedium
        )
        if (downloading != null || checking) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(
            enabled = !offlineOnly && !checking && downloading == null,
            onClick = {
                checking = true
                message = null
                scope.launch {
                    try {
                        val latest = repository.check()
                        if (latest != null && installed.isNotEmpty() &&
                            latest.packs.none { it.id in installed && installed[it.id] != it.sha256 }) {
                            message = "Your downloaded card data is up to date."
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        message = "Could not check for card data. Try again later."
                    } finally {
                        checking = false
                    }
                }
            }
        ) { Text("Check for card data updates") }
        if (full != null) {
            val current = installed[full.id] == full.sha256
            val action = if (full.id in installed) "Update all cards" else "Download all cards"
            Button(
                enabled = !offlineOnly && !checking && downloading == null && !current,
                onClick = { confirmFullCatalog = true }
            ) {
                Text(if (current) "All cards downloaded" else "$action · ${Formatter.formatShortFileSize(context, full.bytes)}")
            }
            OutlinedButton(
                enabled = !offlineOnly && !checking && downloading == null,
                onClick = { query = ""; showSets = true }
            ) { Text("Choose a set") }
            Text(
                "All cards includes one printing per card. Set downloads include individual collector numbers. Card images are loaded separately.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    val fullToConfirm = manifest?.packs?.firstOrNull()
    if (confirmFullCatalog && fullToConfirm != null) {
        val size = Formatter.formatShortFileSize(context, fullToConfirm.bytes)
        val action = if (fullToConfirm.id in installed) "Update" else "Download"
        AlertDialog(
            onDismissRequest = { confirmFullCatalog = false },
            title = { Text("$action all card data?") },
            text = {
                Text("This downloads $size of compressed card data for ${fullToConfirm.count} cards. " +
                    "The installed catalog takes up more space. Card images are downloaded separately.")
            },
            confirmButton = {
                TextButton(
                    enabled = !offlineOnly && downloading == null &&
                        installed[fullToConfirm.id] != fullToConfirm.sha256,
                    onClick = {
                        confirmFullCatalog = false
                        download(fullToConfirm)
                    }
                ) { Text("$action · $size") }
            },
            dismissButton = {
                TextButton(onClick = { confirmFullCatalog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSets) {
        val sets = manifest?.packs.orEmpty().filter { it.id.startsWith("set-") }
        val matching = sets.filter { it.name.contains(query.trim(), true) || it.id.removePrefix("set-").contains(query.trim(), true) }
        AlertDialog(
            onDismissRequest = { showSets = false },
            title = { Text("Download a set") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(query, { query = it }, label = { Text("Set name or code") }, singleLine = true)
                    if (matching.isEmpty()) Text("No sets match.")
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(matching, key = { it.id }) { pack ->
                            val current = installed[pack.id] == pack.sha256
                            ListItem(
                                headlineContent = { Text(pack.name) },
                                supportingContent = {
                                    Text("${pack.id.removePrefix("set-").uppercase()} · ${pack.count} printings · ${Formatter.formatShortFileSize(context, pack.bytes)}")
                                },
                                trailingContent = {
                                    TextButton(enabled = !current && !offlineOnly, onClick = { download(pack) }) {
                                        Text(if (current) "Saved" else if (pack.id in installed) "Update" else "Get")
                                    }
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSets = false }) { Text("Close") } }
        )
    }
}
