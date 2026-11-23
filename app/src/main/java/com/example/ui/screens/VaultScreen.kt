package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.CardEntity
import com.example.ui.components.CardInspectorSheet
import com.example.ui.components.ManaCostRow
import com.example.ui.components.ManaSymbolPip
import com.example.ui.components.RarityPill
import com.example.ui.components.VaultStatusBadge
import com.example.ui.viewmodel.VaultViewModel

private val cardTypeFilters = listOf("All", "Creature", "Instant", "Sorcery", "Artifact", "Enchantment", "Planeswalker", "Land")
private val colorFilters = listOf("W", "U", "B", "R", "G", "C")

@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var inspectedCard by remember { mutableStateOf<CardEntity?>(null) }
    var showCreateCardDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.statusBanner) {
        val msg = uiState.statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
        ) {
            // Sticky Header + Offline Search + Filter Chips
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "Card Vault",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${uiState.cards.size} of ${uiState.totalVaultCount} cards offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VaultStatusBadge(
                                isOnline = uiState.isOnline,
                                offlineOnlyMode = uiState.offlineOnlyMode,
                                cachedCardCount = uiState.totalVaultCount
                            )
                            IconButton(
                                onClick = onOpenSettings,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("open_settings_button_vault")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = stringResource(R.string.action_open_settings),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = uiState.query,
                            onValueChange = viewModel::setQuery,
                            placeholder = { Text("Search name, oracle text, tag...") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search vault") },
                            trailingIcon = {
                                if (uiState.query.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setQuery("") }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("vault_search_input")
                        )

                        OutlinedButton(
                            onClick = { viewModel.syncFromScryfall() },
                            enabled = !uiState.isSyncing,
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("sync_scryfall_button")
                        ) {
                            if (uiState.isSyncing) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(Icons.Filled.CloudSync, contentDescription = "Sync Scryfall", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    // Color + Bookmark Filters
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        colorFilters.forEach { code ->
                            val selected = code in uiState.selectedColors
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    1.5.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clickable { viewModel.toggleColor(code) }
                                    .testTag("color_filter_$code")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    ManaSymbolPip(symbol = code, size = 20.dp)
                                }
                            }
                        }

                        FilterChip(
                            selected = uiState.bookmarkedOnly,
                            onClick = viewModel::toggleBookmarkedOnly,
                            leadingIcon = {
                                Icon(
                                    if (uiState.bookmarkedOnly) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("Saved") },
                            modifier = Modifier.testTag("filter_bookmarked_chip")
                        )

                        FilterChip(
                            selected = uiState.maxCmcFilter == 2,
                            onClick = { viewModel.setMaxCmc(if (uiState.maxCmcFilter == 2) null else 2) },
                            label = { Text("CMC ≤ 2") },
                            modifier = Modifier.testTag("filter_cmc2_chip")
                        )
                    }

                    // Type Filters
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        cardTypeFilters.forEach { type ->
                            FilterChip(
                                selected = uiState.selectedType == type,
                                onClick = { viewModel.setTypeFilter(type) },
                                label = { Text(type) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier.testTag("type_filter_${type.lowercase()}")
                            )
                        }
                    }
                }
            }

            if (uiState.cards.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No matching cards in offline Room vault",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Clear filters, create a custom card, or tap Sync when online to cache Scryfall cards locally.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(onClick = viewModel::clearFilters) {
                            Text("Reset Filters")
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.cards, key = { it.id }) { card ->
                        VaultCardRow(
                            card = card,
                            onClick = { inspectedCard = card },
                            onToggleBookmark = { viewModel.toggleBookmark(card) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateCardDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("create_custom_card_fab")
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_create_card))
        }
    }

    inspectedCard?.let { card ->
        // Observe updated bookmark state from uiState.cards if present
        val liveCard = uiState.cards.firstOrNull { it.id == card.id } ?: card
        CardInspectorSheet(
            card = liveCard,
            decks = uiState.decks,
            isOnline = uiState.isOnline && !uiState.offlineOnlyMode && uiState.showCardArt,
            onDismiss = { inspectedCard = null },
            onToggleBookmark = { viewModel.toggleBookmark(liveCard) },
            onAddToDeck = { deckId, zone -> viewModel.addCardToDeck(deckId, liveCard.id, zone) },
            onDeleteCustomCard = if (liveCard.isCustom) {
                { viewModel.deleteCustomCard(liveCard.id) }
            } else null
        )
    }

    if (showCreateCardDialog) {
        CreateCustomCardDialog(
            onDismiss = { showCreateCardDialog = false },
            onSave = { name, mana, cmc, type, oracle, colors, rarity, p, t, tags ->
                showCreateCardDialog = false
                viewModel.createCustomCard(name, mana, cmc, type, oracle, colors, rarity, p, t, tags)
            }
        )
    }
}

@Composable
private fun VaultCardRow(
    card: CardEntity,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("vault_card_${card.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = card.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    ManaCostRow(manaCost = card.manaCost, pipSize = 16.dp)
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = card.typeLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RarityPill(rarity = card.rarity)
                    Text(
                        text = card.setCode,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (card.power != null && card.toughness != null) {
                        Text(
                            text = "• ${card.power}/${card.toughness}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (card.isCustom) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "CUSTOM",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
            IconButton(
                onClick = onToggleBookmark,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("bookmark_card_${card.id}")
            ) {
                Icon(
                    imageVector = if (card.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Save card",
                    tint = if (card.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CreateCustomCardDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Double, String, String, List<String>, String, String?, String?, List<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var manaCost by remember { mutableStateOf("{2}{U}") }
    var cmcText by remember { mutableStateOf("3") }
    var typeLine by remember { mutableStateOf("Artifact Creature — Construct") }
    var oracleText by remember { mutableStateOf("") }
    var power by remember { mutableStateOf("") }
    var toughness by remember { mutableStateOf("") }
    var rarity by remember { mutableStateOf("rare") }
    var colors by remember { mutableStateOf(setOf("U")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Offline Card", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Card Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_card_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manaCost,
                        onValueChange = { manaCost = it },
                        label = { Text("Mana Cost") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cmcText,
                        onValueChange = { cmcText = it },
                        label = { Text("CMC") },
                        singleLine = true,
                        modifier = Modifier.width(80.dp)
                    )
                }
                OutlinedTextField(
                    value = typeLine,
                    onValueChange = { typeLine = it },
                    label = { Text("Type Line") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = oracleText,
                    onValueChange = { oracleText = it },
                    label = { Text("Oracle Text") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = power,
                        onValueChange = { power = it },
                        label = { Text("Power") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = toughness,
                        onValueChange = { toughness = it },
                        label = { Text("Toughness") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("W", "U", "B", "R", "G").forEach { c ->
                        val active = c in colors
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .clickable { colors = if (active) colors - c else colors + c }
                                .padding(6.dp)
                        ) {
                            ManaSymbolPip(symbol = c, size = 20.dp)
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf("common", "uncommon", "rare", "mythic").forEach { r ->
                        FilterChip(
                            selected = rarity == r,
                            onClick = { rarity = r },
                            label = { Text(r.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cmc = cmcText.toDoubleOrNull() ?: 0.0
                    onSave(
                        name,
                        manaCost,
                        cmc,
                        typeLine,
                        oracleText,
                        colors.toList(),
                        rarity,
                        power.ifBlank { null },
                        toughness.ifBlank { null },
                        listOf("Custom")
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_custom_card_button")
            ) {
                Text("Save Card")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
