package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LatticeDatabase
import com.example.data.remote.ScryfallApi
import com.example.data.repository.CardRepository
import com.example.data.repository.DeckRepository
import com.example.data.repository.NetworkMonitor
import com.example.data.repository.OfflineCatalogRepository
import com.example.data.repository.PreferencesRepository
import com.example.data.local.CardMetadataStatus
import com.example.domain.DeckAnalytics
import com.example.domain.SynergyAnalyzerEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class PreviewOfflineExamplesTest {
    @Test
    fun `bundled catalog resolves all four sample decks offline`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = PreferencesRepository(context)
        val database = Room.inMemoryDatabaseBuilder(context, LatticeDatabase::class.java).build()
        try {
            preferences.setOfflineOnlyMode(true)
            val catalog = OfflineCatalogRepository(context, database, preferences)
            catalog.installBundledCatalog()
            val initialCount = database.cardDao().getCardCount()
            assertEquals(37_001, initialCount)
            assertTrue("all" in catalog.installed.first())

            catalog.installBundledCatalog()
            assertEquals(initialCount, database.cardDao().getCardCount())

            val decks = DeckRepository(
                database.deckDao(),
                CardRepository(database.cardDao(), ScryfallApi.create(), NetworkMonitor(context))
            )
            val paths = context.assets.list("playtest-decks").orEmpty()
            assertEquals(4, paths.size)
            paths.forEach { name ->
                val commander = name.startsWith("Commander-")
                val format = if (commander) "Commander" else "Casual"
                val text = context.assets.open("playtest-decks/$name").bufferedReader().use { it.readText() }
                val imported = decks.importDeckFromText(name, format, text, offlineOnlyMode = true)
                assertEquals(if (commander) 100 else 60, imported.totalCardsImported)
            }
            assertEquals(initialCount, database.cardDao().getCardCount())
            database.deckDao().observeAllDecksWithEntries().first().forEach { deck ->
                val commander = deck.deck.format == "Commander"
                assertEquals(if (commander) 1 else 0, deck.commanderEntries.size)
                assertEquals(if (commander) 99 else 60, deck.mainboardEntries.sumOf { it.entry.quantity })
                assertEquals(if (commander) 99 else 60, DeckAnalytics.analyze(deck).drawLibrarySize)
                if (commander) {
                    val report = SynergyAnalyzerEngine.analyzeDeck(deck)
                    assertTrue(report.primaryArchetype?.template?.id != "artifact_lattice")
                }
                assertTrue((deck.mainboardEntries + deck.commanderEntries).all {
                    it.card.metadataStatus != CardMetadataStatus.UNRESOLVED
                })
            }
        } finally {
            preferences.setOfflineOnlyMode(false)
            database.close()
        }
    }
}
