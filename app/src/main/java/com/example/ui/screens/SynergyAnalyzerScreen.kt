package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.RelationshipType
import com.example.ui.components.ColorIdentityRow
import com.example.ui.components.SynergyCardLatticeCanvas
import com.example.ui.theme.LatticeForestBase
import com.example.ui.theme.LatticeSparkContainer
import com.example.ui.theme.LatticeSparkGreen
import com.example.ui.viewmodel.SynergyAnalyzerViewModel

@Composable
fun SynergyAnalyzerScreen(
    viewModel: SynergyAnalyzerViewModel,
    onAskOracleForDeck: (Long) -> Unit,
    onOpenDeck: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val report = uiState.report

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
        ) {
            // Branded Header + Deck Switcher
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = LatticeForestBase,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.2.dp, LatticeSparkGreen.copy(alpha = 0.7f)),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Image(
                                        painter = painterResource(id = R.drawable.lattice_logo),
                                        contentDescription = "Lattice MTG Logo",
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.synergy_title),
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Text(
                                    text = stringResource(R.string.synergy_subtitle),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (report != null) {
                                AssistChip(
                                    onClick = { onAskOracleForDeck(report.deckWithEntries.deck.id) },
                                    label = { Text("Oracle") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.AutoAwesome,
                                            contentDescription = "Ask Oracle about synergy",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("synergy_ask_oracle_button")
                                )
                            }
                            IconButton(
                                onClick = onOpenSettings,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("open_settings_button_synergy")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = stringResource(R.string.action_open_settings),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Saved Decks Switcher
                    if (uiState.decks.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            uiState.decks.forEach { deckItem ->
                                val selected = deckItem.deck.id == uiState.selectedDeckId
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.selectDeck(deckItem.deck.id) },
                                    label = {
                                        Text(
                                            text = deckItem.deck.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = LatticeSparkContainer,
                                        selectedLabelColor = LatticeSparkGreen
                                    ),
                                    modifier = Modifier.testTag("synergy_deck_chip_${deckItem.deck.id}")
                                )
                            }
                        }
                    }
                }
            }

            if (report == null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Hub,
                            contentDescription = null,
                            tint = LatticeSparkGreen,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Saved Decks Yet",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Create or import a deck first to map card relationships and archetype fit.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                // 1. Saved Deck Synergy Summary Banner
                item {
                    val primary = report.primaryArchetype
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, LatticeSparkGreen.copy(alpha = 0.35f)),
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = report.deckWithEntries.deck.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "${report.deckWithEntries.deck.format} • ${report.roles.playableTotal} cards",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        ColorIdentityRow(
                                            colors = report.deckWithEntries.resolvedColorIdentity,
                                            pipSize = 14.dp
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = { onOpenDeck(report.deckWithEntries.deck.id) },
                                    modifier = Modifier.testTag("synergy_open_deck_button")
                                ) {
                                    Text("Edit List")
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SynergyMetricTile(
                                    label = "Card Links",
                                    value = "${report.relationships.size}",
                                    accent = LatticeSparkGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                SynergyMetricTile(
                                    label = "Avg. Mana Value",
                                    value = report.roles.avgCmc?.let { "%.2f".format(it) } ?: "—",
                                    accent = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (report.skippedCopies > 0) {
                                Text(
                                    text = "${report.skippedCopies} copies without verified card data were excluded from the analysis.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (primary != null) {
                                Surface(
                                    color = LatticeSparkContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = LatticeSparkGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Possible Archetype: ${primary.template.name}",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = LatticeSparkGreen
                                            )
                                            Text(
                                                text = primary.template.tagline,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFFD5E6D7)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "Insufficient evidence to suggest an archetype.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 2. Interactive Card Relationship Lattice Graph
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(18.dp),
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Card Relationship Lattice",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = if (uiState.selectedCardId == null) {
                                            "Tap a card to focus its links."
                                        } else {
                                            val name = report.deckWithEntries.entries.firstOrNull {
                                                it.card.id == uiState.selectedCardId
                                            }?.card?.name ?: "Selected card"
                                            "Focused on $name. Tap again to clear card focus."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (uiState.selectedCardId != null) {
                                    TextButton(
                                        onClick = { viewModel.selectCardNode(null) },
                                        modifier = Modifier.testTag("clear_node_focus_button")
                                    ) {
                                        Text("Reset")
                                    }
                                }
                            }

                            val graphLinks = uiState.graphRelationships
                            val nodeCount = graphLinks.flatMap { listOf(it.cardA.id, it.cardB.id) }.distinct().size
                            Text(
                                text = "Graph: $nodeCount cards, ${graphLinks.size} of ${uiState.filteredRelationships.size} links",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            SynergyCardLatticeCanvas(
                                relationships = graphLinks,
                                selectedCardId = uiState.selectedCardId,
                                onSelectCard = viewModel::selectCardNode
                            )

                            // Filter Chips by Relationship Type
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                FilterChip(
                                    selected = uiState.selectedRelationshipFilter == null,
                                    onClick = { viewModel.setRelationshipFilter(null) },
                                    label = { Text("All (${uiState.focusedRelationships.size})") },
                                    modifier = Modifier.testTag("rel_filter_all")
                                )
                                RelationshipType.entries.forEach { relType ->
                                    val count = uiState.focusedRelationships.count { it.type == relType }
                                    if (count > 0) {
                                        FilterChip(
                                            selected = uiState.selectedRelationshipFilter == relType,
                                            onClick = { viewModel.setRelationshipFilter(relType) },
                                            label = { Text("${relType.label} ($count)") },
                                            modifier = Modifier.testTag("rel_filter_${relType.name.lowercase()}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Highlighted Card Relationships Section
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Card links (${uiState.listedRelationships.size} of ${uiState.filteredRelationships.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                if (uiState.listedRelationships.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No card links match the current selection.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(uiState.listedRelationships, key = { it.id }) { rel ->
                        RelationshipDetailCard(
                            relationship = rel,
                            onSelectCard = viewModel::selectCardNode
                        )
                    }
                }

                // 4. Common MTG Deck Archetypes Comparison & Blueprint
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Common Deck Archetypes",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Compare your saved deck against classic MTG archetype blueprints.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        report.archetypeMatches.forEach { match ->
                            ArchetypeAffinityCard(
                                match = match,
                                isSelected = match.template.id == uiState.selectedArchetypeId,
                                onClick = { viewModel.selectArchetype(match.template.id) }
                            )
                        }
                    }
                }

                item {
                    val activeMatch = report.archetypeMatches.firstOrNull {
                        it.template.id == uiState.selectedArchetypeId
                    } ?: report.archetypeMatches.first()
                    ArchetypeBlueprintCard(
                        template = activeMatch.template,
                        roles = report.roles,
                        deckCardNames = report.analyzedCardNames
                    )
                }
            }
        }
    }
}
