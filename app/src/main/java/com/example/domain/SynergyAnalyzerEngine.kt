package com.example.domain

import com.example.data.local.CardEntity
import com.example.data.local.CardMetadataStatus
import com.example.data.local.DeckEntryWithCard
import com.example.data.local.DeckWithEntries
import com.example.data.local.DeckZones
import kotlin.math.abs

enum class RelationshipType(val label: String) {
    COMBO_LOCK("Combo / Lock"),
    ENGINE_ENABLER("Engine Enabler"),
    TUTOR_TARGET("Tutor Target"),
    PROTECTION_PAYOFF("Protection & Payoff"),
    CURVE_SYNERGY("Tempo / Curve")
}

data class CardRelationship(
    val id: String,
    val cardA: CardEntity,
    val cardB: CardEntity,
    val type: RelationshipType,
    val strength: Int, // 1..3
    val title: String,
    val explanation: String
)

data class DeckCardNode(
    val card: CardEntity,
    val quantity: Int,
    val roleLabel: String,
    val connectionCount: Int,
    val totalStrength: Int
)

data class ArchetypeTemplate(
    val id: String,
    val name: String,
    val category: String,
    val tagline: String,
    val strategyOverview: String,
    val coreStrength: String,
    val vulnerableTo: String,
    val targetLandRatio: Double,
    val targetThreats60: Int,
    val targetInteraction60: Int,
    val targetDrawRamp60: Int,
    val idealAvgCmc: Double,
    val signatureTags: List<String>,
    val signatureKeywords: List<String>,
    val benchmarkCards: List<String>
)

data class ArchetypeMatchResult(
    val template: ArchetypeTemplate,
    val matchScore: Int, // 0..100
    val matchedCards: List<String>,
    val matchedTags: List<String>,
    val matchedBenchmarkCount: Int = 0
)

data class DeckRoleCounts(
    val playableTotal: Int,
    val lands: Int,
    val threats: Int,
    val interaction: Int,
    val drawAndRamp: Int,
    val avgCmc: Double
)

data class SynergyAnalyzerReport(
    val deckWithEntries: DeckWithEntries,
    val roles: DeckRoleCounts,
    val archetypeMatches: List<ArchetypeMatchResult>,
    val relationships: List<CardRelationship>,
    val graphNodes: List<DeckCardNode>,
    val keystoneCards: List<DeckCardNode>
) {
    val primaryArchetype: ArchetypeMatchResult?
        get() = archetypeMatches.firstOrNull { match ->
            match.matchScore >= 30 && match.matchedCards.size >= 2 &&
                (match.template.id != "artifact_lattice" || match.matchedBenchmarkCount >= 2)
        }
}

object SynergyAnalyzerEngine {

    val commonArchetypes: List<ArchetypeTemplate> = listOf(
        ArchetypeTemplate(
            id = "artifact_lattice",
            name = "Artifact Lattice Combo",
            category = "Combo / Engine",
            tagline = "Converge artifact ramp, cost reducers & permanent locks",
            strategyOverview = "Accelerates with mana rocks and artifact discounts to deploy asymmetrical locks (like Karn + Mycosynth Lattice) or overwhelming Construct/draw engines.",
            coreStrength = "Explosive mana generation, inevitability, and tutor density",
            vulnerableTo = "Collector Ouphe, Null Rod, and mass artifact sweepers like Farewell",
            targetLandRatio = 0.36,
            targetThreats60 = 12,
            targetInteraction60 = 10,
            targetDrawRamp60 = 16,
            idealAvgCmc = 2.6,
            signatureTags = listOf("Artifact", "Combo", "Lattice Core", "Lock", "Cost Reduction", "Tutor", "Ramp"),
            signatureKeywords = listOf("Improvise", "Affinity", "Indestructible"),
            benchmarkCards = listOf(
                "Mycosynth Lattice",
                "Urza, Lord High Artificer",
                "Karn, the Great Creator",
                "Unwinding Clock",
                "Darksteel Forge",
                "Whir of Invention",
                "Urza's Saga"
            )
        ),
        ArchetypeTemplate(
            id = "spellslinger_tempo",
            name = "Spellslinger Tempo",
            category = "Tempo",
            tagline = "Chain cheap cantrips, prowess threats & stack interaction",
            strategyOverview = "Operates at instant speed with low-curve threats that scale off noncreature casts while protecting the lead with efficient countermagic and burn.",
            coreStrength = "Card velocity, stack flexibility, and low mana requirements",
            vulnerableTo = "Rule-of-Law tax pieces and graveyard hate against Delve/Delirium",
            targetLandRatio = 0.34,
            targetThreats60 = 12,
            targetInteraction60 = 14,
            targetDrawRamp60 = 14,
            idealAvgCmc = 1.9,
            signatureTags = listOf("Spellslinger", "Card Draw", "Countermagic", "Card Advantage", "Burn"),
            signatureKeywords = listOf("Prowess", "Delve", "Surveil", "Flashback"),
            benchmarkCards = listOf(
                "Dragon's Rage Channeler",
                "Monastery Swiftspear",
                "Murktide Regent",
                "Expressive Iteration",
                "Brainstorm",
                "Counterspell",
                "Lightning Bolt"
            )
        ),
        ArchetypeTemplate(
            id = "aggro_burn",
            name = "Aggro & Burn",
            category = "Aggro",
            tagline = "Maximum early pressure converting mana directly into damage",
            strategyOverview = "Wins before slower engines stabilize by pairing haste/prowess creatures on turns 1–2 with reach burn spells aimed straight at life totals.",
            coreStrength = "Punishes clunky opening hands and slow tap-land mana bases",
            vulnerableTo = "Lifegain stabilization, cheap sweepers, and early blockers",
            targetLandRatio = 0.32,
            targetThreats60 = 20,
            targetInteraction60 = 14,
            targetDrawRamp60 = 6,
            idealAvgCmc = 1.65,
            signatureTags = listOf("Aggro", "Burn", "Removal", "Lifegain"),
            signatureKeywords = listOf("Haste", "Prowess", "First strike", "Double strike", "Dash"),
            benchmarkCards = listOf(
                "Monastery Swiftspear",
                "Ragavan, Nimble Pilferer",
                "Lightning Bolt",
                "Lightning Helix",
                "Boros Charm",
                "Thalia, Guardian of Thraben"
            )
        ),
        ArchetypeTemplate(
            id = "control_tax",
            name = "Control & Tax",
            category = "Control",
            tagline = "Deny resources, sweep boards & close with protected finishers",
            strategyOverview = "Trades one-for-one early, taxes opponent spellcasting and card draw, resets overextended boards, and wins once opponents run out of gas.",
            coreStrength = "Dominates long games and punishes over-extension",
            vulnerableTo = "Uncounterable threats and fast hand disruption",
            targetLandRatio = 0.42,
            targetThreats60 = 6,
            targetInteraction60 = 18,
            targetDrawRamp60 = 11,
            idealAvgCmc = 2.8,
            signatureTags = listOf("Countermagic", "Board Wipe", "Removal", "Tax", "Protection"),
            signatureKeywords = listOf("Overload", "Flash", "Indestructible"),
            benchmarkCards = listOf(
                "Counterspell",
                "Dovin's Veto",
                "Farewell",
                "Cyclonic Rift",
                "Rhystic Study",
                "Smothering Tithe",
                "Teferi's Protection"
            )
        ),
        ArchetypeTemplate(
            id = "midrange_value",
            name = "Midrange Value",
            category = "Midrange",
            tagline = "Flexible threats, discard & efficient universal answers",
            strategyOverview = "Balances proactive threats with hand disruption and versatile removal so every topdeck trades up on card quality and board presence.",
            coreStrength = "Adaptable to any matchup; pivots seamlessly between beatdown and control",
            vulnerableTo = "Hyper-linear big-mana decks that go completely over the top",
            targetLandRatio = 0.38,
            targetThreats60 = 16,
            targetInteraction60 = 13,
            targetDrawRamp60 = 8,
            idealAvgCmc = 2.45,
            signatureTags = listOf("Removal", "Disruption", "Finisher", "Card Advantage", "Lifegain"),
            signatureKeywords = listOf("Deathtouch", "Flash", "Flying", "Revolt"),
            benchmarkCards = listOf(
                "Thoughtseize",
                "Fatal Push",
                "Orcish Bowmasters",
                "Sheoldred, the Apocalypse",
                "Assassin's Trophy",
                "Eternal Witness"
            )
        ),
        ArchetypeTemplate(
            id = "ramp_big_mana",
            name = "Big Mana & Ramp",
            category = "Ramp",
            tagline = "Accelerate land & artifact mana ahead of curve into finishers",
            strategyOverview = "Spends turns 1–3 deploying mana dorks, rocks, and land-fetch spells so 6–9 mana haymakers resolve turns ahead of schedule.",
            coreStrength = "Overpowers fair decks once ahead on available mana",
            vulnerableTo = "Early countermagic on payoff spells and aggressive clocks",
            targetLandRatio = 0.38,
            targetThreats60 = 11,
            targetInteraction60 = 8,
            targetDrawRamp60 = 18,
            idealAvgCmc = 3.1,
            signatureTags = listOf("Ramp", "Mana Fixing", "Finisher", "Card Advantage"),
            signatureKeywords = listOf("Treasure", "Affinity"),
            benchmarkCards = listOf(
                "Sol Ring",
                "Ancient Tomb",
                "Cultivate",
                "Birds of Paradise",
                "Jeska's Will",
                "Darksteel Forge"
            )
        ),
        ArchetypeTemplate(
            id = "tokens_go_wide",
            name = "Tokens & Go-Wide",
            category = "Synergy",
            tagline = "Flood the board with tokens and turn bodies into cards",
            strategyOverview = "Generates repeatable creature or artifact tokens off spell triggers and pairs them with Skullclamp draw engines or protection spells.",
            coreStrength = "Resilient to spot removal and explosive with Skullclamp",
            vulnerableTo = "Toxic Deluge, Blasphemous Act, and other cheap sweepers",
            targetLandRatio = 0.36,
            targetThreats60 = 14,
            targetInteraction60 = 10,
            targetDrawRamp60 = 14,
            idealAvgCmc = 2.35,
            signatureTags = listOf("Tokens", "Protection", "Card Draw", "Counters"),
            signatureKeywords = listOf("Prowess", "Proliferate", "Equip", "Treasure"),
            benchmarkCards = listOf(
                "Monastery Mentor",
                "Skullclamp",
                "Urza, Lord High Artificer",
                "Smothering Tithe",
                "Heroic Intervention",
                "Boros Charm"
            )
        ),
        ArchetypeTemplate(
            id = "graveyard_delirium",
            name = "Graveyard & Delirium",
            category = "Graveyard",
            tagline = "Fuel the bin with cheap card types for Delirium & recursion",
            strategyOverview = "Turns the graveyard into a second hand using self-mill, surveil, 0-mana baubles, and flashback spells to unlock Delirium, Delve, and recursion loops.",
            coreStrength = "Incredible late-game attrition and under-costed threats",
            vulnerableTo = "Farewell, Rest in Peace, and exile-based graveyard hate",
            targetLandRatio = 0.35,
            targetThreats60 = 14,
            targetInteraction60 = 12,
            targetDrawRamp60 = 13,
            idealAvgCmc = 2.1,
            signatureTags = listOf("Graveyard", "Card Draw", "Spellslinger", "Recursion"),
            signatureKeywords = listOf("Delirium", "Delve", "Surveil", "Flashback"),
            benchmarkCards = listOf(
                "Dragon's Rage Channeler",
                "Mishra's Bauble",
                "Faithless Looting",
                "Murktide Regent",
                "Academy Ruins",
                "Eternal Witness"
            )
        )
    )

    private data class CuratedPairRule(
        val nameA: String,
        val nameB: String,
        val type: RelationshipType,
        val strength: Int,
        val title: String,
        val explanation: String
    )

    private val curatedPairRules: List<CuratedPairRule> = listOf(
        CuratedPairRule(
            "Mycosynth Lattice", "Karn, the Great Creator",
            RelationshipType.COMBO_LOCK, 3,
            "Hard Mana & Artifact Lock",
            "Lattice turns all opponents' permanents—including lands—into artifacts, and Karn prevents opponents from activating any artifact abilities."
        ),
        CuratedPairRule(
            "Mycosynth Lattice", "Darksteel Forge",
            RelationshipType.PROTECTION_PAYOFF, 3,
            "Board-Wide Indestructible Shell",
            "Every permanent you control becomes an artifact under Lattice and gains indestructible from Darksteel Forge."
        ),
        CuratedPairRule(
            "Mycosynth Lattice", "Urza, Lord High Artificer",
            RelationshipType.COMBO_LOCK, 3,
            "Every Permanent Taps for {U}",
            "Lattice makes all permanents artifacts so Urza can tap lands, tokens, and enchantments alike for blue mana, then spend any color to spin Urza's ability."
        ),
        CuratedPairRule(
            "Mycosynth Lattice", "Cyclonic Rift",
            RelationshipType.PROTECTION_PAYOFF, 2,
            "Colorless Mana Overload Setup",
            "Lattice lets you spend any mana as blue so off-color mana rocks cast Overloaded Rift smoothly."
        ),
        CuratedPairRule(
            "Urza, Lord High Artificer", "Unwinding Clock",
            RelationshipType.ENGINE_ENABLER, 3,
            "Every-Turn Construct & Mana Engine",
            "Unwinding Clock untaps your artifacts on each opponent's untap step, letting you generate {U} with Urza and activate his top-deck spin every turn cycle."
        ),
        CuratedPairRule(
            "Urza, Lord High Artificer", "Foundry Inspector",
            RelationshipType.ENGINE_ENABLER, 2,
            "Discounted Mox Sapphire Chain",
            "Foundry Inspector cuts artifact costs while Urza turns each artifact into immediate blue mana."
        ),
        CuratedPairRule(
            "Urza, Lord High Artificer", "Etherium Sculptor",
            RelationshipType.ENGINE_ENABLER, 2,
            "Zero-Net Artifact Acceleration",
            "Etherium Sculptor discounts artifact spells and immediately taps for {U} via Urza upon resolving."
        ),
        CuratedPairRule(
            "Whir of Invention", "Mycosynth Lattice",
            RelationshipType.TUTOR_TARGET, 3,
            "Instant-Speed Lattice Tutor",
            "Improvise lets you tap mana rocks and Tokens to Whir directly for Mycosynth Lattice onto the battlefield at opponents' end step."
        ),
        CuratedPairRule(
            "Whir of Invention", "The One Ring",
            RelationshipType.TUTOR_TARGET, 3,
            "End-Step Artifact Tutor",
            "Fetch The One Ring straight into play with Improvise for fog protection and scaling card draw."
        ),
        CuratedPairRule(
            "Fabricate", "Mycosynth Lattice",
            RelationshipType.TUTOR_TARGET, 2,
            "Direct Combo Tutor",
            "Fabricate reliably locates Mycosynth Lattice, Darksteel Forge, or Unwinding Clock from anywhere in the library."
        ),
        CuratedPairRule(
            "Urza's Saga", "Sol Ring",
            RelationshipType.TUTOR_TARGET, 3,
            "Chapter III {1}-Drop Acceleration",
            "Urza's Saga creates artifact Constructs at Chapter II and tutors Sol Ring (or Sensei's Divining Top / Skullclamp) directly into play on Chapter III."
        ),
        CuratedPairRule(
            "Urza's Saga", "Sensei's Divining Top",
            RelationshipType.TUTOR_TARGET, 2,
            "Library Manipulation Tutor",
            "Chapter III fetches Sensei's Divining Top straight onto the battlefield to smooth draws and stack top-deck triggers."
        ),
        CuratedPairRule(
            "Urza's Saga", "Skullclamp",
            RelationshipType.TUTOR_TARGET, 3,
            "Token-to-Draw Pipeline",
            "Urza's Saga builds Construct tokens and then fetches Skullclamp to turn spare 1/1s into two-card bursts."
        ),
        CuratedPairRule(
            "Skullclamp", "Monastery Mentor",
            RelationshipType.ENGINE_ENABLER, 3,
            "Endless Monk Draw Engine",
            "Casting noncreature spells (including Equip or cantrips) spawns 1/1 Monk tokens that Skullclamp immediately converts into two cards."
        ),
        CuratedPairRule(
            "Dragon's Rage Channeler", "Mishra's Bauble",
            RelationshipType.ENGINE_ENABLER, 3,
            "Zero-Mana Delirium & Surveil",
            "Mishra's Bauble costs {0}, triggers Surveil 1 immediately, and puts Artifact into the graveyard to turn on 3/3 Flying Delirium fast."
        ),
        CuratedPairRule(
            "Dragon's Rage Channeler", "Faithless Looting",
            RelationshipType.ENGINE_ENABLER, 3,
            "Delirium Fuel & Card Selection",
            "Faithless Looting stacks Sorcery plus discarded types in the graveyard while Channeler filters the top of your library."
        ),
        CuratedPairRule(
            "Dragon's Rage Channeler", "Murktide Regent",
            RelationshipType.CURVE_SYNERGY, 3,
            "Surveil-to-Delve Finisher Setup",
            "Surveil 1 bins instants and sorceries cleanly to cast Murktide Regent for {U}{U} with bonus +1/+1 counters."
        ),
        CuratedPairRule(
            "Monastery Swiftspear", "Lightning Bolt",
            RelationshipType.CURVE_SYNERGY, 3,
            "4+ Damage Prowess Burst",
            "Lightning Bolt clears blockers or hits face while triggering Swiftspear's Prowess for extra combat pressure."
        ),
        CuratedPairRule(
            "Monastery Swiftspear", "Expressive Iteration",
            RelationshipType.CURVE_SYNERGY, 2,
            "Refuel + Prowess Trigger",
            "Expressive Iteration triggers prowess on cast and finds two low-curve spells to keep pressure going."
        ),
        CuratedPairRule(
            "Monastery Swiftspear", "Boros Charm",
            RelationshipType.COMBO_LOCK, 3,
            "Double Strike Prowess Alpha",
            "Giving a prowess-boosted Swiftspear Double Strike plus +1/+1 converts cheap instants into lethal bursts."
        ),
        CuratedPairRule(
            "Smothering Tithe", "Rhystic Study",
            RelationshipType.ENGINE_ENABLER, 3,
            "Dual Resource Tax Net",
            "Opponents are squeezed between paying taxes on every spell cast and every card drawn, burying them in Treasures and cards."
        ),
        CuratedPairRule(
            "Sheoldred, the Apocalypse", "The One Ring",
            RelationshipType.PROTECTION_PAYOFF, 3,
            "Burden Life-Loss Offset",
            "Sheoldred gains 2 life per card drawn off The One Ring, completely negating burden counter life loss."
        ),
        CuratedPairRule(
            "Academy Ruins", "Mishra's Bauble",
            RelationshipType.ENGINE_ENABLER, 2,
            "Repeatable Artifact Recursion",
            "Sacrifice Mishra's Bauble for a draw and recur it back on top with Academy Ruins during stalled board states."
        ),
        CuratedPairRule(
            "Inventors' Fair", "Mycosynth Lattice",
            RelationshipType.TUTOR_TARGET, 2,
            "Land-Slot Artifact Tutor",
            "Having three artifacts enables life gain and lets Inventors' Fair tutor your Lattice combo piece without spending a spell slot."
        )
    )

    fun analyzeDeck(deckWithEntries: DeckWithEntries): SynergyAnalyzerReport {
        val playable = deckWithEntries.entries.filter {
            it.entry.zone == DeckZones.MAINBOARD || it.entry.zone == DeckZones.COMMANDER
        }
        val playableTotal = playable.sumOf { it.entry.quantity }

        val roles = computeRoleCounts(playable, playableTotal)
        val archetypeMatches = scoreArchetypes(playable, playableTotal, roles.avgCmc)
        val relationships = discoverRelationships(playable)
        val nodeStats = buildDeckNodes(playable, relationships)

        return SynergyAnalyzerReport(
            deckWithEntries = deckWithEntries,
            roles = roles,
            archetypeMatches = archetypeMatches,
            relationships = relationships,
            graphNodes = nodeStats.take(12),
            keystoneCards = nodeStats.take(5)
        )
    }

    private fun computeRoleCounts(
        playable: List<DeckEntryWithCard>,
        playableTotal: Int
    ): DeckRoleCounts {
        var lands = 0
        var threats = 0
        var interaction = 0
        var drawAndRamp = 0
        var nonLandCount = 0
        var cmcSum = 0.0

        playable.forEach { item ->
            val qty = item.entry.quantity
            val card = item.card
            val typeLower = card.typeLine.lowercase()
            val tagsLower = card.synergyTags.lowercase()

            if (PlaytestMana.isLand(card)) {
                lands += qty
            } else {
                if (card.metadataStatus != CardMetadataStatus.UNRESOLVED) {
                    nonLandCount += qty
                    cmcSum += card.cmc * qty
                }

                val isInteraction = "removal" in tagsLower ||
                    "countermagic" in tagsLower ||
                    "board wipe" in tagsLower ||
                    "disruption" in tagsLower ||
                    "burn" in tagsLower
                val isDrawOrRamp = "card draw" in tagsLower ||
                    "card advantage" in tagsLower ||
                    "ramp" in tagsLower ||
                    "cost reduction" in tagsLower ||
                    "tutor" in tagsLower
                val isThreat = "creature" in typeLower ||
                    "planeswalker" in typeLower ||
                    "finisher" in tagsLower ||
                    "combo" in tagsLower ||
                    "tokens" in tagsLower

                when {
                    isThreat -> threats += qty
                    isInteraction -> interaction += qty
                    isDrawOrRamp -> drawAndRamp += qty
                    else -> interaction += qty
                }
            }
        }

        val avgCmc = if (nonLandCount > 0) cmcSum / nonLandCount else 0.0
        return DeckRoleCounts(
            playableTotal = playableTotal,
            lands = lands,
            threats = threats,
            interaction = interaction,
            drawAndRamp = drawAndRamp,
            avgCmc = avgCmc
        )
    }

    private fun scoreArchetypes(
        playable: List<DeckEntryWithCard>,
        playableTotal: Int,
        avgCmc: Double
    ): List<ArchetypeMatchResult> {
        val uniqueNonLand = playable.filter { "land" !in it.card.typeLine.lowercase() || "tutor" in it.card.synergyTags.lowercase() }
        val deckNames = playable.map { it.card.name }.toSet()

        return commonArchetypes.map { template ->
            val matchedCards = linkedSetOf<String>()
            val matchedTags = linkedSetOf<String>()
            var weightedPoints = 0.0
            var totalWeight = 0.0

            playable.forEach { item ->
                val card = item.card
                val qtyWeight = item.entry.quantity.coerceAtMost(4).toDouble()
                val tags = card.synergyTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val keywords = card.keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val tagHits = tags.filter { it in template.signatureTags }
                val kwHits = keywords.filter { it in template.signatureKeywords }
                val benchmarkHit = card.name in template.benchmarkCards

                if ("land" !in card.typeLine.lowercase() || benchmarkHit || tagHits.isNotEmpty()) {
                    totalWeight += qtyWeight
                    var cardScore = 0.0
                    if (tagHits.isNotEmpty()) {
                        cardScore += (tagHits.size * 0.45).coerceAtMost(1.0)
                        matchedTags.addAll(tagHits)
                    }
                    if (kwHits.isNotEmpty()) {
                        cardScore += (kwHits.size * 0.35).coerceAtMost(0.75)
                    }
                    if (benchmarkHit) {
                        cardScore += 0.65
                    }
                    if (cardScore > 0.0) {
                        matchedCards.add(card.name)
                    }
                    weightedPoints += qtyWeight * cardScore.coerceAtMost(1.25)
                }
            }

            // Benchmark presence bonus + CMC proximity
            val benchmarkHits = template.benchmarkCards.count { it in deckNames }
            val benchmarkBonus = (benchmarkHits * 6.5).coerceAtMost(26.0)
            val cmcDiff = abs(avgCmc - template.idealAvgCmc)
            val cmcBonus = if (playableTotal > 0) (14.0 - cmcDiff * 8.0).coerceIn(0.0, 14.0) else 0.0

            val ratioScore = if (totalWeight > 0.0) {
                ((weightedPoints / totalWeight) * 72.0).coerceIn(0.0, 72.0)
            } else 0.0

            val totalScore = if (uniqueNonLand.isEmpty() && playableTotal == 0) {
                0
            } else {
                (ratioScore + benchmarkBonus + cmcBonus).toInt().coerceIn(5, 99)
            }

            ArchetypeMatchResult(
                template = template,
                matchScore = totalScore,
                matchedCards = matchedCards.toList(),
                matchedTags = matchedTags.toList(),
                matchedBenchmarkCount = benchmarkHits
            )
        }.sortedByDescending { it.matchScore }
    }

    private fun discoverRelationships(playable: List<DeckEntryWithCard>): List<CardRelationship> {
        val uniqueCards = playable
            .map { it.card }
            .distinctBy { it.id }
            .filter { card ->
                val isBasicLand = card.name in setOf("Plains", "Island", "Swamp", "Mountain", "Forest", "Wastes")
                !isBasicLand
            }

        val relationships = mutableListOf<CardRelationship>()
        val seenPairs = mutableSetOf<String>()

        fun pairKey(a: CardEntity, b: CardEntity): String {
            return if (a.id <= b.id) "${a.id}|${b.id}" else "${b.id}|${a.id}"
        }

        // 1. Curated iconic MTG pairs
        val byName = uniqueCards.associateBy { it.name.lowercase() }
        curatedPairRules.forEach { rule ->
            val cardA = byName[rule.nameA.lowercase()]
            val cardB = byName[rule.nameB.lowercase()]
            if (cardA != null && cardB != null && cardA.id != cardB.id) {
                val key = pairKey(cardA, cardB)
                if (seenPairs.add(key)) {
                    relationships.add(
                        CardRelationship(
                            id = key,
                            cardA = cardA,
                            cardB = cardB,
                            type = rule.type,
                            strength = rule.strength,
                            title = rule.title,
                            explanation = rule.explanation
                        )
                    )
                }
            }
        }

        // 2. Dynamic functional synergy discovery for any custom/imported cards
        for (i in uniqueCards.indices) {
            for (j in i + 1 until uniqueCards.size) {
                val a = uniqueCards[i]
                val b = uniqueCards[j]
                val key = pairKey(a, b)
                if (key in seenPairs) continue

                val dynamic = evaluateDynamicPair(a, b)
                if (dynamic != null) {
                    seenPairs.add(key)
                    relationships.add(dynamic)
                }
            }
        }

        return relationships
            .sortedWith(
                compareByDescending<CardRelationship> { it.strength }
                    .thenBy { it.type.ordinal }
                    .thenBy { it.cardA.name }
            )
            .take(32)
    }

    private fun evaluateDynamicPair(a: CardEntity, b: CardEntity): CardRelationship? {
        val key = if (a.id <= b.id) "${a.id}|${b.id}" else "${b.id}|${a.id}"
        val oracleA = a.oracleText.lowercase()
        val oracleB = b.oracleText.lowercase()
        val typeA = a.typeLine.lowercase()
        val typeB = b.typeLine.lowercase()
        val tagsA = a.synergyTags.split(",").map { it.trim() }.filter { it.isNotEmpty() && it != "Land" }.toSet()
        val tagsB = b.synergyTags.split(",").map { it.trim() }.filter { it.isNotEmpty() && it != "Land" }.toSet()

        // Artifact tutor -> high-impact artifact
        val aArtifactTutor = "search your library for an artifact" in oracleA
        val bArtifactTutor = "search your library for an artifact" in oracleB
        if (aArtifactTutor && "artifact" in typeB && b.cmc >= 3.0) {
            return CardRelationship(
                id = key,
                cardA = a,
                cardB = b,
                type = RelationshipType.TUTOR_TARGET,
                strength = 2,
                title = "Artifact Tutor Line",
                explanation = "${a.name} can search directly for ${b.name} to assemble your artifact plan on demand."
            )
        }
        if (bArtifactTutor && "artifact" in typeA && a.cmc >= 3.0) {
            return CardRelationship(
                id = key,
                cardA = b,
                cardB = a,
                type = RelationshipType.TUTOR_TARGET,
                strength = 2,
                title = "Artifact Tutor Line",
                explanation = "${b.name} can search directly for ${a.name} to assemble your artifact plan on demand."
            )
        }

        // Cost reducer -> high-CMC artifact
        val aReducesArtifact = "artifact spells you cast cost" in oracleA
        val bReducesArtifact = "artifact spells you cast cost" in oracleB
        if (aReducesArtifact && "artifact" in typeB && b.cmc >= 4.0) {
            return CardRelationship(
                id = key,
                cardA = a,
                cardB = b,
                type = RelationshipType.CURVE_SYNERGY,
                strength = 2,
                title = "Artifact Cost Discount",
                explanation = "${a.name} shaves mana off ${b.name}, letting you deploy heavy artifacts a turn earlier."
            )
        }
        if (bReducesArtifact && "artifact" in typeA && a.cmc >= 4.0) {
            return CardRelationship(
                id = key,
                cardA = b,
                cardB = a,
                type = RelationshipType.CURVE_SYNERGY,
                strength = 2,
                title = "Artifact Cost Discount",
                explanation = "${b.name} shaves mana off ${a.name}, letting you deploy heavy artifacts a turn earlier."
            )
        }

        // Noncreature / prowess payoff + cheap instant/sorcery
        val aProwess = "prowess" in a.keywords.lowercase() || "whenever you cast a noncreature spell" in oracleA
        val bProwess = "prowess" in b.keywords.lowercase() || "whenever you cast a noncreature spell" in oracleB
        if (aProwess && ("instant" in typeB || "sorcery" in typeB) && b.cmc <= 2.0) {
            return CardRelationship(
                id = key,
                cardA = a,
                cardB = b,
                type = RelationshipType.CURVE_SYNERGY,
                strength = 2,
                title = "Cheap Spell Trigger",
                explanation = "Casting ${b.name} for ${b.cmc.toInt()} mana triggers ${a.name}'s noncreature payoff cleanly on curve."
            )
        }
        if (bProwess && ("instant" in typeA || "sorcery" in typeA) && a.cmc <= 2.0) {
            return CardRelationship(
                id = key,
                cardA = b,
                cardB = a,
                type = RelationshipType.CURVE_SYNERGY,
                strength = 2,
                title = "Cheap Spell Trigger",
                explanation = "Casting ${a.name} for ${a.cmc.toInt()} mana triggers ${b.name}'s noncreature payoff cleanly on curve."
            )
        }

        // Protection / Countermagic -> high-value combo or finisher
        val aProtects = "Protection" in tagsA || "Countermagic" in tagsA
        val bProtects = "Protection" in tagsB || "Countermagic" in tagsB
        val aPayoff = "Combo" in tagsA || "Finisher" in tagsA || "Lattice Core" in tagsA
        val bPayoff = "Combo" in tagsB || "Finisher" in tagsB || "Lattice Core" in tagsB
        if (aProtects && bPayoff && !aPayoff) {
            return CardRelationship(
                id = key,
                cardA = a,
                cardB = b,
                type = RelationshipType.PROTECTION_PAYOFF,
                strength = 2,
                title = "Wincon Shield",
                explanation = "${a.name} protects ${b.name} from opposing removal or stack interaction when closing the game."
            )
        }
        if (bProtects && aPayoff && !bPayoff) {
            return CardRelationship(
                id = key,
                cardA = b,
                cardB = a,
                type = RelationshipType.PROTECTION_PAYOFF,
                strength = 2,
                title = "Wincon Shield",
                explanation = "${b.name} protects ${a.name} from opposing removal or stack interaction when closing the game."
            )
        }

        // Fast ramp -> 4+ CMC engine/finisher
        val aFastRamp = "Ramp" in tagsA && a.cmc <= 2.0 && "land" !in typeA
        val bFastRamp = "Ramp" in tagsB && b.cmc <= 2.0 && "land" !in typeB
        if (aFastRamp && b.cmc >= 4.0 && bPayoff) {
            return CardRelationship(
                id = key,
                cardA = a,
                cardB = b,
                type = RelationshipType.CURVE_SYNERGY,
                strength = 1,
                title = "Fast Mana Acceleration",
                explanation = "${a.name} accelerates into ${b.name} ahead of normal land drops."
            )
        }
        if (bFastRamp && a.cmc >= 4.0 && aPayoff) {
            return CardRelationship(
                id = key,
                cardA = b,
                cardB = a,
                type = RelationshipType.CURVE_SYNERGY,
                strength = 1,
                title = "Fast Mana Acceleration",
                explanation = "${b.name} accelerates into ${a.name} ahead of normal land drops."
            )
        }

        // Shared deep synergy (2+ shared tactical tags)
        val shared = tagsA.intersect(tagsB)
        if (shared.size >= 2) {
            val strength = if (shared.size >= 3) 2 else 1
            val type = when {
                "Combo" in shared -> RelationshipType.ENGINE_ENABLER
                "Protection" in shared -> RelationshipType.PROTECTION_PAYOFF
                else -> RelationshipType.CURVE_SYNERGY
            }
            return CardRelationship(
                id = key,
                cardA = a,
                cardB = b,
                type = type,
                strength = strength,
                title = "Shared ${shared.take(2).joinToString(" + ")} Core",
                explanation = "${a.name} and ${b.name} reinforce the same ${shared.joinToString(", ")} axis inside your deck."
            )
        }

        return null
    }

    private fun buildDeckNodes(
        playable: List<DeckEntryWithCard>,
        relationships: List<CardRelationship>
    ): List<DeckCardNode> {
        val degreeMap = mutableMapOf<String, Int>()
        val strengthMap = mutableMapOf<String, Int>()

        relationships.forEach { rel ->
            degreeMap[rel.cardA.id] = (degreeMap[rel.cardA.id] ?: 0) + 1
            degreeMap[rel.cardB.id] = (degreeMap[rel.cardB.id] ?: 0) + 1
            strengthMap[rel.cardA.id] = (strengthMap[rel.cardA.id] ?: 0) + rel.strength
            strengthMap[rel.cardB.id] = (strengthMap[rel.cardB.id] ?: 0) + rel.strength
        }

        val uniqueEntries = playable
            .filter { it.card.name !in setOf("Plains", "Island", "Swamp", "Mountain", "Forest", "Wastes") }
            .groupBy { it.card.id }
            .map { (_, items) ->
                val card = items.first().card
                val qty = items.sumOf { it.entry.quantity }
                val tags = card.synergyTags.split(",").map { it.trim() }.filter { it.isNotEmpty() && it != "Land" }
                val roleLabel = tags.firstOrNull() ?: card.typeLine.substringBefore("—").trim()
                DeckCardNode(
                    card = card,
                    quantity = qty,
                    roleLabel = roleLabel,
                    connectionCount = degreeMap[card.id] ?: 0,
                    totalStrength = strengthMap[card.id] ?: 0
                )
            }

        return uniqueEntries.sortedWith(
            compareByDescending<DeckCardNode> { it.totalStrength }
                .thenByDescending { it.connectionCount }
                .thenBy { it.card.name }
        )
    }
}
