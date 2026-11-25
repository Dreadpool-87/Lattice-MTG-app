package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.local.DeckWithEntries
import com.example.data.local.VaultCollectionCard
import com.example.domain.OwnedDeckCandidates
import com.example.domain.OwnedDeckSwap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val colorLabels = mapOf("W" to "White", "U" to "Blue", "B" to "Black", "R" to "Red", "G" to "Green")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OracleOwnedCandidatesDialog(
    deck: DeckWithEntries,
    collections: Map<String, List<VaultCollectionCard>>,
    onDismiss: () -> Unit
) {
    val verifiedColors = remember(deck) { OwnedDeckCandidates.deckColors(deck) }
    var selectedCollections by remember { mutableStateOf(emptySet<String>()) }
    var selectedColors by remember(deck) {
        mutableStateOf(verifiedColors ?: OwnedDeckCandidates.provisionalColors(deck))
    }
    var includeColorless by remember { mutableStateOf(true) }
    var showResult by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Find a replacement") },
        text = {
            if (showResult) {
                OwnedSwapResult(deck, collections, selectedCollections, selectedColors, includeColorless, verifiedColors == null)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Text(deck.deck.name, style = MaterialTheme.typography.titleMedium) }
                    if (collections.isEmpty()) {
                        item { Text("Import a collection in Vault → Collections.") }
                    } else {
                        item { Text("Select the collections to search.") }
                        items(collections.keys.sortedBy { it.lowercase() }) { name ->
                            FilterChip(
                                selected = name in selectedCollections,
                                onClick = {
                                    selectedCollections = if (name in selectedCollections) {
                                        selectedCollections - name
                                    } else selectedCollections + name
                                },
                                label = { Text(name) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    item {
                        Text("Color identity")
                        if (verifiedColors == null) {
                            Text("Deck color identity could not be verified. Pick the colors to search. Format legality remains unverified.")
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            colorLabels.filterKeys { verifiedColors == null || it in verifiedColors }.forEach { (symbol, label) ->
                                FilterChip(
                                    selected = symbol in selectedColors,
                                    onClick = {
                                        selectedColors = if (symbol in selectedColors) {
                                            selectedColors - symbol
                                        } else selectedColors + symbol
                                    },
                                    label = { Text(label) }
                                )
                            }
                            FilterChip(
                                selected = includeColorless,
                                onClick = { includeColorless = !includeColorless },
                                label = { Text("Colorless") }
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = if (showResult) ({ showResult = false }) else onDismiss) {
                Text(if (showResult) "Back to filters" else "Close")
            }
        },
        confirmButton = {
            if (!showResult) {
                TextButton(
                    onClick = { showResult = true },
                    enabled = selectedCollections.any(collections::containsKey) &&
                        (selectedColors.isNotEmpty() || includeColorless)
                ) { Text("Find replacement") }
            }
        }
    )
}

@Composable
private fun OwnedSwapResult(
    deck: DeckWithEntries,
    collections: Map<String, List<VaultCollectionCard>>,
    selectedCollections: Set<String>,
    selectedColors: Set<String>,
    includeColorless: Boolean,
    provisionalColors: Boolean
) {
    val result by produceState<Result<OwnedDeckSwap?>?>(null, deck, collections) {
        value = withContext(Dispatchers.Default) {
            Result.success(
                OwnedDeckCandidates.bestSwap(
                    deck, selectedCollections.flatMap { collections[it].orEmpty() }, selectedColors, includeColorless
                )
            )
        }
    }
    val swap = result?.getOrNull()
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (result == null) {
            item { Text("Checking your deck and selected collections…") }
        } else if (swap == null) {
            item { Text("No clear one-card upgrade found in the selected collections and colors.") }
        } else {
            item {
                Text("Remove one: ${swap.remove.name}", style = MaterialTheme.typography.titleMedium)
                Text("Add one: ${swap.add.card.name}", style = MaterialTheme.typography.titleMedium)
                Text("${swap.add.availableCopies} owned outside this deck")
            }
            item {
                Text("Better-supported deck tags: ${swap.additionalTags.joinToString(" · ")}")
                Text("Same card types. Mana value: ${swap.remove.cmc} → ${swap.add.card.cmc}.")
                Text(swap.add.card.oracleText)
                if (provisionalColors) Text("Provisional result based on the selected colors. Deck legality is unverified.")
            }
        }
    }
}
