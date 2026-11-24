package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.CardEntity
import com.example.data.local.DeckEntryWithCard
import com.example.data.local.DeckZones
import com.example.ui.components.ColorIdentityRow
import com.example.ui.components.ColorPipBreakdownBar
import com.example.ui.components.ManaCostRow
import com.example.ui.components.ManaCurveChart
import com.example.ui.components.SynergyLatticeGraph
import com.example.ui.viewmodel.DeckDetailViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckDetailScreen(
    viewModel: DeckDetailViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAskOracleAboutDeck: (Long) -> Unit,
    onOpenSynergyAnalyzer: (Long) -> Unit,
    cardInspector: @Composable (List<CardEntity>, CardEntity, () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val deckWithEntries = uiState.deckWithEntries
    val analytics = uiState.analytics
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddPicker by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var inspectedCard by remember { mutableStateOf<Pair<List<CardEntity>, CardEntity>?>(null) }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = deckWithEntries?.deck?.name ?: "Deck",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (deckWithEntries != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${deckWithEntries.deck.format} • ${deckWithEntries.playableCount} cards",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                ColorIdentityRow(
                                    colors = deckWithEntries.resolvedColorIdentity,
                                    pipSize = 14.dp
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("deck_detail_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to decks")
                    }
                },
                actions = {
                    if (deckWithEntries != null) {
                        IconButton(
                            onClick = { onAskOracleAboutDeck(deckWithEntries.deck.id) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("ask_oracle_deck_button")
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = "Ask Lattice Oracle about this deck",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                val exported = viewModel.exportDeckText()
                                clipboard.setText(AnnotatedString(exported))
                                scope.launch { snackbarHostState.showSnackbar("Copied decklist to clipboard.") }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("export_deck_button")
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Export decklist text")
                        }
                        IconButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("edit_deck_meta_button")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit deck details")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0 && deckWithEntries != null) {
                FloatingActionButton(
                    onClick = { showAddPicker = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("deck_add_card_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add card to deck")
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        if (deckWithEntries == null || analytics == null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Text("Loading deck from Room...", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

        val tabs = listOf("List", "Analytics", "Lattice", "Playtest")

        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
            ) {
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { idx, title ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(title, maxLines = 1) },
                            modifier = Modifier.testTag("deck_tab_${title.lowercase()}")
                        )
                    }
                }

                when (selectedTab) {
                    0 -> DeckListZoneTab(
                        deckEntries = deckWithEntries.entries.filter { it.entry.zone == uiState.activeZone },
                        activeZone = uiState.activeZone,
                        zoneCounts = mapOf(
                            DeckZones.MAINBOARD to deckWithEntries.mainboardEntries.sumOf { it.entry.quantity },
                            DeckZones.COMMANDER to deckWithEntries.commanderEntries.sumOf { it.entry.quantity },
                            DeckZones.SIDEBOARD to deckWithEntries.sideboardEntries.sumOf { it.entry.quantity },
                            DeckZones.MAYBEBOARD to deckWithEntries.maybeboardEntries.sumOf { it.entry.quantity }
                        ),
                        onSelectZone = viewModel::setActiveZone,
                        onInspectCard = { cards, card -> inspectedCard = cards to card },
                        onAdjustQty = { cardId, delta -> viewModel.adjustQuantity(cardId, uiState.activeZone, delta) },
                        onMoveZone = { cardId, toZone -> viewModel.moveZone(cardId, uiState.activeZone, toZone) },
                        modifier = Modifier.weight(1f)
                    )

                    1 -> LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Mana Curve", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = "Avg CMC: ${"%.2f".format(analytics.averageCmc)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    ManaCurveChart(manaCurve = analytics.manaCurve)
                                }
                            }
                        }

                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Color Pip Distribution", style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    ColorPipBreakdownBar(colorPips = analytics.colorPips)
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
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text("Type Breakdown", style = MaterialTheme.typography.titleMedium)
                                    analytics.typeCounts.forEach { (type, count) ->
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(type, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                text = count.toString(),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
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
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("Checks Performed (Partial Local Check)", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = "Offline checks on cached Room data only — not full tournament format validation.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.testTag("deck_validation_scope_subtitle")
                                        )
                                    }
                                    analytics.validationNotes.forEach { note ->
                                        Row(verticalAlignment = Alignment.Top) {
                                            Icon(
                                                imageVector = if (note.isWarning) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                tint = if (note.isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(note.title, style = MaterialTheme.typography.titleSmall)
                                                Text(
                                                    note.detail,
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

                    2 -> LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Synergy Lattice", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "Tap any synergy node to highlight connected cards inside this deck.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    SynergyLatticeGraph(
                                        clusters = analytics.synergyClusters,
                                        connections = analytics.synergyConnections,
                                        selectedTag = uiState.selectedSynergyTag,
                                        onSelectTag = viewModel::selectSynergyTag
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { onOpenSynergyAnalyzer(deckWithEntries.deck.id) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("open_full_synergy_analyzer_button")
                                    ) {
                                        Icon(
                                            Icons.Filled.Hub,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Open Synergy Analyzer & Archetypes")
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
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text("Hypergeometric Draw Consistency", style = MaterialTheme.typography.titleMedium)
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("2+ Lands in Opening 7", style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = "${(analytics.openingLandOdds7 * 100).toInt()}%",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("3+ Lands by Turn 3 (9 cards)", style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = "${(analytics.turn3Land3Odds * 100).toInt()}%",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    3 -> PlaytestGoldfishTab(
                        playtest = uiState.playtest,
                        onDealSeven = { viewModel.startNewTestHand(isMulligan = false) },
                        onMulligan = { viewModel.startNewTestHand(isMulligan = true) },
                        onDrawCard = viewModel::drawCard,
                        onPlayCard = viewModel::playCardFromHand,
                        onPutBottom = viewModel::putOnBottomForMulligan,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    inspectedCard?.let { (cards, card) -> cardInspector(cards, card) { inspectedCard = null } }

    if (showAddPicker) {
        AddCardPickerDialog(
            query = uiState.cardPickerQuery,
            cards = uiState.allVaultCards,
            activeZone = uiState.activeZone,
            onQueryChange = viewModel::setPickerQuery,
            onAddCard = { card -> viewModel.adjustQuantity(card.id, uiState.activeZone, 1) },
            onDismiss = { showAddPicker = false }
        )
    }

    if (showEditDialog && deckWithEntries != null) {
        EditDeckMetaDialog(
            initialName = deckWithEntries.deck.name,
            initialFormat = deckWithEntries.deck.format,
            initialDesc = deckWithEntries.deck.description,
            initialNotes = deckWithEntries.deck.notes,
            onDismiss = { showEditDialog = false },
            onSave = { n, f, d, notes ->
                showEditDialog = false
                viewModel.updateDeckMeta(n, f, d, notes)
            }
        )
    }
}

@Composable
private fun DeckListZoneTab(
    deckEntries: List<DeckEntryWithCard>,
    activeZone: String,
    zoneCounts: Map<String, Int>,
    onSelectZone: (String) -> Unit,
    onInspectCard: (List<CardEntity>, CardEntity) -> Unit,
    onAdjustQty: (String, Int) -> Unit,
    onMoveZone: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            DeckZones.allZones.forEach { zone ->
                val count = zoneCounts[zone] ?: 0
                val label = zone.lowercase().replaceFirstChar { it.uppercase() }
                FilterChip(
                    selected = activeZone == zone,
                    onClick = { onSelectZone(zone) },
                    label = { Text("$label ($count)") },
                    modifier = Modifier.testTag("zone_chip_${zone.lowercase()}")
                )
            }
        }

        if (deckEntries.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = "No cards in ${activeZone.lowercase()}. Tap + to add cards from your offline Room vault.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val ordered = remember(deckEntries) { deckEntries.sortedBy { it.card.cmc } }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(ordered, key = { it.entry.id }) { item ->
                    DeckEntryRow(
                        item = item,
                        onInspect = { onInspectCard(ordered.map { it.card }, item.card) },
                        onDecrement = { onAdjustQty(item.card.id, -1) },
                        onIncrement = { onAdjustQty(item.card.id, 1) },
                        onMoveZone = { toZone -> onMoveZone(item.card.id, toZone) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeckEntryRow(
    item: DeckEntryWithCard,
    onInspect: () -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onMoveZone: (String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "${item.entry.quantity}x",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f).clickable(onClick = onInspect)
                    .testTag("inspect_deck_card_${item.card.id}")
            ) {
                Text(
                    text = item.card.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.card.typeLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    ManaCostRow(manaCost = item.card.manaCost, pipSize = 14.dp)
                }
            }
            IconButton(onClick = onDecrement, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onIncrement, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.Filled.Add, contentDescription = "Increase quantity", modifier = Modifier.size(18.dp))
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = "Move zone", modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DeckZones.allZones
                        .filter { it != item.entry.zone }
                        .forEach { target ->
                            DropdownMenuItem(
                                text = { Text("Move to ${target.lowercase().replaceFirstChar { it.uppercase() }}") },
                                onClick = {
                                    menuExpanded = false
                                    onMoveZone(target)
                                }
                            )
                        }
                }
            }
        }
    }
}

@Composable
private fun EditDeckMetaDialog(
    initialName: String,
    initialFormat: String,
    initialDesc: String,
    initialNotes: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var format by remember { mutableStateOf(initialFormat) }
    var desc by remember { mutableStateOf(initialDesc) }
    var notes by remember { mutableStateOf(initialNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Deck Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Deck Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf("Commander", "Modern", "Standard", "Pioneer", "Pauper", "Casual").forEach { f ->
                        FilterChip(selected = format == f, onClick = { format = f }, label = { Text(f) })
                    }
                }
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Combo & Matchup Notes") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, format, desc, notes) },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
