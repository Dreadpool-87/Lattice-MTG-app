package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.DeckWithEntries
import com.example.data.repository.CardRepository
import com.example.data.repository.DeckRepository
import com.example.data.repository.NetworkMonitor
import com.example.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DecksUiState(
    val decks: List<DeckWithEntries> = emptyList(),
    val selectedFormatFilter: String = "All",
    val searchQuery: String = "",
    val cachedCardCount: Int = 0,
    val isOnline: Boolean = false,
    val offlineOnlyMode: Boolean = false,
    val statusMessage: String? = null
)

class DecksViewModel(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    networkMonitor: NetworkMonitor,
    preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val formatFilter = MutableStateFlow("All")
    private val searchQuery = MutableStateFlow("")
    private val statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DecksUiState> = combine(
        deckRepository.allDecks,
        formatFilter,
        searchQuery,
        cardRepository.cardCount,
        networkMonitor.isOnline,
        preferencesRepository.offlineOnlyMode,
        statusMessage
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allDecks = values[0] as List<DeckWithEntries>
        val format = values[1] as String
        val query = values[2] as String
        val cardCount = values[3] as Int
        val online = values[4] as Boolean
        val offlineForced = values[5] as Boolean
        val message = values[6] as String?

        val filtered = allDecks.filter { d ->
            val matchesFormat = format == "All" || d.deck.format.equals(format, ignoreCase = true)
            val matchesQuery = query.isBlank() ||
                d.deck.name.contains(query, ignoreCase = true) ||
                d.deck.description.contains(query, ignoreCase = true)
            matchesFormat && matchesQuery
        }

        DecksUiState(
            decks = filtered,
            selectedFormatFilter = format,
            searchQuery = query,
            cachedCardCount = cardCount,
            isOnline = online,
            offlineOnlyMode = offlineForced,
            statusMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DecksUiState()
    )

    fun setFormatFilter(format: String) {
        formatFilter.value = format
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun clearMessage() {
        statusMessage.value = null
    }

    fun createDeck(
        name: String,
        format: String,
        description: String,
        colors: List<String>,
        onCreated: (Long) -> Unit
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = deckRepository.createDeck(name, format, description, colors)
            statusMessage.value = "Created \"$name\" offline in Room."
            onCreated(id)
        }
    }

    fun duplicateDeck(deckId: Long) {
        viewModelScope.launch {
            val copied = deckRepository.duplicateDeck(deckId)
            if (copied != null) {
                statusMessage.value = "Deck duplicated locally."
            }
        }
    }

    fun deleteDeck(deckId: Long, name: String) {
        viewModelScope.launch {
            deckRepository.deleteDeck(deckId)
            statusMessage.value = "Deleted \"$name\"."
        }
    }

    fun importDeck(name: String, format: String, rawList: String, onImported: (Long) -> Unit) {
        if (rawList.isBlank()) return
        viewModelScope.launch {
            val result = deckRepository.importDeckFromText(
                name = name,
                format = format,
                rawText = rawList,
                offlineOnlyMode = uiState.value.offlineOnlyMode
            )
            statusMessage.value = "Imported ${result.totalCardsImported} cards (${result.uniqueEntries} unique)."
            onImported(result.deckId)
        }
    }

    class Factory(
        private val deckRepository: DeckRepository,
        private val cardRepository: CardRepository,
        private val networkMonitor: NetworkMonitor,
        private val preferencesRepository: PreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DecksViewModel(deckRepository, cardRepository, networkMonitor, preferencesRepository) as T
        }
    }
}
