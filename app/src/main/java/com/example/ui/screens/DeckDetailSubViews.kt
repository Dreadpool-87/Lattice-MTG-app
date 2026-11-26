package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CardEntity
import com.example.domain.PlaytestMana
import com.example.ui.components.ManaCostRow
import com.example.ui.theme.BurnishedGold
import com.example.ui.theme.LatticeSparkContainer
import com.example.ui.theme.LatticeSparkGreen
import com.example.ui.viewmodel.PlaytestDestination
import com.example.ui.viewmodel.PlaytestHandState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaytestGoldfishTab(
    playtest: PlaytestHandState,
    onDealSeven: () -> Unit,
    onMulligan: () -> Unit,
    onDrawCard: () -> Unit,
    onPlayCard: (Int) -> Unit,
    onInspectCard: (CardEntity) -> Unit = {},
    onPutBottom: (Int) -> Unit,
    onNextTurn: () -> Unit = {},
    onUntapAll: () -> Unit = {},
    onTogglePermanentTapped: (Long) -> Unit = {},
    onMoveBattlefieldCard: (Long, PlaytestDestination) -> Unit = { _, _ -> },
    onCastCommander: (Int) -> Unit = {},
    onScryTop: () -> Unit = {},
    onResolveScry: (Boolean) -> Unit = {},
    onShuffleLibrary: () -> Unit = {},
    onReturnFromGraveyard: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showGraveyardExile by remember { mutableStateOf(false) }
    var showActionLog by remember { mutableStateOf(false) }
    var showCardEffects by remember { mutableStateOf(false) }

    val hasSession = playtest.hand.isNotEmpty() ||
        playtest.battlefield.isNotEmpty() ||
        playtest.graveyard.isNotEmpty() ||
        playtest.exile.isNotEmpty() ||
        playtest.commanderIds.isNotEmpty() ||
        playtest.scryCard != null ||
        playtest.library.isNotEmpty()
    val pendingBottoms = (playtest.mulliganCount - playtest.bottomedThisMulligan).coerceAtLeast(0)

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.testTag("playtest_lazy_list")
    ) {
        if (hasSession) {
            item {
                val tappedCount = playtest.battlefield.count { it.tapped }
                Text(
                    text = "Battlefield (${playtest.battlefield.size}${if (tappedCount > 0) " • $tappedCount tapped" else ""})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (playtest.battlefield.isEmpty()) {
                    Text("No permanents in play", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(playtest.battlefield, key = { it.instanceId }) { permanent ->
                var menuExpanded by remember { mutableStateOf(false) }
                Surface(
                    color = if (permanent.tapped) {
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (permanent.tapped) MaterialTheme.colorScheme.outline else LatticeSparkGreen.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTogglePermanentTapped(permanent.instanceId) }
                        .testTag("playtest_battlefield_${permanent.instanceId}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                    ) {
                        Surface(
                            color = if (permanent.tapped) MaterialTheme.colorScheme.outlineVariant else LatticeSparkContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (permanent.tapped) "TAPPED" else "UNTAPPED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (permanent.tapped) MaterialTheme.colorScheme.onSurfaceVariant else LatticeSparkGreen,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f).clickable(onClickLabel = "Read card details") { onInspectCard(permanent.card) }) {
                            Text(
                                text = permanent.card.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = permanent.card.typeLine,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        ManaCostRow(manaCost = permanent.card.manaCost, pipSize = 14.dp)
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Move permanent")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Send to Graveyard") },
                                    onClick = {
                                        menuExpanded = false
                                        onMoveBattlefieldCard(permanent.instanceId, PlaytestDestination.GRAVEYARD)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Exile") },
                                    onClick = {
                                        menuExpanded = false
                                        onMoveBattlefieldCard(permanent.instanceId, PlaytestDestination.EXILE)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Return to Hand") },
                                    onClick = {
                                        menuExpanded = false
                                        onMoveBattlefieldCard(permanent.instanceId, PlaytestDestination.HAND)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Return to Command Zone") },
                                    enabled = permanent.card.id in playtest.commanderIds,
                                    onClick = {
                                        menuExpanded = false
                                        onMoveBattlefieldCard(permanent.instanceId, PlaytestDestination.COMMAND_ZONE)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (pendingBottoms > 0) {
            item {
                Text("London mulligan · Put $pendingBottoms card(s) on the bottom to keep this hand.",
                    style = MaterialTheme.typography.bodySmall, color = BurnishedGold)
            }
        }
        if (hasSession) {
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
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f).clickable(onClickLabel = "Read card details") { onInspectCard(card) }) {
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
                        if (pendingBottoms > 0) TextButton(
                            onClick = { onPutBottom(index) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Bottom", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = { onPlayCard(index) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text(if (PlaytestMana.isLand(card)) "Play land" else "Cast", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(if (hasSession) "Turn ${playtest.turnNumber}" else "Playtest",
                        style = MaterialTheme.typography.titleMedium)
                    if (hasSession) {
                        Text("Library ${playtest.library.size} · Land drops ${playtest.landsPlayedThisTurn} · Mulligans ${playtest.mulliganCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (playtest.manaPool.isNotEmpty()) {
                            Text("Mana pool: " + playtest.manaPool.joinToString(" ") { "{$it}" },
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (hasSession) {
                            Button(
                                onClick = onNextTurn,
                                enabled = playtest.scryCard == null && pendingBottoms == 0,
                                modifier = Modifier.testTag("playtest_next_turn_button")
                            ) { Text("Next Turn") }
                            OutlinedButton(
                                onClick = { showCardEffects = true },
                                enabled = playtest.scryCard == null && pendingBottoms == 0,
                                modifier = Modifier.testTag("playtest_card_effects_button")
                            ) { Text("Card effects") }
                            TextButton(onClick = { showActionLog = true },
                                modifier = Modifier.testTag("playtest_action_log_button")) { Text("Action log") }
                        }
                        val handButton = Modifier.testTag("playtest_deal_button")
                        if (hasSession) {
                            TextButton(onClick = onDealSeven, modifier = handButton) { Text("New Hand") }
                            TextButton(onClick = onMulligan,
                                modifier = Modifier.testTag("playtest_mulligan_button")) { Text("Mulligan") }
                        } else {
                            Button(onClick = onDealSeven, modifier = handButton) { Text("Draw 7") }
                        }
                    }
                }
            }
        }

        // 2. Scry 1 Top Card Preview
        if (playtest.scryCard != null) {
            item {
                val scry = playtest.scryCard
                Card(
                    colors = CardDefaults.cardColors(containerColor = LatticeSparkContainer),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.2.dp, LatticeSparkGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playtest_scry_card")
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Text(
                            text = "Scry 1 — Top of Library",
                            style = MaterialTheme.typography.labelLarge,
                            color = LatticeSparkGreen
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(scry.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    scry.typeLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ManaCostRow(manaCost = scry.manaCost, pipSize = 15.dp)
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { onResolveScry(true) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("playtest_scry_keep")
                            ) {
                                Text("Keep on Top")
                            }
                            OutlinedButton(
                                onClick = { onResolveScry(false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("playtest_scry_bottom")
                            ) {
                                Text("Put on Bottom")
                            }
                        }
                    }
                }
            }
        }

        // 3. Command Zone (for Commander decks)
        if (playtest.commanderIds.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Command Zone (${playtest.commandZone.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = BurnishedGold
                        )
                    }
                    playtest.commandZone.forEachIndexed { idx, commander ->
                        val tax = 2 * playtest.commanderCasts.getOrDefault(commander.id, 0)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BurnishedGold.copy(alpha = 0.55f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f).clickable(onClickLabel = "Read card details") { onInspectCard(commander) }) {
                                    Text(
                                        text = commander.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = commander.typeLine,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                ManaCostRow(manaCost = commander.manaCost, pipSize = 14.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { onCastCommander(idx) },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("playtest_cast_commander_$idx")
                                ) {
                                    Text(if (tax == 0) "Cast" else "Cast +{$tax}")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Graveyard & Exile Expandable Summary
        if (playtest.graveyard.isNotEmpty() || playtest.exile.isNotEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showGraveyardExile = !showGraveyardExile }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "Graveyard (${playtest.graveyard.size}) • Exile (${playtest.exile.size})",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (showGraveyardExile) "Hide" else "View",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        AnimatedVisibility(visible = showGraveyardExile) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                playtest.graveyard.forEachIndexed { idx, card ->
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "GY: ${card.name}",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        TextButton(
                                            onClick = { onReturnFromGraveyard(idx) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Text("To Hand", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                playtest.exile.forEach { card ->
                                    Text(
                                        text = "Exile: ${card.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    if (showCardEffects) {
        val close = { showCardEffects = false }
        AlertDialog(
            onDismissRequest = close,
            title = { Text("Card effects") },
            text = {
                Column {
                    Text("Apply an effect from a card you played.", style = MaterialTheme.typography.bodySmall)
                    TextButton(
                        onClick = { close(); onDrawCard() },
                        enabled = playtest.library.isNotEmpty(),
                        modifier = Modifier.testTag("playtest_draw_button")
                    ) { Text("Draw for card effect") }
                    TextButton(
                        onClick = { close(); onScryTop() },
                        enabled = playtest.library.isNotEmpty(),
                        modifier = Modifier.testTag("playtest_scry_button")
                    ) { Text("Scry 1") }
                    TextButton(
                        onClick = { close(); onShuffleLibrary() },
                        enabled = playtest.library.size > 1,
                        modifier = Modifier.testTag("playtest_shuffle_button")
                    ) { Text("Shuffle library") }
                    TextButton(
                        onClick = { close(); onUntapAll() },
                        enabled = playtest.battlefield.any { it.tapped },
                        modifier = Modifier.testTag("playtest_untap_all_button")
                    ) { Text("Untap all permanents") }
                }
            },
            confirmButton = { TextButton(onClick = close) { Text("Close") } }
        )
    }

    if (showActionLog) {
        val close = { showActionLog = false }
        AlertDialog(
            onDismissRequest = close,
            title = { Text("Action log") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(playtest.actionLog) { entry ->
                        Text(entry, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = close) { Text("Close") } }
        )
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
                Text(
                    text = "Searches local Room cache only — not the full MTG catalog.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("picker_local_cache_notice")
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
