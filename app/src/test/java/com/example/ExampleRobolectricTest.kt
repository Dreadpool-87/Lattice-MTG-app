package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DeckEntity
import com.example.data.local.DeckEntryEntity
import com.example.data.local.DeckZones
import com.example.data.local.LatticeDatabase
import com.example.data.local.StarterCards
import com.example.data.local.StarterDecks
import com.example.data.remote.ScryfallApi
import com.example.data.remote.ScryfallCardDto
import com.example.data.remote.ScryfallSearchResponse
import com.example.data.repository.CardRepository
import com.example.data.repository.NetworkMonitor
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.SyncResult
import com.example.domain.DeckAnalytics
import com.example.domain.RelationshipType
import com.example.domain.SynergyAnalyzerEngine
import com.example.ui.theme.ThemeBrightnessMode
import com.example.ui.theme.WubrgTheme
import com.example.ui.theme.colorSchemeForWubrg
import com.example.ui.theme.withOledSurfaces
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        val expectedName = if (BuildConfig.APPLICATION_ID.endsWith(".preview")) {
            "Lattice MTG Preview"
        } else {
            "Lattice MTG"
        }
        assertEquals(expectedName, appName)
    }

    @Test
    fun `offline room database seeds starter cards and decks`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LatticeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            StarterDecks.seedIfEmpty(db.cardDao(), db.deckDao())

            val latticeMatches = db.cardDao().searchCards(
                query = "Mycosynth",
                type = "All",
                rarity = "All",
                bookmarkedOnly = false,
                maxCmc = null,
                colors = "",
                limit = 100
            ).first()
            assertTrue(latticeMatches.any { it.name == "Mycosynth Lattice" })

            val decks = db.deckDao().observeAllDecksWithEntries().first()
            assertEquals(3, decks.size)

            val latticeDeck = decks.first { it.deck.name == "Mycosynth Lattice Engine" }
            val summary = DeckAnalytics.analyze(latticeDeck)
            assertTrue(summary.playableCount > 0)
            assertTrue(summary.synergyClusters.any { it.tag == "Artifact" })
        } finally {
            db.close()
        }
    }

    @Test
    fun `syncing and restoring cards updates metadata in place without deleting deck entries or bookmarks`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LatticeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            var syncVersion = 1
            val fakeApi = object : ScryfallApi {
                override suspend fun searchCards(query: String, order: String): ScryfallSearchResponse {
                    val dto = ScryfallCardDto(
                        id = "11111111-2222-3333-4444-555555555555",
                        name = "Atraxa, Grand Unifier",
                        manaCost = "{3}{G}{W}{U}{B}",
                        cmc = 7.0,
                        typeLine = "Legendary Creature — Phyrexian Angel",
                        oracleText = if (syncVersion == 1) {
                            "Flying, vigilance, deathtouch, lifelink. V1 oracle text."
                        } else {
                            "Flying, vigilance, deathtouch, lifelink. V2 updated oracle text."
                        },
                        colors = listOf("W", "U", "B", "G"),
                        colorIdentity = listOf("W", "U", "B", "G"),
                        rarity = "mythic",
                        set = "ONE",
                        setName = if (syncVersion == 1) "Phyrexia V1" else "Phyrexia: All Will Be One V2",
                        power = "7",
                        toughness = "7",
                        legalities = mapOf("commander" to "legal", "modern" to "legal")
                    )
                    return ScryfallSearchResponse(data = listOf(dto))
                }

                override suspend fun getCardNamed(fuzzyName: String): ScryfallCardDto {
                    error("Unused in test")
                }

                override suspend fun getCardNamedExact(exactName: String, setCode: String?): ScryfallCardDto {
                    error("Unused in test")
                }

                override suspend fun getCardByScryfallId(scryfallId: String): ScryfallCardDto {
                    error("Unused in test")
                }

                override suspend fun getCardBySetNumberLang(
                    setCode: String,
                    collectorNumber: String,
                    lang: String
                ): ScryfallCardDto {
                    error("Unused in test")
                }

                override suspend fun getCardBySetNumber(
                    setCode: String,
                    collectorNumber: String
                ): ScryfallCardDto {
                    error("Unused in test")
                }

                override suspend fun getCardRulings(scryfallId: String) =
                    com.example.data.remote.ScryfallRulingsResponse()
            }
            val alwaysOnlineMonitor = object : NetworkMonitor(context) {
                override fun isCurrentlyOnline(): Boolean = true
            }
            val repo = CardRepository(
                cardDao = db.cardDao(),
                scryfallApi = fakeApi,
                networkMonitor = alwaysOnlineMonitor
            )

            // Sync #1 -> bookmark -> add to deck across multiple zones
            val firstSync = repo.syncFromScryfall("Atraxa", offlineOnlyMode = false)
            assertTrue(firstSync is SyncResult.Success)
            val cardId = "sf-11111111-2222-3333-4444-555555555555"
            val initialCard = db.cardDao().getCardById(cardId)
            assertNotNull(initialCard)
            assertEquals("Phyrexia V1", initialCard?.setName)

            repo.toggleBookmark(cardId, current = false)
            assertTrue(db.cardDao().getCardById(cardId)?.isBookmarked == true)

            val deckId = db.deckDao().insertDeck(
                DeckEntity(
                    name = "Sync Regression Deck",
                    format = "Commander",
                    description = "Verifies FK survival across card refreshes",
                    colorIdentity = "W,U,B,G"
                )
            )
            db.deckDao().insertEntries(
                listOf(
                    DeckEntryEntity(deckId = deckId, cardId = cardId, quantity = 1, zone = DeckZones.COMMANDER),
                    DeckEntryEntity(deckId = deckId, cardId = cardId, quantity = 3, zone = DeckZones.SIDEBOARD)
                )
            )

            // Sync #2 with updated metadata on same card ID
            syncVersion = 2
            val secondSync = repo.syncFromScryfall("Atraxa", offlineOnlyMode = false)
            assertTrue(secondSync is SyncResult.Success)

            val updatedCard = db.cardDao().getCardById(cardId)
            assertNotNull(updatedCard)
            assertEquals(cardId, updatedCard?.id)
            assertEquals("Phyrexia: All Will Be One V2", updatedCard?.setName)
            assertTrue(updatedCard?.oracleText?.contains("V2 updated oracle text") == true)
            assertTrue(updatedCard?.isBookmarked == true)

            val deckAfterSecondSync = db.deckDao().getDeckWithEntriesOnce(deckId)
            assertNotNull(deckAfterSecondSync)
            assertEquals(2, deckAfterSecondSync!!.entries.size)
            val commanderEntry = deckAfterSecondSync.entries.first { it.entry.zone == DeckZones.COMMANDER }
            val sideboardEntry = deckAfterSecondSync.entries.first { it.entry.zone == DeckZones.SIDEBOARD }
            assertEquals(1, commanderEntry.entry.quantity)
            assertEquals(3, sideboardEntry.entry.quantity)

            // Also verify restoreStarterCatalog updates in place without cascading deletes
            val solRing = StarterCards.cards.first { it.id == "local-sol-ring" }
            repo.restoreStarterCatalog()
            repo.toggleBookmark(solRing.id, current = false)
            db.deckDao().upsertEntry(
                DeckEntryEntity(deckId = deckId, cardId = solRing.id, quantity = 2, zone = DeckZones.MAINBOARD)
            )
            repo.restoreStarterCatalog()
            val solRingAfterRestore = db.cardDao().getCardById(solRing.id)
            assertTrue(solRingAfterRestore?.isBookmarked == true)
            val mainboardSolRing = db.deckDao().getEntry(deckId, solRing.id, DeckZones.MAINBOARD)
            assertEquals(2, mainboardSolRing?.quantity)
        } finally {
            db.close()
        }
    }

    @Test
    fun `synergy analyzer identifies archetypes and card relationships`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LatticeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            StarterDecks.seedIfEmpty(db.cardDao(), db.deckDao())
            val decks = db.deckDao().observeAllDecksWithEntries().first()
            val latticeDeck = decks.first { it.deck.name == "Mycosynth Lattice Engine" }

            val report = SynergyAnalyzerEngine.analyzeDeck(latticeDeck)
            assertEquals("Artifact Lattice Combo", report.primaryArchetype?.template?.name)
            assertTrue(report.archetypeMatches.size == SynergyAnalyzerEngine.commonArchetypes.size)
            assertTrue(report.relationships.any { it.type == RelationshipType.COMBO_LOCK })
            assertTrue(
                report.relationships.any {
                    setOf(it.cardA.name, it.cardB.name) == setOf("Mycosynth Lattice", "Karn, the Great Creator")
                }
            )

            val izzetDeck = decks.first { it.deck.name == "Izzet Arcane Tempo" }
            val izzetReport = SynergyAnalyzerEngine.analyzeDeck(izzetDeck)
            assertTrue(
                izzetReport.primaryArchetype?.template?.id in setOf("spellslinger_tempo", "graveyard_delirium")
            )
            assertTrue(izzetReport.relationships.isNotEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `all six themes produce distinct palettes and OLED preferences persist`() = runTest {
        assertEquals(6, WubrgTheme.entries.size)
        val darkPrimaries = WubrgTheme.entries.map { colorSchemeForWubrg(it, isDark = true).primary }.toSet()
        val lightPrimaries = WubrgTheme.entries.map { colorSchemeForWubrg(it, isDark = false).primary }.toSet()
        assertEquals(6, darkPrimaries.size)
        assertEquals(6, lightPrimaries.size)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesRepository(context)
        prefs.setWubrgTheme(WubrgTheme.WUBRG)
        prefs.setThemeBrightnessMode(ThemeBrightnessMode.OLED)
        val oled = colorSchemeForWubrg(WubrgTheme.WUBRG, isDark = true).withOledSurfaces()
        assertEquals(Color.Black, oled.background)
        assertEquals(Color.Black, oled.surface)
        assertEquals(colorSchemeForWubrg(WubrgTheme.WUBRG, isDark = true).primary, oled.primary)
        prefs.setShowCardArt(false)

        assertEquals(WubrgTheme.WUBRG, prefs.wubrgTheme.first())
        assertEquals(ThemeBrightnessMode.OLED, prefs.themeBrightnessMode.first())
        assertFalse(prefs.showCardArt.first())

        prefs.resetAllPreferences()
        assertEquals(WubrgTheme.GREEN, prefs.wubrgTheme.first())
        assertEquals(ThemeBrightnessMode.SYSTEM, prefs.themeBrightnessMode.first())
    }

    @Test
    fun `hypergeometric probability computes expected range`() {
        val odds = DeckAnalytics.hypergeometricAtLeastK(
            population = 99,
            successes = 37,
            draws = 7,
            minHits = 2
        )
        assertTrue(odds in 0.70..0.90)
    }
}
