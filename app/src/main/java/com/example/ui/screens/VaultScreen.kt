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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.MainTabHeader
import com.example.data.local.CardEntity
import com.example.ui.components.CardInspectorSheet
import com.example.data.repository.OfflineArtworkRepository
import com.example.ui.components.ManaSymbolPip
import com.example.ui.viewmodel.VaultViewModel
import com.example.ui.viewmodel.DecksViewModel
import com.example.PreviewLog
import com.example.domain.VaultListCard

internal val cardTypeFilters = listOf("All", "Creature", "Instant", "Sorcery", "Artifact", "Enchantment", "Planeswalker", "Land")
private val colorFilters = listOf("W", "U", "B", "R", "G", "C")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    decksViewModel: DecksViewModel? = null,
    onOpenDeck: (Long) -> Unit = {},
    newerCardData: Boolean = false,
    offlineArtwork: OfflineArtworkRepository? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val tabs = if (decksViewModel != null) listOf("Collections", "Decks", "Catalog") else listOf("Collections", "Catalog")
    var selectedTab by rememberSaveable { mutableStateOf("Collections") }
    val showCollections = selectedTab == "Collections"
    val showCatalog = selectedTab == "Catalog"
    val showDecks = selectedTab == "Decks" && decksViewModel != null
    DisposableEffect(viewModel, showCatalog) {
        viewModel.setCatalogOpen(showCatalog)
        onDispose { viewModel.setCatalogOpen(false) }
    }
    LaunchedEffect(selectedTab) { PreviewLog.record("Vault", "Opened $selectedTab.") }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    var inspectedCard by remember { mutableStateOf<Pair<List<CardEntity>, CardEntity>?>(null) }
    var showCreateCardDialog by remember { mutableStateOf(false) }
    var showCsvImportDialog by remember { mutableStateOf(false) }
    var showDeckCsvImport by remember { mutableStateOf(false) }
    var showCreateDeck by remember { mutableStateOf(false) }

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
                .widthIn(max = 960.dp)
                .fillMaxSize()
        ) {
            MainTabHeader(
                title = stringResource(R.string.nav_vault_compact),
                onOpenSettings = onOpenSettings,
                settingsTag = "open_settings_button_vault",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            PrimaryTabRow(selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)) {
                tabs.forEach { tab ->
                    Tab(selected = selectedTab == tab, onClick = { selectedTab = tab }, text = { Text(tab) })
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
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
                    }
                } else if (showDecks) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showCreateDeck = true }) { Text("New deck") }
                        OutlinedButton(onClick = { decksViewModel.clearCsvImport(); showDeckCsvImport = true }) {
                            Text("Import CSV")
                        }
                    }
                } else if (showCatalog) {
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
                            uiState.bookmarkedOnly, uiState.maxCmcFilter != null, uiState.multiColorOnly
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
                            FilterChip(
                                selected = uiState.multiColorOnly,
                                onClick = viewModel::toggleMultiColorOnly,
                                label = { Text("Multicolor") },
                                modifier = Modifier.testTag("vault_multicolor_filter")
                            )
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
                            TextButton(onClick = viewModel::clearFilters) { Text("Reset filters") }
                        }
                    }
                }
            }

            if (showCollections) {
                val lists = remember(collections) {
                    collections.map { (name, cards) ->
                        VaultCardList(name, name, cards.map { VaultListCard(it.card, it.entry.quantity.toLong()) })
                    }
                }
                VaultCardLists(lists, "Import a CSV to add a collection.",
                    onOpenCard = { cards, card -> inspectedCard = cards to card }, offlineArtwork = offlineArtwork)
            } else if (showDecks) {
                val lists = remember(uiState.decks) {
                    uiState.decks.map { deck ->
                        val cards = deck.entries.groupBy { it.card.id }.values.map { copies ->
                            VaultListCard(copies.first().card, copies.sumOf { it.entry.quantity.toLong() })
                        }
                        VaultCardList(deck.deck.id.toString(), deck.deck.name, cards, deck.deck.format, deck.deck.id)
                    }
                }
                VaultCardLists(lists, "Create a deck or import a deck CSV.",
                    onOpenCard = { cards, card -> inspectedCard = cards to card },
                    offlineArtwork = offlineArtwork, onEditDeck = onOpenDeck)
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
                            offlineArtwork = offlineArtwork,
                            onClick = { inspectedCard = uiState.cards to card },
                            onToggleBookmark = { viewModel.toggleBookmark(card) }
                        )
                    }
                    if (uiState.hasMoreCards) {
                        item(key = "more-catalog-cards") {
                            OutlinedButton(onClick = viewModel::loadMoreCatalogCards) {
                                Text("Load more cards")
                            }
                        }
                    }
                }
            }
        }

        if (showCatalog) {
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

    inspectedCard?.let { (cards, card) ->
        CatalogCardInspector(viewModel, card, onDismiss = { inspectedCard = null }, cards = cards,
            allowAddToDeck = true, offlineArtwork = offlineArtwork)
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

    if (showDecks) {
        if (showCreateDeck) {
            CreateDeckDialog(
                onDismiss = { showCreateDeck = false },
                onCreate = { name, format, description, colors ->
                    showCreateDeck = false
                    decksViewModel.createDeck(name, format, description, colors, onOpenDeck)
                }
            )
        }
        if (showDeckCsvImport) {
            val imported by decksViewModel.csvImport.collectAsStateWithLifecycle()
            CsvImportDialog(
                isImporting = imported.isImporting,
                report = imported.report,
                forDeck = true,
                importError = imported.error,
                onImportCsv = { csv, name, format, _ ->
                    decksViewModel.importDeckCsv(name, format, csv) { showDeckCsvImport = false }
                },
                onClearReport = decksViewModel::clearCsvImport,
                onDismiss = { showDeckCsvImport = false }
            )
        }
    }

    if (showCollections && showCsvImportDialog) {
        val csvImportError by viewModel.csvImportError.collectAsStateWithLifecycle()
        CsvImportDialog(
            isImporting = uiState.isImportingCsv,
            report = uiState.csvImportReport,
            collectionNames = collections.keys,
            importError = csvImportError,
            onImportCsv = { text, name, _, replace ->
                viewModel.importCollectionCsv(text, name, replace)
            },
            onClearReport = viewModel::clearCsvImportReport,
            onDismiss = { showCsvImportDialog = false }
        )
    }
}


@Composable
internal fun CatalogCardInspector(
    viewModel: VaultViewModel,
    initialCard: CardEntity,
    onDismiss: () -> Unit,
    cards: List<CardEntity> = listOf(initialCard),
    allowAddToDeck: Boolean,
    offlineArtwork: OfflineArtworkRepository? = null
) {
    var index by remember(initialCard.id, cards) {
        mutableStateOf(cards.indexOfFirst { it.id == initialCard.id })
    }
    val card = cards.getOrNull(index) ?: initialCard
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val extras by viewModel.inspectionExtras.collectAsStateWithLifecycle()
    val currentCard by remember(viewModel, card.id) { viewModel.observeCard(card.id) }
        .collectAsStateWithLifecycle(initialValue = card)
    val liveCard = currentCard ?: card
    val current = extras.takeIf { it.cardId == card.id }
    val art = liveCard.imageUri ?: current?.imageUri
    val context = LocalContext.current
    LaunchedEffect(card.id, state.showCardArt, state.isOnline, state.offlineOnlyMode) {
        val localArt = OfflineArtworkRepository.localFile(context, liveCard.scryfallId)
        viewModel.loadCardInspectionExtras(liveCard, fetchArtwork = localArt == null)
    }
    CardInspectorSheet(
        card = liveCard.copy(imageUri = art, cardFacesJson = current?.artworkFacesJson ?: liveCard.cardFacesJson),
        decks = state.decks,
        isOnline = state.isOnline && !state.offlineOnlyMode,
        showCardArt = state.showCardArt,
        offlineArtwork = offlineArtwork,
        onDismiss = {
            viewModel.cancelCardInspection()
            onDismiss()
        },
        onToggleBookmark = { viewModel.toggleBookmark(liveCard) },
        onAddToDeck = if (allowAddToDeck) {
            { deckId, zone -> viewModel.addCardToDeck(deckId, liveCard.id, zone) }
        } else null,
        onDeleteCustomCard = if (liveCard.isCustom) {
            { viewModel.deleteCustomCard(liveCard.id) }
        } else null,
        printingVariants = current?.printingVariants.orEmpty(),
        rulings = current?.rulings.orEmpty(),
        isLoadingRulings = current?.isLoadingRulings == true,
        onPrevious = if (index > 0) ({ index-- }) else null,
        onNext = if (index >= 0 && index < cards.lastIndex) ({ index++ }) else null
    )
}
