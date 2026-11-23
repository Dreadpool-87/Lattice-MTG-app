package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChatDao
import com.example.data.repository.CardRepository
import com.example.data.repository.DeckRepository
import com.example.data.repository.NetworkMonitor
import com.example.data.repository.PreferencesRepository
import com.example.ui.theme.ThemeBrightnessMode
import com.example.ui.theme.WubrgTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val wubrgTheme: WubrgTheme = WubrgTheme.GREEN,
    val brightnessMode: ThemeBrightnessMode = ThemeBrightnessMode.SYSTEM,
    val offlineOnlyMode: Boolean = false,
    val showCardArt: Boolean = true,
    val keepScreenAwake: Boolean = true,
    val defaultLife: Int = 40,
    val playerCount: Int = 2,
    val cachedCardCount: Int = 0,
    val deckCount: Int = 0,
    val oracleMessageCount: Int = 0,
    val isOnline: Boolean = false,
    val statusMessage: String? = null
)

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val cardRepository: CardRepository,
    deckRepository: DeckRepository,
    private val chatDao: ChatDao,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    private val statusMessageFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.wubrgTheme,
        preferencesRepository.themeBrightnessMode,
        preferencesRepository.offlineOnlyMode,
        preferencesRepository.showCardArt,
        preferencesRepository.keepScreenAwake,
        preferencesRepository.defaultLife,
        preferencesRepository.playerCount,
        cardRepository.cardCount,
        deckRepository.deckCount,
        chatDao.observeMessages(),
        networkMonitor.isOnline,
        statusMessageFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val messages = values[9] as List<*>
        SettingsUiState(
            wubrgTheme = values[0] as WubrgTheme,
            brightnessMode = values[1] as ThemeBrightnessMode,
            offlineOnlyMode = values[2] as Boolean,
            showCardArt = values[3] as Boolean,
            keepScreenAwake = values[4] as Boolean,
            defaultLife = values[5] as Int,
            playerCount = values[6] as Int,
            cachedCardCount = values[7] as Int,
            deckCount = values[8] as Int,
            oracleMessageCount = messages.size,
            isOnline = values[10] as Boolean,
            statusMessage = values[11] as String?
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun selectWubrgTheme(theme: WubrgTheme) {
        viewModelScope.launch {
            preferencesRepository.setWubrgTheme(theme)
        }
    }

    fun selectBrightnessMode(mode: ThemeBrightnessMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeBrightnessMode(mode)
        }
    }

    fun setOfflineOnlyMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setOfflineOnlyMode(enabled)
            statusMessageFlow.value = if (enabled) {
                "Offline-Only Mode enabled."
            } else {
                "Online Scryfall sync unlocked."
            }
        }
    }

    fun setShowCardArt(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setShowCardArt(enabled)
        }
    }

    fun setKeepScreenAwake(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setKeepScreenAwake(enabled)
        }
    }

    fun setTabletopDefaults(life: Int, players: Int) {
        viewModelScope.launch {
            preferencesRepository.setTabletopDefaults(life = life, players = players)
            statusMessageFlow.value = "Default tabletop set to ${players}P • $life life."
        }
    }

    fun restoreStarterCatalog() {
        viewModelScope.launch {
            cardRepository.restoreStarterCatalog()
            statusMessageFlow.value = "Verified & restored offline starter catalog."
        }
    }

    fun clearUnusedRemoteCache() {
        viewModelScope.launch {
            val removed = cardRepository.clearUnusedRemoteCache()
            statusMessageFlow.value = if (removed > 0) {
                "Cleared $removed unused cached remote cards."
            } else {
                "No unused remote cache entries to remove."
            }
        }
    }

    fun clearOracleHistory() {
        viewModelScope.launch {
            chatDao.clearConversation()
            statusMessageFlow.value = "Cleared Lattice Oracle conversation history."
        }
    }

    fun resetPreferences() {
        viewModelScope.launch {
            preferencesRepository.resetAllPreferences()
            statusMessageFlow.value = "Preferences reset to defaults."
        }
    }

    fun clearStatusMessage() {
        statusMessageFlow.value = null
    }

    class Factory(
        private val preferencesRepository: PreferencesRepository,
        private val cardRepository: CardRepository,
        private val deckRepository: DeckRepository,
        private val chatDao: ChatDao,
        private val networkMonitor: NetworkMonitor
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                preferencesRepository = preferencesRepository,
                cardRepository = cardRepository,
                deckRepository = deckRepository,
                chatDao = chatDao,
                networkMonitor = networkMonitor
            ) as T
        }
    }
}
