package com.example

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CardMetadataStatus
import com.example.data.local.DeckEntity
import com.example.data.local.DeckEntryEntity
import com.example.data.local.DeckZones
import com.example.data.local.LatticeDatabase
import com.example.data.local.StarterCards
import com.example.data.remote.ScryfallCardDto
import com.example.domain.DeckAnalytics
import com.example.domain.DelverCsvImporter
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataRetentionRegressionTest {
    private lateinit var database: LatticeDatabase

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LatticeDatabase::class.java).build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun startupSeedsOnlyCardsAndPreservesExistingDecks() = runTest {
        seedStarterCatalogIfEmpty(database.cardDao())
        assertEquals(StarterCards.cards.size, database.cardDao().getCardCount())
        assertEquals(0, database.deckDao().getDeckCount())

        val card = StarterCards.cards.first()
        database.cardDao().updateBookmark(card.id, true)
        val deck = database.deckDao().insertDeckWithEntries(
            DeckEntity(name = "My deck", format = "Casual"),
            listOf(DeckEntryEntity(deckId = 0, cardId = card.id, quantity = 3))
        )
        seedStarterCatalogIfEmpty(database.cardDao())
        assertEquals(1, database.deckDao().getDeckCount())
        assertEquals(3, database.deckDao().getDeckWithEntriesOnce(deck)?.entries?.single()?.entry?.quantity)
        assertTrue(database.cardDao().getCardById(card.id)?.isBookmarked == true)
    }

    @Test
    fun upgradedPlaceholderKeepsItsPrintingAndIsCheckedForLegality() = runTest {
        val cards = database.cardDao()
        val printing = ScryfallCardDto(
            id = "00000000-0000-4000-8000-000000000001",
            name = "Example Spell",
            set = "abc",
            collectorNumber = "1",
            typeLine = "Instant",
            legalities = mapOf("modern" to "legal")
        ).toCardEntity()
        val stub = printing.copy(
            id = "offline-example-spell",
            scryfallId = "",
            isBookmarked = true,
            legalFormats = "Unknown",
            metadataStatus = CardMetadataStatus.UNRESOLVED
        )
        cards.upsertPreservingState(stub)
        val deckId = database.deckDao().insertDeckWithEntries(
            DeckEntity(name = "Example", format = "Standard"),
            listOf(DeckEntryEntity(deckId = 0, cardId = stub.id))
        )

        assertEquals(stub.id, cards.upsertPreservingState(printing).id)
        val other = printing.copy(id = "sf-second-printing", scryfallId = "00000000-0000-4000-8000-000000000002", setCode = "DEF")
        assertEquals(other.id, cards.upsertPreservingState(other).id)
        assertEquals(2, cards.getCardCount())
        val deck = checkNotNull(database.deckDao().getDeckWithEntriesOnce(deckId))
        val retained = deck.entries.single().card
        assertEquals(printing.scryfallId, retained.scryfallId)
        assertTrue(retained.isBookmarked)
        val notes = DeckAnalytics.analyze(deck).validationNotes
        assertFalse(notes.any { it.title.startsWith("Unresolved Cards") })
        assertTrue(notes.any { it.title.startsWith("Not Listed Legal in Standard") })
    }

    @Test
    fun failingDeckInsertAndZoneMoveRollBack() = runTest {
        val decks = database.deckDao()
        val card = StarterCards.cards.first()
        database.cardDao().upsertPreservingState(card)
        val valid = DeckEntryEntity(deckId = 0, cardId = card.id, quantity = 2)
        val importFailed = try {
            decks.insertDeckWithEntries(
                DeckEntity(name = "Broken import", format = "Casual"),
                listOf(valid, valid.copy(cardId = "missing-card"))
            )
            false
        } catch (_: SQLiteConstraintException) {
            true
        }
        assertTrue(importFailed)
        assertEquals(0, decks.getDeckCount())

        val deckId = decks.insertDeckWithEntries(
            DeckEntity(name = "Existing deck", format = "Casual"),
            listOf(valid, valid.copy(zone = DeckZones.SIDEBOARD, quantity = Int.MAX_VALUE))
        )
        val moveFailed = try {
            decks.moveCardZone(deckId, card.id, DeckZones.MAINBOARD, DeckZones.SIDEBOARD)
            false
        } catch (_: ArithmeticException) {
            true
        }
        assertTrue(moveFailed)
        assertEquals(2, decks.getEntry(deckId, card.id, DeckZones.MAINBOARD)?.quantity)
        assertEquals(Int.MAX_VALUE, decks.getEntry(deckId, card.id, DeckZones.SIDEBOARD)?.quantity)
    }

    @Test
    fun csvRejectsMalformedQuantityAndCustomMatch() = runTest {
        val custom = StarterCards.cards.first().copy(id = "custom-test", isCustom = true, metadataStatus = CardMetadataStatus.CUSTOM)
        database.cardDao().upsertPreservingState(custom)
        val rows = DelverCsvImporter.parseCsv("Name,Quantity\n${custom.name},2\n${custom.name},abc")
        val report = DelverCsvImporter.matchAndImportRows(rows, database.cardDao(), null, canFetchOnline = false)
        assertEquals(0, report.totalVerifiedCopies)
        assertEquals(2, report.unresolvedRows.size)
        assertTrue(report.unresolvedRows.last().statusDetail.contains("Invalid quantity"))
        assertEquals(1, database.cardDao().getCardCount())
    }
}
