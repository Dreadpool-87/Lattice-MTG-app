package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.CardEntity
import com.example.data.local.DeckWithEntries
import com.example.data.local.DeckZones
import com.example.data.repository.CardRepository
import com.example.data.repository.DeckRepository
import com.example.domain.DeckAnalytics
import com.example.domain.DeckAnalyticsSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaytestHandState(
    val library: List<CardEntity> = emptyList(),
    val hand: List<CardEntity> = emptyList(),
    val battlefield: List<CardEntity> = emptyList(),
    val mulliganCount: Int = 0,
    val turnNumber: Int = 1,
    val landsInHand: Int = 0
)

data class DeckDetailUiState(
    val deckWithEntries: DeckWithEntries? = null,
    val analytics: DeckAnalyticsSummary? = null,
    val allVaultCards: List<CardEntity> = emptyList(),
    val cardPickerQuery: String = "",
    val activeZone: String = DeckZones.MAINBOARD,
    val playtest: PlaytestHandState = PlaytestHandState(),
    val selectedSynergyTag: String? = null,
    val snackbarMessage: String? = null
)

class DeckDetailViewModel(
    private val deckId: Long,
    private val deckRepository: DeckRepository,
    cardRepository: CardRepository
) : ViewModel() {

    private val cardPickerQuery = MutableStateFlow("")
    private val activeZone = MutableStateFlow(DeckZones.MAINBOARD)
    private val playtestState = MutableStateFlow(PlaytestHandState())
    private val selectedSynergyTag = MutableStateFlow<String?>(null)
    private val snackbarMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DeckDetailUiState> = combine(
        deckRepository.observeDeck(deckId),
        cardRepository.allCards,
        cardPickerQuery,
        activeZone,
        playtestState,
        selectedSynergyTag,
        snackbarMessage
    ) { values ->
        val deck = values[0] as DeckWithEntries?
        @Suppress("UNCHECKED_CAST")
        val allCards = values[1] as List<CardEntity>
        val pickerQ = values[2] as String
        val zone = values[3] as String
        val playtest = values[4] as PlaytestHandState
        val selectedTag = values[5] as String?
        val snack = values[6] as String?

        val analytics = deck?.let { DeckAnalytics.analyze(it) }
        val filteredCards = if (pickerQ.isBlank()) {
            allCards
        } else {
            allCards.filter {
                it.name.contains(pickerQ, ignoreCase = true) ||
                    it.typeLine.contains(pickerQ, ignoreCase = true) ||
                    it.oracleText.contains(pickerQ, ignoreCase = true) ||
                    it.synergyTags.contains(pickerQ, ignoreCase = true)
            }
        }

        DeckDetailUiState(
            deckWithEntries = deck,
            analytics = analytics,
            allVaultCards = filteredCards,
            cardPickerQuery = pickerQ,
            activeZone = zone,
            playtest = playtest,
            selectedSynergyTag = selectedTag,
            snackbarMessage = snack
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DeckDetailUiState()
    )

    fun setActiveZone(zone: String) {
        activeZone.value = zone
    }

    fun setPickerQuery(q: String) {
        cardPickerQuery.value = q
    }

    fun selectSynergyTag(tag: String?) {
        selectedSynergyTag.value = if (selectedSynergyTag.value == tag) null else tag
    }

    fun clearSnackbar() {
        snackbarMessage.value = null
    }

    fun adjustQuantity(cardId: String, zone: String, delta: Int) {
        viewModelScope.launch {
            deckRepository.addCardToDeck(deckId, cardId, zone, delta)
        }
    }

    fun moveZone(cardId: String, fromZone: String, toZone: String) {
        viewModelScope.launch {
            deckRepository.moveCardZone(deckId, cardId, fromZone, toZone)
            snackbarMessage.value = "Moved card to $toZone."
        }
    }

    fun updateDeckMeta(name: String, format: String, description: String, notes: String) {
        val current = uiState.value.deckWithEntries?.deck ?: return
        viewModelScope.launch {
            deckRepository.updateDeckMeta(current, name, format, description, notes)
            snackbarMessage.value = "Deck details saved offline."
        }
    }

    fun exportDeckText(): String {
        val current = uiState.value.deckWithEntries ?: return ""
        return deckRepository.exportDeckAsText(current)
    }

    // === Offline Goldfish Playtest Simulator ===
    fun startNewTestHand(isMulligan: Boolean = false) {
        val deck = uiState.value.deckWithEntries ?: return
        val expandedPool = deck.mainboardEntries.flatMap { entryWithCard ->
            List(entryWithCard.entry.quantity) { entryWithCard.card }
        }.shuffled()

        if (expandedPool.isEmpty()) {
            snackbarMessage.value = "Add cards to Mainboard before starting a playtest hand."
            return
        }

        val handSize = minOf(7, expandedPool.size)
        val hand = expandedPool.take(handSize)
        val remaining = expandedPool.drop(handSize)
        val nextMull = if (isMulligan) playtestState.value.mulliganCount + 1 else 0

        playtestState.value = PlaytestHandState(
            library = remaining,
            hand = hand,
            battlefield = emptyList(),
            mulliganCount = nextMull,
            turnNumber = 1,
            landsInHand = hand.count { "land" in it.typeLine.lowercase() }
        )
    }

    fun drawCard() {
        val current = playtestState.value
        if (current.library.isEmpty()) {
            snackbarMessage.value = "Library is empty!"
            return
        }
        val drawn = current.library.first()
        val nextHand = current.hand + drawn
        playtestState.value = current.copy(
            library = current.library.drop(1),
            hand = nextHand,
            turnNumber = current.turnNumber + 1,
            landsInHand = nextHand.count { "land" in it.typeLine.lowercase() }
        )
    }

    fun playCardFromHand(index: Int) {
        val current = playtestState.value
        if (index !in current.hand.indices) return
        val card = current.hand[index]
        val nextHand = current.hand.toMutableList().apply { removeAt(index) }
        playtestState.value = current.copy(
            hand = nextHand,
            battlefield = current.battlefield + card,
            landsInHand = nextHand.count { "land" in it.typeLine.lowercase() }
        )
    }

    fun putOnBottomForMulligan(index: Int) {
        val current = playtestState.value
        if (index !in current.hand.indices) return
        val card = current.hand[index]
        val nextHand = current.hand.toMutableList().apply { removeAt(index) }
        playtestState.value = current.copy(
            hand = nextHand,
            library = current.library + card,
            landsInHand = nextHand.count { "land" in it.typeLine.lowercase() }
        )
    }

    class Factory(
        private val deckId: Long,
        private val deckRepository: DeckRepository,
        private val cardRepository: CardRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DeckDetailViewModel(deckId, deckRepository, cardRepository) as T
        }
    }
}
