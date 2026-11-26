package com.example

import com.example.data.local.CardMetadataStatus
import com.example.data.local.StarterCards
import com.example.domain.VaultListCard
import com.example.domain.VaultCollectionGrouping
import com.example.domain.VaultCollectionOrder
import com.example.domain.VaultCollectionSort
import org.junit.Assert.assertEquals
import org.junit.Test

class VaultCollectionOrderTest {
    private fun card(
        name: String,
        quantity: Int,
        colors: String = "",
        type: String = "Artifact",
        status: String = CardMetadataStatus.VERIFIED,
        cmc: Double = 0.0,
        id: String = name
    ): VaultListCard {
        val card = StarterCards.cards.first().copy(
            id = id, name = name, colors = colors, colorIdentity = "U", typeLine = type,
            metadataStatus = status, cmc = cmc
        )
        return VaultListCard(card, quantity.toLong())
    }

    @Test
    fun `sorts in either direction and retains separate printings`() {
        val first = card("Alfa", 2, id = "a1")
        val second = card("Alfa", 4, id = "a2")
        val third = card("Zulu", 1, id = "z1")
        val entries = listOf(second, third, first)
        fun ids(sort: VaultCollectionSort) =
            VaultCollectionOrder.arrange(entries, sort, VaultCollectionGrouping.NONE)
                .single().entries.map { it.card.id }
        assertEquals(listOf("a1", "a2", "z1"), ids(VaultCollectionSort.NAME_ASC))
        assertEquals(listOf("z1", "a1", "a2"), ids(VaultCollectionSort.NAME_DESC))
        assertEquals(listOf("a2", "a1", "z1"), ids(VaultCollectionSort.QUANTITY_DESC))
        assertEquals(listOf("z1", "a1", "a2"), ids(VaultCollectionSort.QUANTITY_ASC))
    }

    @Test
    fun `grouping keeps actual colors and each printing appears once`() {
        val entries = listOf(
            card("Two colors", 1, "U,R", "Artifact Creature — Construct"),
            card("Blue source", 2, type = "Basic Land — Island"),
            card("Small blue creature", 1, "U", "Creature — Wizard"),
            card("Large blue creature", 3, "U", "Creature — Wizard"),
            card("Unknown", 3, status = CardMetadataStatus.UNRESOLVED)
        )
        val groups = VaultCollectionOrder.arrange(entries, VaultCollectionSort.QUANTITY_DESC, VaultCollectionGrouping.COLOR_AND_TYPE)
        assertEquals(listOf("Blue · Creature", "Multicolor · Creature", "Colorless · Land", "Unknown color · Other type"), groups.map { it.heading })
        assertEquals(listOf("Large blue creature", "Small blue creature"), groups.first().entries.map { it.card.name })
        assertEquals(entries.size, groups.sumOf { it.entries.size })
    }

    @Test
    fun `type uses the front face and unknown mana values sort last`() {
        val modal = card("Front spell", 1, "U", "Sorcery // Land", cmc = 2.0)
        val land = card("Land", 1, type = "Basic Land — Forest")
        val unknown = card("Unknown", 1, status = CardMetadataStatus.UNRESOLVED)
        val entries = listOf(unknown, modal, land)
        val groups = VaultCollectionOrder.arrange(entries, VaultCollectionSort.NAME_ASC, VaultCollectionGrouping.TYPE)
        assertEquals(listOf("Land", "Sorcery", "Other type"), groups.map { it.heading })
        assertEquals(listOf(land, modal, unknown), VaultCollectionOrder.arrange(entries, VaultCollectionSort.MANA_VALUE_ASC, VaultCollectionGrouping.NONE).single().entries)
    }
}
