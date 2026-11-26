package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.CardEntity
import com.example.data.local.DeckWithEntries
import com.example.data.local.DeckZones
import com.example.data.local.VaultCollectionCard
import com.example.data.local.VaultCollectionDao
import com.example.data.repository.CardRepository
import com.example.data.repository.DeckRepository
import com.example.data.repository.NetworkMonitor
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.SyncResult
import com.example.domain.CardRulingInfo
import com.example.domain.CsvLookupException
import com.example.domain.DelverImportReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CardInspectionExtras(
    val imageUri: String? = null,
    val artworkFacesJson: String? = null,
    val cardId: String = "",
    val printingVariants: List<CardEntity> = emptyList(),
    val rulings: List<CardRulingInfo> = emptyList(),
    val isLoadingRulings: Boolean = false
)

data class VaultUiState(
    val cards: List<CardEntity> = emptyList(),
    val hasMoreCards: Boolean = false,
    val totalVaultCount: Int = 0,
    val query: String = "",
    val selectedColors: Set<String> = emptySet(),
    val selectedType: String = "All",
    val selectedRarity: String = "All",
    val bookmarkedOnly: Boolean = false,
    val maxCmcFilter: Int? = null,
    val decks: List<DeckWithEntries> = emptyList(),
    val isSyncing: Boolean = false,
    val isImportingCsv: Boolean = false,
    val csvImportReport: DelverImportReport? = null,
    val isOnline: Boolean = false,
    val offlineOnlyMode: Boolean = false,
    val showCardArt: Boolean = true,
    val statusBanner: String? = null
)

private const val CATALOG_PAGE_SIZE = 100

private data class CatalogFilters(
    val query: String,
    val colors: Set<String>,
    val type: String,
    val rarity: String,
    val maxCmc: Int?
)

private data class CatalogResults(val cards: List<CardEntity>, val hasMore: Boolean)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class VaultViewModel(
    private val cardRepository: CardRepository,
    private val deckRepository: DeckRepository,
    networkMonitor: NetworkMonitor,
    preferencesRepository: PreferencesRepository,
    private val collectionDao: VaultCollectionDao
) : ViewModel() {

    val collections: StateFlow<Map<String, List<VaultCollectionCard>>> = collectionDao.observeCards()
        .map { rows -> rows.groupBy { it.entry.listName } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val query = MutableStateFlow("")
    private val selectedColors = MutableStateFlow<Set<String>>(emptySet())
    private val selectedType = MutableStateFlow("All")
    private val selectedRarity = MutableStateFlow("All")
    private val bookmarkedOnly = MutableStateFlow(false)
    private val maxCmcFilter = MutableStateFlow<Int?>(null)
    private val isSyncing = MutableStateFlow(false)
    private val isImportingCsv = MutableStateFlow(false)
    private val csvImportReport = MutableStateFlow<DelverImportReport?>(null)
    private val _csvImportError = MutableStateFlow<String?>(null)
    val csvImportError: StateFlow<String?> = _csvImportError
    private val statusBanner = MutableStateFlow<String?>(null)

    private val _inspectionExtras = MutableStateFlow(CardInspectionExtras())
    val inspectionExtras: StateFlow<CardInspectionExtras> = _inspectionExtras
    private var inspectionJob: Job? = null

    private val catalogOpen = MutableStateFlow(false)
    private val catalogLimit = MutableStateFlow(CATALOG_PAGE_SIZE)
    private val catalogFilters = combine(
        query.debounce(180), selectedColors, selectedType, selectedRarity, maxCmcFilter,
        ::CatalogFilters
    )

    private val searchedCardsFlow = catalogOpen.flatMapLatest { open ->
        if (!open) return@flatMapLatest flowOf(CatalogResults(emptyList(), false))
        combine(catalogFilters, bookmarkedOnly, catalogLimit, ::Triple)
            .flatMapLatest { (filters, bookmarked, limit) ->
                cardRepository.searchLocalCards(
                    filters.query, filters.type, filters.rarity, bookmarked, filters.maxCmc,
                    filters.colors, limit + 1
                ).map { CatalogResults(it.take(limit), it.size > limit) }
            }
    }

    fun setCatalogOpen(open: Boolean) {
        catalogOpen.value = open
        if (!open) catalogLimit.value = CATALOG_PAGE_SIZE
    }

    fun loadMoreCatalogCards() {
        if (uiState.value.hasMoreCards) catalogLimit.value += CATALOG_PAGE_SIZE
    }

    fun observeCard(cardId: String) = cardRepository.observeCard(cardId)

    val uiState: StateFlow<VaultUiState> = combine(
        searchedCardsFlow,
        cardRepository.cardCount,
        query,
        selectedColors,
        selectedType,
        selectedRarity,
        bookmarkedOnly,
        maxCmcFilter,
        deckRepository.allDecks,
        isSyncing,
        isImportingCsv,
        csvImportReport,
        networkMonitor.isOnline,
        preferencesRepository.offlineOnlyMode,
        preferencesRepository.showCardArt,
        statusBanner
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val catalog = values[0] as CatalogResults
        val totalCount = values[1] as Int
        val q = values[2] as String
        val colors = values[3] as Set<String>
        val type = values[4] as String
        val rarity = values[5] as String
        val bookmarked = values[6] as Boolean
        val maxCmc = values[7] as Int?
        @Suppress("UNCHECKED_CAST")
        val decks = values[8] as List<DeckWithEntries>
        val syncing = values[9] as Boolean
        val importingCsv = values[10] as Boolean
        val csvReport = values[11] as DelverImportReport?
        val online = values[12] as Boolean
        val offlineForced = values[13] as Boolean
        val cardArt = values[14] as Boolean
        val banner = values[15] as String?

        VaultUiState(
            cards = catalog.cards,
            hasMoreCards = catalog.hasMore,
            totalVaultCount = totalCount,
            query = q,
            selectedColors = colors,
            selectedType = type,
            selectedRarity = rarity,
            bookmarkedOnly = bookmarked,
            maxCmcFilter = maxCmc,
            decks = decks,
            isSyncing = syncing,
            isImportingCsv = importingCsv,
            csvImportReport = csvReport,
            isOnline = online,
            offlineOnlyMode = offlineForced,
            showCardArt = cardArt,
            statusBanner = banner
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VaultViewModelInitial
    )

    fun setQuery(newQuery: String) {
        catalogLimit.value = CATALOG_PAGE_SIZE
        query.value = newQuery
    }

    fun toggleColor(color: String) {
        catalogLimit.value = CATALOG_PAGE_SIZE
        selectedColors.value = if (color in selectedColors.value) {
            selectedColors.value - color
        } else {
            selectedColors.value + color
        }
    }

    fun setTypeFilter(type: String) {
        catalogLimit.value = CATALOG_PAGE_SIZE
        selectedType.value = type
    }

    fun setRarityFilter(rarity: String) {
        catalogLimit.value = CATALOG_PAGE_SIZE
        selectedRarity.value = rarity
    }

    fun toggleBookmarkedOnly() {
        catalogLimit.value = CATALOG_PAGE_SIZE
        bookmarkedOnly.value = !bookmarkedOnly.value
    }

    fun setMaxCmc(cmc: Int?) {
        catalogLimit.value = CATALOG_PAGE_SIZE
        maxCmcFilter.value = cmc
    }

    fun clearFilters() {
        catalogLimit.value = CATALOG_PAGE_SIZE
        query.value = ""
        selectedColors.value = emptySet()
        selectedType.value = "All"
        selectedRarity.value = "All"
        bookmarkedOnly.value = false
        maxCmcFilter.value = null
    }

    fun clearStatusBanner() {
        statusBanner.value = null
    }

    fun loadCardInspectionExtras(card: CardEntity, fetchArtwork: Boolean = true) {
        inspectionJob?.cancel()
        inspectionJob = viewModelScope.launch {
            val state = uiState.value
            val variants = cardRepository.getPrintingVariants(card)
            _inspectionExtras.value = CardInspectionExtras(
                cardId = card.id,
                printingVariants = variants,
                rulings = com.example.domain.CardJsonCodec.decodeRulings(card.rulingsJson),
                isLoadingRulings = card.rulingsJson.isBlank() &&
                    card.scryfallId.isNotBlank() &&
                    !uiState.value.offlineOnlyMode &&
                    uiState.value.isOnline
            )
            if (fetchArtwork && state.showCardArt && card.imageUri.isNullOrBlank()) {
                val artwork = cardRepository.fetchAndCacheArtwork(card, state.offlineOnlyMode)
                _inspectionExtras.value = _inspectionExtras.value.copy(
                    imageUri = artwork?.imageUri, artworkFacesJson = artwork?.cardFacesJson
                )
            }
            if (_inspectionExtras.value.isLoadingRulings) {
                val fetched = cardRepository.fetchAndCacheRulings(card, state.offlineOnlyMode)
                _inspectionExtras.value = _inspectionExtras.value.copy(rulings = fetched, isLoadingRulings = false)
            }
        }
    }

    fun cancelCardInspection() {
        inspectionJob?.cancel()
        _inspectionExtras.value = CardInspectionExtras()
    }

    fun toggleBookmark(card: CardEntity) {
        viewModelScope.launch {
            cardRepository.toggleBookmark(card.id, card.isBookmarked)
        }
    }

    fun addCardToDeck(deckId: Long, cardId: String, zone: String = DeckZones.MAINBOARD) {
        viewModelScope.launch {
            deckRepository.addCardToDeck(deckId, cardId, zone, 1)
            statusBanner.value = "Added card to deck ($zone)."
        }
    }

    fun createCustomCard(
        name: String,
        manaCost: String,
        cmc: Double,
        typeLine: String,
        oracleText: String,
        colors: List<String>,
        rarity: String,
        power: String?,
        toughness: String?,
        synergyTags: List<String>
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            cardRepository.createCustomCard(
                name = name,
                manaCost = manaCost,
                cmc = cmc,
                typeLine = typeLine,
                oracleText = oracleText,
                colors = colors,
                rarity = rarity,
                power = power,
                toughness = toughness,
                synergyTags = synergyTags
            )
            statusBanner.value = "Saved \"$name\" to offline Room vault."
        }
    }

    fun deleteCustomCard(cardId: String) {
        viewModelScope.launch {
            statusBanner.value = if (cardRepository.deleteCustomCard(cardId)) {
                "Deleted custom card."
            } else {
                "Card was not deleted. Remove it from any decks or collections first."
            }
        }
    }

    fun importCollectionCsv(
        csvContent: String,
        collectionName: String,
        replaceCollection: Boolean
    ) {
        if (csvContent.isBlank() || collectionName.isBlank() || !isImportingCsv.compareAndSet(false, true)) return
        clearCsvImportReport()
        viewModelScope.launch {
            try {
                val report = cardRepository.importDelverCsv(
                    csvContent = csvContent,
                    offlineOnlyMode = uiState.value.offlineOnlyMode,
                    requireExactPrinting = true
                )
                csvImportReport.value = report
                val name = collectionName.trim()
                val savedCollection = if (report.verifiedRows.isNotEmpty()) {
                    val quantities = report.verifiedRows.groupBy { checkNotNull(it.resolvedCard).id }
                        .mapValues { (_, matches) -> matches.sumOf { it.row.quantity } }
                    collectionDao.saveSnapshot(
                        name, quantities, replaceCollection,
                        complete = report.conflictRows.isEmpty() && report.unresolvedRows.isEmpty()
                    )
                } else false
                csvImportReport.value = report.copy(
                    collectionName = name.takeIf { savedCollection },
                    collectionNotice = if (!savedCollection) {
                        when {
                            report.verifiedRows.isEmpty() -> "No verified cards. The collection was not saved."
                            !replaceCollection -> "A collection with that name already exists. Confirm replacement or use another name."
                            else -> "The existing collection was kept because some rows could not be verified."
                        }
                    } else null
                )
                statusBanner.value = buildString {
                    append("CSV review: ${report.totalVerifiedCopies} verified copies")
                    if (report.conflictRows.isNotEmpty()) append(", ${report.conflictRows.size} mismatches")
                    if (report.unresolvedRows.isNotEmpty()) append(", ${report.unresolvedRows.size} unresolved")
                    append(".")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: CsvLookupException) {
                _csvImportError.value = error.message
            } catch (_: Exception) {
                _csvImportError.value = if (csvImportReport.value == null) {
                    "Could not save the CSV import. Please try again."
                } else {
                    "Catalog cards were saved, but the collection could not be saved."
                }
            } finally {
                isImportingCsv.value = false
            }
        }
    }

    fun clearCsvImportReport() {
        csvImportReport.value = null
        _csvImportError.value = null
    }

    fun syncFromScryfall() {
        viewModelScope.launch {
            isSyncing.value = true
            val result = cardRepository.syncFromScryfall(
                query = query.value,
                offlineOnlyMode = uiState.value.offlineOnlyMode
            )
            statusBanner.value = when (result) {
                is SyncResult.Success ->
                    "Cached ${result.addedOrUpdated} Scryfall cards into offline Room vault."
                is SyncResult.OfflineOrError -> result.message
            }
            isSyncing.value = false
        }
    }

    companion object {
        private val VaultViewModelInitial = VaultUiState()
    }

    class Factory(
        private val cardRepository: CardRepository,
        private val deckRepository: DeckRepository,
        private val networkMonitor: NetworkMonitor,
        private val preferencesRepository: PreferencesRepository,
        private val collectionDao: VaultCollectionDao
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VaultViewModel(cardRepository, deckRepository, networkMonitor, preferencesRepository, collectionDao) as T
        }
    }
}
