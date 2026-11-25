package com.example

import com.example.data.local.CardEntity
import com.example.data.local.CardMetadataStatus
import com.example.data.local.DeckEntity
import com.example.data.local.DeckEntryEntity
import com.example.data.local.DeckEntryWithCard
import com.example.data.local.DeckWithEntries
import com.example.data.local.DeckZones
import com.example.data.local.VaultCollectionCard
import com.example.data.local.VaultCollectionEntry
import com.example.domain.OwnedDeckCandidates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnedDeckCandidatesTest {
    private fun card(
        id: String,
        colors: String = "W,B",
        tags: String = "Lifegain",
        legal: String = "Standard,Commander"
    ) = CardEntity(
        id = id, name = id, manaCost = "{W}{B}", cmc = 2.0, typeLine = "Creature",
        oracleText = "Lifelink", colors = colors, colorIdentity = colors, rarity = "common",
        setCode = "test", setName = "Fixture", synergyTags = tags, legalFormats = legal, oracleId = id
    )

    private fun entry(card: CardEntity, count: Int = 1, zone: String = DeckZones.MAINBOARD) =
        DeckEntryWithCard(DeckEntryEntity(deckId = 1L, cardId = card.id, quantity = count, zone = zone), card)

    private fun owned(card: CardEntity, count: Int = 1) =
        VaultCollectionCard(VaultCollectionEntry("My collection", card.id, count), card)

    @Test
    fun usesOwnedCopiesAcrossPrintingsAndFiltersByColorsAndFormat() {
        val anchor = card("Active")
        val anotherPrinting = anchor.copy(id = "Active alt")
        val deck = DeckWithEntries(
            DeckEntity(id = 1L, name = "Deck", format = "Standard"),
            listOf(entry(anchor, 2), entry(anotherPrinting, 1, DeckZones.SIDEBOARD))
        )
        val candidates = OwnedDeckCandidates.find(deck, listOf(
            owned(anotherPrinting, 2),
            owned(anchor, 2),
            owned(card("Unsupported format", legal = "Commander")),
            owned(card("Off color", colors = "R")),
            owned(card("Unverified").copy(metadataStatus = CardMetadataStatus.UNRESOLVED)),
            owned(card("Custom").copy(isCustom = true)),
            owned(card("Unused"), 2),
            owned(card("Other", tags = "Removal"), 1)
        ))
        assertEquals(listOf("Active", "Unused", "Other"), candidates.map { it.card.name })
        assertEquals(listOf(1L, 2L, 1L), candidates.map { it.availableCopies })
        assertEquals(listOf("Lifegain"), candidates.first().sharedTags)
    }

    @Test
    fun commanderUsesItsCommanderColorsAndRejectsSecondNonbasicCopy() {
        val leader = card("Leader", colors = "W")
        val offColor = card("Existing off color", colors = "B")
        val duplicate = card("Existing", colors = "W")
        val deck = DeckWithEntries(
            DeckEntity(id = 1L, name = "Deck", format = "Commander"),
            listOf(entry(leader, zone = DeckZones.COMMANDER), entry(offColor), entry(duplicate))
        )
        val available = card("Available", colors = "W")
        val pool = listOf(owned(leader, 2), owned(duplicate, 2), owned(offColor, 3), owned(available, 1))
        assertEquals(listOf("Available"), OwnedDeckCandidates.find(deck, pool).map { it.card.name })
        assertTrue(OwnedDeckCandidates.find(deck.copy(entries = listOf(entry(duplicate))), pool).isEmpty())
    }

    @Test
    fun combinesOverlappingCollectionsAndFiltersColors() {
        val white = card("White deck card", colors = "W")
        val black = card("Black deck card", colors = "B")
        val deck = DeckWithEntries(
            DeckEntity(id = 1L, name = "Orzhov deck", format = "Casual"),
            listOf(entry(white), entry(black))
        )
        val blackCandidate = card("Black candidate", colors = "B")
        val colorless = card("Colorless", colors = "")
        val pool = listOf(
            owned(blackCandidate, 2),
            owned(blackCandidate, 3).let { it.copy(entry = it.entry.copy(listName = "Overlap")) },
            owned(blackCandidate.copy(id = "Different printing"), 1),
            owned(card("White candidate", colors = "W"), 3),
            owned(colorless)
        )
        val results = OwnedDeckCandidates.find(deck, pool, setOf("B"), includeColorless = false)
        assertEquals(listOf("Black candidate"), results.map { it.card.name })
        assertEquals(4L, results.first().availableCopies)
        assertEquals(
            listOf("Colorless"),
            OwnedDeckCandidates.find(deck, pool, emptySet(), includeColorless = true).map { it.card.name }
        )
    }

    @Test
    fun choosesAOneCardSwapWithNoReferenceEvenWhenCommanderColorsAreMissing() {
        val anchor = card("Life engine", tags = "Lifegain")
        val cut = card("Low synergy")
            .copy(synergyTags = "Tokens", oracleText = "When this creature enters, create a token.")
        val removal = card("Removal creature", tags = "Removal")
        val legal = card("Owned upgrade", tags = "Lifegain").copy(cmc = 1.0)
        val pool = listOf(
            owned(legal),
            owned(card("Off color", colors = "R").copy(cmc = 1.0)),
            owned(card("Too costly").copy(cmc = 4.0)),
            owned(card("Wrong type").copy(typeLine = "Enchantment"))
        )
        val deck = DeckWithEntries(
            DeckEntity(id = 1L, name = "Orzhov deck", format = "Commander"),
            listOf(entry(anchor), entry(cut), entry(removal))
        )
        assertEquals(null, OwnedDeckCandidates.deckColors(deck))
        val swap = requireNotNull(OwnedDeckCandidates.bestSwap(deck, pool, setOf("W", "B"), true))
        assertEquals(cut.name, swap.remove.name)
        assertEquals(legal.name, swap.add.card.name)
        assertEquals(listOf("Lifegain"), swap.additionalTags)
        assertEquals(null, OwnedDeckCandidates.bestSwap(deck, pool, setOf("G"), false))
    }

    @Test
    fun preservesOracleTextRolesWhenCachedTagsMissToughnessReductionAndLifeGain() {
        val moment = card("Moment of Craving", colors = "B", tags = "Spellslinger").copy(
            manaCost = "{1}{B}", typeLine = "Instant",
            oracleText = "Target creature gets -2/-2 until end of turn. You gain 2 life."
        )
        val protection = moment.copy(
            id = "fake", oracleId = "fake", name = "Fake Your Own Death",
            synergyTags = "Spellslinger,Artifact,Ramp,Tokens",
            oracleText = "Until end of turn, target creature gets +2/+0 and gains an ability that creates a Treasure token."
        )
        val engine = card("Token engine", tags = "Tokens,Ramp")
        val deck = DeckWithEntries(
            DeckEntity(id = 1L, name = "Orzhov", format = "Casual"),
            listOf(entry(moment), entry(engine))
        )
        assertEquals(null, OwnedDeckCandidates.bestSwap(deck, listOf(owned(protection)), setOf("W", "B"), true))

        val comparableRemoval = moment.copy(
            id = "fixture", oracleId = "fixture", name = "Same-role fixture",
            synergyTags = "Spellslinger,Tokens",
            oracleText = "Target creature gets −3/−3 until end of turn. You gain 3 life. Create a token."
        )
        val swap = requireNotNull(OwnedDeckCandidates.bestSwap(
            deck, listOf(owned(protection), owned(comparableRemoval)), setOf("W", "B"), true
        ))
        assertEquals(moment.name, swap.remove.name)
        assertEquals(comparableRemoval.name, swap.add.card.name)
        assertEquals(listOf("Lifegain", "Removal"), swap.preservedRoles)
    }
}
