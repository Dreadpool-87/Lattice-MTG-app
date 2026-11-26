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
import com.example.data.local.LatticeDatabase
import com.example.data.repository.DeckRepository
import com.example.data.repository.OfflineCatalogRepository
import com.example.domain.DeckAnalytics
import com.example.domain.SynergyAnalyzerEngine
import com.example.ui.screens.SettingsSectionCard
import java.io.File
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
    private const val SEARCH_ROUNDS = 10
    private const val LOG_LIMIT = 12
    private val seedMutex = Mutex()
    private val logMutex = Mutex()

    private data class ExampleDeck(val name: String, val format: String, val expectedCards: Int) {
        val asset = "playtest-decks/$format-${name.replace(' ', '-')}.txt"
    }

    private val examples = listOf(
        ExampleDeck("Orzhov Lifegain", "Casual", 60),
        ExampleDeck("Golgari Midrange", "Casual", 60),
        ExampleDeck("Klingon Fury", "Commander", 100),
        ExampleDeck("Multiverse Reforged", "Commander", 100)
    )

    suspend fun prepare(
        context: Context,
        database: LatticeDatabase,
        decks: DeckRepository,
        restore: Boolean = false
    ): Int = withContext(Dispatchers.IO) {
        seedMutex.withLock {
            val prefs = context.getSharedPreferences("preview_tools", Context.MODE_PRIVATE)
            if (!restore && prefs.getBoolean(SEEDED, false)) return@withLock 0
            val added = database.withTransaction {
                val existing = decks.allDecks.first().map { it.deck.name to it.deck.format }.toSet()
                val missing = examples.filter { (it.name to it.format) !in existing }
                missing.forEach { example ->
                    val list = context.assets.open(example.asset).bufferedReader().use { it.readText() }
                    val result = decks.importDeckFromText(example.name, example.format, list, offlineOnlyMode = true)
                    check(result.totalCardsImported == example.expectedCards)
                    val imported = checkNotNull(database.deckDao().getDeckWithEntriesOnce(result.deckId))
                    check(imported.entries.none { it.card.metadataStatus == CardMetadataStatus.UNRESOLVED })
                }
                missing.size
            }
            check(prefs.edit().putBoolean(SEEDED, true).commit())
            added
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
