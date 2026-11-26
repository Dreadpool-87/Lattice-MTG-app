package com.example

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.local.CardEntity
import com.example.data.local.CardMetadataStatus
import com.example.data.local.DeckEntity
import com.example.data.local.DeckEntryEntity
import com.example.data.local.DeckEntryWithCard
import com.example.data.local.DeckWithEntries
import com.example.data.local.DeckZones
import com.example.data.local.StarterCards
import com.example.data.repository.ArtworkQuality
import com.example.data.repository.ArtworkSide
import com.example.data.repository.OfflineArtworkRepository
import com.example.domain.CardFaceInfo
import com.example.domain.CardJsonCodec
import com.example.domain.CardRulingInfo
import com.example.domain.DeckAnalytics
import com.example.ui.components.CardInspectorSheet
import com.example.ui.theme.LatticeMtgTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class Step3DataAndValidationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun deckValidation_countsCopiesAcrossPrintings_flagsUnresolvedAsUnknown_andDisclosesPartialChecks() {
        val boltPrintingA = CardEntity(
            id = "sf-bolt-sta",
            oracleId = "oracle-bolt-001",
            scryfallId = "sf-bolt-sta",
            name = "Lightning Bolt",
            manaCost = "{R}",
            cmc = 1.0,
            typeLine = "Instant",
            oracleText = "Lightning Bolt deals 3 damage to any target.",
            colors = "R",
            colorIdentity = "R",
            rarity = "rare",
            setCode = "STA",
            setName = "Strixhaven Mystical Archive",
            collectorNumber = "42",
            lang = "en",
            legalFormats = "Modern,Legacy,Vintage,Commander",
            metadataStatus = CardMetadataStatus.VERIFIED
        )
        val boltPrintingB = boltPrintingA.copy(
            id = "sf-bolt-2x2",
            scryfallId = "sf-bolt-2x2",
            setCode = "2X2",
            setName = "Double Masters 2022",
            collectorNumber = "117"
        )
        val mountainA = CardEntity(
            id = "sf-mountain-dmu",
            oracleId = "oracle-mountain",
            scryfallId = "sf-mountain-dmu",
            name = "Mountain",
            manaCost = "",
            cmc = 0.0,
            typeLine = "Basic Land — Mountain",
            oracleText = "{T}: Add {R}.",
            colors = "",
            colorIdentity = "R",
            rarity = "common",
            setCode = "DMU",
            setName = "Dominaria United",
            collectorNumber = "279",
            lang = "en",
            legalFormats = "Standard,Pioneer,Modern,Pauper,Legacy,Vintage,Commander"
        )
        val mountainB = mountainA.copy(
            id = "sf-mountain-tla",
            scryfallId = "sf-mountain-tla",
            setCode = "TLA",
            collectorNumber = "285"
        )
        val unresolvedCard = CardEntity(
            id = "offline-mystery-spell",
            name = "Mystery Offline Card",
            manaCost = "",
            cmc = 0.0,
            typeLine = "Unresolved Card (Offline Entry)",
            oracleText = "Unresolved offline entry.",
            colors = "",
            colorIdentity = "",
            rarity = "unknown",
            setCode = "LOCAL",
            setName = "Unresolved Offline Import",
            legalFormats = "Unknown",
            metadataStatus = CardMetadataStatus.UNRESOLVED
        )

        val modernDeck = DeckWithEntries(
            deck = DeckEntity(id = 1L, name = "Red Burn Check", format = "Modern"),
            entries = listOf(
                DeckEntryWithCard(DeckEntryEntity(id = 1, deckId = 1, cardId = boltPrintingA.id, quantity = 3, zone = DeckZones.MAINBOARD), boltPrintingA),
                DeckEntryWithCard(DeckEntryEntity(id = 2, deckId = 1, cardId = boltPrintingB.id, quantity = 2, zone = DeckZones.SIDEBOARD), boltPrintingB),
                DeckEntryWithCard(DeckEntryEntity(id = 3, deckId = 1, cardId = mountainA.id, quantity = 10, zone = DeckZones.MAINBOARD), mountainA),
                DeckEntryWithCard(DeckEntryEntity(id = 4, deckId = 1, cardId = mountainB.id, quantity = 10, zone = DeckZones.MAINBOARD), mountainB),
                DeckEntryWithCard(DeckEntryEntity(id = 5, deckId = 1, cardId = unresolvedCard.id, quantity = 1, zone = DeckZones.MAINBOARD), unresolvedCard)
            )
        )

        val summary = DeckAnalytics.analyze(modernDeck)
        val copyLimitNote = summary.validationNotes.firstOrNull { it.title == "4-Copy Limit Exceeded" }
        assertTrue(copyLimitNote != null && copyLimitNote.isWarning)
        assertTrue(copyLimitNote!!.detail.contains("Lightning Bolt (5x across 2 printings)"))
        assertFalse(copyLimitNote.detail.contains("Mountain"))

        val unresolvedNote = summary.validationNotes.firstOrNull {
            it.title.startsWith("Unresolved Cards — Legality Unknown")
        }
        assertTrue(unresolvedNote != null && unresolvedNote.isWarning)
        assertTrue(unresolvedNote!!.detail.contains("Mystery Offline Card"))

        val notLegalNote = summary.validationNotes.firstOrNull {
            it.title.startsWith("Not Listed Legal in Modern")
        }
        // Unresolved cards must NOT show up as "Not Listed Legal"; they are handled separately as Unknown.
        assertTrue(notLegalNote == null)

        val scopeNote = summary.validationNotes.firstOrNull {
            it.title == "Checks Performed (Partial Local Check)"
        }
        assertTrue(scopeNote != null && !scopeNote.isWarning)
        assertTrue(scopeNote!!.detail.contains("cross-printing 4-copy limit"))
        assertTrue(scopeNote.detail.contains("Not verified"))
    }

    @Test
    fun commanderValidation_detectsSingletonAcrossStarterAndScryfallPrintings() {
        val starterSolRing = CardEntity(
            id = "local-sol-ring",
            name = "Sol Ring",
            manaCost = "{1}",
            cmc = 1.0,
            typeLine = "Artifact",
            oracleText = "{T}: Add {C}{C}.",
            colors = "",
            colorIdentity = "",
            rarity = "uncommon",
            setCode = "CMM",
            setName = "Commander Masters",
            legalFormats = "Commander,Vintage"
        )
        val foilSolRing = starterSolRing.copy(
            id = "sf-sol-ring-c21",
            oracleId = "oracle-sol-ring",
            scryfallId = "sf-sol-ring-c21",
            setCode = "C21",
            collectorNumber = "263"
        )

        val edhDeck = DeckWithEntries(
            deck = DeckEntity(id = 2L, name = "EDH Duplicate Test", format = "Commander"),
            entries = listOf(
                DeckEntryWithCard(DeckEntryEntity(id = 1, deckId = 2, cardId = starterSolRing.id, quantity = 1, zone = DeckZones.MAINBOARD), starterSolRing),
                DeckEntryWithCard(DeckEntryEntity(id = 2, deckId = 2, cardId = foilSolRing.id, quantity = 1, zone = DeckZones.MAINBOARD), foilSolRing)
            )
        )

        val summary = DeckAnalytics.analyze(edhDeck)
        val singletonNote = summary.validationNotes.firstOrNull { it.title == "Singleton Limit Exceeded" }
        assertTrue(singletonNote != null && singletonNote.isWarning)
        assertTrue(singletonNote!!.detail.contains("Sol Ring (2x across 2 printings)"))
    }

    @Test
    fun cardInspectorSheet_rendersCardFaces_printingVariants_rulings_andUnknownLegality() {
        val facesJson = CardJsonCodec.encodeFaces(
            listOf(
                CardFaceInfo(
                    name = "Delver of Secrets",
                    manaCost = "{U}",
                    typeLine = "Creature — Human Wizard",
                    oracleText = "At the beginning of your upkeep, look at the top card of your library.",
                    power = "1",
                    toughness = "1"
                ),
                CardFaceInfo(
                    name = "Insectile Aberration",
                    manaCost = "",
                    typeLine = "Creature — Human Insect",
                    oracleText = "Flying",
                    power = "3",
                    toughness = "2"
                )
            )
        )
        val rulings = listOf(
            CardRulingInfo(
                publishedAt = "2011-09-22",
                source = "wotc",
                comment = "You may look at the top card of your library only once per upkeep trigger."
            )
        )
        val mainCard = CardEntity(
            id = "sf-delver-isd",
            oracleId = "oracle-delver-id",
            scryfallId = "scryfall-delver-isd",
            name = "Delver of Secrets // Insectile Aberration",
            manaCost = "{U}",
            cmc = 1.0,
            typeLine = "Creature — Human Wizard // Creature — Human Insect",
            oracleText = "",
            colors = "U",
            colorIdentity = "U",
            rarity = "common",
            setCode = "ISD",
            setName = "Innistrad",
            collectorNumber = "51",
            lang = "en",
            legalFormats = "Unknown",
            metadataStatus = CardMetadataStatus.UNRESOLVED,
            cardFacesJson = facesJson,
            rulingsJson = CardJsonCodec.encodeRulings(rulings)
        )
        val variantCard = mainCard.copy(
            id = "sf-delver-mid",
            scryfallId = "scryfall-delver-mid",
            setCode = "MID",
            setName = "Innistrad: Midnight Hunt",
            collectorNumber = "47"
        )

        composeRule.setContent {
            LatticeMtgTheme {
                CardInspectorSheet(
                    card = mainCard,
                    decks = emptyList(),
                    isOnline = false,
                    showCardArt = false,
                    onDismiss = {},
                    onToggleBookmark = {},
                    onAddToDeck = { _, _ -> },
                    printingVariants = listOf(mainCard, variantCard),
                    rulings = rulings,
                    isLoadingRulings = false
                )
            }
        }

        composeRule.onNodeWithTag("card_faces_section").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Face 1: Delver of Secrets").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Face 2: Insectile Aberration").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("card_legality_status").performScrollTo()
            .assertTextContains("Unknown", substring = true)

        composeRule.onNodeWithTag("card_identifiers_section").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Scryfall Printing ID: scryfall-delver-isd", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Oracle Identity ID: oracle-delver-id", substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag("card_printing_variants_section").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("MID #47 (EN)").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("sheet_open_rulings").performScrollTo().performClick()
        composeRule.onNodeWithTag("card_rulings_section").assertIsDisplayed()
        composeRule.onNodeWithText("2011-09-22 • WOTC").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cardInspectorSwipesBetweenCardsAndEnlargesTheSelectedSide() {
        val starter = StarterCards.cards.first()
        val twoSided = starter.copy(
            name = "Two-sided test card",
            scryfallId = "e09573a5-779b-482b-9349-c3bdba770d2c",
            imageUri = null,
            cardFacesJson = CardJsonCodec.encodeFaces(listOf(
                CardFaceInfo("Front"),
                CardFaceInfo("Back")
            ))
        )
        val neighbor = starter.copy(id = "neighbor", name = "Neighbor test card", imageUri = null)
        val selected = mutableStateOf(twoSided)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val artworkDirectory = File(context.filesDir, "offline-card-art").apply { mkdirs() }
        ArtworkSide.entries.forEach { side ->
            val filename = OfflineArtworkRepository.artworkFilename(twoSided.scryfallId, side, ArtworkQuality.SD)
            context.resources.openRawResource(R.drawable.img_empty_deckbox).use { image ->
                File(artworkDirectory, filename).outputStream().use { image.copyTo(it) }
            }
        }

        composeRule.setContent {
            LatticeMtgTheme {
                CardInspectorSheet(
                    card = selected.value,
                    decks = emptyList(),
                    isOnline = false,
                    showCardArt = true,
                    onDismiss = { error("Scrolling must not close card details") },
                    onToggleBookmark = {},
                    onAddToDeck = { _, _ -> },
                    onPrevious = if (selected.value == neighbor) ({ selected.value = twoSided }) else null,
                    onNext = if (selected.value == twoSided) ({ selected.value = neighbor }) else null
                )
            }
        }

        val details = composeRule.onNodeWithTag("sheet_card_details")
        details.performTouchInput { swipeDown() }
        details.assertIsDisplayed()
        details.performTouchInput { swipeLeft() }
        composeRule.onNodeWithText(neighbor.name).assertIsDisplayed()
        details.performTouchInput { swipeRight() }
        composeRule.onNodeWithText("Show back").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("${twoSided.name} · back").performScrollTo().performClick()
        composeRule.onNodeWithTag("sheet_enlarged_art").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close enlarged image").performClick()
        composeRule.onNodeWithText("Show front").assertIsDisplayed()
    }
    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h640dp")
    fun cardInspectorScrollsPastTheEntireLastRulingWithNavigationInsets() {
        val lastComment = "If the target is still legal as the spell resolves but the spell can't be " +
            "countered, its controller still creates two Treasure tokens."
        val rulings = (1..8).map { index ->
            CardRulingInfo("2022-04-29", comment = "Ruling $index. " + lastComment)
        } + CardRulingInfo("2022-04-29", comment = lastComment)

        composeRule.setContent {
            LatticeMtgTheme {
                CardInspectorSheet(
                    card = StarterCards.cards.first(),
                    decks = emptyList(),
                    isOnline = false,
                    showCardArt = false,
                    onDismiss = {},
                    onToggleBookmark = {},
                    onAddToDeck = { _, _ -> },
                    rulings = rulings
                )
            }
        }
        composeRule.onNodeWithTag("sheet_open_rulings").performClick()
        composeRule.runOnIdle {
            val window = checkNotNull(ShadowDialog.getLatestDialog().window)
            val navBarHeight = (72 * window.decorView.resources.displayMetrics.density).toInt()
            ViewCompat.dispatchApplyWindowInsets(
                window.decorView,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navBarHeight))
                    .build()
            )
        }
        val scrollArea = composeRule.onNodeWithTag("card_rulings_list")
        scrollArea.performScrollToIndex(rulings.lastIndex)

        val lastRuling = composeRule.onNodeWithText(lastComment).assertIsDisplayed()
        assertTrue(lastRuling.getBoundsInRoot() == lastRuling.getUnclippedBoundsInRoot())
        val listBottom = scrollArea.getBoundsInRoot().bottom
        assertTrue(listBottom - lastRuling.getBoundsInRoot().bottom >= 28.dp)
        composeRule.onNodeWithContentDescription("Back to card details").performClick()
        composeRule.onNodeWithTag("sheet_card_details").assertIsDisplayed()
        composeRule.onNodeWithTag("sheet_open_rulings").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w320dp-h480dp")
    fun addToDeckConfirmationStaysFullyVisibleAndUsesTheSelectedZone() {
        val deck = DeckWithEntries(DeckEntity(id = 42, name = "Test deck", format = "Modern"), emptyList())
        var added: Pair<Long, String>? = null
        var dismissed = false
        composeRule.setContent {
            LatticeMtgTheme {
                CardInspectorSheet(
                    card = StarterCards.cards.first(),
                    decks = listOf(deck),
                    isOnline = false,
                    showCardArt = false,
                    onDismiss = { dismissed = true },
                    onToggleBookmark = {},
                    onAddToDeck = { deckId, zone -> added = deckId to zone }
                )
            }
        }
        composeRule.onNodeWithTag("sheet_open_deck_picker").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Sideboard").performScrollTo().performClick()
        val confirm = composeRule.onNodeWithTag("sheet_add_to_deck_button").assertIsDisplayed()
        assertTrue(confirm.getBoundsInRoot() == confirm.getUnclippedBoundsInRoot())
        composeRule.onNodeWithText("Add 1x to Sideboard").assertIsDisplayed()
        confirm.performClick()
        composeRule.runOnIdle {
            assertTrue(added == (42L to DeckZones.SIDEBOARD))
            assertTrue(dismissed)
        }
    }

}
