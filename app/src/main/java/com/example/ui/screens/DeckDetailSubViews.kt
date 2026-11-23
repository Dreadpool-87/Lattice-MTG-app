package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CardEntity
import com.example.ui.components.ManaCostRow
import com.example.ui.viewmodel.PlaytestHandState

@Composable
fun PlaytestGoldfishTab(
    playtest: PlaytestHandState,
    onDealSeven: () -> Unit,
    onMulligan: () -> Unit,
    onDrawCard: () -> Unit,
    onPlayCard: (Int) -> Unit,
    onPutBottom: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "Offline Goldfish Simulator",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (playtest.hand.isEmpty() && playtest.battlefield.isEmpty()) {
                                    "Shuffle your local Room decklist and test opening hands."
                                } else {
                                    "Turn ${playtest.turnNumber} • Mulligans: ${playtest.mulliganCount} • Lands in hand: ${playtest.landsInHand} • Library: ${playtest.library.size}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onDealSeven,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("playtest_deal_button")
                        ) {
                            Icon(Icons.Filled.Casino, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (playtest.hand.isEmpty()) "Draw 7" else "New Hand")
                        }
                        OutlinedButton(
                            onClick = onMulligan,
                            enabled = playtest.hand.isNotEmpty() || playtest.library.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("playtest_mulligan_button")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mulligan")
                        }
                        OutlinedButton(
                            onClick = onDrawCard,
                            enabled = playtest.library.isNotEmpty(),
                            modifier = Modifier
                                .height(46.dp)
                                .testTag("playtest_draw_button")
                        ) {
                            Text("+1 Draw")
                        }
                    }
                }
            }
        }

        if (playtest.hand.isNotEmpty()) {
            item {
                Text(
                    text = "Hand (${playtest.hand.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(playtest.hand.size) { index ->
                val card = playtest.hand[index]
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = card.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                ManaCostRow(manaCost = card.manaCost, pipSize = 15.dp)
                            }
                            Text(
                                text = card.typeLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(onClick = { onPutBottom(index) }) {
                            Text("Bottom", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = { onPlayCard(index) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Play", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        if (playtest.battlefield.isNotEmpty()) {
            item {
                Text(
                    text = "Battlefield / Cast (${playtest.battlefield.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            items(playtest.battlefield.size) { idx ->
                val card = playtest.battlefield[idx]
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(card.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        ManaCostRow(manaCost = card.manaCost, pipSize = 15.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun AddCardPickerDialog(
    query: String,
    cards: List<CardEntity>,
    activeZone: String,
    onQueryChange: (String) -> Unit,
    onAddCard: (CardEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add from Offline Vault (${activeZone.lowercase().replaceFirstChar { it.uppercase() }})",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Filter local cards...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("picker_search_input")
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                ) {
                    items(cards, key = { it.id }) { card ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAddCard(card) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = card.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = card.typeLine,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                ManaCostRow(manaCost = card.manaCost, pipSize = 15.dp)
                                IconButton(onClick = { onAddCard(card) }) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = "Add ${card.name}",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
