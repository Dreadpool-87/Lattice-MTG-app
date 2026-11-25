package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.local.CardEntity
import com.example.data.local.CardMetadataStatus
import com.example.data.local.ChatMessageEntity
import com.example.data.local.DeckEntity
import com.example.data.local.DeckEntryEntity
import com.example.data.local.DeckEntryWithCard
import com.example.data.local.DeckWithEntries
import com.example.data.local.DeckZones
import com.example.data.local.LatticeDatabase
import com.example.data.local.VaultCollectionCard
import com.example.data.local.VaultCollectionEntry
import com.example.data.remote.ScryfallApi
import com.example.data.remote.ScryfallCardDto
import com.example.data.remote.ScryfallRulingsResponse
import com.example.data.remote.ScryfallSearchResponse
import com.example.data.repository.CardRepository
import com.example.data.repository.DeckRepository
import com.example.data.repository.NetworkMonitor
import com.example.data.repository.PreferencesRepository
import com.example.domain.OracleDeckCheck
import com.example.ui.screens.OracleChatScreen
import com.example.ui.theme.LatticeMtgTheme
import com.example.ui.viewmodel.OracleChatViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class Step4OracleContextTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun dummyCard(index: Int): CardEntity = CardEntity(
        id = "sf-card-$index",
        oracleId = "ora-card-$index",
        scryfallId = "sf-card-$index",
        name = "Test Card $index",
        manaCost = "{1}{U}",
        cmc = (index % 5).toDouble(),
        typeLine = "Instant",
        oracleText = "Draw a card.",
        colors = "U",
        colorIdentity = "U",
        rarity = "common",
        setCode = "TST",
        setName = "Test Set",
        collectorNumber = "$index",
        lang = "en"
    )

    @Test
    fun deckExplanationCountsZonesAndFlagsCrossPrintingCopiesAndColors() {
        val commander = dummyCard(0)
        val land = dummyCard(1).copy(name = "Island", typeLine = "Basic Land — Island")
        val offColor = dummyCard(2).copy(colorIdentity = "R")
        val entries = listOf(
            Triple(commander, 1, DeckZones.COMMANDER),
            Triple(land, 97, DeckZones.MAINBOARD),
            Triple(offColor, 1, DeckZones.MAINBOARD),
            Triple(offColor.copy(id = "second-printing"), 1, DeckZones.MAINBOARD),
            Triple(dummyCard(3), 10, DeckZones.MAYBEBOARD)
        ).map { (card, quantity, zone) ->
            DeckEntryWithCard(DeckEntryEntity(deckId = 1L, cardId = card.id, quantity = quantity, zone = zone), card)
        }
        val check = OracleDeckCheck.analyze(DeckWithEntries(DeckEntity(name = "Test", format = "Commander"), entries))
        assertEquals(100, check.cards)
        assertEquals(97, check.lands)
        assertTrue(check.copyWarning)
        assertEquals("flagged", check.identity)
        assertEquals(listOf(offColor.name), check.offColorNames)
    }

    @Test
    fun deckExplanationDoesNotAssumeIdentityForUnknownCards() {
        val commander = dummyCard(0)
        val unknown = dummyCard(1).copy(oracleId = "", metadataStatus = CardMetadataStatus.UNRESOLVED)
        val entries = listOf(
            DeckEntryWithCard(DeckEntryEntity(deckId = 1L, cardId = commander.id, zone = DeckZones.COMMANDER), commander),
            DeckEntryWithCard(DeckEntryEntity(deckId = 1L, cardId = unknown.id), unknown)
        )
        val check = OracleDeckCheck.analyze(DeckWithEntries(DeckEntity(name = "Test", format = "Commander"), entries))
        assertEquals("incomplete", check.identity)
    }

    @Test
    fun `buildDeckContextPreview distinguishes complete vs truncated mainboard and omitted zones`() {
        val smallCards = (1..5).map { dummyCard(it) }
        val completeDeck = DeckWithEntries(
            deck = DeckEntity(id = 1L, name = "Compact Tempo", format = "Standard"),
            entries = smallCards.mapIndexed { idx, c ->
                DeckEntryWithCard(
                    DeckEntryEntity(id = idx.toLong() + 1, deckId = 1L, cardId = c.id, quantity = 2, zone = DeckZones.MAINBOARD),
                    c
                )
            }
        )
        val completePreview = OracleChatViewModel.buildDeckContextPreview(completeDeck)
        assertFalse(completePreview.isPartialContext)
        assertEquals(5, completePreview.includedMainboardSlots)
        assertEquals(5, completePreview.totalMainboardSlots)
        assertTrue(completePreview.scopeSummary.contains("Complete active mainboard sent"))

        // 42 unique mainboard entries -> capped at 35 -> partial
        val bigCards = (1..42).map { dummyCard(it) }
        val bigMainEntries = bigCards.mapIndexed { idx, c ->
            DeckEntryWithCard(
                DeckEntryEntity(id = idx.toLong() + 1, deckId = 2L, cardId = c.id, quantity = 1, zone = DeckZones.MAINBOARD),
                c
            )
        }
        val sideboardEntry = DeckEntryWithCard(
            DeckEntryEntity(id = 100L, deckId = 2L, cardId = bigCards.first().id, quantity = 3, zone = DeckZones.SIDEBOARD),
            bigCards.first()
        )
        val partialDeck = DeckWithEntries(
            deck = DeckEntity(id = 2L, name = "Large Singleton List", format = "Commander"),
            entries = bigMainEntries + sideboardEntry
        )
        val partialPreview = OracleChatViewModel.buildDeckContextPreview(partialDeck)
        assertTrue(partialPreview.isPartialContext)
        assertEquals(OracleChatViewModel.MAX_CONTEXT_MAINBOARD_SLOTS, partialPreview.includedMainboardSlots)
        assertEquals(42, partialPreview.totalMainboardSlots)
        assertEquals(3, partialPreview.omittedSideboardCount)
        assertTrue(partialPreview.scopeSummary.contains("Partial decklist sent (35/42"))
        assertTrue(partialPreview.scopeSummary.contains("3 sideboard cards"))
        assertTrue(partialPreview.scopeSummary.contains("do not validate the entire deck"))
    }

    @Test
    fun `oracle screen shows gemini disclosure before sending and flags partial deck context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LatticeDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val cards = (1..40).map { dummyCard(it) }
            val completeDeckId: Long
            val partialDeckId: Long
            runBlocking {
                db.cardDao().upsertAllPreservingState(cards)
                completeDeckId = db.deckDao().insertDeck(DeckEntity(name = "Small Complete Deck", format = "Standard"))
                partialDeckId = db.deckDao().insertDeck(DeckEntity(name = "Huge Partial Deck", format = "Commander"))

                // 3 cards in Small Complete Deck
                db.deckDao().insertEntries(
                    cards.take(3).map { c ->
                        DeckEntryEntity(deckId = completeDeckId, cardId = c.id, quantity = 4, zone = DeckZones.MAINBOARD)
                    }
                )
                // 40 cards in Huge Partial Deck (>35 limit)
                db.deckDao().insertEntries(
                    cards.map { c ->
                        DeckEntryEntity(deckId = partialDeckId, cardId = c.id, quantity = 1, zone = DeckZones.MAINBOARD)
                    }
                )
                db.chatDao().insertMessage(
                    ChatMessageEntity(
                        role = "model",
                        content = "Your included 1-drops look smooth, though late-game curve wasn't included.",
                        contextDeckName = "Huge Partial Deck",
                        isPartialContext = true,
                        contextScopeNote = "Partial decklist sent (35/40 mainboard entries). Replies do not validate the entire deck."
                    )
                )
            }

            val noOpApi = object : ScryfallApi {
                override suspend fun searchCards(query: String, order: String) = ScryfallSearchResponse(data = emptyList())
                override suspend fun getCardNamed(fuzzyName: String) = throw UnsupportedOperationException()
                override suspend fun getCardNamedExact(exactName: String, setCode: String?) = throw UnsupportedOperationException()
                override suspend fun getCardByScryfallId(scryfallId: String): ScryfallCardDto = throw UnsupportedOperationException()
                override suspend fun getCardBySetNumberLang(setCode: String, collectorNumber: String, lang: String) = throw UnsupportedOperationException()
                override suspend fun getCardBySetNumber(setCode: String, collectorNumber: String) = throw UnsupportedOperationException()
                override suspend fun getCardRulings(scryfallId: String) = ScryfallRulingsResponse(data = emptyList())
            }
            val onlineMonitor = object : NetworkMonitor(context) {
                override fun isCurrentlyOnline(): Boolean = true
            }
            val cardRepo = CardRepository(db.cardDao(), noOpApi, onlineMonitor)
            val deckRepo = DeckRepository(db.deckDao(), cardRepo)
            val vm = OracleChatViewModel(
                chatDao = db.chatDao(),
                deckRepository = deckRepo,
                cardRepository = cardRepo,
                networkMonitor = onlineMonitor,
                preferencesRepository = PreferencesRepository(context)
            )

            val ownedCandidate = cards[3].copy(legalFormats = "Standard")
            val collection = MutableStateFlow(mapOf(
                "Test inventory" to listOf(
                    VaultCollectionCard(VaultCollectionEntry("Test inventory", ownedCandidate.id, 2), ownedCandidate)
                )
            ))
            composeRule.setContent {
                LatticeMtgTheme {
                    OracleChatScreen(viewModel = vm, onOpenSettings = {}, collections = collection)
                }
            }

            composeRule.waitUntil(5_000) {
                ShadowLooper.idleMainLooper()
                vm.uiState.value.decks.size == 2 && vm.uiState.value.messages.isNotEmpty()
            }

            if (BuildConfig.ORACLE_DECK_PREVIEW) {
                composeRule.onNodeWithText("General MTG").assertDoesNotExist()
                composeRule.onNodeWithTag("oracle_bubble_partial_notice").assertDoesNotExist()
                composeRule.onNodeWithTag("oracle_context_deck_$completeDeckId").performClick()
                composeRule.onNodeWithText("Ask about this deck").performClick()
                composeRule.onNodeWithText("Small Complete Deck").assertIsDisplayed()
                composeRule.onNodeWithText("MTG question").assertIsDisplayed()
                composeRule.onNodeWithText("Close").performClick()
                composeRule.onNodeWithText("Explain deck check · Preview").assertIsDisplayed()
                composeRule.onNodeWithText("Kandidaten uit mijn collectie").performClick()
                composeRule.onNodeWithText("Test inventory").performClick()
                composeRule.onNodeWithText("Kies een referentiekaart").performScrollTo().performClick()
                composeRule.waitUntil(5_000) {
                    composeRule.onAllNodesWithText(cards[0].name).fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.onNodeWithText(cards[0].name).performClick()
                composeRule.onNodeWithText("Toon kandidaten").performClick()
                composeRule.waitUntil(5_000) {
                    composeRule.onAllNodesWithText(ownedCandidate.name).fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.onNodeWithText(ownedCandidate.name).assertIsDisplayed()
                composeRule.onNodeWithText("2 in collectie buiten dit deck").performScrollTo().assertIsDisplayed()
                return
            }

            // Prior partial reply renders its partial-context disclaimer inside the bubble
            composeRule.onNodeWithTag("oracle_bubble_partial_notice")
                .assertIsDisplayed()
                .assertTextContains("Partial decklist sent", substring = true)

            // With General MTG selected, no deck-send disclosure banner is shown
            composeRule.onNodeWithTag("oracle_deck_context_disclosure").assertDoesNotExist()

            // Select small complete deck -> disclosure banner tells user deck info will be sent to Gemini
            composeRule.onNodeWithTag("oracle_context_deck_$completeDeckId").performClick()
            composeRule.waitUntil(5_000) {
                ShadowLooper.idleMainLooper()
                vm.uiState.value.selectedContextDeckId == completeDeckId
            }
            composeRule.onNodeWithTag("oracle_deck_context_disclosure").assertIsDisplayed()
            composeRule.onNodeWithText("Sending \"Small Complete Deck\" (Standard) deck info to Gemini when you ask.")
                .assertIsDisplayed()
            composeRule.onNodeWithTag("oracle_partial_deck_notice").assertDoesNotExist()

            // Select huge partial deck -> disclosure + partial decklist warning appear
            composeRule.onNodeWithTag("oracle_context_deck_$partialDeckId").performClick()
            composeRule.waitUntil(5_000) {
                ShadowLooper.idleMainLooper()
                vm.uiState.value.selectedContextDeckId == partialDeckId
            }
            composeRule.onNodeWithTag("oracle_deck_context_disclosure").assertIsDisplayed()
            composeRule.onNodeWithText("Sending \"Huge Partial Deck\" (Commander) deck info to Gemini when you ask.")
                .assertIsDisplayed()
            composeRule.onNodeWithTag("oracle_partial_deck_notice")
                .assertIsDisplayed()
                .assertTextContains("35/40", substring = true)
            composeRule.onNodeWithTag("oracle_partial_deck_notice")
                .assertTextContains("do not validate the entire deck", substring = true)
        } finally {
            db.close()
        }
    }
}
