package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.DeckWithEntries
import com.example.data.repository.DeckRepository
import com.example.domain.CardRelationship
import com.example.domain.RelationshipType
import com.example.domain.SynergyAnalyzerEngine
import com.example.domain.SynergyAnalyzerReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SynergyAnalyzerUiState(
    val decks: List<DeckWithEntries> = emptyList(),
    val selectedDeckId: Long? = null,
    val report: SynergyAnalyzerReport? = null,
    val selectedCardId: String? = null,
    val selectedRelationshipFilter: RelationshipType? = null,
    val selectedArchetypeId: String = SynergyAnalyzerEngine.commonArchetypes.first().id
) {
    val focusedRelationships: List<CardRelationship> = report?.relationships.orEmpty().filter {
        selectedCardId == null || it.cardA.id == selectedCardId || it.cardB.id == selectedCardId
    }
    val filteredRelationships: List<CardRelationship> = focusedRelationships.filter {
        selectedRelationshipFilter == null || it.type == selectedRelationshipFilter
    }
    val listedRelationships: List<CardRelationship> = filteredRelationships.take(12)
    val graphRelationships: List<CardRelationship> = buildList {
        val nodeIds = mutableSetOf<String>()
        filteredRelationships.forEach { link ->
            val endpoints = setOf(link.cardA.id, link.cardB.id)
            if (nodeIds.size + endpoints.count { it !in nodeIds } <= 8) {
                nodeIds.addAll(endpoints)
                add(link)
            }
        }
    }
}

class SynergyAnalyzerViewModel(
    deckRepository: DeckRepository
) : ViewModel() {

    private val selectedDeckId = MutableStateFlow<Long?>(null)
    private val selectedCardId = MutableStateFlow<String?>(null)
    private val selectedRelFilter = MutableStateFlow<RelationshipType?>(null)
    private val selectedArchetypeId = MutableStateFlow<String?>(null)

    private val analysis = combine(deckRepository.allDecks, selectedDeckId) { decks, chosenDeckId ->
        val deck = decks.firstOrNull { it.deck.id == chosenDeckId } ?: decks.firstOrNull()
        decks to deck?.let(SynergyAnalyzerEngine::analyzeDeck)
    }

    val uiState: StateFlow<SynergyAnalyzerUiState> = combine(
        analysis,
        selectedCardId,
        selectedRelFilter,
        selectedArchetypeId
    ) { (decks, report), cardId, relFilter, chosenArchetypeId ->
        val defaultArchetypeId = report?.primaryArchetype?.template?.id
            ?: SynergyAnalyzerEngine.commonArchetypes.first().id
        val effectiveArchetypeId = chosenArchetypeId ?: defaultArchetypeId

        SynergyAnalyzerUiState(
            decks = decks,
            selectedDeckId = report?.deckWithEntries?.deck?.id,
            report = report,
            selectedCardId = cardId,
            selectedRelationshipFilter = relFilter,
            selectedArchetypeId = effectiveArchetypeId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SynergyAnalyzerUiState()
    )

    fun selectDeck(deckId: Long) {
        selectedDeckId.value = deckId
        selectedCardId.value = null
        selectedRelFilter.value = null
        selectedArchetypeId.value = null
    }

    fun selectCardNode(cardId: String?) {
        selectedCardId.value = if (cardId == null || selectedCardId.value == cardId) null else cardId
    }

    fun setRelationshipFilter(type: RelationshipType?) {
        selectedRelFilter.value = if (selectedRelFilter.value == type) null else type
    }

    fun selectArchetype(archetypeId: String) {
        selectedArchetypeId.value = archetypeId
    }

    class Factory(
        private val deckRepository: DeckRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SynergyAnalyzerViewModel(deckRepository) as T
        }
    }
}
