package com.example.domain

import com.example.data.local.CardEntity
import com.example.data.local.DeckEntryWithCard
import com.example.data.local.DeckWithEntries
import com.example.data.local.DeckZones
import kotlin.math.exp
import kotlin.math.ln

data class ValidationNote(
    val title: String,
    val detail: String,
    val isWarning: Boolean
)

data class SynergyCluster(
    val tag: String,
    val cardNames: List<String>,
    val totalCopies: Int
)

data class SynergyConnection(
    val tagA: String,
    val tagB: String,
    val sharedCards: Int
)

data class DeckAnalyticsSummary(
    val playableCount: Int,
    val sideboardCount: Int,
    val landCount: Int,
    val nonLandCount: Int,
    val averageCmc: Double,
    val manaCurve: Map<Int, Int>,
    val colorPips: Map<String, Int>,
    val typeCounts: Map<String, Int>,
    val synergyClusters: List<SynergyCluster>,
    val synergyConnections: List<SynergyConnection>,
    val validationNotes: List<ValidationNote>,
    val openingLandOdds7: Double,
    val turn3Land3Odds: Double
)

object DeckAnalytics {
    private val basicLandNames = setOf("Plains", "Island", "Swamp", "Mountain", "Forest", "Wastes")
    private val pipRegex = Regex("""\{([^}]+)\}""")

    fun analyze(deckWithEntries: DeckWithEntries): DeckAnalyticsSummary {
        val playable = deckWithEntries.entries.filter {
            it.entry.zone == DeckZones.MAINBOARD || it.entry.zone == DeckZones.COMMANDER
        }
        val playableCount = playable.sumOf { it.entry.quantity }
        val sideboardCount = deckWithEntries.sideboardCount

        var landCount = 0
        var nonLandCount = 0
        var cmcSum = 0.0
        val curve = mutableMapOf(0 to 0, 1 to 0, 2 to 0, 3 to 0, 4 to 0, 5 to 0, 6 to 0)
        val pips = mutableMapOf("W" to 0, "U" to 0, "B" to 0, "R" to 0, "G" to 0, "C" to 0)
        val types = linkedMapOf(
            "Creature" to 0,
            "Instant" to 0,
            "Sorcery" to 0,
            "Artifact" to 0,
            "Enchantment" to 0,
            "Planeswalker" to 0,
            "Land" to 0
        )

        val tagToCards = linkedMapOf<String, MutableList<Pair<String, Int>>>()

        playable.forEach { item ->
            val qty = item.entry.quantity
            val card = item.card
            val typeLower = card.typeLine.lowercase()

            when {
                "land" in typeLower -> {
                    landCount += qty
                    types["Land"] = (types["Land"] ?: 0) + qty
                }
                else -> {
                    nonLandCount += qty
                    cmcSum += card.cmc * qty
                    val bucket = card.cmc.toInt().coerceIn(0, 6)
                    curve[bucket] = (curve[bucket] ?: 0) + qty

                    when {
                        "creature" in typeLower -> types["Creature"] = (types["Creature"] ?: 0) + qty
                        "planeswalker" in typeLower -> types["Planeswalker"] = (types["Planeswalker"] ?: 0) + qty
                        "instant" in typeLower -> types["Instant"] = (types["Instant"] ?: 0) + qty
                        "sorcery" in typeLower -> types["Sorcery"] = (types["Sorcery"] ?: 0) + qty
                        "artifact" in typeLower -> types["Artifact"] = (types["Artifact"] ?: 0) + qty
                        "enchantment" in typeLower -> types["Enchantment"] = (types["Enchantment"] ?: 0) + qty
                    }
                }
            }

            // Count mana pips
            pipRegex.findAll(card.manaCost).forEach { match ->
                val sym = match.groupValues[1].uppercase()
                listOf("W", "U", "B", "R", "G", "C").forEach { color ->
                    if (color in sym) {
                        pips[color] = (pips[color] ?: 0) + qty
                    }
                }
            }

            // Synergy tags
            card.synergyTags.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "Land" && it != "Imported" }
                .forEach { tag ->
                    tagToCards.getOrPut(tag) { mutableListOf() }.add(card.name to qty)
                }
        }

        val averageCmc = if (nonLandCount > 0) cmcSum / nonLandCount else 0.0

        val clusters = tagToCards.entries
            .map { (tag, list) ->
                SynergyCluster(
                    tag = tag,
                    cardNames = list.map { it.first }.distinct(),
                    totalCopies = list.sumOf { it.second }
                )
            }
            .sortedByDescending { it.cardNames.size }
            .take(8)

        val connections = mutableListOf<SynergyConnection>()
        for (i in clusters.indices) {
            for (j in i + 1 until clusters.size) {
                val a = clusters[i]
                val b = clusters[j]
                val shared = a.cardNames.intersect(b.cardNames.toSet()).size
                if (shared > 0) {
                    connections.add(SynergyConnection(a.tag, b.tag, shared))
                }
            }
        }

        val validationNotes = validateDeck(deckWithEntries, playable, playableCount, landCount)

        val openingLandOdds7 = if (playableCount >= 7 && landCount > 0) {
            hypergeometricAtLeastK(playableCount, landCount, 7, 2)
        } else 0.0

        val turn3Land3Odds = if (playableCount >= 9 && landCount > 0) {
            hypergeometricAtLeastK(playableCount, landCount, 9, 3)
        } else 0.0

        return DeckAnalyticsSummary(
            playableCount = playableCount,
            sideboardCount = sideboardCount,
            landCount = landCount,
            nonLandCount = nonLandCount,
            averageCmc = averageCmc,
            manaCurve = curve,
            colorPips = pips,
            typeCounts = types.filterValues { it > 0 },
            synergyClusters = clusters,
            synergyConnections = connections.sortedByDescending { it.sharedCards },
            validationNotes = validationNotes,
            openingLandOdds7 = openingLandOdds7,
            turn3Land3Odds = turn3Land3Odds
        )
    }

    private fun canonicalCardIdentityKey(
        card: CardEntity,
        oracleByNormalizedName: Map<String, String>
    ): String {
        val directOracle = card.oracleId.trim().lowercase()
        if (directOracle.isNotEmpty()) return "oracle:$directOracle"
        val normalizedName = card.name.trim().lowercase()
        val linkedOracle = oracleByNormalizedName[normalizedName]
        if (!linkedOracle.isNullOrEmpty()) return "oracle:$linkedOracle"
        return "name:$normalizedName"
    }

    private fun isUnlimitedCopyCard(card: CardEntity): Boolean {
        val trimmedName = card.name.trim()
        if (trimmedName in basicLandNames || trimmedName.startsWith("Snow-Covered ") && trimmedName.removePrefix("Snow-Covered ") in basicLandNames) {
            return true
        }
        val typeLower = card.typeLine.lowercase()
        if ("basic" in typeLower && "land" in typeLower) {
            return true
        }
        val oracleLower = card.oracleText.lowercase()
        return "a deck can have any number of cards named" in oracleLower
    }

    private fun isUnresolvedCard(card: CardEntity): Boolean {
        return card.metadataStatus == com.example.data.local.CardMetadataStatus.UNRESOLVED ||
            card.id.startsWith("offline-") ||
            card.legalFormats.equals("Unknown", ignoreCase = true) ||
            card.legalFormats.isBlank()
    }

    private fun validateDeck(
        deckWithEntries: DeckWithEntries,
        playable: List<DeckEntryWithCard>,
        playableCount: Int,
        landCount: Int
    ): List<ValidationNote> {
        val notes = mutableListOf<ValidationNote>()
        val format = deckWithEntries.deck.format.trim()
        val isCommander = format.equals("Commander", ignoreCase = true)
        val isCasual = format.equals("Casual", ignoreCase = true)

        val allEntries = deckWithEntries.entries
        val oracleByNormalizedName = allEntries
            .mapNotNull { item ->
                val oracle = item.card.oracleId.trim().lowercase()
                if (oracle.isNotEmpty()) item.card.name.trim().lowercase() to oracle else null
            }
            .toMap()

        // 1. Deck / Zone Size Check
        if (isCommander) {
            if (playableCount == 100) {
                notes.add(ValidationNote("Commander Zone Size (100/100)", "Exact 100 cards including Commander.", false))
            } else {
                notes.add(
                    ValidationNote(
                        "Commander Size (${playableCount}/100)",
                        "Commander decks require 100 cards (${100 - playableCount} remaining).",
                        true
                    )
                )
            }
            if (deckWithEntries.commanderEntries.isEmpty()) {
                notes.add(ValidationNote("Missing Commander", "Assign a Legendary Creature in the Commander zone.", true))
            }
        } else {
            if (playableCount >= 60) {
                notes.add(ValidationNote("Mainboard Size Check ($playableCount/60+)", "$playableCount cards (meets 60-card minimum).", false))
            } else {
                notes.add(
                    ValidationNote(
                        "Under 60 Cards ($playableCount/60)",
                        "Constructed formats typically require at least 60 mainboard cards.",
                        true
                    )
                )
            }
        }

        // 2. Copy Limit Check Across Printings (grouped by Oracle ID / canonical card identity)
        val copyLimitEntries = if (isCommander) {
            playable
        } else {
            allEntries.filter {
                it.entry.zone == DeckZones.MAINBOARD ||
                    it.entry.zone == DeckZones.SIDEBOARD ||
                    it.entry.zone == DeckZones.COMMANDER
            }
        }

        val groupedByIdentity = linkedMapOf<String, MutableList<DeckEntryWithCard>>()
        copyLimitEntries.forEach { item ->
            val key = canonicalCardIdentityKey(item.card, oracleByNormalizedName)
            groupedByIdentity.getOrPut(key) { mutableListOf() }.add(item)
        }

        if (isCommander) {
            val violations = groupedByIdentity.values.mapNotNull { group ->
                val sample = group.first().card
                if (isUnlimitedCopyCard(sample)) return@mapNotNull null
                val totalCopies = group.sumOf { it.entry.quantity }
                if (totalCopies > 1) {
                    val printingCount = group.map { it.card.id }.distinct().size
                    val suffix = if (printingCount > 1) " across $printingCount printings" else ""
                    "${sample.name} (${totalCopies}x$suffix)"
                } else null
            }
            if (violations.isNotEmpty()) {
                notes.add(
                    ValidationNote(
                        "Singleton Limit Exceeded",
                        violations.joinToString("; "),
                        true
                    )
                )
            } else {
                notes.add(
                    ValidationNote(
                        "Singleton Check Passed (Cross-Printing)",
                        "No non-basic card exceeds 1 copy across printings.",
                        false
                    )
                )
            }
        } else {
            val overFour = groupedByIdentity.values.mapNotNull { group ->
                val sample = group.first().card
                if (isUnlimitedCopyCard(sample)) return@mapNotNull null
                val totalCopies = group.sumOf { it.entry.quantity }
                if (totalCopies > 4) {
                    val printingCount = group.map { it.card.id }.distinct().size
                    val suffix = if (printingCount > 1) " across $printingCount printings" else ""
                    "${sample.name} (${totalCopies}x$suffix)"
                } else null
            }
            if (overFour.isNotEmpty()) {
                notes.add(
                    ValidationNote(
                        "4-Copy Limit Exceeded",
                        overFour.joinToString("; "),
                        true
                    )
                )
            } else if (copyLimitEntries.isNotEmpty()) {
                notes.add(
                    ValidationNote(
                        "4-Copy Limit Check Passed (Cross-Printing)",
                        "No non-basic card exceeds 4 total copies across Mainboard & Sideboard printings.",
                        false
                    )
                )
            }
        }

        // 3. Unresolved Cards Check (legality strictly treated as Unknown)
        val activeDeckCards = allEntries
            .filter { it.entry.zone != DeckZones.MAYBEBOARD }
            .map { it.card }
            .distinctBy { it.id }

        val unresolvedCards = activeDeckCards.filter { isUnresolvedCard(it) }
        if (unresolvedCards.isNotEmpty()) {
            notes.add(
                ValidationNote(
                    "Unresolved Cards — Legality Unknown (${unresolvedCards.size})",
                    "Format legality is Unknown for: ${unresolvedCards.joinToString { it.name }}. Sync online to resolve metadata.",
                    true
                )
            )
        }

        // 4. Cached Format Legality Snapshot Check (resolved non-custom cards only)
        val checkedSnapshot = !isCasual && format.isNotBlank()
        if (checkedSnapshot) {
            val notLegalCached = activeDeckCards.filter { card ->
                !isUnresolvedCard(card) &&
                    !card.isCustom &&
                    card.legalFormats
                        .split(",")
                        .map { it.trim() }
                        .none { it.equals(format, ignoreCase = true) }
            }
            if (notLegalCached.isNotEmpty()) {
                notes.add(
                    ValidationNote(
                        "Not Listed Legal in $format (Cached Snapshot)",
                        notLegalCached.joinToString { it.name },
                        true
                    )
                )
            }
        }

        // 5. Land Ratio Estimate
        if (playableCount > 0) {
            val ratio = (landCount.toDouble() / playableCount) * 100
            if (ratio < 28.0) {
                notes.add(
                    ValidationNote(
                        "Low Land Ratio (${landCount} lands)",
                        "Only ${ratio.toInt()}% lands. Consider 35–40% for consistent curve hits.",
                        true
                    )
                )
            } else {
                notes.add(
                    ValidationNote(
                        "Land Ratio Estimate ($landCount lands)",
                        "$landCount lands (${ratio.toInt()}% of playable deck).",
                        false
                    )
                )
            }
        }

        // 6. Explicit Scope Disclosure — Report only checks actually performed
        val performed = buildList {
            add(if (isCommander) "Commander 100-card count" else "60-card mainboard count")
            add(if (isCommander) "cross-printing singleton limit" else "cross-printing 4-copy limit")
            add("unresolved card legality check")
            if (checkedSnapshot) add("cached $format legality snapshot")
            if (playableCount > 0) add("land ratio heuristic")
        }
        notes.add(
            ValidationNote(
                "Checks Performed (Partial Local Check)",
                "Executed locally: ${performed.joinToString(", ")}. Not verified: live Scryfall banlist updates, Commander color-identity rules, or full tournament validation.",
                false
            )
        )

        return notes
    }

    fun hypergeometricAtLeastK(
        population: Int,
        successes: Int,
        draws: Int,
        minHits: Int
    ): Double {
        if (population <= 0 || draws <= 0 || successes <= 0) return 0.0
        val n = draws.coerceAtMost(population)
        val kMax = minOf(successes, n)
        if (minHits > kMax) return 0.0
        var totalProb = 0.0
        for (k in minHits..kMax) {
            if (n - k <= population - successes) {
                totalProb += hypergeometricExact(population, successes, n, k)
            }
        }
        return totalProb.coerceIn(0.0, 1.0)
    }

    fun hypergeometricExact(
        population: Int,
        successes: Int,
        draws: Int,
        hits: Int
    ): Double {
        if (hits < 0 || hits > successes || hits > draws || draws - hits > population - successes) {
            return 0.0
        }
        val logP = logChoose(successes, hits) +
            logChoose(population - successes, draws - hits) -
            logChoose(population, draws)
        return exp(logP)
    }

    private fun logChoose(n: Int, k: Int): Double {
        if (k < 0 || k > n) return Double.NEGATIVE_INFINITY
        if (k == 0 || k == n) return 0.0
        return logFactorial(n) - logFactorial(k) - logFactorial(n - k)
    }

    private fun logFactorial(n: Int): Double {
        if (n <= 1) return 0.0
        var acc = 0.0
        for (i in 2..n) {
            acc += ln(i.toDouble())
        }
        return acc
    }

    fun parseManaSymbols(manaCost: String): List<String> {
        return pipRegex.findAll(manaCost).map { it.groupValues[1].uppercase() }.toList()
    }

    fun formatListForPreview(cards: List<CardEntity>): String {
        return cards.joinToString(", ") { it.name }
    }
}
