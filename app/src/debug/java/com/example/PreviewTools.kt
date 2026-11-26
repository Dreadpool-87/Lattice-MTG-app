package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build as BuildIcon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.room.withTransaction
import com.example.data.local.CardMetadataStatus
import com.example.data.local.DeckWithEntries
import com.example.data.local.LatticeDatabase
import com.example.data.remote.ScryfallCardDto
import com.example.data.repository.DeckRepository
import com.example.data.repository.OfflineCatalogRepository
import com.example.domain.DeckAnalytics
import com.example.domain.SynergyAnalyzerEngine
import com.example.ui.screens.SettingsSectionCard
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal object PreviewTools {
    private const val SEEDED = "example_decks_created"
    private const val MULTIVERSE_PRINTINGS_PREPARED = "multiverse_printings_prepared"
    private const val LEGACY_MULTIVERSE_FINGERPRINT = "90455416515339f590a3f4b44e14807e07248d32f64d89c205f2765d2849c8ad"
    private const val SEARCH_ROUNDS = 10
    private const val LOG_LIMIT = 12
    private val seedMutex = Mutex()
    private val logMutex = Mutex()

    private data class ExampleDeck(val name: String, val format: String, val expectedCards: Int) {
        val asset = "playtest-decks/$format-${name.replace(' ', '-')}.txt"
    }

    private val multiverse = ExampleDeck("Multiverse Reforged", "Commander", 100)
    private val examples = listOf(
        ExampleDeck("Orzhov Lifegain", "Casual", 60),
        ExampleDeck("Golgari Midrange", "Casual", 60),
        ExampleDeck("Klingon Fury", "Commander", 100),
        multiverse
    )

    suspend fun prepare(
        context: Context,
        database: LatticeDatabase,
        decks: DeckRepository,
        restore: Boolean = false
    ): Int = withContext(Dispatchers.IO) {
        seedMutex.withLock {
            val prefs = context.getSharedPreferences("preview_tools", Context.MODE_PRIVATE)
            val seeded = prefs.getBoolean(SEEDED, false)
            val printingsPrepared = prefs.getBoolean(MULTIVERSE_PRINTINGS_PREPARED, false)
            if (!restore && seeded && printingsPrepared) return@withLock 0
            val added = database.withTransaction {
                val existing = decks.allDecks.first()
                val missing = if (!seeded || restore) {
                    val names = existing.map { it.deck.name to it.deck.format }.toSet()
                    examples.filter { (it.name to it.format) !in names }
                } else emptyList()
                val legacy = if (!printingsPrepared) existing.filter(::isUnchangedLegacyMultiverse) else emptyList()
                missing.filter { it != multiverse }.forEach { importExample(context, database, decks, it) }
                if (multiverse in missing || legacy.isNotEmpty()) {
                    installMultiversePrintings(context, database)
                }
                if (multiverse in missing) importExample(context, database, decks, multiverse)
                legacy.forEach { previous ->
                    val updated = importExample(context, database, decks, multiverse)
                    val dao = database.deckDao()
                    previous.entries.forEach { dao.deleteEntryById(it.entry.id) }
                    dao.insertEntries(updated.entries.map { it.entry.copy(id = 0L, deckId = previous.deck.id) })
                    dao.deleteDeckById(updated.deck.id)
                }
                missing.size
            }
            check(prefs.edit().putBoolean(SEEDED, true)
                .putBoolean(MULTIVERSE_PRINTINGS_PREPARED, true).commit())
            added
        }
    }

    private fun isUnchangedLegacyMultiverse(deck: DeckWithEntries): Boolean {
        if (deck.deck.name != multiverse.name || deck.deck.format != multiverse.format ||
            deck.deck.description != "Imported from decklist text"
        ) return false
        // Match the zones, printing IDs and quantities from the old bundled catalog before replacing its entries.
        val signature = deck.entries.map { "${it.entry.zone}|${it.card.scryfallId}|${it.entry.quantity}" }
            .sorted().joinToString("\n")
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return fingerprint == LEGACY_MULTIVERSE_FINGERPRINT
    }

    private suspend fun installMultiversePrintings(context: Context, database: LatticeDatabase) {
        val type = Types.newParameterizedType(List::class.java, ScryfallCardDto::class.java)
        val adapter = Moshi.Builder().build().adapter<List<ScryfallCardDto>>(type)
        val cards = context.assets.open("playtest-decks/multiverse-printings.json.gz").use { file ->
            GZIPInputStream(file).bufferedReader().use { checkNotNull(adapter.fromJson(it.readText())) }
        }
        val dao = database.cardDao()
        cards.forEach { card ->
            if (dao.findByCanonicalScryfallId(card.id)?.metadataStatus != CardMetadataStatus.VERIFIED) {
                dao.storeCatalogPrinting(card.toCardEntity())
            }
        }
    }

    private suspend fun importExample(
        context: Context,
        database: LatticeDatabase,
        decks: DeckRepository,
        example: ExampleDeck
    ): DeckWithEntries {
        val list = context.assets.open(example.asset).bufferedReader().use { it.readText() }
        val result = decks.importDeckFromText(example.name, example.format, list, offlineOnlyMode = true)
        check(result.totalCardsImported == example.expectedCards)
        return checkNotNull(database.deckDao().getDeckWithEntriesOnce(result.deckId)).also { imported ->
            check(imported.entries.none { it.card.metadataStatus == CardMetadataStatus.UNRESOLVED })
        }
    }

    private suspend fun runStressTest(database: LatticeDatabase, decks: DeckRepository): String {
        val samples = decks.allDecks.first().filter { deck ->
            examples.any { it.name == deck.deck.name && it.format == deck.deck.format }
        }.take(examples.size)
        val searchTerms = listOf("Island", "counterspell", "creature", "draw a card")
        val started = SystemClock.elapsedRealtime()
        repeat(SEARCH_ROUNDS) {
            searchTerms.forEach { query ->
                database.cardDao().searchCards(query, "All", "All", false, null, "", 40).first()
            }
            samples.forEach { deck ->
                DeckAnalytics.analyze(deck)
                SynergyAnalyzerEngine.analyzeDeck(deck)
            }
            kotlin.coroutines.coroutineContext.ensureActive()
        }
        val elapsed = SystemClock.elapsedRealtime() - started
        return "${SEARCH_ROUNDS * searchTerms.size} catalog searches; " +
            "${SEARCH_ROUNDS * samples.size * 2} deck analyses; ${elapsed} ms."
    }

    @Composable
    fun Settings(context: Context, database: LatticeDatabase, decks: DeckRepository, catalog: OfflineCatalogRepository) {
        val scope = rememberCoroutineScope()
        val file = remember(context) { File(context.filesDir, "preview-diagnostics.log") }
        var log by remember { mutableStateOf("") }
        var status by remember { mutableStateOf<String?>(null) }
        var running by remember { mutableStateOf<Job?>(null) }

        LaunchedEffect(file) {
            log = withContext(Dispatchers.IO) {
                logMutex.withLock { if (file.isFile) file.readText() else "" }
            }
        }

        fun run(label: String, action: suspend () -> String) {
            if (running != null) return
            running = scope.launch {
                status = "$label running…"
                try {
                    log = withContext(Dispatchers.IO) {
                        val result = try {
                            action()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            "Failed (${error.javaClass.simpleName})."
                        }
                        val time = DateFormat.getDateTimeInstance().format(Date())
                        logMutex.withLock {
                            val previous = if (file.isFile) file.readLines() else emptyList()
                            val updated = (previous + "$time | $label | $result").takeLast(LOG_LIMIT).joinToString("\n")
                            file.writeText(updated)
                            updated
                        }
                    }
                    status = null
                } catch (cancelled: CancellationException) {
                    status = "Test stopped."
                    throw cancelled
                } catch (_: Exception) {
                    status = "Could not save the debug log."
                } finally {
                    running = null
                }
            }
        }

        SettingsSectionCard(title = "Debug tools", icon = Icons.Filled.BuildIcon) {
            Text("Run local checks and copy the results. The stress test reads the catalog and sample decks without downloading images.")
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = running == null,
                onClick = {
                    run("Data check") {
                        val installed = "all" in catalog.installed.first()
                        val cards = database.cardDao().getCardCount()
                        val deckCount = database.deckDao().getDeckCount()
                        "Preview ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), Android ${Build.VERSION.SDK_INT}; " +
                            "catalog ready: $installed; $cards cards; $deckCount decks."
                    }
                }
            ) { Text("Check local data") }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = running == null,
                onClick = { run("Stress test") { runStressTest(database, decks) } }
            ) { Text("Stress test catalog & decks") }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = running == null,
                onClick = {
                    run("Example decks") {
                        catalog.installBundledCatalog()
                        "Added ${prepare(context, database, decks, restore = true)} missing example decks."
                    }
                }
            ) { Text("Restore missing example decks") }
            if (running != null) {
                OutlinedButton(onClick = { running?.cancel() }) { Text("Stop") }
            }
            status?.let { Text(it) }
            if (log.isNotBlank()) {
                OutlinedButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("Lattice MTG Preview log", log))
                }) { Text("Copy debug log") }
                SelectionContainer { Text(log) }
            }
        }
    }
}
