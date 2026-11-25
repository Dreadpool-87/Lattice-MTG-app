package com.example.domain

import com.example.data.local.CardEntity
import com.example.data.local.CardMetadataStatus
import com.example.data.local.VaultCollectionCard
import java.util.Locale

internal enum class VaultCollectionSort(val label: String) {
    NAME_ASC("A–Z"),
    NAME_DESC("Z–A"),
    QUANTITY_DESC("Most copies"),
    QUANTITY_ASC("Fewest copies"),
    MANA_VALUE_ASC("Mana value")
}

internal enum class VaultCollectionGrouping(val label: String) {
    NONE("None"),
    COLOR("Color"),
    TYPE("Type"),
    COLOR_AND_TYPE("Color + type")
}

internal data class VaultCollectionGroup(
    val heading: String?,
    val entries: List<VaultCollectionCard>
)

internal object VaultCollectionOrder {
    private enum class CardColor(val symbol: String, val label: String) {
        WHITE("W", "White"),
        BLUE("U", "Blue"),
        BLACK("B", "Black"),
        RED("R", "Red"),
        GREEN("G", "Green"),
        MULTICOLOR("", "Multicolor"),
        COLORLESS("", "Colorless"),
        UNKNOWN("", "Unknown color")
    }

    // A multi-type card appears once. Artifact creatures belong with creatures.
    private enum class CardType(val label: String) {
        LAND("Land"),
        CREATURE("Creature"),
        INSTANT("Instant"),
        SORCERY("Sorcery"),
        ARTIFACT("Artifact"),
        ENCHANTMENT("Enchantment"),
        PLANESWALKER("Planeswalker"),
        BATTLE("Battle"),
        OTHER("Other type")
    }

    private data class GroupKey(val color: CardColor?, val type: CardType?) {
        val label: String get() = listOfNotNull(color?.label, type?.label).joinToString(" · ")
    }

    fun arrange(
        entries: List<VaultCollectionCard>,
        sort: VaultCollectionSort,
        grouping: VaultCollectionGrouping
    ): List<VaultCollectionGroup> {
        val name = compareBy<VaultCollectionCard> { it.card.name.lowercase(Locale.ROOT) }
        val comparator = when (sort) {
            VaultCollectionSort.NAME_ASC -> name
            VaultCollectionSort.NAME_DESC -> name.reversed()
            VaultCollectionSort.QUANTITY_DESC -> compareByDescending<VaultCollectionCard> { it.entry.quantity }.then(name)
            VaultCollectionSort.QUANTITY_ASC -> compareBy<VaultCollectionCard> { it.entry.quantity }.then(name)
            VaultCollectionSort.MANA_VALUE_ASC -> compareBy<VaultCollectionCard> {
                it.card.metadataStatus == CardMetadataStatus.UNRESOLVED
            }.thenBy { it.card.cmc }.then(name)
        }
        val ordered = entries.sortedWith(comparator.thenBy { it.card.id })
        if (grouping == VaultCollectionGrouping.NONE) return listOf(VaultCollectionGroup(null, ordered))

        return ordered.groupBy {
            when (grouping) {
                VaultCollectionGrouping.COLOR -> GroupKey(colorOf(it.card), null)
                VaultCollectionGrouping.TYPE -> GroupKey(null, typeOf(it.card))
                VaultCollectionGrouping.COLOR_AND_TYPE -> GroupKey(colorOf(it.card), typeOf(it.card))
                VaultCollectionGrouping.NONE -> error("Ungrouped entries are handled before this step.")
            }
        }.entries.sortedWith(compareBy({ it.key.color?.ordinal ?: 0 }, { it.key.type?.ordinal ?: 0 }))
            .map { (key, group) -> VaultCollectionGroup(key.label, group) }
    }

    private fun colorOf(card: CardEntity): CardColor {
        if (card.metadataStatus == CardMetadataStatus.UNRESOLVED) return CardColor.UNKNOWN
        val colors = card.colors.split(',').map { it.trim().uppercase(Locale.ROOT) }.filter(String::isNotEmpty).toSet()
        if (colors.isEmpty()) return CardColor.COLORLESS
        val matching = CardColor.entries.filter { it.symbol in colors }
        return when {
            matching.size != colors.size -> CardColor.UNKNOWN
            matching.size > 1 -> CardColor.MULTICOLOR
            else -> matching.single()
        }
    }

    private fun typeOf(card: CardEntity): CardType {
        if (card.metadataStatus == CardMetadataStatus.UNRESOLVED) return CardType.OTHER
        val types = card.typeLine.substringBefore(" // ").substringBefore('—')
            .split(' ').mapTo(mutableSetOf()) { it.uppercase(Locale.ROOT) }
        return CardType.entries.firstOrNull { it.name in types } ?: CardType.OTHER
    }
}
