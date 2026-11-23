package com.example.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class DeckEntryWithCard(
    @Embedded val entry: DeckEntryEntity,
    @Relation(
        parentColumn = "cardId",
        entityColumn = "id"
    )
    val card: CardEntity
)

data class DeckWithEntries(
    @Embedded val deck: DeckEntity,
    @Relation(
        entity = DeckEntryEntity::class,
        parentColumn = "id",
        entityColumn = "deckId"
    )
    val entries: List<DeckEntryWithCard>
) {
    val mainboardEntries: List<DeckEntryWithCard>
        get() = entries.filter { it.entry.zone == DeckZones.MAINBOARD }

    val commanderEntries: List<DeckEntryWithCard>
        get() = entries.filter { it.entry.zone == DeckZones.COMMANDER }

    val sideboardEntries: List<DeckEntryWithCard>
        get() = entries.filter { it.entry.zone == DeckZones.SIDEBOARD }

    val maybeboardEntries: List<DeckEntryWithCard>
        get() = entries.filter { it.entry.zone == DeckZones.MAYBEBOARD }

    val playableCount: Int
        get() = entries
            .filter { it.entry.zone == DeckZones.MAINBOARD || it.entry.zone == DeckZones.COMMANDER }
            .sumOf { it.entry.quantity }

    val sideboardCount: Int
        get() = sideboardEntries.sumOf { it.entry.quantity }

    val resolvedColorIdentity: List<String>
        get() {
            val fromCards = entries
                .filter { it.entry.zone == DeckZones.MAINBOARD || it.entry.zone == DeckZones.COMMANDER }
                .flatMap { it.card.colorIdentity.split(",") }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            val ordered = listOf("W", "U", "B", "R", "G")
            val combined = if (fromCards.isNotEmpty()) {
                ordered.filter { it in fromCards }
            } else {
                deck.colorIdentity.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
            return combined
        }
}
