package com.example.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.ArtworkQuality
import com.example.data.repository.ArtworkOption
import com.example.data.repository.ArtworkSelection
import com.example.data.repository.OfflineArtworkRepository
import kotlinx.coroutines.CancellationException

private enum class ArtworkScope(val label: String) {
    COLLECTION("My collection"), DECKS("My decks"), SETS("Choose sets"), ALL("All cards")
}

@Composable
internal fun OfflineArtworkSettings(repository: OfflineArtworkRepository, offlineOnly: Boolean) {
    val state by repository.state.collectAsStateWithLifecycle()
    var showDownload by remember { mutableStateOf(false) }

    SettingsSectionCard(title = "Offline card images", icon = Icons.Filled.FileDownload) {
        Text("Save card images on this device for use in card details.", style = MaterialTheme.typography.bodySmall)
        if (state.downloading) {
            Text("${state.checked} checked · ${state.onDevice} images available offline",
                style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(
            onClick = { showDownload = true },
            modifier = Modifier.fillMaxWidth().testTag("open_offline_artwork")
        ) { Text(if (state.busy) "View image download" else "Choose images to download") }
    }

    if (showDownload) OfflineArtworkDialog(repository, offlineOnly) { showDownload = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OfflineArtworkDialog(
    repository: OfflineArtworkRepository,
    offlineOnly: Boolean,
    onDismiss: () -> Unit
) {
    val state by repository.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val previous = remember { repository.state.value.selection }
    var scope by remember {
        mutableStateOf(when (previous) {
            is ArtworkSelection.Decks -> ArtworkScope.DECKS
            is ArtworkSelection.Sets -> ArtworkScope.SETS
            ArtworkSelection.AllCards -> ArtworkScope.ALL
            else -> ArtworkScope.COLLECTION
        })
    }
    var selected by remember {
        mutableStateOf(when (previous) {
            is ArtworkSelection.Decks -> previous.ids.map(Long::toString).toSet()
            is ArtworkSelection.Sets -> previous.codes
            else -> emptySet()
        })
    }
    var quality by remember { mutableStateOf(repository.state.value.quality) }
    val allCardsReady by repository.allCardsReady.collectAsStateWithLifecycle(initialValue = false)
    LaunchedEffect(offlineOnly) {
        if (!offlineOnly) repository.checkAvailability()
    }
    var query by remember { mutableStateOf("") }
    var options by remember { mutableStateOf<List<ArtworkOption>>(emptyList()) }
    var loadingOptions by remember { mutableStateOf(false) }
    var optionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(scope) {
        if (scope != ArtworkScope.DECKS && scope != ArtworkScope.SETS) return@LaunchedEffect
        loadingOptions = true
        try {
            options = if (scope == ArtworkScope.DECKS) repository.deckOptions() else repository.setOptions()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            optionError = "Could not load the available choices."
        } finally {
            loadingOptions = false
        }
    }

    val selection = when (scope) {
        ArtworkScope.COLLECTION -> ArtworkSelection.Collection
        ArtworkScope.DECKS -> ArtworkSelection.Decks(selected.mapNotNull(String::toLongOrNull).toSet())
            .takeIf { it.ids.isNotEmpty() }
        ArtworkScope.SETS -> ArtworkSelection.Sets(selected).takeIf { it.codes.isNotEmpty() }
        ArtworkScope.ALL -> ArtworkSelection.AllCards.takeIf { allCardsReady }
    }
    val visibleOptions = remember(options, query) { options.filter { it.label.contains(query.trim(), ignoreCase = true) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download card images") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ArtworkScope.entries.forEach { value ->
                        FilterChip(
                            selected = scope == value,
                            enabled = !state.busy && (value != ArtworkScope.ALL || allCardsReady),
                            onClick = {
                                if (scope != value) {
                                    scope = value
                                    selected = emptySet()
                                    options = emptyList()
                                    optionError = null
                                    query = ""
                                    repository.resetSelection()
                                }
                            },
                            label = { Text(value.label) }
                        )
                    }
                }

                if (!allCardsReady) {
                    Text("All cards requires the full offline card catalog.", style = MaterialTheme.typography.bodySmall)
                }

                when (scope) {
                    ArtworkScope.DECKS, ArtworkScope.SETS -> {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            enabled = !state.busy,
                            label = { Text(if (scope == ArtworkScope.DECKS) "Find a deck" else "Find a set") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        when {
                            loadingOptions -> Text("Loading choices…")
                            optionError != null -> Text(optionError.orEmpty(), color = MaterialTheme.colorScheme.error)
                            scope == ArtworkScope.SETS && query.isBlank() -> Unit
                            visibleOptions.isEmpty() -> Text("No matches.")
                            else -> LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                                items(visibleOptions, key = { it.id }) { choice ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = choice.id in selected,
                                            enabled = !state.busy,
                                            onCheckedChange = { checked ->
                                                selected = if (checked) selected + choice.id else selected - choice.id
                                                repository.resetSelection()
                                            }
                                        )
                                        Text(choice.label, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                    ArtworkScope.ALL -> Text(
                        "One representative printing per card. This selection can require several GB.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    ArtworkScope.COLLECTION -> Unit
                }

                Text("Image quality", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ArtworkQuality.entries.forEach { value ->
                        FilterChip(
                            selected = quality == value,
                            enabled = !state.busy,
                            onClick = {
                                if (quality != value) {
                                    quality = value
                                    repository.resetSelection()
                                }
                            },
                            label = { Text(value.label) }
                        )
                    }
                }

                if (scope == ArtworkScope.SETS || scope == ArtworkScope.ALL) {
                    Text("Checking size may install matching offline card data. Images download after confirmation.",
                        style = MaterialTheme.typography.bodySmall)
                }
                if (offlineOnly) Text("Turn off offline-only mode before downloading.", color = MaterialTheme.colorScheme.error)

                state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (state.downloading) {
                    LinearProgressIndicator(
                        progress = { (state.checked.toFloat() / state.remaining.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (state.total > 0) {
                    Text("${state.onDevice} of ${state.total} images on this device", style = MaterialTheme.typography.bodySmall)
                    if (!state.busy && state.remaining > 0) {
                        val estimate = Formatter.formatShortFileSize(context, state.estimatedBytes)
                        val free = Formatter.formatShortFileSize(context, state.freeBytes)
                        Text(
                            "${state.remaining} images to download · about $estimate · $free free",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (state.unavailable > 0) {
                    Text("${state.unavailable} selections had no available image.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            when {
                state.busy -> TextButton(onClick = { repository.stop() }) { Text("Stop") }
                state.remaining > 0 -> TextButton(onClick = repository::download, enabled = !offlineOnly) {
                    Text("Download images")
                }
                else -> TextButton(
                    enabled = selection != null && !loadingOptions && !offlineOnly,
                    onClick = { selection?.let { repository.prepare(it, quality) } }
                ) { Text("Check size") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
