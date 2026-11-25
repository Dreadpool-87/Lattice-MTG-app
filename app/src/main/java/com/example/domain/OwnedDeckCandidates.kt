package com.example.domain

import com.example.data.local.CardEntity
import com.example.data.local.CardMetadataStatus
import com.example.data.local.DeckWithEntries
import com.example.data.local.DeckZones
import com.example.data.local.VaultCollectionCard

internal data class OwnedDeckCandidate(
    val card: CardEntity,
    val availableCopies: Long,
    val sharedTags: List<String>
)

internal data class OwnedDeckSwap(
    val remove: CardEntity,
    val add: OwnedDeckCandidate,
    val additionalTags: List<String>
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

    fun provisionalColors(deck: DeckWithEntries): Set<String> =
        (deck.commanderEntries + deck.mainboardEntries)
            .filter { it.card.hasVerifiedIdentity() }.flatMap { it.card.identity() }.toSet()

    fun bestSwap(
        deck: DeckWithEntries,
        inventory: List<VaultCollectionCard>,
        colors: Set<String>,
        includeColorless: Boolean
    ): OwnedDeckSwap? {
        val cuts = deck.mainboardEntries.asSequence()
            .filter { it.entry.quantity > 0 && it.card.hasVerifiedIdentity() }
            .map { it.card }
            .filter { "Land" !in it.mainTypes() && it.oracleText.isNotBlank() && it.cmc.isFinite() }
            .distinctBy { it.oracleId }
            .toList()
        val active = (deck.commanderEntries + deck.mainboardEntries)
            .map { it.card }.filter { it.hasVerifiedIdentity() }.distinctBy { it.oracleId }
        val protectionTags = setOf("removal", "ramp", "card draw", "board wipe", "protection", "recursion", "countermagic")
        val additions = find(deck, inventory, colors, includeColorless)

        val choices = cuts.flatMap { cut ->
            val support = active.filter { it.oracleId != cut.oracleId }.flatMap { it.tags() }
                .groupingBy { it }.eachCount()
            val cutTags = cut.tags()
            val protected = cutTags.filter { it.lowercase() in protectionTags }
            additions.mapNotNull { add ->
                val replacement = add.card
                val addTags = replacement.tags()
                if (replacement.oracleId == cut.oracleId || replacement.oracleText.isBlank() ||
                    !replacement.cmc.isFinite() || replacement.cmc > cut.cmc ||
                    replacement.mainTypes() != cut.mainTypes() || !addTags.containsAll(protected)) {
                    return@mapNotNull null
                }
                val extra = (addTags - cutTags).filter { it in support }.sorted()
                val gain = addTags.sumOf { support[it] ?: 0 } - cutTags.sumOf { support[it] ?: 0 }
                if (gain <= 0 || extra.isEmpty()) null else OwnedDeckSwap(cut, add, extra) to gain
            }
        }
        return choices.sortedWith(
            compareByDescending<Pair<OwnedDeckSwap, Int>> { it.second }
                .thenBy { it.first.add.card.cmc }
                .thenBy { it.first.add.card.name }
                .thenBy { it.first.remove.name }
        ).firstOrNull()?.first
    }

    fun find(
        deck: DeckWithEntries,
        inventory: List<VaultCollectionCard>,
        colorFilter: Set<String>? = null,
        includeColorless: Boolean = true
    ): List<OwnedDeckCandidate> {
        val playable = deck.commanderEntries + deck.mainboardEntries
        val format = deck.deck.format.trim()
        val allowedColors = deckColors(deck) ?: colorFilter?.intersect(magicColors) ?: return emptyList()
        if (format.isEmpty()) return emptyList()
        val commander = format.equals("Commander", ignoreCase = true)
        val copyLimit = if (commander) 1L else 4L
        val selectedColors = colorFilter?.intersect(allowedColors) ?: allowedColors

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
                    card, available, tags.filter { it in tagCounts }.sorted()
                )
            }
            .sortedWith(
                compareByDescending<OwnedDeckCandidate> { match -> match.sharedTags.sumOf { tagCounts.getValue(it) } }
                    .thenBy { it.card.name.lowercase() }
                    .thenBy { it.card.oracleId }
            )
            .toList()
    }

    private fun CardEntity.mainTypes(): Set<String> =
        typeLine.substringBefore('—').split(' ').filter { it in cardTypes }.toSet()

    private val cardTypes = setOf("Artifact", "Battle", "Creature", "Enchantment", "Instant", "Kindred", "Land", "Planeswalker", "Sorcery")

    private fun CardEntity.hasVerifiedIdentity(): Boolean =
        !isCustom && metadataStatus == CardMetadataStatus.VERIFIED && oracleId.isNotBlank() &&
            identity().all { it in magicColors }

    private fun CardEntity.identity(): Set<String> =
        colorIdentity.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private fun CardEntity.tags(): Set<String> =
        synergyTags.split(',').map { it.trim() }.filter { it.isNotEmpty() && !it.equals("Land", true) }.toSet()
}
