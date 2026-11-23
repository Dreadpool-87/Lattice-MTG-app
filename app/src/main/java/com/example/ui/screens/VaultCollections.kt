package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.data.local.CardEntity
import com.example.data.local.VaultCollectionCard

@Composable
internal fun VaultCollections(
    collections: Map<String, List<VaultCollectionCard>>,
    onOpenCard: (CardEntity) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
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
                items(entries.sortedBy { it.card.name.lowercase() }, key = { "entry:$name:${it.card.id}" }) { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenCard(entry.card) }.padding(14.dp),
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
