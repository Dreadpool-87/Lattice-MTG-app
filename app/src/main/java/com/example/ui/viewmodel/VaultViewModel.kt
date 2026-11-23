package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.CardEntity
import com.example.data.local.DeckWithEntries
import com.example.data.local.DeckZones
import com.example.data.repository.CardRepository
import com.example.data.repository.DeckRepository
import com.example.data.repository.NetworkMonitor
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.SyncResult
import com.example.domain.CardRulingInfo
import com.example.domain.DelverImportReport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CardInspectionExtras(
    val cardId: String = "",
    val printingVariants: List<CardEntity> = emptyList(),
    val rulings: List<CardRulingInfo> = emptyList(),
    val isLoadingRulings: Boolean = false
)

data class VaultUiState(
    val cards: List<CardEntity> = emptyList(),
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

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModel(
    private val cardRepository: CardRepository,
    private val deckRepository: DeckRepository,
    networkMonitor: NetworkMonitor,
    preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedColors = MutableStateFlow<Set<String>>(emptySet())
    private val selectedType = MutableStateFlow("All")
    private val selectedRarity = MutableStateFlow("All")
    private val bookmarkedOnly = MutableStateFlow(false)
    private val maxCmcFilter = MutableStateFlow<Int?>(null)
    private val isSyncing = MutableStateFlow(false)
    private val isImportingCsv = MutableStateFlow(false)
    private val csvImportReport = MutableStateFlow<DelverImportReport?>(null)
    private val statusBanner = MutableStateFlow<String?>(null)

    private val _inspectionExtras = MutableStateFlow(CardInspectionExtras())
    val inspectionExtras: StateFlow<CardInspectionExtras> = _inspectionExtras

    private val searchedCardsFlow = query.flatMapLatest { q ->
        cardRepository.searchLocalCards(q)
    }

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
        val rawCards = values[0] as List<CardEntity>
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

        val filtered = rawCards.filter { card ->
            val cardColors = card.colorIdentity
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()

            val matchesColors = if (colors.isEmpty()) {
                true
            } else {
                val wantsColorless = "C" in colors
                val wantedMana = colors - "C"
                (wantsColorless && cardColors.isEmpty()) ||
                    (wantedMana.isNotEmpty() && cardColors.any { it in wantedMana })
            }

            val matchesType = type == "All" || card.typeLine.contains(type, ignoreCase = true)
            val matchesRarity = rarity == "All" || card.rarity.equals(rarity, ignoreCase = true)
            val matchesBookmark = !bookmarked || card.isBookmarked
            val matchesCmc = maxCmc == null || card.cmc <= maxCmc.toDouble()

            matchesColors && matchesType && matchesRarity && matchesBookmark && matchesCmc
        }

        VaultUiState(
            cards = filtered,
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
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VaultViewModelInitial
    )

    fun setQuery(newQuery: String) {
        query.value = newQuery
    }

    fun toggleColor(color: String) {
        selectedColors.value = if (color in selectedColors.value) {
            selectedColors.value - color
        } else {
            selectedColors.value + color
        }
    }

    fun setTypeFilter(type: String) {
        selectedType.value = type
    }

    fun setRarityFilter(rarity: String) {
        selectedRarity.value = rarity
    }

    fun toggleBookmarkedOnly() {
        bookmarkedOnly.value = !bookmarkedOnly.value
    }

    fun setMaxCmc(cmc: Int?) {
        maxCmcFilter.value = cmc
    }

    fun clearFilters() {
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

    fun loadCardInspectionExtras(card: CardEntity) {
        viewModelScope.launch {
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
            if (card.rulingsJson.isBlank() && card.scryfallId.isNotBlank()) {
                val fetched = cardRepository.fetchAndCacheRulings(card, uiState.value.offlineOnlyMode)
                _inspectionExtras.value = _inspectionExtras.value.copy(
                    rulings = fetched,
                    isLoadingRulings = false
                )
            }
        }
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
            cardRepository.deleteCustomCard(cardId)
            statusBanner.value = "Deleted custom card."
        }
    }

    fun importDelverCsv(
        csvContent: String,
        createDeckName: String? = null,
        deckFormat: String = "Commander"
    ) {
        if (csvContent.isBlank()) return
        viewModelScope.launch {
            isImportingCsv.value = true
            val baseReport = cardRepository.importDelverCsv(
                csvContent = csvContent,
                offlineOnlyMode = uiState.value.offlineOnlyMode
            )
            val createdDeckId = if (!createDeckName.isNullOrBlank() && baseReport.verifiedRows.isNotEmpty()) {
                deckRepository.addVerifiedCsvToNewDeck(createDeckName, deckFormat, baseReport)
            } else {
                null
            }
            val finalReport = baseReport.copy(deckIdCreated = createdDeckId)
            csvImportReport.value = finalReport
            statusBanner.value = buildString {
                append("CSV review: ${finalReport.totalVerifiedCopies} verified copies")
                if (finalReport.conflictRows.isNotEmpty()) {
                    append(", ${finalReport.conflictRows.size} mismatches")
                }
                if (finalReport.unresolvedRows.isNotEmpty()) {
                    append(", ${finalReport.unresolvedRows.size} unresolved")
                }
                append(".")
            }
            isImportingCsv.value = false
        }
    }

    fun clearCsvImportReport() {
        csvImportReport.value = null
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
        private val preferencesRepository: PreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VaultViewModel(cardRepository, deckRepository, networkMonitor, preferencesRepository) as T
        }
    }
}
