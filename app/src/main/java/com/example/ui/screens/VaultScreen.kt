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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.CardEntity
import com.example.ui.components.CardInspectorSheet
import com.example.ui.components.ManaSymbolPip
import com.example.ui.viewmodel.VaultViewModel

private val cardTypeFilters = listOf("All", "Creature", "Instant", "Sorcery", "Artifact", "Enchantment", "Planeswalker", "Land")
private val colorFilters = listOf("W", "U", "B", "R", "G", "C")

@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenCatalog: () -> Unit = {},
    onBackFromCatalog: (() -> Unit)? = null,
    newerCardData: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val showCollections = onBackFromCatalog == null
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    val inspectionExtras by viewModel.inspectionExtras.collectAsStateWithLifecycle()
    var inspectedCard by remember { mutableStateOf<CardEntity?>(null) }
    var showCreateCardDialog by remember { mutableStateOf(false) }
    var showCsvImportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.statusBanner) {
        val msg = uiState.statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    LaunchedEffect(inspectedCard?.id) {
        inspectedCard?.let { viewModel.loadCardInspectionExtras(it) }
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
                        onBackFromCatalog?.let {
                            IconButton(onClick = it) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to collections")
                            }
                        }
                        Text(
                            text = if (showCollections) "My collection" else "Card catalog",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("open_settings_button_vault")
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_open_settings))
                        }
                    }

                    if (showCollections) {
                        if (newerCardData) {
                            TextButton(onClick = onOpenSettings) {
                                Text("New card data available")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showCsvImportDialog = true },
                                modifier = Modifier.testTag("open_csv_import_button")
                            ) { Text("Import CSV") }
                            OutlinedButton(onClick = onOpenCatalog) { Text("Browse catalog") }
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = uiState.query,
                                onValueChange = viewModel::setQuery,
                                placeholder = { Text("Search catalog") },
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
                                contentPadding = PaddingValues(horizontal = 10.dp),
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
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sync", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${uiState.cards.size} of ${uiState.totalVaultCount} local cards",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("vault_local_cache_notice")
                            )
                            val active = uiState.selectedColors.size + listOf(
                                uiState.selectedType != "All", uiState.selectedRarity != "All",
                                uiState.bookmarkedOnly, uiState.maxCmcFilter != null
                            ).count { it }
                            FilterChip(
                                selected = filtersExpanded,
                                onClick = { filtersExpanded = !filtersExpanded },
                                label = { Text(if (active == 0) "Filters" else "Filters ($active)") },
                                modifier = Modifier.testTag("vault_catalog_filters")
                            )
                        }
                        if (filtersExpanded) {
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
                            OutlinedButton(onClick = viewModel::clearFilters) {
                                Text("Reset filters")
                            }
                        }
                    }
                }
            }

            if (showCollections) {
                VaultCollections(collections, onOpenCard = { inspectedCard = it })
            } else if (uiState.cards.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No matching cards",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Clear filters, search Scryfall with Sync, or download card data from Settings.",
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
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
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

        if (!showCollections) {
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
    }

    inspectedCard?.let { card ->
        val liveCard = uiState.cards.firstOrNull { it.id == card.id } ?: card
        val extrasForCard = if (inspectionExtras.cardId == liveCard.id) inspectionExtras else null
        CardInspectorSheet(
            card = liveCard,
            decks = uiState.decks,
            isOnline = uiState.isOnline && !uiState.offlineOnlyMode && uiState.showCardArt,
            onDismiss = { inspectedCard = null },
            onToggleBookmark = { viewModel.toggleBookmark(liveCard) },
            onAddToDeck = { deckId, zone -> viewModel.addCardToDeck(deckId, liveCard.id, zone) },
            onDeleteCustomCard = if (liveCard.isCustom) {
                { viewModel.deleteCustomCard(liveCard.id) }
            } else null,
            printingVariants = extrasForCard?.printingVariants.orEmpty(),
            rulings = extrasForCard?.rulings.orEmpty(),
            isLoadingRulings = extrasForCard?.isLoadingRulings == true
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

    if (showCsvImportDialog) {
        VaultCsvImportDialog(
            isImporting = uiState.isImportingCsv,
            report = uiState.csvImportReport,
            collectionNames = collections.keys,
            onImportCsv = { text, deckName, format, name, replace ->
                viewModel.importDelverCsv(text, deckName, format, name, replace)
            },
            onClearReport = viewModel::clearCsvImportReport,
            onDismiss = { showCsvImportDialog = false }
        )
    }
}
