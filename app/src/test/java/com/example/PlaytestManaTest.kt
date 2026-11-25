package com.example

import com.example.data.local.StarterCards
import com.example.domain.DeckAnalytics
import com.example.domain.PlaytestMana
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaytestManaTest {
    @Test
    fun `payment requires both enough mana and the right colors`() {
        val island = PlaytestMana.Source(1, listOf(listOf("U")))
        val cost = requireNotNull(PlaytestMana.cost("{1}{U}"))
        assertNull(PlaytestMana.pay(cost, emptyList(), emptyList()))
        assertNull(PlaytestMana.pay(cost, emptyList(), listOf(island)))
        assertNull(PlaytestMana.pay(requireNotNull(PlaytestMana.cost("{W}")), emptyList(), listOf(island)))

        val dual = PlaytestMana.Source(2, listOf(listOf("U"), listOf("W")))
        val exact = requireNotNull(PlaytestMana.cost("{W}{U}"))
        assertEquals(setOf(1L, 2L), PlaytestMana.pay(exact, emptyList(), listOf(dual, island))?.sources)
    }

    @Test
    fun `generic mana may be paid with Sol Ring and leftovers stay in the pool`() {
        val ring = PlaytestMana.Source(9, listOf(listOf("C", "C")))
        val payment = requireNotNull(PlaytestMana.pay(requireNotNull(PlaytestMana.cost("{1}")), emptyList(), listOf(ring)))
        assertEquals(setOf(9L), payment.sources)
        assertEquals(listOf("C"), payment.pool)
        assertNull(PlaytestMana.pay(requireNotNull(PlaytestMana.cost("{1}", tax = 2)), emptyList(), listOf(ring)))
        assertNotNull(PlaytestMana.pay(requireNotNull(PlaytestMana.cost("{0}")), emptyList(), emptyList()))
        assertNull(PlaytestMana.cost("{X}{U}"))
    }

    @Test
    fun `simple mana abilities respect commander identity and tapped entries`() {
        val card = StarterCards.cards.first().copy(
            typeLine = "Land",
            oracleText = "This land enters tapped.\n{T}: Add {U} or {R}."
        )
        assertTrue(PlaytestMana.isLand(card))
        assertTrue(PlaytestMana.entersTapped(card))
        assertEquals(listOf(listOf("U"), listOf("R")), PlaytestMana.sourceChoices(card))

        val tower = card.copy(oracleText = "{T}: Add one mana of any color in your commander's color identity.")
        assertEquals(listOf(listOf("W"), listOf("B")), PlaytestMana.sourceChoices(tower, setOf("W", "B")))
        assertTrue(PlaytestMana.sourceChoices(tower).isEmpty())
    }

    @Test
    fun `land count odds match independent combinations for 99 cards and 38 lands`() {
        assertEquals(0.8290, DeckAnalytics.hypergeometricAtLeastK(99, 38, 7, 2), 0.0001)
        assertEquals(0.7482, DeckAnalytics.hypergeometricAtLeastK(99, 38, 9, 3), 0.0001)
        assertEquals(0.8190, DeckAnalytics.hypergeometricAtLeastK(99, 38, 10, 3), 0.0001)
    }
}
