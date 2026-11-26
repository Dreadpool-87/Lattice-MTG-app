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
            val legacyList = context.assets.open("playtest-decks/Commander-Multiverse-Reforged.txt")
                .bufferedReader().use { it.readText() }
                .replace(Regex(""" \((FRC|FRA)\) \d+"""), "")
            val oldMultiverse = decks.importDeckFromText("Legacy fixture", "Commander", legacyList, true).deckId
            assertEquals(4, PreviewTools.prepare(context, database, decks))
            assertEquals(0, PreviewTools.prepare(context, database, decks))
            assertEquals(6, database.deckDao().getDeckCount())
            assertEquals(initialCount + 66, database.cardDao().getCardCount())
            val exact = decks.allDecks.first().first { it.deck.name == "Multiverse Reforged" }
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
            val expected = mapOf("Swords to Plowshares" to "37", "Battlefield Forge" to "65", "Path of Ancestry" to "77")
            for ((name, number) in expected) {
                val card = exact.mainboardEntries.single { it.card.name == name }.card
                assertEquals("FRC", card.setCode)
                assertEquals(number, card.collectorNumber)
            }
            assertEquals(mapOf("281" to 2, "282" to 2), exact.mainboardEntries
                .filter { it.card.name == "Plains" }.associate { it.card.collectorNumber to it.entry.quantity })

            val dao = database.deckDao()
            val legacy = checkNotNull(dao.getDeckWithEntriesOnce(oldMultiverse))
            assertEquals("40K", legacy.mainboardEntries.single { it.card.name == "Path of Ancestry" }.card.setCode)
            decks.deleteDeck(exact.deck.id)
            dao.updateDeck(legacy.deck.copy(name = "Multiverse Reforged", notes = "Keep my notes"))
            check(context.getSharedPreferences("preview_tools", Context.MODE_PRIVATE).edit()
                .remove("multiverse_printings_prepared").commit())
            assertEquals(0, PreviewTools.prepare(context, database, decks))
            val upgraded = checkNotNull(dao.getDeckWithEntriesOnce(oldMultiverse))
            assertEquals("Keep my notes", upgraded.deck.notes)
            assertEquals(exact.entries.map { Triple(it.entry.zone, it.card.scryfallId, it.entry.quantity) }.toSet(),
                upgraded.entries.map { Triple(it.entry.zone, it.card.scryfallId, it.entry.quantity) }.toSet())
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
