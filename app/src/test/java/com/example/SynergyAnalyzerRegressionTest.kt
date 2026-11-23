package com.example

import com.example.data.local.CardEntity
import com.example.data.local.CardMetadataStatus
import com.example.data.local.DeckEntity
import com.example.data.local.DeckEntryEntity
import com.example.data.local.DeckEntryWithCard
import com.example.data.local.DeckWithEntries
import com.example.data.local.DeckZones
import com.example.domain.DeckAnalytics
import com.example.domain.RelationshipType
import com.example.domain.SynergyAnalyzerEngine
import com.example.ui.viewmodel.SynergyAnalyzerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SynergyAnalyzerRegressionTest {
    @Test
    fun `artifact tutors respect minimum maximum and exact mana values`() {
        val solemn = card("Solemn Simulacrum", "Artifact Creature — Golem", 4.0, tags = "Artifact,Ramp")
        val ring = card("Sol Ring", "Artifact", 1.0)
        val lattice = card("Mycosynth Lattice", "Artifact", 6.0)
        val sphere = card("Commander's Sphere", "Artifact", 3.0)
        val nonArtifact = card("Garruk's Packleader", "Creature — Beast", 5.0)
        val treasure = mage("Treasure Mage", "6 or greater").copy(synergyTags = "Artifact,Ramp,Tutor")
        val trinket = mage("Trinket Mage", "1 or less")
        val trophy = mage("Trophy Mage", "3")
        val report = analyze(treasure, trinket, trophy, solemn, ring, lattice, sphere, nonArtifact)

        val tutorLinks = report.relationships.filter { it.type == RelationshipType.TUTOR_TARGET }
        assertEquals(
            setOf(treasure.name to lattice.name, trinket.name to ring.name, trophy.name to sphere.name),
            tutorLinks.map { it.cardA.name to it.cardB.name }.toSet()
        )
        assertTrue(tutorLinks.all { "your hand" in it.explanation })
        assertTrue(report.relationships.none { setOf(it.cardA, it.cardB) == setOf(treasure, solemn) })
    }

    @Test
    fun `Whir puts the Ring onto the battlefield without its cast protection`() {
        val whir = card(
            "Whir of Invention", "Instant", 3.0,
            "Search your library for an artifact card with mana value X or less, put it onto the battlefield, then shuffle."
        )
        val ring = card("The One Ring", "Legendary Artifact", 4.0)
        val links = analyze(whir, ring, card("Sol Ring", "Artifact", 1.0)).relationships
        val ringLink = links.single { it.cardB == ring }
        assertEquals(RelationshipType.TUTOR_TARGET, ringLink.type)
        assertTrue("X at least 4" in ringLink.explanation)
        assertTrue("battlefield" in ringLink.explanation)
        assertTrue("requires casting the Ring itself" in ringLink.explanation)
        assertTrue("does not trigger" in ringLink.explanation)
        assertTrue("X at least 1" in links.single { it.cardB.name == "Sol Ring" }.explanation)
    }

    @Test
    fun `Boros Charm triggers Swiftspear prowess and can grant double strike`() {
        val swiftspear = card("Monastery Swiftspear", "Creature — Human Monk", 1.0)
        val charm = card("Boros Charm", "Instant", 2.0)
        val link = analyze(swiftspear, charm).relationships.single()
        assertEquals(RelationshipType.COMBAT_SYNERGY, link.type)
        assertTrue("Swiftspear's prowess for +1/+1" in link.explanation)
        assertTrue("double-strike mode and target Swiftspear" in link.explanation)
    }

    @Test
    fun `empty and Forest-only decks have no archetype and a single identity is insufficient`() {
        val forest = card("Forest", "Basic Land — Forest", 0.0)
        assertNull(analyze().primaryArchetype)
        val landsOnly = SynergyAnalyzerEngine.analyzeDeck(deck(entry(forest, quantity = 60)))
        assertNull(landsOnly.primaryArchetype)
        assertEquals(60, landsOnly.roles.lands)
        assertNull(landsOnly.roles.avgCmc)
        assertTrue(landsOnly.archetypeMatches.none { it.hasEvidence })

        val ring = card("Sol Ring", "Artifact", 1.0, tags = "Artifact,Ramp")
        assertNull(analyze(ring, ring.copy(id = "sol-ring-other-printing")).primaryArchetype)
    }

    @Test
    fun `unresolved copies stay in deck size and the front spell stays in the curve`() {
        val opt = card("Opt", "Instant", 1.0, tags = "Card Draw")
        val sink = card("Sink into Stupor // Soporific Springs", "Instant // Land", 3.0)
        val forest = card("Forest", "Basic Land — Forest", 0.0)
        val unresolved = card("Unresolved", "Unresolved Card", 0.0, tags = "Removal").copy(
            metadataStatus = CardMetadataStatus.UNRESOLVED
        )
        val legacy = unresolved.copy(id = "legacy", metadataStatus = CardMetadataStatus.VERIFIED, legalFormats = "Unknown")
        val custom = opt.copy(id = "custom-opt", metadataStatus = CardMetadataStatus.CUSTOM, isCustom = true)
        val list = deck(
            entry(opt, quantity = 4), entry(sink), entry(forest, quantity = 50),
            entry(unresolved, quantity = 4), entry(legacy)
        )
        val report = SynergyAnalyzerEngine.analyzeDeck(list.copy(entries = list.entries + entry(custom)))
        val analytics = DeckAnalytics.analyze(list)

        assertEquals(61, report.roles.playableTotal)
        assertEquals(6, report.skippedCopies)
        assertEquals(50, report.roles.lands)
        assertEquals(0, report.roles.interaction)
        assertEquals(4, report.roles.drawAndRamp)
        assertEquals(1.4, report.roles.avgCmc ?: Double.NaN, 0.0001)
        assertEquals(60, analytics.playableCount)
        assertEquals(1.4, analytics.averageCmc, 0.0001)
        assertEquals(50, analytics.landCount)
        assertEquals(4, analytics.manaCurve[1])
        assertEquals(1, analytics.manaCurve[3])
        assertEquals(0, analytics.manaCurve[0])
    }

    @Test
    fun `Oracle identity collapses printings and legacy name matches into one endpoint`() {
        val ring = card("Sol Ring", "Artifact", 1.0, tags = "Artifact,Ramp")
        val otherPrinting = ring.copy(id = "ring-two", name = "Renamed printing")
        val legacy = ring.copy(id = "legacy-ring", oracleId = "")
        val second = card("Arcane Signet", "Artifact", 2.0, tags = "Artifact,Ramp")
        val links = analyze(ring, otherPrinting, legacy, second).relationships
        assertEquals(setOf(ring.id, second.id), links.single().let { setOf(it.cardA.id, it.cardB.id) })
    }

    @Test
    fun `only commander and mainboard contribute to the analysis and pillars`() {
        val karn = card("Karn, the Great Creator", "Legendary Planeswalker — Karn", 4.0)
        val lattice = card("Mycosynth Lattice", "Artifact", 6.0)
        val solRing = card("Sol Ring", "Artifact", 1.0)
        val forge = card("Darksteel Forge", "Artifact", 9.0)
        val list = deck(
            entry(karn, zone = DeckZones.COMMANDER),
            entry(lattice),
            entry(solRing, zone = DeckZones.SIDEBOARD),
            entry(forge, zone = DeckZones.MAYBEBOARD),
            format = "Commander"
        )
        val report = SynergyAnalyzerEngine.analyzeDeck(list)

        assertEquals(2, report.roles.playableTotal)
        assertEquals(setOf(karn.name, lattice.name), report.analyzedCardNames)
        assertEquals(RelationshipType.COMBO_LOCK, report.relationships.single().type)
        assertEquals("artifact_lattice", report.primaryArchetype?.template?.id)
    }

    @Test
    fun `list and graph follow category and card filters with truthful display limits`() {
        val trinket = mage("Trinket Mage", "1 or less")
        val artifacts = (1..10).map { card("Artifact $it", "Artifact", 1.0, tags = "Artifact,Ramp") }
        val report = analyze(trinket, *artifacts.toTypedArray())
        val all = SynergyAnalyzerUiState(report = report)
        val tutors = all.copy(selectedRelationshipFilter = RelationshipType.TUTOR_TARGET)
        val focused = tutors.copy(selectedCardId = artifacts.last().id)
        val noLinks = focused.copy(selectedCardId = "another-card")

        assertEquals(55, all.filteredRelationships.size)
        assertEquals(12, all.listedRelationships.size)
        assertEquals(10, tutors.filteredRelationships.size)
        assertEquals(10, tutors.listedRelationships.size)
        assertEquals(7, tutors.graphRelationships.size)
        assertEquals(8, tutors.graphRelationships.flatMap { listOf(it.cardA.id, it.cardB.id) }.distinct().size)
        assertTrue(tutors.graphRelationships.all { it.type == RelationshipType.TUTOR_TARGET })
        assertEquals(focused.listedRelationships.single(), focused.graphRelationships.single())
        assertEquals(artifacts.last().id, focused.graphRelationships.single().cardB.id)
        assertTrue(noLinks.listedRelationships.isEmpty())
        assertTrue(noLinks.graphRelationships.isEmpty())
        assertFalse(all.graphRelationships.isEmpty())
    }

    private fun mage(name: String, requirement: String) = card(
        name, "Creature — Human Wizard", 3.0,
        "When this creature enters, you may search your library for an artifact card with mana value $requirement, reveal that card, put it into your hand, then shuffle."
    )

    private fun card(name: String, type: String, manaValue: Double, text: String = "", tags: String = "") =
        CardEntity(
            id = name, name = name, manaCost = "", cmc = manaValue, typeLine = type,
            oracleText = text, colors = "", colorIdentity = "", rarity = "common",
            setCode = "TEST", setName = "Test", synergyTags = tags, oracleId = name
        )

    private fun entry(card: CardEntity, quantity: Int = 1, zone: String = DeckZones.MAINBOARD) =
        DeckEntryWithCard(DeckEntryEntity(deckId = 1, cardId = card.id, quantity = quantity, zone = zone), card)

    private fun analyze(vararg cards: CardEntity) =
        SynergyAnalyzerEngine.analyzeDeck(deck(*cards.map { entry(it) }.toTypedArray()))

    private fun deck(vararg entries: DeckEntryWithCard, format: String = "Casual") =
        DeckWithEntries(DeckEntity(id = 1, name = "Test", format = format), entries.toList())
}
