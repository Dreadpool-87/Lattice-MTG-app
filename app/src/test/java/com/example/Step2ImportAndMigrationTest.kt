package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.example.data.remote.ScryfallRulingsResponse
import com.example.data.remote.ScryfallSearchResponse
import com.example.data.repository.CardRepository
import com.example.data.repository.DeckRepository
import com.example.data.repository.NetworkMonitor
import com.example.data.repository.PreferencesRepository
import com.example.domain.CsvRowMatchStatus
import com.example.domain.DelverCsvImporter
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.LatticeMtgTheme
import com.example.ui.viewmodel.VaultViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Step2ImportAndMigrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun createAbandonAttachmentsApi(
        failFuzzyLookup: Boolean = true,
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
                namedInSet[exactName to setCode]?.let { return it }
                if (exactName.equals("Abandon Attachments", ignoreCase = true)) return dto
                error("Exact card not found: $exactName")
            }

            override suspend fun getCardByScryfallId(scryfallId: String): ScryfallCardDto {
                if (scryfallId.equals(abandonAttachmentsId, ignoreCase = true)) return dto
                error("Printing ID not found: $scryfallId")
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
                error("Set/number/lang not found")
            }

            override suspend fun getCardBySetNumber(setCode: String, collectorNumber: String): ScryfallCardDto {
                if (setCode.equals("tla", ignoreCase = true) && collectorNumber == "205") return dto
                error("Set/number not found")
            }

            override suspend fun getCardRulings(scryfallId: String): ScryfallRulingsResponse =
                ScryfallRulingsResponse()
        }
    }

    @Test
    fun `CSV searches a supplied set even when a different printing is cached`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Room.inMemoryDatabaseBuilder(context, LatticeDatabase::class.java).build().use { db ->
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
            .addMigrations(LatticeDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val solRing = migratedDb.cardDao().getCardById("sf-1234-5678")
            assertNotNull(solRing)
            assertTrue(solRing!!.isBookmarked)
            assertEquals("1234-5678", solRing.scryfallId)
            assertEquals(CardMetadataStatus.VERIFIED, solRing.metadataStatus)

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
            val prefs = PreferencesRepository(context)
            val viewModel = VaultViewModel(cardRepo, deckRepo, onlineMonitor, prefs)

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
                viewModel.uiState.value.cards.any { it.id == solRing.id }
            }

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

            // Close detail sheet via explicit close button
            composeRule.onNodeWithTag("sheet_close_button").performClick()
            composeRule.waitForIdle()

            // Assert filters preserved after closing card details
            assertEquals("Sol Ring", viewModel.uiState.value.query)
            assertEquals("Artifact", viewModel.uiState.value.selectedType)
            composeRule.onNodeWithTag("vault_card_${solRing.id}").assertIsDisplayed()

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
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                viewModel.uiState.value.csvImportReport != null && !viewModel.uiState.value.isImportingCsv
            }
            composeRule.waitForIdle()
            val report = viewModel.uiState.value.csvImportReport
            assertNotNull(report)
            assertEquals(2, report!!.totalVerifiedCopies)
            assertEquals(1, report.conflictRows.size)
            composeRule.onNodeWithTag("csv_verified_summary")
                .performScrollTo()
                .assertIsDisplayed()
            composeRule.onNodeWithTag("csv_conflicts_section")
                .performScrollTo()
                .assertIsDisplayed()
        } finally {
            db.close()
        }
    }
}
