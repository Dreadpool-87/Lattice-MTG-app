package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LatticeDatabase
import com.example.data.local.StarterDecks
import com.example.data.repository.PreferencesRepository
import com.example.domain.DeckAnalytics
import com.example.domain.RelationshipType
import com.example.domain.SynergyAnalyzerEngine
import com.example.ui.theme.ThemeBrightnessMode
import com.example.ui.theme.WubrgTheme
import com.example.ui.theme.colorSchemeForWubrg
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
        assertEquals("Lattice MTG", appName)
    }

    @Test
    fun `offline room database seeds starter cards and decks`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LatticeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            StarterDecks.seedIfEmpty(db.cardDao(), db.deckDao())

            val latticeMatches = db.cardDao().searchCards("Mycosynth").first()
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
    fun `all five wubrg themes produce distinct palettes and persist in preferences`() = runTest {
        assertEquals(5, WubrgTheme.entries.size)
        val darkPrimaries = WubrgTheme.entries.map { colorSchemeForWubrg(it, isDark = true).primary }.toSet()
        val lightPrimaries = WubrgTheme.entries.map { colorSchemeForWubrg(it, isDark = false).primary }.toSet()
        assertEquals(5, darkPrimaries.size)
        assertEquals(5, lightPrimaries.size)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesRepository(context)
        prefs.setWubrgTheme(WubrgTheme.RED)
        prefs.setThemeBrightnessMode(ThemeBrightnessMode.DARK)
        prefs.setShowCardArt(false)

        assertEquals(WubrgTheme.RED, prefs.wubrgTheme.first())
        assertEquals(ThemeBrightnessMode.DARK, prefs.themeBrightnessMode.first())
        assertFalse(prefs.showCardArt.first())

        prefs.resetAllPreferences()
        assertEquals(WubrgTheme.GREEN, prefs.wubrgTheme.first())
        assertNotEquals(ThemeBrightnessMode.DARK, prefs.themeBrightnessMode.first())
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
