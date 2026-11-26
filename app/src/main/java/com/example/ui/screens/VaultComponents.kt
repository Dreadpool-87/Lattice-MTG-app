package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.ArtworkSide
import com.example.data.repository.OfflineArtworkRepository
import com.example.domain.CardJsonCodec
import com.example.data.local.CardEntity
import com.example.data.local.CardMetadataStatus
import com.example.ui.components.ManaCostRow
import com.example.ui.components.ManaSymbolPip
import com.example.ui.components.RarityPill

@Composable
fun VaultCardRow(
    card: CardEntity,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    offlineArtwork: OfflineArtworkRepository? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("vault_card_${card.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = card.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    ManaCostRow(manaCost = card.manaCost, pipSize = 16.dp)
                }
                OfflineImageBadge(card, offlineArtwork)
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = card.typeLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (card.rarity != "unknown") RarityPill(rarity = card.rarity)
                    val printingSuffix = buildString {
                        append(card.setCode)
                        if (card.collectorNumber.isNotBlank()) append(" #${card.collectorNumber}")
                        if (card.lang.isNotBlank() && !card.lang.equals("en", ignoreCase = true)) {
                            append(" (${card.lang.uppercase()})")
                        }
                    }
                    Text(
                        text = printingSuffix,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (card.power != null && card.toughness != null) {
                        Text(
                            text = "• ${card.power}/${card.toughness}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (card.isCustom) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "CUSTOM",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    } else if (card.metadataStatus == CardMetadataStatus.UNRESOLVED) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "UNRESOLVED",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
            IconButton(
                onClick = onToggleBookmark,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("bookmark_card_${card.id}")
            ) {
                Icon(
                    imageVector = if (card.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Save card",
                    tint = if (card.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CreateCustomCardDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Double, String, String, List<String>, String, String?, String?, List<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var manaCost by remember { mutableStateOf("{2}{U}") }
    var cmcText by remember { mutableStateOf("3") }
    var typeLine by remember { mutableStateOf("Artifact Creature — Construct") }
    var oracleText by remember { mutableStateOf("") }
    var power by remember { mutableStateOf("") }
    var toughness by remember { mutableStateOf("") }
    var rarity by remember { mutableStateOf("rare") }
    var colors by remember { mutableStateOf(setOf("U")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Offline Card", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Card Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_card_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manaCost,
                        onValueChange = { manaCost = it },
                        label = { Text("Mana Cost") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cmcText,
                        onValueChange = { cmcText = it },
                        label = { Text("CMC") },
                        singleLine = true,
                        modifier = Modifier.width(80.dp)
                    )
                }
                OutlinedTextField(
                    value = typeLine,
                    onValueChange = { typeLine = it },
                    label = { Text("Type Line") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = oracleText,
                    onValueChange = { oracleText = it },
                    label = { Text("Oracle Text") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = power,
                        onValueChange = { power = it },
                        label = { Text("Power") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = toughness,
                        onValueChange = { toughness = it },
                        label = { Text("Toughness") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("W", "U", "B", "R", "G").forEach { c ->
                        val active = c in colors
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .clickable { colors = if (active) colors - c else colors + c }
                                .padding(6.dp)
                        ) {
                            ManaSymbolPip(symbol = c, size = 20.dp)
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf("common", "uncommon", "rare", "mythic").forEach { r ->
                        FilterChip(
                            selected = rarity == r,
                            onClick = { rarity = r },
                            label = { Text(r.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cmc = cmcText.toDoubleOrNull() ?: 0.0
                    onSave(
                        name,
                        manaCost,
                        cmc,
                        typeLine,
                        oracleText,
                        colors.toList(),
                        rarity,
                        power.ifBlank { null },
                        toughness.ifBlank { null },
                        listOf("Custom")
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_custom_card_button")
            ) {
                Text("Save Card")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


@Composable
internal fun OfflineImageBadge(card: CardEntity, repository: OfflineArtworkRepository?) {
    if (repository == null) return
    val context = LocalContext.current
    val revision by repository.localImageRevision.collectAsStateWithLifecycle()
    val frontSaved = remember(context, card.scryfallId, revision) {
        OfflineArtworkRepository.localFile(context, card.scryfallId, ArtworkSide.FRONT) != null
    }
    val backSaved = remember(context, card.scryfallId, revision) {
        OfflineArtworkRepository.localFile(context, card.scryfallId, ArtworkSide.BACK) != null
    }
    val hasBackSide = remember(repository, card, revision) { repository.hasBackSide(card) }
    val hasMultipleTextParts = remember(card.cardFacesJson) {
        CardJsonCodec.decodeFaces(card.cardFacesJson).size > 1
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasBackSide) {
            Icon(
                Icons.Outlined.FlipToBack,
                contentDescription = "Double-sided card",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        } else if (hasMultipleTextParts) {
            Icon(
                Icons.Outlined.Description,
                contentDescription = "Multiple rules text sections",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        OfflineImageIcon(frontSaved, if (hasBackSide) "Front image" else "Card image")
        if (hasBackSide) OfflineImageIcon(backSaved, "Back image")
    }
}

@Composable
private fun OfflineImageIcon(saved: Boolean, label: String) {
    Icon(
        imageVector = if (saved) Icons.Outlined.Image else Icons.Outlined.ImageNotSupported,
        contentDescription = if (saved) "$label saved offline" else "$label not saved offline",
        tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp)
    )
}
