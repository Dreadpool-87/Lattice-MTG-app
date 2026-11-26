package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CardMetadataStatus
import com.example.data.local.DeckEntity
import com.example.data.local.DeckEntryEntity
import com.example.data.local.DeckZones
import com.example.data.local.LatticeDatabase
import com.example.data.local.StarterCards
import com.example.data.remote.ScryfallApi
import com.example.data.remote.ScryfallCardDto
import com.example.data.remote.ScryfallImageUrisDto
import com.example.data.remote.ScryfallRulingsResponse
import com.example.data.remote.ScryfallSearchResponse
import com.example.data.repository.CardRepository
import com.example.data.repository.DeckCardLookupException
import com.example.data.repository.DeckRepository
import com.example.data.repository.NetworkMonitor
import com.example.data.repository.PreferencesRepository
import com.example.domain.CsvRowMatchStatus
import com.example.domain.DelverCsvImporter
import com.example.ui.screens.VaultScreen
import com.example.ui.screens.DecksScreen
import com.example.ui.screens.DeckDetailScreen
import com.example.ui.screens.PlaytestGoldfishTab
import com.example.ui.screens.CatalogCardInspector
import com.example.ui.theme.LatticeMtgTheme
import com.example.ui.theme.WubrgTheme
import com.example.ui.viewmodel.VaultViewModel
import com.example.ui.viewmodel.DecksViewModel
import com.example.ui.viewmodel.DeckDetailViewModel
import com.example.ui.viewmodel.BattlefieldPermanent
import com.example.ui.viewmodel.PlaytestHandState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step2ImportAndMigrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun createAbandonAttachmentsApi(
        failFuzzyLookup: Boolean = true,
        remoteLookupFailure: HttpException? = null,
        namedInSet: Map<Pair<String, String?>, ScryfallCardDto> = emptyMap()
    ): ScryfallApi {
        val abandonAttachmentsId = "aaaaaaaa-1111-4444-8888-bbbbbbbbbbbb"
        val abandonAttachmentsOracleId = "99999999-0000-4444-8888-cccccccccccc"
        val dto = ScryfallCardDto(
            id = abandonAttachmentsId,
            oracleId = abandonAttachmentsOracleId,
            name = "Abandon Attachments",
            lang = "en",
            manaCost = "{1}{U/R}",
            cmc = 2.0,
            typeLine = "Instant — Lesson",
            oracleText = "Discard a card, then draw two cards.",
            colors = listOf("U", "R"),
            colorIdentity = listOf("U", "R"),
            rarity = "common",
            set = "tla",
            setName = "Avatar: The Last Airbender",
            collectorNumber = "205",
            imageUris = ScryfallImageUrisDto(normal = "https://cards.scryfall.io/test.jpg"),
            keywords = emptyList(),
            legalities = mapOf("standard" to "legal", "commander" to "legal", "pauper" to "legal")
        )
        return object : ScryfallApi {
            override suspend fun searchCards(query: String, order: String): ScryfallSearchResponse =
                ScryfallSearchResponse()

            override suspend fun getCardNamed(fuzzyName: String): ScryfallCardDto {
                if (failFuzzyLookup) error("Fuzzy matching must NOT be used during CSV import!")
                error("Card not found")
            }

            override suspend fun getCardNamedExact(exactName: String, setCode: String?): ScryfallCardDto {
                if (remoteLookupFailure != null) throw remoteLookupFailure
                namedInSet[exactName to setCode]?.let { return it }
                if (exactName.equals("Abandon Attachments", ignoreCase = true)) return dto
                throw HttpException(Response.error<ScryfallCardDto>(404, "".toResponseBody()))
            }

            override suspend fun getCardByScryfallId(scryfallId: String): ScryfallCardDto {
                if (scryfallId.equals(abandonAttachmentsId, ignoreCase = true)) return dto
                throw HttpException(Response.error<ScryfallCardDto>(404, "".toResponseBody()))
            }

            override suspend fun getCardBySetNumberLang(
                setCode: String,
                collectorNumber: String,
                lang: String
            ): ScryfallCardDto {
                if (setCode.equals("tla", ignoreCase = true) &&
                    collectorNumber == "205" &&
                    lang.equals("en", ignoreCase = true)
                ) {
                    return dto
                }
                throw HttpException(Response.error<ScryfallCardDto>(404, "".toResponseBody()))
            }

            override suspend fun getCardBySetNumber(setCode: String, collectorNumber: String): ScryfallCardDto {
                if (setCode.equals("tla", ignoreCase = true) && collectorNumber == "205") return dto
                throw HttpException(Response.error<ScryfallCardDto>(404, "".toResponseBody()))
            }

            override suspend fun getCardRulings(scryfallId: String): ScryfallRulingsResponse =
                ScryfallRulingsResponse()
        }
    }

    @Test
    fun `playtest groups manual effects and opens a separate action log`() {
        val card = StarterCards.cards.first()
        var extraDraws = 0
        var turns = 0
        composeRule.setContent {
            LatticeMtgTheme {
                PlaytestGoldfishTab(
                    playtest = PlaytestHandState(
                        library = listOf(card),
                        hand = listOf(card),
                        battlefield = listOf(BattlefieldPermanent(1, card)),
                        actionLog = listOf("Dealt opening hand.")
                    ),
                    onDealSeven = {},
                    onMulligan = {},
                    onDrawCard = { extraDraws++ },
                    onPlayCard = {},
                    onPutBottom = {},
                    onNextTurn = { turns++ }
                )
            }
        }
        composeRule.onNodeWithText("Battlefield (1)").assertIsDisplayed()
        composeRule.onNodeWithText("Hand (1)").assertIsDisplayed()
        composeRule.onNodeWithTag("playtest_draw_button").assertDoesNotExist()
        composeRule.onNodeWithTag("playtest_lazy_list").performScrollToNode(hasTestTag("playtest_next_turn_button"))
        composeRule.onNodeWithTag("playtest_next_turn_button").performClick()
        composeRule.onNodeWithTag("playtest_card_effects_button").performClick()
        composeRule.onNodeWithTag("playtest_draw_button").performClick()
        composeRule.onNodeWithTag("playtest_draw_button").assertDoesNotExist()
        composeRule.onNodeWithTag("playtest_lazy_list").performScrollToNode(hasTestTag("playtest_action_log_button"))
        composeRule.onNodeWithTag("playtest_action_log_button").performClick()
        composeRule.onNodeWithText("Dealt opening hand.").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(1, turns)
            assertEquals(1, extraDraws)
        }
    }

    @Test
    fun `playtest marks spells payable only while the matching source is available`() {
        val island = StarterCards.cards.first().copy(name = "Island", typeLine = "Basic Land — Island", oracleText = "")
        val spell = island.copy(name = "Test spell", typeLine = "Instant", manaCost = "{U}")
        val land = BattlefieldPermanent(1, island, tapped = true)
        val state = mutableStateOf(PlaytestHandState(hand = listOf(spell), battlefield = listOf(land)))
        composeRule.setContent {
            LatticeMtgTheme {
                PlaytestGoldfishTab(
                    playtest = state.value,
                    onDealSeven = {}, onMulligan = {}, onDrawCard = {}, onPlayCard = {}, onPutBottom = {}
                )
            }
        }
        composeRule.onNodeWithText("Need mana").assertIsNotEnabled()
        composeRule.onNodeWithText("Produces").assertIsDisplayed()
        composeRule.runOnIdle { state.value = state.value.copy(battlefield = listOf(land.copy(tapped = false))) }
        composeRule.onNodeWithText("Cast").assertIsEnabled()
    }

    @Test
    fun `ambiguous deck names require a printing and ownership needs printing evidence`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LatticeDatabase::class.java).build()
        try {
            val firstId = "aaaaaaaa-1111-4444-8888-bbbbbbbbbbbb"
            val secondId = "bbbbbbbb-1111-4444-8888-aaaaaaaaaaaa"
            val cardDao = db.cardDao()
            val deckDao = db.deckDao()
            val first = StarterCards.cards.first().copy(
                id = "sf-$firstId",
                name = "Shared Name",
                setCode = "TLA",
                collectorNumber = "205",
                lang = "en",
                oracleId = "aaaaaaaa-2222-4444-8888-bbbbbbbbbbbb",
                scryfallId = firstId,
                metadataStatus = CardMetadataStatus.VERIFIED
            )
            val second = first.copy(
                id = "sf-$secondId",
                setCode = "FDN",
                collectorNumber = "86",
                oracleId = "bbbbbbbb-2222-4444-8888-aaaaaaaaaaaa",
                scryfallId = secondId
            )
            cardDao.upsertAllPreservingState(listOf(first, second))
            val cards = CardRepository(cardDao, createAbandonAttachmentsApi(), NetworkMonitor(context))
            val decks = DeckRepository(deckDao, cards)

            val ambiguous = runCatching {
                decks.importDeckFromText("Ambiguous", "Casual", "4 Shared Name", offlineOnlyMode = true)
            }.exceptionOrNull()
            assertTrue(ambiguous is DeckCardLookupException)
            assertEquals(0, deckDao.getDeckCount())

            val resolved = decks.importDeckFromText(
                "Exact", "Casual", "4 Shared Name (FDN) 86", offlineOnlyMode = true
            )
            val entry = checkNotNull(deckDao.getDeckWithEntriesOnce(resolved.deckId)).mainboardEntries.single()
            assertEquals(second.id, entry.card.id)
            assertEquals(4, entry.entry.quantity)

            val catalogCard = first.copy(id = "catalog-card-id", name = "Other Card", scryfallId = "other-printing-id")
            cardDao.upsertAllPreservingState(
                listOf(catalogCard, catalogCard.copy(id = "local-other-card", oracleId = "", scryfallId = ""))
            )
            assertEquals(catalogCard.id, cards.resolveOrCreateCardByName("Other Card", offlineOnlyMode = true).id)

            val report = cards.importDelverCsv(
                "Quantity,Name,Set,Collector Number\n1,Shared Name,,\n2,Shared Name,FDN,86",
                offlineOnlyMode = true,
                requireExactPrinting = true
            )
            assertEquals(1, report.unresolvedRows.size)
            assertEquals(second.id, report.verifiedRows.single().resolvedCard?.id)
        } finally {
            db.close()
        }
    }

    @Test
    fun `custom card deletion preserves a referring deck`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LatticeDatabase::class.java).build()
        try {
            val card = StarterCards.cards.first().copy(
                id = "custom-delete-test",
                isCustom = true,
                metadataStatus = CardMetadataStatus.CUSTOM
            )
            db.cardDao().insertIgnore(card)
            val deckDao = db.deckDao()
            val deckId = deckDao.insertDeckWithEntries(
                DeckEntity(name = "Keep my cards", format = "Casual"),
                listOf(DeckEntryEntity(deckId = 0, cardId = card.id, zone = DeckZones.MAINBOARD, quantity = 1))
            )

            assertEquals(0, db.cardDao().deleteUnusedCustomCard(card.id))
            assertNotNull(deckDao.getDeckWithEntriesOnce(deckId)?.mainboardEntries?.single())
            deckDao.deleteDeckById(deckId)
            assertEquals(1, db.cardDao().deleteUnusedCustomCard(card.id))
            assertNull(db.cardDao().getCardById(card.id))
        } finally {
            db.close()
        }
    }

    @Test
    fun `rate limiting preserves local CSV matches and reports the unavailable row`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LatticeDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val monitor = object : NetworkMonitor(context) {
                override fun isCurrentlyOnline() = true
            }
            val failure = HttpException(Response.error<ScryfallCardDto>(429, "".toResponseBody()))
            val api = createAbandonAttachmentsApi(remoteLookupFailure = failure)
            db.cardDao().storeCatalogPrinting(api.getCardBySetNumber("tla", "205").toCardEntity())
            val repo = CardRepository(db.cardDao(), api, monitor)
            val report = repo.importDelverCsv(
                "Quantity,Name,Set,Collector Number\n1,Refute,FDN,\n2,Abandon Attachments,TLA,205",
                offlineOnlyMode = false
            )
            assertEquals(1, report.verifiedRows.size)
            assertEquals(2, report.totalVerifiedCopies)
            assertEquals(1, report.unresolvedRows.size)
            assertTrue(report.lookupNotice.orEmpty().contains("rate limiting"))
            val exact = api.getCardBySetNumber("tla", "205").copy(
                id = "53a4e402-2c9f-439f-9e12-6c7af5cc6200",
                name = "Set package match",
                set = "fdn",
                collectorNumber = "77"
            ).toCardEntity()
            val packReview = DelverCsvImporter.matchAndImportRows(
                rows = DelverCsvImporter.parseCsv("Quantity,Name,Set,Collector Number\n1,Set package match,FDN,77"),
                cardDao = db.cardDao(),
                scryfallApi = null,
                canFetchOnline = true,
                requireExactPrinting = true,
                loadPrintingSet = { set ->
                    assertEquals("fdn", set)
                    db.cardDao().storeCatalogPrinting(exact)
                },
                keepLocalOnLookupError = true
            )
            assertEquals(exact.scryfallId, packReview.verifiedRows.single().resolvedCard?.scryfallId)
        } finally {
            db.close()
        }
    }

    @Test
    fun `deck CSV stays out of collections and deletion requires confirmation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LatticeDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val monitor = object : NetworkMonitor(context) {
                override fun isCurrentlyOnline() = true
            }
            val cards = CardRepository(db.cardDao(), createAbandonAttachmentsApi(), monitor)
            val decks = DeckRepository(db.deckDao(), cards)
            val prefs = PreferencesRepository(context)
            val model = DecksViewModel(decks, cards, monitor, prefs)
            val vault = VaultViewModel(cards, decks, monitor, prefs, db.vaultCollectionDao())
            composeRule.setContent {
                LatticeMtgTheme(wubrgTheme = WubrgTheme.WUBRG) {
                    val host = remember { SnackbarHostState() }
                    val openDeck = remember { mutableStateOf<Long?>(null) }
                    val deckId = openDeck.value
                    if (deckId == null) {
                        DecksScreen(model, host, onOpenDeck = { openDeck.value = it }, onOpenSettings = {})
                    } else {
                        val detail = remember(deckId) { DeckDetailViewModel(deckId, decks, cards) }
                        DeckDetailScreen(
                            detail, host, onBack = { openDeck.value = null },
                            onOpenSynergyAnalyzer = {},
                            cardInspector = { cards, card, close -> CatalogCardInspector(vault, card, close, cards, allowAddToDeck = false) }
                        )
                    }
                }
            }

            composeRule.onNodeWithTag("decks_header").assertIsDisplayed()
            composeRule.onNodeWithText("Example decks").assertDoesNotExist()
            for (band in 0..4) {
                composeRule.onNodeWithTag("wubrg_band_$band", useUnmergedTree = true).assertExists()
            }
            composeRule.onNodeWithTag("import_deck_button").performClick()
            composeRule.onNodeWithTag("open_deck_csv_import").performClick()
            composeRule.onNodeWithTag("pick_csv_file_button").assertIsEnabled()
            val input = composeRule.onNodeWithTag("csv_import_text_input")
            val validCsv = "Quantity,Name,Set\n2,Abandon Attachments,TLA"
            input.performTextReplacement("$validCsv\n1,Unknown test card,FDN")
            composeRule.onNodeWithTag("run_csv_import_button").performClick()
            composeRule.waitUntil(timeoutMillis = 8_000) {
                ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
                model.csvImport.value.error == "Some rows could not be verified. No deck was saved."
            }
            assertTrue(runBlocking { decks.allDecks.first().isEmpty() })

            input.performTextReplacement(validCsv)
            composeRule.onNodeWithTag("run_csv_import_button").performClick()
            composeRule.waitUntil(timeoutMillis = 8_000) {
                ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
                model.uiState.value.decks.size == 1 && !model.csvImport.value.isImporting
            }
            val deck = model.uiState.value.decks.single()
            assertEquals(2, deck.entries.single().entry.quantity)
            assertTrue(runBlocking { db.vaultCollectionDao().observeCards().first().isEmpty() })

            val inspect = composeRule.onNodeWithTag("inspect_deck_card_${deck.entries.single().card.id}")
            composeRule.waitUntil(timeoutMillis = 8_000) {
                ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
                runCatching { inspect.assertExists() }.isSuccess
            }
            inspect.performClick()
            composeRule.onNodeWithTag("sheet_close_button").assertIsDisplayed()
            composeRule.onNodeWithTag("sheet_card_art").assertExists()
            composeRule.onNodeWithTag("sheet_open_deck_picker").assertDoesNotExist()
            composeRule.onNodeWithTag("sheet_close_button").performClick()
            composeRule.onNodeWithTag("deck_detail_back_button").performClick()

            val deleteTag = "delete_deck_${deck.deck.id}"
            composeRule.onNodeWithTag("decks_list").performScrollToNode(hasTestTag(deleteTag))
            composeRule.onNodeWithTag(deleteTag, useUnmergedTree = true).performClick()
            composeRule.onNodeWithText("Delete deck?").assertIsDisplayed()
            composeRule.onNodeWithText("Cancel").performClick()
            assertEquals(1, runBlocking { decks.allDecks.first().size })

            composeRule.onNodeWithTag(deleteTag, useUnmergedTree = true).performClick()
            composeRule.onNodeWithTag("confirm_delete_deck_button").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
                model.uiState.value.decks.isEmpty()
            }
            assertTrue(runBlocking { decks.allDecks.first().isEmpty() })
        } finally {
            db.close()
        }
    }

    @Test
    fun `CSV searches a supplied set even when a different printing is cached`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LatticeDatabase::class.java).build()
        try {
            val foundations = ScryfallCardDto(
                id = "11111111-2222-4333-8444-555555555555",
                name = "Valorous Stance",
                set = "fdn",
                collectorNumber = "583"
            )
            db.cardDao().upsertPreservingState(
                foundations.copy(id = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee", set = "trc").toCardEntity()
            )
            val rows = DelverCsvImporter.parseCsv("Quantity,Card Name,Set Code\n2,Valorous Stance,FDN")
            val api = createAbandonAttachmentsApi(namedInSet = mapOf(("Valorous Stance" to "fdn") to foundations))
            val report = DelverCsvImporter.matchAndImportRows(rows, db.cardDao(), api, canFetchOnline = true)
            assertEquals(2, report.totalVerifiedCopies)
            assertEquals("FDN", report.verifiedRows.single().resolvedCard?.setCode)
            assertEquals("583", report.verifiedRows.single().resolvedCard?.collectorNumber)
        } finally {
            db.close()
        }
    }

    @Test
    fun `CSV accepts Token suffix only on a token with the exact printing ID`() {
        val printingId = "11111111-2222-4333-8444-555555555555"
        val token = ScryfallCardDto(
            id = printingId,
            name = "Ajani's Pridemate",
            set = "tfra",
            collectorNumber = "2",
            typeLine = "Token Creature — Cat Soldier"
        ).toCardEntity()
        val row = DelverCsvImporter.parseCsv(
            "Name,Quantity,Edition code,Collector's number,Scryfall ID\nAjani's Pridemate Token,1,TFRA,2,$printingId"
        ).single()
        assertTrue(DelverCsvImporter.verifySuppliedFields(row, token).isEmpty())
        assertTrue(DelverCsvImporter.verifySuppliedFields(row.copy(scryfallId = null), token).isNotEmpty())
        assertTrue(
            DelverCsvImporter.verifySuppliedFields(row, token.copy(typeLine = "Creature — Cat Soldier")).isNotEmpty()
        )
    }

    @Test
    fun `delver csv verifies Abandon Attachments TLA 205 English qty 2 and flags conflicting set without fuzzy fallback`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LatticeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val abandonPrintingId = "aaaaaaaa-1111-4444-8888-bbbbbbbbbbbb"
            val abandonOracleId = "99999999-0000-4444-8888-cccccccccccc"
            val onlineMonitor = object : NetworkMonitor(context) {
                override fun isCurrentlyOnline(): Boolean = true
            }
            val repo = CardRepository(
                cardDao = db.cardDao(),
                scryfallApi = createAbandonAttachmentsApi(failFuzzyLookup = true),
                networkMonitor = onlineMonitor
            )

            // 1. Verify parenthesized set code (TLA), collector number 205, English, Scryfall printing ID, quantity 2
            val verifiedCsv = """
                Quantity,Name,Edition (code),Collector's number,Language,Scryfall ID
                2,"Abandon Attachments","(TLA)",205,English,$abandonPrintingId
            """.trimIndent()

            val report = repo.importDelverCsv(verifiedCsv, offlineOnlyMode = false)
            assertEquals(1, report.verifiedRows.size)
            assertEquals(0, report.conflictRows.size)
            assertEquals(0, report.unresolvedRows.size)
            assertEquals(2, report.totalVerifiedCopies)

            val savedCard = db.cardDao().findByScryfallId(abandonPrintingId)
            assertNotNull(savedCard)
            assertEquals("Abandon Attachments", savedCard?.name)
            assertEquals("TLA", savedCard?.setCode)
            assertEquals("205", savedCard?.collectorNumber)
            assertEquals("en", savedCard?.lang)
            assertEquals(abandonPrintingId, savedCard?.scryfallId)
            assertEquals(abandonOracleId, savedCard?.oracleId)
            assertNotEquals(savedCard?.oracleId, savedCard?.scryfallId)

            // 2. Conflicting set code with same Scryfall printing ID -> visible mismatch, NOT accepted
            val conflictingCsv = """
                Quantity,Name,Edition (code),Collector's number,Language,Scryfall ID
                2,"Abandon Attachments",(MH3),205,English,$abandonPrintingId
            """.trimIndent()

            val conflictReport = repo.importDelverCsv(conflictingCsv, offlineOnlyMode = false)
            assertEquals(0, conflictReport.verifiedRows.size)
            assertEquals(1, conflictReport.conflictRows.size)
            assertEquals(0, conflictReport.totalVerifiedCopies)
            val conflictRow = conflictReport.conflictRows.first()
            assertEquals(CsvRowMatchStatus.CONFLICT, conflictRow.status)
            assertTrue(conflictRow.mismatchReasons.any { it.contains("Set mismatch") && it.contains("MH3") })
            assertNull(db.cardDao().findByPrintingExact("MH3", "205", "en"))

            // 3. Unresolved row -> flagged as UNRESOLVED, never fuzzy-substituted
            val unresolvedCsv = """
                Quantity,Name,Edition code,Collector's number,Language,Scryfall ID
                1,"Abandn Attchmnts",(TLA),999,English,
            """.trimIndent()
            val unresolvedReport = repo.importDelverCsv(unresolvedCsv, offlineOnlyMode = false)
            assertEquals(0, unresolvedReport.verifiedRows.size)
            assertEquals(1, unresolvedReport.unresolvedRows.size)
            assertTrue(
                unresolvedReport.unresolvedRows.first().statusDetail.contains("Fuzzy substitution disabled")
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun `database migration 1 to 2 preserves cards bookmarks decks and marks offline stubs unresolved`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_v1_v2_test.db"
        val dbFile = context.getDatabasePath(dbName)
        if (dbFile.exists()) dbFile.delete()

        // Create authentic v1 SQLite schema & user data manually
        val rawDb: SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        rawDb.execSQL("PRAGMA foreign_keys = ON")
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cards` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `manaCost` TEXT NOT NULL,
                `cmc` REAL NOT NULL,
                `typeLine` TEXT NOT NULL,
                `oracleText` TEXT NOT NULL,
                `colors` TEXT NOT NULL,
                `colorIdentity` TEXT NOT NULL,
                `rarity` TEXT NOT NULL,
                `setCode` TEXT NOT NULL,
                `setName` TEXT NOT NULL,
                `power` TEXT,
                `toughness` TEXT,
                `loyalty` TEXT,
                `keywords` TEXT NOT NULL,
                `synergyTags` TEXT NOT NULL,
                `legalFormats` TEXT NOT NULL,
                `imageUri` TEXT,
                `isCustom` INTEGER NOT NULL,
                `isBookmarked` INTEGER NOT NULL,
                `cachedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_name` ON `cards` (`name`)")
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_typeLine` ON `cards` (`typeLine`)")
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_colorIdentity` ON `cards` (`colorIdentity`)")
        rawDb.execSQL("CREATE INDEX IF NOT EXISTS `index_cards_cmc` ON `cards` (`cmc`)")
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `decks` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `format` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `commanderCardId` TEXT,
                `colorIdentity` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `deck_entries` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `deckId` INTEGER NOT NULL,
                `cardId` TEXT NOT NULL,
                `quantity` INTEGER NOT NULL,
                `zone` TEXT NOT NULL,
                FOREIGN KEY(`deckId`) REFERENCES `decks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`cardId`) REFERENCES `cards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        rawDb.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_deck_entries_deckId` ON `deck_entries` (`deckId`)"
        )
        rawDb.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_deck_entries_cardId` ON `deck_entries` (`cardId`)"
        )
        rawDb.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_deck_entries_deckId_cardId_zone` ON `deck_entries` (`deckId`, `cardId`, `zone`)"
        )
        rawDb.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chat_messages` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `role` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `contextDeckName` TEXT,
                `timestamp` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        rawDb.execSQL("PRAGMA user_version = 1")

        // Insert v1 records: synced Scryfall card (bookmarked), offline unresolved stub, deck, and deck entries
        rawDb.execSQL(
            """
            INSERT INTO cards VALUES (
                'sf-1234-5678', 'Sol Ring', '{1}', 1.0, 'Artifact', '{T}: Add {C}{C}.',
                '', '', 'uncommon', 'CMM', 'Commander Masters', NULL, NULL, NULL,
                '', 'Ramp', 'Commander,Vintage', NULL, 0, 1, 1000
            )
            """.trimIndent()
        )
        rawDb.execSQL(
            """
            INSERT INTO cards VALUES (
                'offline-mystery-card', 'Mystery Card', '', 0.0, 'Unresolved Card (Offline Entry)',
                'Unresolved offline entry.', '', '', 'unknown', 'LOCAL', 'Unresolved Offline Import',
                NULL, NULL, NULL, '', 'Unresolved', 'Commander,Casual', NULL, 1, 0, 1000
            )
            """.trimIndent()
        )
        rawDb.execSQL(
            "INSERT INTO decks (id, name, format, description, commanderCardId, colorIdentity, notes, createdAt, updatedAt) VALUES (1, 'Preserved Deck', 'Commander', 'Desc', NULL, 'W', '', 1000, 1000)"
        )
        rawDb.execSQL(
            "INSERT INTO deck_entries (deckId, cardId, quantity, zone) VALUES (1, 'sf-1234-5678', 1, 'MAINBOARD')"
        )
        rawDb.execSQL(
            "INSERT INTO deck_entries (deckId, cardId, quantity, zone) VALUES (1, 'offline-mystery-card', 2, 'SIDEBOARD')"
        )
        rawDb.close()

        // Open via Room with MIGRATION_1_2
        val migratedDb = Room.databaseBuilder(context, LatticeDatabase::class.java, dbName)
            .addMigrations(LatticeDatabase.MIGRATION_1_2, LatticeDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            val solRing = migratedDb.cardDao().getCardById("sf-1234-5678")
            assertNotNull(solRing)
            assertTrue(solRing!!.isBookmarked)
            assertEquals("1234-5678", solRing.scryfallId)
            assertEquals(CardMetadataStatus.VERIFIED, solRing.metadataStatus)
            assertTrue(migratedDb.vaultCollectionDao().observeCards().first().isEmpty())
            assertTrue(migratedDb.vaultCollectionDao().saveSnapshot("Binder", mapOf(solRing.id to 2), false, true))
            assertEquals(2, migratedDb.vaultCollectionDao().observeCards().first().single().entry.quantity)

            val offlineStub = migratedDb.cardDao().getCardById("offline-mystery-card")
            assertNotNull(offlineStub)
            assertEquals(CardMetadataStatus.UNRESOLVED, offlineStub!!.metadataStatus)
            assertEquals("Unknown", offlineStub.legalFormats)

            val migratedDeck = migratedDb.deckDao().getDeckWithEntriesOnce(1L)
            assertNotNull(migratedDeck)
            assertEquals(2, migratedDeck!!.entries.size)
            assertEquals(2, migratedDeck.entries.first { it.card.id == "offline-mystery-card" }.entry.quantity)
        } finally {
            migratedDb.close()
            dbFile.delete()
        }
    }

    @Test
    fun `vault preserves filters when opening and dismissing card details and runs csv import ui`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LatticeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            kotlinx.coroutines.runBlocking {
                db.cardDao().upsertAllPreservingState(StarterCards.cards.take(10))
            }
            val onlineMonitor = object : NetworkMonitor(context) {
                override fun isCurrentlyOnline(): Boolean = true
            }
            val cardRepo = CardRepository(
                cardDao = db.cardDao(),
                scryfallApi = createAbandonAttachmentsApi(failFuzzyLookup = true),
                networkMonitor = onlineMonitor
            )
            val deckRepo = DeckRepository(db.deckDao(), cardRepo)
            runBlocking { deckRepo.createDeck("Offline test deck", "Modern", "", emptyList()) }
            val prefs = PreferencesRepository(context)
            val viewModel = VaultViewModel(cardRepo, deckRepo, onlineMonitor, prefs, db.vaultCollectionDao())

            composeRule.setContent {
                LatticeMtgTheme {
                    val snackbarHostState = remember { SnackbarHostState() }
                    VaultScreen(
                        viewModel = viewModel,
                        snackbarHostState = snackbarHostState,
                        onOpenSettings = {}
                    )
                }
            }

            val solRing = StarterCards.cards.first { it.name == "Sol Ring" }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                viewModel.uiState.value.decks.isNotEmpty()
            }

            assertTrue(viewModel.uiState.value.cards.isEmpty())
            composeRule.onNodeWithTag("vault_search_input").assertDoesNotExist()
            composeRule.onNodeWithTag("open_csv_import_button").assertIsDisplayed()
            composeRule.onNodeWithText("Catalog").performClick()
            composeRule.onNodeWithTag("open_csv_import_button").assertDoesNotExist()
            composeRule.onNodeWithTag("type_filter_artifact").assertDoesNotExist()
            composeRule.onNodeWithTag("vault_catalog_filters").performClick()
            // Apply search + type filter
            composeRule.onNodeWithTag("vault_search_input").performTextInput("Sol Ring")
            composeRule.onNodeWithTag("type_filter_artifact").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                viewModel.uiState.value.cards.size == 1 && viewModel.uiState.value.cards.first().id == solRing.id
            }
            composeRule.waitForIdle()

            // Verify filtered card row is shown and local cache notice is visible
            composeRule.onNodeWithTag("vault_local_cache_notice").assertIsDisplayed()
            composeRule.onNodeWithTag("vault_card_${solRing.id}").assertIsDisplayed()

            // Open card details bottom sheet
            composeRule.onNodeWithTag("vault_card_${solRing.id}").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("sheet_bookmark_button").assertIsDisplayed()
            composeRule.onNodeWithTag("sheet_open_deck_picker").assertIsDisplayed()

            // Close detail sheet via explicit close button
            composeRule.onNodeWithTag("sheet_close_button").performClick()
            composeRule.waitForIdle()

            // Assert filters preserved after closing card details
            assertEquals("Sol Ring", viewModel.uiState.value.query)
            assertEquals("Artifact", viewModel.uiState.value.selectedType)
            composeRule.onNodeWithTag("vault_card_${solRing.id}").assertIsDisplayed()

            composeRule.onNodeWithText("Collections").performClick()
            // Clear filters and verify CSV Import dialog flow
            viewModel.clearFilters()
            composeRule.waitForIdle()

            val abandonId = "aaaaaaaa-1111-4444-8888-bbbbbbbbbbbb"
            composeRule.onNodeWithTag("open_csv_import_button").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("csv_import_text_input").performTextReplacement(
                "Quantity,Name,Edition code,Collector's number,Language,Scryfall ID\n" +
                    "2,\"Abandon Attachments\",(TLA),205,English,$abandonId\n" +
                    "1,\"Abandon Attachments\",(MH3),205,English,$abandonId"
            )
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("run_csv_import_button")
                .assertIsEnabled()
                .performClick()
            composeRule.waitUntil(timeoutMillis = 8_000) {
                ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
                viewModel.uiState.value.csvImportReport != null && !viewModel.uiState.value.isImportingCsv
            }
            composeRule.waitForIdle()
            val report = viewModel.uiState.value.csvImportReport
            assertNotNull(report)
            assertEquals(2, report!!.totalVerifiedCopies)
            assertEquals(1, report.conflictRows.size)
            assertEquals("My cards", report.collectionName)
            composeRule.waitUntil(timeoutMillis = 8_000) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                viewModel.collections.value["My cards"]?.singleOrNull()?.entry?.quantity == 2
            }
            composeRule.onNodeWithTag("csv_verified_summary")
                .performScrollTo()
                .assertIsDisplayed()
            composeRule.onNodeWithTag("csv_conflicts_section")
                .performScrollTo()
                .assertIsDisplayed()
            composeRule.onNodeWithText("Close").performClick()
            composeRule.onNodeWithText("My cards").performClick()
            composeRule.onNodeWithText("×2").assertIsDisplayed()
            composeRule.onNodeWithText("Abandon Attachments").performClick()
            composeRule.onNodeWithTag("sheet_open_deck_picker").assertIsDisplayed()
        } finally {
            db.close()
        }
    }
}
