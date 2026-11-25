package com.example

import android.app.Application
import com.example.data.local.CardDao
import com.example.data.local.LatticeDatabase
import com.example.data.local.StarterCards
import com.example.data.remote.ScryfallApi
import com.example.data.repository.CardRepository
import com.example.data.repository.DeckRepository
import com.example.data.repository.NetworkMonitor
import com.example.data.repository.OfflineCatalogRepository
import com.example.data.repository.OfflineArtworkRepository
import com.example.data.repository.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(application: Application) {
    private val database by lazy { LatticeDatabase.getInstance(application) }
    val networkMonitor by lazy { NetworkMonitor(application) }
    val preferencesRepository by lazy { PreferencesRepository(application) }
    private val scryfallApi by lazy { ScryfallApi.create() }
    val vaultCollectionDao by lazy { database.vaultCollectionDao() }
    val offlineCatalog by lazy { OfflineCatalogRepository(application, database, preferencesRepository) }
    val offlineArtwork by lazy { OfflineArtworkRepository(application, database, preferencesRepository, offlineCatalog) }

    val cardRepository by lazy {
        CardRepository(
            cardDao = database.cardDao(),
            scryfallApi = scryfallApi,
            networkMonitor = networkMonitor
        )
    }

    val deckRepository by lazy {
        DeckRepository(
            deckDao = database.deckDao(),
            cardRepository = cardRepository
        )
    }

    suspend fun ensureSeeded() = seedStarterCatalogIfEmpty(database.cardDao())
}

internal suspend fun seedStarterCatalogIfEmpty(cardDao: CardDao) {
    if (cardDao.getCardCount() == 0) {
        cardDao.upsertAllPreservingState(StarterCards.cards)
    }
}

class LatticeApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            container.ensureSeeded()
        }
    }
}
