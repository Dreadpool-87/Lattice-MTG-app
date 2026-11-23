package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.CardRepository
import com.example.data.repository.DeckRepository
import com.example.data.repository.NetworkMonitor
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.SyncResult
import com.example.domain.DeckAnalytics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerTrackerState(
    val id: Int,
    val name: String,
    val life: Int,
    val poison: Int = 0,
    val commanderDamage: Int = 0
)

data class TabletopUiState(
    val players: List<PlayerTrackerState> = listOf(
        PlayerTrackerState(1, "Player 1", 40),
        PlayerTrackerState(2, "Player 2", 40)
    ),
    val startingLife: Int = 40,
    val playerCount: Int = 2,
    val cachedCardCount: Int = 0,
    val deckCount: Int = 0,
    val totalDeckEntriesCount: Int = 0,
    val isOnline: Boolean = false,
    val offlineOnlyMode: Boolean = false,
    val keepScreenAwake: Boolean = true,
    val isSyncing: Boolean = false,
    val statusMessage: String? = null,
    // Hypergeometric lab inputs
    val labDeckSize: Int = 99,
    val labSuccesses: Int = 37,
    val labCardsSeen: Int = 9,
    val labDesiredHits: Int = 3
) {
    val labProbabilityPercent: Double
        get() = DeckAnalytics.hypergeometricAtLeastK(
            population = labDeckSize,
            successes = labSuccesses,
            draws = labCardsSeen,
            minHits = labDesiredHits
        ) * 100.0
}

class TabletopViewModel(
    private val cardRepository: CardRepository,
    deckRepository: DeckRepository,
    networkMonitor: NetworkMonitor,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val playersFlow = MutableStateFlow(
        listOf(
            PlayerTrackerState(1, "Player 1", 40),
            PlayerTrackerState(2, "Player 2", 40)
        )
    )
    private val startingLifeFlow = MutableStateFlow(40)
    private val playerCountFlow = MutableStateFlow(2)
    private val isSyncingFlow = MutableStateFlow(false)
    private val statusMessageFlow = MutableStateFlow<String?>(null)
    private val labParamsFlow = MutableStateFlow(listOf(99, 37, 9, 3))

    init {
        viewModelScope.launch {
            val savedPlayers = preferencesRepository.playerCount
            val savedLife = preferencesRepository.defaultLife
            combine(savedPlayers, savedLife) { p, l -> p to l }
                .collect { (p, l) ->
                    if (playerCountFlow.value != p || startingLifeFlow.value != l) {
                        playerCountFlow.value = p
                        startingLifeFlow.value = l
                        playersFlow.value = (1..p).map { idx ->
                            PlayerTrackerState(id = idx, name = "Player $idx", life = l)
                        }
                    }
                }
        }
    }

    val uiState: StateFlow<TabletopUiState> = combine(
        playersFlow,
        startingLifeFlow,
        playerCountFlow,
        cardRepository.cardCount,
        deckRepository.deckCount,
        deckRepository.totalDeckCardsCount,
        networkMonitor.isOnline,
        preferencesRepository.offlineOnlyMode,
        preferencesRepository.keepScreenAwake,
        isSyncingFlow,
        statusMessageFlow,
        labParamsFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val players = values[0] as List<PlayerTrackerState>
        val startLife = values[1] as Int
        val pCount = values[2] as Int
        val cachedCards = values[3] as Int
        val decks = values[4] as Int
        val totalEntries = values[5] as Int
        val online = values[6] as Boolean
        val offlineForced = values[7] as Boolean
        val keepAwake = values[8] as Boolean
        val syncing = values[9] as Boolean
        val message = values[10] as String?
        @Suppress("UNCHECKED_CAST")
        val lab = values[11] as List<Int>

        TabletopUiState(
            players = players,
            startingLife = startLife,
            playerCount = pCount,
            cachedCardCount = cachedCards,
            deckCount = decks,
            totalDeckEntriesCount = totalEntries,
            isOnline = online,
            offlineOnlyMode = offlineForced,
            keepScreenAwake = keepAwake,
            isSyncing = syncing,
            statusMessage = message,
            labDeckSize = lab[0],
            labSuccesses = lab[1],
            labCardsSeen = lab[2],
            labDesiredHits = lab[3]
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TabletopUiState()
    )

    fun configureMatch(playerCount: Int, startingLife: Int) {
        playerCountFlow.value = playerCount
        startingLifeFlow.value = startingLife
        playersFlow.value = (1..playerCount).map { idx ->
            PlayerTrackerState(id = idx, name = "Player $idx", life = startingLife)
        }
        viewModelScope.launch {
            preferencesRepository.setTabletopDefaults(startingLife, playerCount)
        }
    }

    fun resetMatch() {
        val life = startingLifeFlow.value
        playersFlow.update { list ->
            list.map { it.copy(life = life, poison = 0, commanderDamage = 0) }
        }
    }

    fun adjustLife(playerId: Int, delta: Int) {
        playersFlow.update { list ->
            list.map { if (it.id == playerId) it.copy(life = it.life + delta) else it }
        }
    }

    fun adjustPoison(playerId: Int, delta: Int) {
        playersFlow.update { list ->
            list.map {
                if (it.id == playerId) it.copy(poison = (it.poison + delta).coerceAtLeast(0)) else it
            }
        }
    }

    fun adjustCommanderDamage(playerId: Int, delta: Int) {
        playersFlow.update { list ->
            list.map {
                if (it.id == playerId) {
                    it.copy(
                        commanderDamage = (it.commanderDamage + delta).coerceAtLeast(0),
                        life = it.life - delta
                    )
                } else it
            }
        }
    }

    fun updateLabParams(deckSize: Int, successes: Int, seen: Int, hits: Int) {
        val safeDeck = deckSize.coerceIn(1, 250)
        val safeSucc = successes.coerceIn(0, safeDeck)
        val safeSeen = seen.coerceIn(1, safeDeck)
        val safeHits = hits.coerceIn(1, safeSeen.coerceAtMost(safeSucc).coerceAtLeast(1))
        labParamsFlow.value = listOf(safeDeck, safeSucc, safeSeen, safeHits)
    }

    fun setOfflineOnlyMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setOfflineOnlyMode(enabled)
            statusMessageFlow.value = if (enabled) {
                "Offline-Only Mode enabled. All queries strictly stay on-device."
            } else {
                "Online card lookups are available when connected."
            }
        }
    }

    fun syncStaplesPack() {
        viewModelScope.launch {
            isSyncingFlow.value = true
            val result = cardRepository.syncFromScryfall(
                query = "f:commander (t:artifact or t:land or is:staple)",
                offlineOnlyMode = uiState.value.offlineOnlyMode
            )
            statusMessageFlow.value = when (result) {
                is SyncResult.Success ->
                    "Synced ${result.addedOrUpdated} Commander staples into Room offline storage."
                is SyncResult.OfflineOrError -> result.message
            }
            isSyncingFlow.value = false
        }
    }

    fun restoreStarterCatalog() {
        viewModelScope.launch {
            cardRepository.restoreStarterCatalog()
            statusMessageFlow.value = "Verified & restored built-in offline starter catalog."
        }
    }

    fun clearMessage() {
        statusMessageFlow.value = null
    }

    class Factory(
        private val cardRepository: CardRepository,
        private val deckRepository: DeckRepository,
        private val networkMonitor: NetworkMonitor,
        private val preferencesRepository: PreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TabletopViewModel(cardRepository, deckRepository, networkMonitor, preferencesRepository) as T
        }
    }
}
