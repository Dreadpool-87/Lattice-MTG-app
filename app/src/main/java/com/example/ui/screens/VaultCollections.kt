package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CardEntity
import com.example.data.local.VaultCollectionCard
import com.example.domain.VaultCollectionGrouping
import com.example.domain.VaultCollectionOrder
import com.example.domain.VaultCollectionSort

@Composable
internal fun VaultCollections(
    collections: Map<String, List<VaultCollectionCard>>,
    onOpenCard: (List<CardEntity>, CardEntity) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(VaultCollectionSort.NAME_ASC) }
    var grouping by rememberSaveable { mutableStateOf(VaultCollectionGrouping.NONE) }
    val selectedEntries = collections[expanded].orEmpty()
    val groups = remember(selectedEntries, sort, grouping) {
        VaultCollectionOrder.arrange(selectedEntries, sort, grouping)
    }
    val inspectorCards = remember(groups) { groups.flatMap { group -> group.entries.map { it.card } } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (collections.isEmpty()) {
            item { Text("Import a CSV as a collection to see your cards and copy counts here.") }
        }

        collections.forEach { (name, entries) ->
            val isExpanded = expanded == name
            item(key = "collection:$name") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().clickable(
                        role = Role.Button,
                        onClickLabel = if (isExpanded) "Collapse collection" else "Expand collection"
                    ) { expanded = name.takeUnless { isExpanded } }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleMedium)
                            val copies = entries.sumOf { it.entry.quantity.toLong() }
                            val copyLabel = if (copies == 1L) "copy" else "copies"
                            val printingLabel = if (entries.size == 1) "printing" else "printings"
                            Text("$copies $copyLabel · ${entries.size} $printingLabel", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                    }
                }
            }
            if (isExpanded) {
                item(key = "collection-order:$name") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CollectionOrderMenu(
                            "Sort", sort, VaultCollectionSort.entries, VaultCollectionSort::label,
                            onSelect = { sort = it }, modifier = Modifier.weight(1f)
                        )
                        CollectionOrderMenu(
                            "Group", grouping, VaultCollectionGrouping.entries, VaultCollectionGrouping::label,
                            onSelect = { grouping = it }, modifier = Modifier.weight(1f)
                        )
                    }
                }
                groups.forEach { group ->
                    group.heading?.let { heading ->
                        item(key = "collection-group:$name:$heading") {
                            val copies = group.entries.sumOf { it.entry.quantity.toLong() }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp)
                                    .semantics { heading() }
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Box(Modifier.width(4.dp).height(32.dp).background(
                                        MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)
                                    ))
                                    Text(heading, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    Text(
                                        if (copies == 1L) "1 copy" else "$copies copies",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                    items(group.entries, key = { "entry:$name:${it.card.id}" }) { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onOpenCard(inspectorCards, entry.card) }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.card.name, style = MaterialTheme.typography.bodyLarge)
                                val details = listOfNotNull(
                                    entry.card.setCode.takeIf { it.isNotBlank() },
                                    entry.card.collectorNumber.takeIf { it.isNotBlank() }?.let { "#$it" },
                                    entry.card.lang.takeIf { it.isNotBlank() }?.uppercase()
                                )
                                if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            }
                            Text("×${entry.entry.quantity}", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> CollectionOrderMenu(
    title: String,
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().testTag("collection_${title.lowercase()}_menu")
        ) {
            Text("$title: ${label(selected)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        onSelect(option)
                        open = false
                    }
                )
            }
        }
    }
}
