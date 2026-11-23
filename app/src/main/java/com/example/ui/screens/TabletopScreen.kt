package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.VaultStatusBadge
import com.example.ui.viewmodel.PlayerTrackerState
import com.example.ui.viewmodel.TabletopViewModel

@Composable
fun TabletopScreen(
    viewModel: TabletopViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current

    DisposableEffect(uiState.keepScreenAwake) {
        view.keepScreenOn = uiState.keepScreenAwake
        onDispose {
            view.keepScreenOn = false
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
        ) {
            item {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Tabletop & Offline Vault",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("open_settings_button_tabletop")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.action_open_settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 1. Offline Room Storage & Sync Control
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Room Offline Storage", style = MaterialTheme.typography.titleMedium)
                            }
                            VaultStatusBadge(
                                isOnline = uiState.isOnline,
                                offlineOnlyMode = uiState.offlineOnlyMode,
                                cachedCardCount = uiState.cachedCardCount
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            StatTile(
                                label = "Cached Cards",
                                value = uiState.cachedCardCount.toString(),
                                modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                label = "Local Decks",
                                value = uiState.deckCount.toString(),
                                modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                label = "Deck Slots",
                                value = uiState.totalDeckEntriesCount.toString(),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Offline-Only Mode", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Keep all searches and deck imports strictly on-device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.offlineOnlyMode,
                                onCheckedChange = viewModel::setOfflineOnlyMode,
                                modifier = Modifier.testTag("offline_only_switch")
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = viewModel::syncStaplesPack,
                                enabled = !uiState.isSyncing,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("sync_staples_button")
                            ) {
                                if (uiState.isSyncing) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                } else {
                                    Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cache Staples")
                                }
                            }
                            OutlinedButton(
                                onClick = viewModel::restoreStarterCatalog,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("restore_starter_catalog_button")
                            ) {
                                Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Verify Base DB")
                            }
                        }
                    }
                }
            }

            // 2. Tabletop Life, Poison & Commander Damage Tracker
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Tabletop Life Tracker", style = MaterialTheme.typography.titleMedium)
                            OutlinedButton(
                                onClick = viewModel::resetMatch,
                                modifier = Modifier.testTag("reset_life_button")
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reset")
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = uiState.playerCount == 2 && uiState.startingLife == 20,
                                onClick = { viewModel.configureMatch(2, 20) },
                                label = { Text("1v1 • 20") }
                            )
                            FilterChip(
                                selected = uiState.playerCount == 2 && uiState.startingLife == 40,
                                onClick = { viewModel.configureMatch(2, 40) },
                                label = { Text("2P • 40") }
                            )
                            FilterChip(
                                selected = uiState.playerCount == 4 && uiState.startingLife == 40,
                                onClick = { viewModel.configureMatch(4, 40) },
                                label = { Text("Pod 4P • 40") }
                            )
                        }

                        uiState.players.forEach { player ->
                            PlayerLifeRow(
                                player = player,
                                onAdjustLife = { delta -> viewModel.adjustLife(player.id, delta) },
                                onAdjustPoison = { delta -> viewModel.adjustPoison(player.id, delta) },
                                onAdjustCmdr = { delta -> viewModel.adjustCommanderDamage(player.id, delta) }
                            )
                        }
                    }
                }
            }

            // 3. Hypergeometric Probability Lab
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                Text("Hypergeometric Draw Lab", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Odds of drawing ≥ ${uiState.labDesiredHits} target card(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "${"%.1f".format(uiState.labProbabilityPercent)}%",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        LabStepperRow(
                            label = "Deck Size",
                            value = uiState.labDeckSize,
                            onDelta = { d ->
                                viewModel.updateLabParams(
                                    uiState.labDeckSize + d,
                                    uiState.labSuccesses,
                                    uiState.labCardsSeen,
                                    uiState.labDesiredHits
                                )
                            }
                        )
                        LabStepperRow(
                            label = "Copies / Lands in Deck",
                            value = uiState.labSuccesses,
                            onDelta = { d ->
                                viewModel.updateLabParams(
                                    uiState.labDeckSize,
                                    uiState.labSuccesses + d,
                                    uiState.labCardsSeen,
                                    uiState.labDesiredHits
                                )
                            }
                        )
                        LabStepperRow(
                            label = "Cards Drawn (7 + Turn)",
                            value = uiState.labCardsSeen,
                            onDelta = { d ->
                                viewModel.updateLabParams(
                                    uiState.labDeckSize,
                                    uiState.labSuccesses,
                                    uiState.labCardsSeen + d,
                                    uiState.labDesiredHits
                                )
                            }
                        )
                        LabStepperRow(
                            label = "Minimum Hits Needed",
                            value = uiState.labDesiredHits,
                            onDelta = { d ->
                                viewModel.updateLabParams(
                                    uiState.labDeckSize,
                                    uiState.labSuccesses,
                                    uiState.labCardsSeen,
                                    uiState.labDesiredHits + d
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlayerLifeRow(
    player: PlayerTrackerState,
    onAdjustLife: (Int) -> Unit,
    onAdjustPoison: (Int) -> Unit,
    onAdjustCmdr: (Int) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(player.name, style = MaterialTheme.typography.titleMedium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedButton(
                        onClick = { onAdjustLife(-5) },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(38.dp)
                    ) { Text("-5") }
                    IconButton(
                        onClick = { onAdjustLife(-1) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("life_minus_${player.id}")
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Decrease life")
                    }
                    Text(
                        text = player.life.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    IconButton(
                        onClick = { onAdjustLife(1) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("life_plus_${player.id}")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Increase life")
                    }
                    OutlinedButton(
                        onClick = { onAdjustLife(5) },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(38.dp)
                    ) { Text("+5") }
                }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Poison: ${player.poison}", style = MaterialTheme.typography.labelMedium)
                    IconButton(onClick = { onAdjustPoison(1) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add poison", modifier = Modifier.size(16.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cmdr Dmg: ${player.commanderDamage}", style = MaterialTheme.typography.labelMedium)
                    IconButton(onClick = { onAdjustCmdr(1) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add commander damage", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LabStepperRow(
    label: String,
    value: Int,
    onDelta: (Int) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onDelta(-1) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease $label")
            }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(onClick = { onDelta(1) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.Filled.Add, contentDescription = "Increase $label")
            }
        }
    }
}
