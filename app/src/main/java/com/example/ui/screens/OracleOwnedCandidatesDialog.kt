package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.example.data.local.CardEntity
import com.example.data.local.DeckWithEntries
import com.example.data.local.VaultCollectionCard
import com.example.domain.OwnedDeckCandidate
import com.example.domain.OwnedDeckCandidates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class CollectionReviewPage { Filters, Reference, Results }

private val colorLabels = mapOf("W" to "Wit", "U" to "Blauw", "B" to "Zwart", "R" to "Rood", "G" to "Groen")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OracleOwnedCandidatesDialog(
    deck: DeckWithEntries,
    collections: Map<String, List<VaultCollectionCard>>,
    onDismiss: () -> Unit
) {
    val deckColors = remember(deck) { OwnedDeckCandidates.deckColors(deck) }
    var selectedCollections by remember { mutableStateOf(emptySet<String>()) }
    var selectedColors by remember(deckColors) { mutableStateOf(deckColors.orEmpty()) }
    var includeColorless by remember { mutableStateOf(true) }
    var reference by remember { mutableStateOf<CardEntity?>(null) }
    var page by remember { mutableStateOf(CollectionReviewPage.Filters) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kandidaten uit mijn collectie") },
        text = {
            when (page) {
                CollectionReviewPage.Filters -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Text(deck.deck.name, style = MaterialTheme.typography.titleMedium) }
                    if (collections.isEmpty()) {
                        item { Text("Importeer eerst een collectie in Vault → Collections.") }
                    } else {
                        item { Text("Vink de collecties aan die je wilt doorzoeken.") }
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
                        if (deckColors == null) {
                            Text("Er zijn onvoldoende geverifieerde kaartgegevens om de deckkleuren te bepalen.")
                        } else {
                            Text("Kleuridentiteit")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                colorLabels.filterKeys { it in deckColors }.forEach { (symbol, label) ->
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
                                    label = { Text("Kleurloos") }
                                )
                            }
                        }
                    }
                    item {
                        TextButton(onClick = { page = CollectionReviewPage.Reference }) {
                            Text(reference?.let { "Referentiekaart: ${it.name}" } ?: "Kies een referentiekaart")
                        }
                        if (reference != null) {
                            TextButton(onClick = { reference = null }) { Text("Wis referentiekaart") }
                        }
                    }
                }
                CollectionReviewPage.Reference -> ReferenceCardPicker(
                    deck = deck,
                    inventory = collections,
                    selectedCollections = selectedCollections,
                    onSelect = { reference = it; page = CollectionReviewPage.Filters }
                )
                CollectionReviewPage.Results -> OwnedCandidateResults(
                    deck = deck,
                    collections = collections,
                    selectedCollections = selectedCollections,
                    selectedColors = selectedColors,
                    includeColorless = includeColorless,
                    reference = reference
                )
            }
        },
        dismissButton = {
            TextButton(onClick = if (page == CollectionReviewPage.Filters) onDismiss else {
                { page = CollectionReviewPage.Filters }
            }) { Text(if (page == CollectionReviewPage.Filters) "Sluiten" else "Terug naar filters") }
        },
        confirmButton = {
            if (page == CollectionReviewPage.Filters) {
                TextButton(
                    onClick = { page = CollectionReviewPage.Results },
                    enabled = deckColors != null && selectedCollections.any(collections::containsKey) &&
                        (selectedColors.isNotEmpty() || includeColorless)
                ) { Text("Toon kandidaten") }
            }
        }
    )
}

@Composable
private fun ReferenceCardPicker(
    deck: DeckWithEntries,
    inventory: Map<String, List<VaultCollectionCard>>,
    selectedCollections: Set<String>,
    onSelect: (CardEntity) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val cards by produceState(emptyList<CardEntity>(), deck, inventory, selectedCollections) {
        value = withContext(Dispatchers.Default) {
            (deck.entries.map { it.card } + selectedCollections.flatMap { name -> inventory[name].orEmpty().map { it.card } })
                .distinctBy { it.oracleId.ifBlank { it.id } }.sortedBy { it.name.lowercase() }
        }
    }
    val matches = remember(cards, query) {
        if (query.isBlank()) cards else cards.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Zoek in deck of aangevinkte collecties") },
                singleLine = true
            )
        }
        items(matches, key = { it.id }) { card ->
            TextButton(onClick = { onSelect(card) }) { Text(card.name) }
        }
    }
}

@Composable
private fun OwnedCandidateResults(
    deck: DeckWithEntries,
    collections: Map<String, List<VaultCollectionCard>>,
    selectedCollections: Set<String>,
    selectedColors: Set<String>,
    includeColorless: Boolean,
    reference: CardEntity?
) {
    val candidates by produceState<List<OwnedDeckCandidate>?>(null, collections, selectedCollections) {
        value = withContext(Dispatchers.Default) {
            OwnedDeckCandidates.find(
                deck, selectedCollections.flatMap { collections[it].orEmpty() }, selectedColors, includeColorless, reference
            )
        }
    }
    val results = candidates
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            reference?.let { Text("Referentiekaart: ${it.name}") }
            Text("Per druk geldt het hoogste aantal uit de gekozen collecties. Exemplaren in dit deck zijn afgetrokken. Gedeelde synergietags bepalen de volgorde.")
        }
        when {
            results == null -> item { Text("Collecties controleren…") }
            results.isEmpty() -> item { Text("Geen beschikbare kandidaten binnen deze filters.") }
            else -> items(results, key = { it.card.oracleId }) { candidate ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val card = candidate.card
                        Text(card.name, style = MaterialTheme.typography.titleMedium)
                        Text(listOf(card.manaCost, card.typeLine).filter { it.isNotBlank() }.joinToString(" · "))
                        Text("${candidate.availableCopies} in collectie buiten dit deck")
                        if (candidate.referenceTags.isNotEmpty()) {
                            Text("Match met referentie: ${candidate.referenceTags.joinToString(" · ")}")
                        }
                        if (candidate.sharedTags.isNotEmpty()) Text(candidate.sharedTags.joinToString(" · "))
                        if (card.oracleText.isNotBlank()) Text(card.oracleText)
                    }
                }
            }
        }
    }
}
