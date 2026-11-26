package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.local.CardEntity

internal data class PersonalCardFilters(
    val query: String = "",
    val type: String = "All",
    val color: String = "All",
    val rarity: String = "All"
) {
    fun matches(card: CardEntity): Boolean {
        val colors = card.colors.split(',').map(String::trim).filter(String::isNotBlank).toSet()
        return (query.isBlank() || card.name.contains(query.trim(), ignoreCase = true) ||
            card.setCode.equals(query.trim(), ignoreCase = true)) &&
            (type == "All" || card.typeLine.contains(type, ignoreCase = true)) &&
            (rarity == "All" || card.rarity.equals(rarity, ignoreCase = true)) &&
            when (color) {
                "All" -> true
                "Colorless" -> colors.isEmpty()
                "Multicolor" -> colors.size >= 2
                else -> color in colors
            }
    }
}

@Composable
internal fun PersonalCardFilterControls(
    filters: PersonalCardFilters,
    onChange: (PersonalCardFilters) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val active = listOf(filters.type, filters.color, filters.rarity).count { it != "All" }
    Column(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = filters.query,
                onValueChange = { onChange(filters.copy(query = it)) },
                placeholder = { Text("Find a card or set") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            FilterChip(expanded, { expanded = !expanded }, { Text(if (active == 0) "Filters" else "Filters ($active)") })
        }
        if (expanded) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                CollectionOrderMenu("Type", filters.type, cardTypeFilters, { it }, { onChange(filters.copy(type = it)) })
                CollectionOrderMenu("Color", filters.color, listOf("All", "W", "U", "B", "R", "G", "Multicolor", "Colorless"),
                    { it }, { onChange(filters.copy(color = it)) })
                CollectionOrderMenu("Rarity", filters.rarity, listOf("All", "Common", "Uncommon", "Rare", "Mythic"),
                    { it }, { onChange(filters.copy(rarity = it)) })
            }
        }
    }
}
