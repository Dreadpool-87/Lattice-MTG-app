package com.example.domain

import com.example.data.local.CardEntity
import com.example.data.local.CardMetadataStatus
import com.example.data.local.DeckWithEntries
import com.example.data.local.DeckZones
import com.example.data.local.VaultCollectionCard

internal data class OwnedDeckCandidate(
    val card: CardEntity,
    val availableCopies: Long,
    val sharedTags: List<String>,
    val referenceTags: List<String>
)

internal object OwnedDeckCandidates {
    private val magicColors = setOf("W", "U", "B", "R", "G")

    fun deckColors(deck: DeckWithEntries): Set<String>? {
        val sources = if (deck.deck.format.trim().equals("Commander", ignoreCase = true)) {
            deck.commanderEntries
        } else {
            deck.commanderEntries + deck.mainboardEntries
        }
        if (sources.isEmpty() || sources.any { !it.card.hasVerifiedIdentity() }) return null
        return sources.flatMap { it.card.identity() }.toSet()
    }

    fun find(
        deck: DeckWithEntries,
        inventory: List<VaultCollectionCard>,
        colorFilter: Set<String>? = null,
        includeColorless: Boolean = true,
        reference: CardEntity? = null
    ): List<OwnedDeckCandidate> {
        val playable = deck.commanderEntries + deck.mainboardEntries
        val format = deck.deck.format.trim()
        val allowedColors = deckColors(deck) ?: return emptyList()
        if (format.isEmpty()) return emptyList()
        val commander = format.equals("Commander", ignoreCase = true)
        val copyLimit = if (commander) 1L else 4L
        val selectedColors = colorFilter?.intersect(allowedColors) ?: allowedColors
        val referenceTags = reference?.tags().orEmpty()

        val used = deck.entries.filter { it.entry.zone != DeckZones.MAYBEBOARD && it.entry.quantity > 0 }
        val usedById = used.filter { it.card.oracleId.isNotBlank() }.groupingBy { it.card.oracleId }
            .fold(0L) { total, entry -> total + entry.entry.quantity }
        val usedByNameWithoutId = used.filter { it.card.oracleId.isBlank() }.groupingBy { it.card.name.lowercase() }
            .fold(0L) { total, entry -> total + entry.entry.quantity }
        val tagCounts = playable.distinctBy { it.card.oracleId }.flatMap { it.card.tags() }
            .groupingBy { it }.eachCount()
        val casual = format.equals("Casual", ignoreCase = true)

        return inventory.asSequence()
            .filter { it.entry.quantity > 0 && it.card.hasVerifiedIdentity() }
            .groupBy { it.card.id }
            .values.asSequence()
            .mapNotNull { entries -> entries.maxByOrNull { it.entry.quantity } }
            .groupBy { it.card.oracleId }
            .values
            .mapNotNull { printings ->
                val card = printings.first().card
                if (reference != null && card.oracleId == reference.oracleId) return@mapNotNull null
                val identity = card.identity()
                if (!selectedColors.containsAll(identity) || (identity.isEmpty() && !includeColorless)) {
                    return@mapNotNull null
                }
                if (!casual && card.legalFormats.split(',').none { it.trim().equals(format, ignoreCase = true) }) {
                    return@mapNotNull null
                }
                val usedCopies = (usedById[card.oracleId] ?: 0L) + (usedByNameWithoutId[card.name.lowercase()] ?: 0L)
                // Conservative copy caps for this shortlist. Special deckbuilding exceptions are not inferred.
                val basicLand = card.typeLine.substringBefore('—').contains("Basic Land", ignoreCase = true)
                if (!basicLand && usedCopies >= copyLimit) return@mapNotNull null
                val available = printings.sumOf { it.entry.quantity.toLong() } - usedCopies
                if (available <= 0) return@mapNotNull null
                val tags = card.tags()
                OwnedDeckCandidate(
                    card, available, tags.filter { it in tagCounts }.sorted(), tags.intersect(referenceTags).sorted()
                )
            }
            .sortedWith(
                compareByDescending<OwnedDeckCandidate> { it.referenceTags.size }
                    .thenByDescending { match -> match.sharedTags.sumOf { tagCounts.getValue(it) } }
                    .thenBy { it.card.name.lowercase() }
                    .thenBy { it.card.oracleId }
            )
            .take(12)
            .toList()
    }

    private fun CardEntity.hasVerifiedIdentity(): Boolean =
        !isCustom && metadataStatus == CardMetadataStatus.VERIFIED && oracleId.isNotBlank() &&
            identity().all { it in magicColors }

    private fun CardEntity.identity(): Set<String> =
        colorIdentity.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private fun CardEntity.tags(): Set<String> =
        synergyTags.split(',').map { it.trim() }.filter { it.isNotEmpty() && !it.equals("Land", true) }.toSet()
}
