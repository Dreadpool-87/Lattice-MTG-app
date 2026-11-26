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
    fun `preview adds four complete decks offline without replacing existing decks`() = runBlocking {
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
            val personal = decks.createDeck("Personal deck", "Casual", "", emptyList())
            assertEquals(4, PreviewTools.prepare(context, database, decks))
            assertEquals(0, PreviewTools.prepare(context, database, decks))
            assertEquals(5, database.deckDao().getDeckCount())
            assertEquals(initialCount, database.cardDao().getCardCount())
            decks.allDecks.first().filter { it.deck.id != personal }.forEach { deck ->
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
            val sample = decks.allDecks.first().first { it.deck.name == "Orzhov Lifegain" }
            decks.deleteDeck(sample.deck.id)
            assertEquals(0, PreviewTools.prepare(context, database, decks))
            assertEquals(1, PreviewTools.prepare(context, database, decks, restore = true))
            assertEquals(0, PreviewTools.prepare(context, database, decks, restore = true))
            assertEquals(personal, decks.allDecks.first().first { it.deck.name == "Personal deck" }.deck.id)
        } finally {
            preferences.setOfflineOnlyMode(false)
            database.close()
        }
    }
}
