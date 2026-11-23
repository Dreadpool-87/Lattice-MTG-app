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
    COMBAT_SYNERGY("Combat Synergy"),
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
    internal val ranking: Double,
    val matchedCards: List<String>
) {
    val hasEvidence: Boolean
        get() = matchedCards.size >= 2
}

data class DeckRoleCounts(
    val playableTotal: Int,
    val lands: Int,
    val threats: Int,
    val interaction: Int,
    val drawAndRamp: Int,
    val avgCmc: Double?
)

data class SynergyAnalyzerReport(
    val deckWithEntries: DeckWithEntries,
    val roles: DeckRoleCounts,
    val archetypeMatches: List<ArchetypeMatchResult>,
    val relationships: List<CardRelationship>,
    val analyzedCardNames: Set<String>,
    val skippedCopies: Int
) {
    val primaryArchetype: ArchetypeMatchResult?
        get() = archetypeMatches.firstOrNull { roles.avgCmc != null && it.hasEvidence }
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
                "Sol Ring",
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
            "With X at least 6, Whir puts Mycosynth Lattice from your library onto the battlefield. Improvise can help pay the generic portion of Whir's cost."
        ),
        CuratedPairRule(
            "Whir of Invention", "The One Ring",
            RelationshipType.TUTOR_TARGET, 3,
            "End-Step Artifact Tutor",
            "With X at least 4, Whir puts The One Ring from your library onto the battlefield. You can use the Ring's draw ability. Its protection trigger requires casting the Ring itself, so it does not trigger here."
        ),
        CuratedPairRule(
            "Fabricate", "Mycosynth Lattice",
            RelationshipType.TUTOR_TARGET, 2,
            "Direct Combo Tutor",
            "When Fabricate resolves, it can find Mycosynth Lattice in your library and put it into your hand."
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
            "Monastery Mentor creates a 1/1 Monk when you cast a noncreature spell. If Skullclamp equips a Monk with 1 toughness, the Monk dies and Skullclamp draws you two cards."
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
            RelationshipType.COMBAT_SYNERGY, 3,
            "Prowess and Double Strike",
            "Casting Boros Charm while Monastery Swiftspear is on the battlefield triggers Swiftspear's prowess for +1/+1. Choose Charm's double-strike mode and target Swiftspear before combat damage to give it double strike."
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
            "With Sheoldred on the battlefield, you gain 2 life for each card you draw from The One Ring. This can offset life lost to the Ring's burden counters on your upkeep."
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
            "If you control at least three artifacts, you can pay {4}, tap and sacrifice Inventors' Fair to put Mycosynth Lattice from your library into your hand."
        )
    )

    fun analyzeDeck(deckWithEntries: DeckWithEntries): SynergyAnalyzerReport {
        val playable = deckWithEntries.entries.filter {
            it.entry.zone == DeckZones.MAINBOARD || it.entry.zone == DeckZones.COMMANDER
        }
        val playableTotal = playable.sumOf { it.entry.quantity }
        val verified = playable.filter {
            it.card.metadataStatus == CardMetadataStatus.VERIFIED &&
                !it.card.isCustom && !it.card.isUnresolvedForAnalysis
        }
        val oracleIdsByName = verified.oracleIdsByName()
        val byIdentity = verified.groupBy { canonicalCardIdentityKey(it.card, oracleIdsByName) }
        val cardsByIdentity = byIdentity.values.map { printings ->
            val representative = printings.first()
            representative.copy(
                entry = representative.entry.copy(quantity = printings.sumOf { it.entry.quantity })
            )
        }
        val inLibrary = byIdentity.values.mapNotNull { printings ->
            printings.first().card.id.takeIf { printings.any { it.entry.zone == DeckZones.MAINBOARD } }
        }.toSet()

        val roles = computeRoleCounts(verified, playableTotal)
        return SynergyAnalyzerReport(
            deckWithEntries = deckWithEntries,
            roles = roles,
            archetypeMatches = scoreArchetypes(cardsByIdentity, roles.avgCmc),
            relationships = discoverRelationships(cardsByIdentity.map { it.card }, inLibrary),
            analyzedCardNames = verified.map { it.card.name }.toSet(),
            skippedCopies = playableTotal - verified.sumOf { it.entry.quantity }
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
            val types = card.frontTypes
            val tags = card.synergyTags.lowercase()

            if ("land" in types) {
                lands += qty
            } else {
                nonLandCount += qty
                cmcSum += card.cmc * qty

                val isInteraction = "removal" in tags || "countermagic" in tags ||
                    "board wipe" in tags || "disruption" in tags || "burn" in tags
                val isDrawOrRamp = "card draw" in tags || "card advantage" in tags ||
                    "ramp" in tags || "cost reduction" in tags || "tutor" in tags
                val isThreat = "creature" in types || "planeswalker" in types ||
                    "finisher" in tags || "combo" in tags || "tokens" in tags

                when {
                    isThreat -> threats += qty
                    isInteraction -> interaction += qty
                    isDrawOrRamp -> drawAndRamp += qty
                }
            }
        }

        return DeckRoleCounts(
            playableTotal = playableTotal,
            lands = lands,
            threats = threats,
            interaction = interaction,
            drawAndRamp = drawAndRamp,
            avgCmc = if (nonLandCount > 0) cmcSum / nonLandCount else null
        )
    }

    private fun scoreArchetypes(
        playable: List<DeckEntryWithCard>,
        avgCmc: Double?
    ): List<ArchetypeMatchResult> =
        commonArchetypes.map { template ->
            val matchedCards = mutableListOf<String>()
            var benchmarkHits = 0
            var weightedPoints = 0.0
            var totalWeight = 0.0

            playable.forEach { item ->
                val card = item.card
                val tagHits = card.synergyTags.split(',').count { it.trim() in template.signatureTags }
                val keywordHits = card.keywords.split(',').count { it.trim() in template.signatureKeywords }
                val benchmarkHit = card.name in template.benchmarkCards

                if ("land" !in card.frontTypes || benchmarkHit || tagHits > 0 || keywordHits > 0) {
                    val weight = item.entry.quantity.coerceAtMost(4)
                    val cardPoints = (tagHits * 0.45).coerceAtMost(1.0) +
                        (keywordHits * 0.35).coerceAtMost(0.75) +
                        if (benchmarkHit) 0.65 else 0.0
                    if (cardPoints > 0.0) matchedCards.add(card.name)
                    if (benchmarkHit) benchmarkHits++
                    totalWeight += weight
                    weightedPoints += weight * cardPoints.coerceAtMost(1.25)
                }
            }

            val benchmarkBonus = (benchmarkHits * 6.5).coerceAtMost(26.0)
            val curveBonus = avgCmc?.let { (14.0 - abs(it - template.idealAvgCmc) * 8.0).coerceIn(0.0, 14.0) } ?: 0.0
            val ratioPoints = if (totalWeight > 0) (weightedPoints / totalWeight * 72.0).coerceAtMost(72.0) else 0.0
            ArchetypeMatchResult(template, ratioPoints + benchmarkBonus + curveBonus, matchedCards)
        }.sortedWith(compareByDescending<ArchetypeMatchResult> { it.hasEvidence }.thenByDescending { it.ranking })

    private fun discoverRelationships(
        cards: List<CardEntity>,
        inLibrary: Set<String>
    ): List<CardRelationship> {
        val uniqueCards = cards.filter { "basic" !in it.frontTypes || "land" !in it.frontTypes }

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
            if (cardA != null && cardB != null && cardA.id != cardB.id &&
                (rule.type != RelationshipType.TUTOR_TARGET || cardB.id in inLibrary)
            ) {
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

                val dynamic = tutorRelationship(a, b, key, inLibrary)
                    ?: tutorRelationship(b, a, key, inLibrary)
                    ?: evaluateDynamicPair(a, b, key)
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
    }

    private val artifactSearch = Regex(
        """search your library for an artifact card(?: with mana value (x|[0-9]+)( or less| or greater)?)?, (reveal (?:it|that card), put (?:it|that card) into your hand|put (?:it|that card) onto the battlefield)""",
        RegexOption.IGNORE_CASE
    )

    private fun tutorRelationship(
        tutor: CardEntity,
        target: CardEntity,
        key: String,
        inLibrary: Set<String>
    ): CardRelationship? {
        if ("artifact" !in target.frontTypes || target.id !in inLibrary) return null
        val search = artifactSearch.find(tutor.oracleText) ?: return null
        val (_, value, comparison, destination) = search.groupValues
        val threshold = value.toDoubleOrNull()
        val eligible = when {
            value.isEmpty() -> true
            value.equals("x", ignoreCase = true) -> comparison.equals(" or less", ignoreCase = true)
            threshold == null -> false
            comparison.equals(" or less", ignoreCase = true) -> target.cmc <= threshold
            comparison.equals(" or greater", ignoreCase = true) -> target.cmc >= threshold
            else -> target.cmc == threshold
        }
        if (!eligible) return null

        val condition = when {
            value.equals("x", ignoreCase = true) -> "When the search effect resolves with X at least ${target.cmc.toInt()}"
            tutor.name == "Inventors' Fair" -> "If you control at least three artifacts and activate ${tutor.name}"
            else -> "When ${tutor.name}'s search effect resolves"
        }
        val destinationText = if (destination.startsWith("reveal", ignoreCase = true)) {
            "into your hand"
        } else {
            "onto the battlefield"
        }
        return CardRelationship(
            id = key,
            cardA = tutor,
            cardB = target,
            type = RelationshipType.TUTOR_TARGET,
            strength = 2,
            title = "Artifact Tutor",
            explanation = "$condition, it can put ${target.name} from your library $destinationText."
        )
    }

    private fun evaluateDynamicPair(a: CardEntity, b: CardEntity, key: String): CardRelationship? {
        val oracleA = a.oracleText.lowercase()
        val oracleB = b.oracleText.lowercase()
        val typeA = a.frontTypes
        val typeB = b.frontTypes
        val tagsA = a.synergyTags.split(",").map { it.trim() }.filter { it.isNotEmpty() && it != "Land" }.toSet()
        val tagsB = b.synergyTags.split(",").map { it.trim() }.filter { it.isNotEmpty() && it != "Land" }.toSet()

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
                explanation = "${a.name} shaves mana off ${b.name}, if its cost includes generic mana."
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
                explanation = "${b.name} shaves mana off ${a.name}, if its cost includes generic mana."
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
                explanation = "Casting ${b.name} for ${b.cmc.toInt()} mana triggers ${a.name}'s noncreature payoff while ${a.name} is on the battlefield."
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
                explanation = "Casting ${a.name} for ${a.cmc.toInt()} mana triggers ${b.name}'s noncreature payoff while ${b.name} is on the battlefield."
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
                explanation = "${a.name} may help protect ${b.name} if its effect applies to the opposing spell or ability."
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
                explanation = "${b.name} may help protect ${a.name} if its effect applies to the opposing spell or ability."
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

        // Shared tags alone do not establish a failed or unsupported tutor line.
        val hasArtifactSearch = "search your library for an artifact card"
        if (hasArtifactSearch in oracleA && "artifact" in typeB ||
            hasArtifactSearch in oracleB && "artifact" in typeA
        ) return null

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
                explanation = "${a.name} and ${b.name} share these deck roles: ${shared.joinToString(", ")}."
            )
        }

        return null
    }
}
