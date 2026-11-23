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
        val newDeckId = deckDao.insertDeck(
            source.deck.copy(
                id = 0L,
                name = "${source.deck.name} (Copy)",
                createdAt = now,
                updatedAt = now
            )
        )
        val copiedEntries = source.entries.map {
            it.entry.copy(id = 0L, deckId = newDeckId)
        }
        deckDao.insertEntries(copiedEntries)
        return newDeckId
    }

    suspend fun addCardToDeck(
        deckId: Long,
        cardId: String,
        zone: String = DeckZones.MAINBOARD,
        delta: Int = 1
    ) {
        val existing = deckDao.getEntry(deckId, cardId, zone)
        if (existing == null) {
            if (delta > 0) {
                deckDao.upsertEntry(
                    DeckEntryEntity(
                        deckId = deckId,
                        cardId = cardId,
                        quantity = delta,
                        zone = zone
                    )
                )
            }
        } else {
            val nextQty = existing.quantity + delta
            if (nextQty <= 0) {
                deckDao.deleteEntryById(existing.id)
            } else {
                deckDao.upsertEntry(existing.copy(quantity = nextQty))
            }
        }
        deckDao.touchDeck(deckId)
    }

    suspend fun setCardQuantity(
        deckId: Long,
        cardId: String,
        zone: String,
        quantity: Int
    ) {
        if (quantity <= 0) {
            deckDao.deleteEntry(deckId, cardId, zone)
        } else {
            val existing = deckDao.getEntry(deckId, cardId, zone)
            if (existing == null) {
                deckDao.upsertEntry(
                    DeckEntryEntity(deckId = deckId, cardId = cardId, quantity = quantity, zone = zone)
                )
            } else {
                deckDao.upsertEntry(existing.copy(quantity = quantity))
            }
        }
        deckDao.touchDeck(deckId)
    }

    suspend fun moveCardZone(
        deckId: Long,
        cardId: String,
        fromZone: String,
        toZone: String
    ) {
        if (fromZone == toZone) return
        val existing = deckDao.getEntry(deckId, cardId, fromZone) ?: return
        deckDao.deleteEntryById(existing.id)
        addCardToDeck(deckId, cardId, toZone, existing.quantity)
    }

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
        val deckId = createDeck(
            name = name.ifBlank { "Imported Deck" },
            format = format,
            description = "Imported offline from decklist text",
            colorIdentity = emptyList()
        )

        var currentZone = DeckZones.MAINBOARD
        var totalCards = 0
        var uniqueCount = 0
        val lineRegex = Regex("""^(\d+)x?\s+(.+?)(?:\s+\([A-Za-z0-9]+\)\s*\d*)?$""")

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
                addCardToDeck(deckId, card.id, currentZone, qty)
                totalCards += qty
                uniqueCount += 1
            }
        }

        return ImportDeckResult(
            deckId = deckId,
            totalCardsImported = totalCards,
            uniqueEntries = uniqueCount
        )
    }

    suspend fun addVerifiedCsvToNewDeck(
        name: String,
        format: String,
        report: com.example.domain.DelverImportReport
    ): Long? {
        if (report.verifiedRows.isEmpty()) return null
        val deckId = createDeck(
            name = name.ifBlank { "Delver CSV Import" },
            format = format,
            description = "Imported ${report.totalVerifiedCopies} verified copies from Delver Lens CSV",
            colorIdentity = emptyList()
        )
        report.verifiedRows.forEach { res ->
            val card = res.resolvedCard ?: return@forEach
            addCardToDeck(deckId, card.id, DeckZones.MAINBOARD, res.row.quantity)
        }
        return deckId
    }
}
