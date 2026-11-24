package com.example.data.repository

import com.example.data.local.DeckDao
import com.example.data.local.DeckEntity
import com.example.data.local.DeckEntryEntity
import com.example.data.local.DeckWithEntries
import com.example.data.local.DeckZones
import kotlinx.coroutines.flow.Flow

data class ImportDeckResult(
    val deckId: Long,
    val totalCardsImported: Int,
    val uniqueEntries: Int
)

class DeckRepository(
    private val deckDao: DeckDao,
    private val cardRepository: CardRepository
) {
    val allDecks: Flow<List<DeckWithEntries>> = deckDao.observeAllDecksWithEntries()
    val deckCount: Flow<Int> = deckDao.observeDeckCount()
    val totalDeckCardsCount: Flow<Int> = deckDao.observeTotalDeckCardsCount()

    fun observeDeck(deckId: Long): Flow<DeckWithEntries?> =
        deckDao.observeDeckWithEntries(deckId)

    suspend fun createDeck(
        name: String,
        format: String,
        description: String,
        colorIdentity: List<String>
    ): Long {
        val now = System.currentTimeMillis()
        return deckDao.insertDeck(
            DeckEntity(
                name = name.trim(),
                format = format,
                description = description.trim(),
                colorIdentity = colorIdentity.joinToString(","),
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun updateDeckMeta(
        deck: DeckEntity,
        name: String,
        format: String,
        description: String,
        notes: String
    ) {
        deckDao.updateDeck(
            deck.copy(
                name = name.trim(),
                format = format,
                description = description.trim(),
                notes = notes.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteDeck(deckId: Long) {
        deckDao.deleteDeckById(deckId)
    }

    suspend fun duplicateDeck(deckId: Long): Long? {
        val source = deckDao.getDeckWithEntriesOnce(deckId) ?: return null
        val now = System.currentTimeMillis()
        return deckDao.insertDeckWithEntries(
            source.deck.copy(id = 0L, name = "${source.deck.name} (Copy)", createdAt = now, updatedAt = now),
            source.entries.map { it.entry.copy(id = 0L) }
        )
    }

    suspend fun addCardToDeck(
        deckId: Long,
        cardId: String,
        zone: String = DeckZones.MAINBOARD,
        delta: Int = 1
    ) = deckDao.addCardQuantity(deckId, cardId, zone, delta)

    suspend fun setCardQuantity(
        deckId: Long,
        cardId: String,
        zone: String,
        quantity: Int
    ) = deckDao.setCardQuantity(deckId, cardId, zone, quantity)

    suspend fun moveCardZone(
        deckId: Long,
        cardId: String,
        fromZone: String,
        toZone: String
    ) = deckDao.moveCardZone(deckId, cardId, fromZone, toZone)

    fun exportDeckAsText(deckWithEntries: DeckWithEntries): String {
        val sb = StringBuilder()
        sb.appendLine("// ${deckWithEntries.deck.name} (${deckWithEntries.deck.format})")
        if (deckWithEntries.commanderEntries.isNotEmpty()) {
            sb.appendLine("// Commander")
            deckWithEntries.commanderEntries.forEach {
                sb.appendLine("${it.entry.quantity} ${it.card.name}")
            }
            sb.appendLine()
        }
        if (deckWithEntries.mainboardEntries.isNotEmpty()) {
            sb.appendLine("// Mainboard")
            deckWithEntries.mainboardEntries
                .sortedBy { it.card.name }
                .forEach { sb.appendLine("${it.entry.quantity} ${it.card.name}") }
        }
        if (deckWithEntries.sideboardEntries.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("// Sideboard")
            deckWithEntries.sideboardEntries
                .sortedBy { it.card.name }
                .forEach { sb.appendLine("${it.entry.quantity} ${it.card.name}") }
        }
        return sb.toString().trim()
    }

    suspend fun importDeckFromText(
        name: String,
        format: String,
        rawText: String,
        offlineOnlyMode: Boolean
    ): ImportDeckResult {
        var currentZone = DeckZones.MAINBOARD
        val quantities = linkedMapOf<Pair<String, String>, Int>()
        val lineRegex = Regex("""^(\d+)x?\s+(.+?)(?:\s+\([A-Za-z0-9]+\)(?:\s+[0-9][0-9A-Za-z★-]*)?)?(?:\s+\*[FE]\*)?$""")

        rawText.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith("//") || line.startsWith("#")) {
                val header = line.lowercase()
                currentZone = when {
                    "commander" in header -> DeckZones.COMMANDER
                    "sideboard" in header -> DeckZones.SIDEBOARD
                    "maybe" in header -> DeckZones.MAYBEBOARD
                    else -> DeckZones.MAINBOARD
                }
                return@forEach
            }
            if (line.equals("Sideboard", ignoreCase = true) || line.equals("SIDEBOARD:", ignoreCase = true)) {
                currentZone = DeckZones.SIDEBOARD
                return@forEach
            }
            if (line.equals("Commander", ignoreCase = true) || line.equals("COMMANDER:", ignoreCase = true)) {
                currentZone = DeckZones.COMMANDER
                return@forEach
            }

            val match = lineRegex.matchEntire(line)
            val qty = match?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 99) ?: 1
            val cardName = (match?.groupValues?.get(2) ?: line).trim()
            if (cardName.isNotEmpty()) {
                val card = cardRepository.resolveOrCreateCardByName(cardName, offlineOnlyMode)
                val key = card.id to currentZone
                quantities[key] = Math.addExact(quantities[key] ?: 0, qty)
            }
        }

        val entries = quantities.map { (key, quantity) ->
            DeckEntryEntity(deckId = 0L, cardId = key.first, zone = key.second, quantity = quantity)
        }
        require(entries.isNotEmpty()) { "The decklist contains no cards." }
        val deckId = deckDao.insertDeckWithEntries(
            DeckEntity(name = name.ifBlank { "Imported Deck" }.trim(), format = format, description = "Imported from decklist text"),
            entries
        )
        return ImportDeckResult(deckId, entries.sumOf { it.quantity }, entries.size)
    }

    suspend fun addVerifiedCsvToNewDeck(
        name: String,
        format: String,
        report: com.example.domain.DelverImportReport
    ): Long? {
        if (report.verifiedRows.isEmpty() || report.conflictRows.isNotEmpty() || report.unresolvedRows.isNotEmpty()) {
            return null
        }
        val entries = report.verifiedRows.groupBy { checkNotNull(it.resolvedCard).id }.map { (id, rows) ->
            val quantity = rows.fold(0) { total, resolution -> Math.addExact(total, resolution.row.quantity) }
            DeckEntryEntity(deckId = 0L, cardId = id, quantity = quantity)
        }
        return deckDao.insertDeckWithEntries(
            DeckEntity(
                name = name.ifBlank { "Delver CSV Import" }.trim(),
                format = format,
                description = "Imported ${report.totalVerifiedCopies} verified copies from Delver Lens CSV"
            ),
            entries
        )
    }
}
