package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.DeckWithEntries
import com.example.ui.components.ColorIdentityRow
import com.example.ui.components.ManaSymbolPip
import com.example.ui.components.VaultStatusBadge
import com.example.ui.theme.LatticeForestBase
import com.example.ui.theme.LatticeSparkGreen
import com.example.ui.viewmodel.DecksViewModel

private val deckFormats = listOf("All", "Commander", "Modern", "Standard", "Pioneer", "Pauper", "Casual")

@Composable
fun DecksScreen(
    viewModel: DecksViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenDeck: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

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
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
        ) {
            item {
                // Hero Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(196.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            RoundedCornerShape(22.dp)
                        )
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_lattice_hero),
                        contentDescription = "Lattice MTG Arcane Deck Studio",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0x660B0F17),
                                        Color(0xE60B0F17)
                                    )
                                )
                            )
                    )
                    Column(
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            VaultStatusBadge(
                                isOnline = uiState.isOnline,
                                offlineOnlyMode = uiState.offlineOnlyMode,
                                cachedCardCount = uiState.cachedCardCount
                            )
                            Surface(
                                color = LatticeForestBase.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    Color.White.copy(alpha = 0.18f)
                                )
                            ) {
                                IconButton(
                                    onClick = onOpenSettings,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("open_settings_button_decks")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Settings,
                                        contentDescription = stringResource(R.string.action_open_settings),
                                        tint = Color(0xFFF6EFE2)
                                    )
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                color = LatticeForestBase,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.2.dp,
                                    LatticeSparkGreen.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.lattice_logo),
                                        contentDescription = "Lattice MTG Logo",
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "LATTICE MTG",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color(0xFFF6EFE2)
                                )
                                Text(
                                    text = "Offline-first Room decklists, synergy lattice & playtester",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFD5CCBC)
                                )
                            }
                        }
                    }
                }
            }

            item {
                // Quick Actions + Search
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("create_deck_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_new_deck))
                        }
                        OutlinedButton(
                            onClick = { showImportDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("import_deck_button")
                        ) {
                            Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_import_deck))
                        }
                    }

                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        placeholder = { Text("Search your offline decks...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search decks") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("decks_search_input")
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        deckFormats.forEach { fmt ->
                            FilterChip(
                                selected = uiState.selectedFormatFilter == fmt,
                                onClick = { viewModel.setFormatFilter(fmt) },
                                label = { Text(fmt) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("format_chip_${fmt.lowercase()}")
                            )
                        }
                    }
                }
            }

            if (uiState.decks.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_empty_deckbox),
                                contentDescription = "Empty deck box",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth(0.75f)
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = stringResource(R.string.empty_decks_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.empty_decks_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(uiState.decks, key = { it.deck.id }) { deckItem ->
                    DeckListCard(
                        deckWithEntries = deckItem,
                        onOpen = { onOpenDeck(deckItem.deck.id) },
                        onDuplicate = { viewModel.duplicateDeck(deckItem.deck.id) },
                        onDelete = { viewModel.deleteDeck(deckItem.deck.id, deckItem.deck.name) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateDeckDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, format, desc, colors ->
                showCreateDialog = false
                viewModel.createDeck(name, format, desc, colors, onOpenDeck)
            }
        )
    }

    if (showImportDialog) {
        ImportDeckDialog(
            onDismiss = { showImportDialog = false },
            onImport = { name, format, raw ->
                showImportDialog = false
                viewModel.importDeck(name, format, raw, onOpenDeck)
            }
        )
    }
}

@Composable
private fun DeckListCard(
    deckWithEntries: DeckWithEntries,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deck = deckWithEntries.deck
    val targetSize = if (deck.format.equals("Commander", ignoreCase = true)) 100 else 60
    val commanderName = deckWithEntries.commanderEntries.firstOrNull()?.card?.name

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("deck_card_${deck.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = deck.format.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    ColorIdentityRow(colors = deckWithEntries.resolvedColorIdentity, pipSize = 18.dp)
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Filled.Style,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${deckWithEntries.playableCount}/$targetSize",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = deck.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (commanderName != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Commander: $commanderName",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (deck.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = deck.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${deckWithEntries.entries.size} unique slots • Sideboard ${deckWithEntries.sideboardCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row {
                    IconButton(
                        onClick = onDuplicate,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("duplicate_deck_${deck.id}")
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = "Duplicate deck",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("delete_deck_${deck.id}")
                    ) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "Delete deck",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateDeckDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, List<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var format by remember { mutableStateOf("Commander") }
    var description by remember { mutableStateOf("") }
    var selectedColors by remember { mutableStateOf(setOf("U")) }
    val formats = listOf("Commander", "Modern", "Standard", "Pioneer", "Pauper", "Casual")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forge New Offline Deck", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Deck Name") },
                    placeholder = { Text("e.g. Lattice Artifacts") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_deck_name_input")
                )

                Text("Format", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    formats.forEach { fmt ->
                        FilterChip(
                            selected = format == fmt,
                            onClick = { format = fmt },
                            label = { Text(fmt) }
                        )
                    }
                }

                Text("Color Identity", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("W", "U", "B", "R", "G").forEach { c ->
                        val active = c in selectedColors
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                if (active) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            modifier = Modifier
                                .clickable {
                                    selectedColors = if (active) selectedColors - c else selectedColors + c
                                }
                                .padding(6.dp)
                        ) {
                            ManaSymbolPip(symbol = c, size = 24.dp)
                        }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Strategy Notes (Optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, format, description, selectedColors.toList()) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_deck_button")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ImportDeckDialog(
    onDismiss: () -> Unit,
    onImport: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var format by remember { mutableStateOf("Commander") }
    var rawText by remember {
        mutableStateOf(
            "// Commander\n1 Urza, Lord High Artificer\n\n// Mainboard\n1 Sol Ring\n1 Mycosynth Lattice\n1 Counterspell\n10 Island"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Decklist Offline", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Deck Name") },
                    placeholder = { Text("Imported Deck") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_deck_name_input")
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf("Commander", "Modern", "Standard", "Casual").forEach { fmt ->
                        FilterChip(
                            selected = format == fmt,
                            onClick = { format = fmt },
                            label = { Text(fmt) }
                        )
                    }
                }
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    label = { Text("Decklist Text (e.g. 4 Lightning Bolt)") },
                    minLines = 5,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_deck_text_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(name.ifBlank { "Imported Deck" }, format, rawText) },
                enabled = rawText.isNotBlank(),
                modifier = Modifier.testTag("confirm_import_deck_button")
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
