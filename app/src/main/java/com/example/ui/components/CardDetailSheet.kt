package com.example.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.local.CardEntity
import com.example.data.local.CardMetadataStatus
import com.example.data.repository.ArtworkSide
import com.example.data.repository.OfflineArtworkRepository
import com.example.data.local.DeckWithEntries
import com.example.data.local.DeckZones
import com.example.domain.CardJsonCodec
import com.example.domain.CardRulingInfo

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CardInspectorSheet(
    card: CardEntity,
    decks: List<DeckWithEntries>,
    isOnline: Boolean,
    showCardArt: Boolean,
    onDismiss: () -> Unit,
    onToggleBookmark: () -> Unit,
    onAddToDeck: (deckId: Long, zone: String) -> Unit,
    onDeleteCustomCard: (() -> Unit)? = null,
    printingVariants: List<CardEntity> = emptyList(),
    rulings: List<CardRulingInfo> = emptyList(),
    isLoadingRulings: Boolean = false,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null
) {
    val previousAction by rememberUpdatedState(onPrevious)
    val nextAction by rememberUpdatedState(onNext)
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scrollState = remember(card.id) { ScrollState(0) }
    var showingRulings by remember(card.id) { mutableStateOf(false) }
    var selectedDeckId by remember(decks) { mutableStateOf(decks.firstOrNull()?.deck?.id) }
    var selectedZone by remember { mutableStateOf(DeckZones.MAINBOARD) }
    var deckMenuExpanded by remember { mutableStateOf(false) }

    val decodedFaces = remember(card.cardFacesJson) {
        CardJsonCodec.decodeFaces(card.cardFacesJson)
    }
    val effectiveRulings = remember(rulings, card.rulingsJson) {
        if (rulings.isNotEmpty()) rulings else CardJsonCodec.decodeRulings(card.rulingsJson)
    }
    val isUnresolved = card.metadataStatus == CardMetadataStatus.UNRESOLVED ||
        card.legalFormats.equals("Unknown", ignoreCase = true) ||
        card.legalFormats.isBlank()

    Dialog(
        onDismissRequest = { if (showingRulings) showingRulings = false else onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        if (showingRulings) {
            CardRulings(
                cardName = card.name,
                rulings = effectiveRulings,
                isLoading = isLoadingRulings,
                onBack = { showingRulings = false }
            )
            return@Dialog
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .testTag("sheet_card_details")
                .pointerInput(card.id, isRtl) {
                    val threshold = 64.dp.toPx()
                    var distance = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { distance = 0f },
                        onDragEnd = {
                            when {
                                distance <= -threshold -> nextAction?.invoke()
                                distance >= threshold -> previousAction?.invoke()
                            }
                        }
                    ) { _, dragAmount -> distance += if (isRtl) -dragAmount else dragAmount }
                }
                .safeDrawingPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 28.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = card.typeLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ManaCostRow(manaCost = card.manaCost, pipSize = 22.dp)
                    IconButton(
                        onClick = onToggleBookmark,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("sheet_bookmark_button")
                    ) {
                        Icon(
                            imageVector = if (card.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = "Toggle bookmark",
                            tint = if (card.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("sheet_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close card details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val identityList = card.colorIdentity.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val accentColor = manaColorsForSymbol(identityList.firstOrNull() ?: "C").first

            if (onPrevious != null || onNext != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous card")
                    }
                    IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next card")
                    }
                }
            }
            OutlinedButton(
                onClick = { showingRulings = true },
                modifier = Modifier.fillMaxWidth().testTag("sheet_open_rulings")
            ) {
                Text("Official Rulings (${effectiveRulings.size})")
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (showCardArt) {
                CardInspectorArtwork(card, decodedFaces.firstOrNull()?.imageUri, decodedFaces.getOrNull(1)?.imageUri, isOnline)
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                accentColor.copy(alpha = 0.7f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            RarityPill(rarity = card.rarity)
                            Spacer(modifier = Modifier.width(8.dp))
                            val printingMeta = buildString {
                                append(card.setCode)
                                if (card.collectorNumber.isNotBlank()) append(" #${card.collectorNumber}")
                                if (card.lang.isNotBlank()) append(" (${card.lang.uppercase()})")
                                if (card.setName.isNotBlank()) append(" • ${card.setName}")
                            }
                            Text(
                                text = printingMeta,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (card.power != null && card.toughness != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${card.power}/${card.toughness}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        } else if (card.loyalty != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Loyalty ${card.loyalty}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    if (decodedFaces.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_faces_section")
                        ) {
                            Text(
                                text = "Card Faces (${decodedFaces.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            decodedFaces.forEachIndexed { index, face ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "Face ${index + 1}: ${face.name}",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            ManaCostRow(manaCost = face.manaCost, pipSize = 16.dp)
                                        }
                                        if (face.typeLine.isNotBlank()) {
                                            Text(
                                                text = face.typeLine,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = face.oracleText.ifBlank { "No rules text." },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (face.power != null && face.toughness != null) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "${face.power}/${face.toughness}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        } else if (face.loyalty != null) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Loyalty ${face.loyalty}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = card.oracleText.ifBlank { "No rules text." },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    val tags = card.synergyTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            tags.forEach { tag ->
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isUnresolved) {
                            "Format Legality: Unknown (Unresolved metadata)"
                        } else {
                            "Format Legality (Cached): ${card.legalFormats.replace(",", " • ")}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isUnresolved) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("card_legality_status")
                    )

                    if (card.scryfallId.isNotBlank() || card.oracleId.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.testTag("card_identifiers_section")
                        ) {
                            if (card.scryfallId.isNotBlank()) {
                                Text(
                                    text = "Scryfall Printing ID: ${card.scryfallId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (card.oracleId.isNotBlank()) {
                                Text(
                                    text = "Oracle Identity ID: ${card.oracleId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (printingVariants.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_printing_variants_section")
                ) {
                    Text(
                        text = "Cached Printing Variants (${printingVariants.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        printingVariants.forEach { variant ->
                            val isCurrent = variant.id == card.id
                            val label = buildString {
                                append(variant.setCode)
                                if (variant.collectorNumber.isNotBlank()) append(" #${variant.collectorNumber}")
                                append(" (${variant.lang.uppercase()})")
                                if (isCurrent) append(" • Current")
                            }
                            Surface(
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isCurrent) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (decks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Add to Offline Deck",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                val selectedDeckName = decks.firstOrNull { it.deck.id == selectedDeckId }?.deck?.name ?: "Select Deck"
                ExposedDropdownMenuBox(
                    expanded = deckMenuExpanded,
                    onExpandedChange = { deckMenuExpanded = !deckMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedDeckName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target Deck") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deckMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("sheet_deck_selector")
                    )
                    ExposedDropdownMenu(
                        expanded = deckMenuExpanded,
                        onDismissRequest = { deckMenuExpanded = false }
                    ) {
                        decks.forEach { d ->
                            DropdownMenuItem(
                                text = { Text("${d.deck.name} (${d.deck.format})") },
                                onClick = {
                                    selectedDeckId = d.deck.id
                                    deckMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(DeckZones.MAINBOARD, DeckZones.SIDEBOARD, DeckZones.COMMANDER).forEach { z ->
                        val active = selectedZone == z
                        AssistChip(
                            onClick = { selectedZone = z },
                            label = {
                                Text(
                                    text = z.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        selectedDeckId?.let { id ->
                            onAddToDeck(id, selectedZone)
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sheet_add_to_deck_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add 1x to ${selectedZone.lowercase().replaceFirstChar { it.uppercase() }}")
                }
            }

            if (card.isCustom && onDeleteCustomCard != null) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        onDeleteCustomCard()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sheet_delete_custom_card_button")
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Custom Card", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun CardRulings(
    cardName: String,
    rulings: List<CardRulingInfo>,
    isLoading: Boolean,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .safeDrawingPadding()
            .testTag("card_rulings_section")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to card details")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Official Rulings (${rulings.size})", style = MaterialTheme.typography.titleLarge)
                Text(cardName, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isLoading) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            }
        }

        if (rulings.isEmpty()) {
            Text(
                text = if (isLoading) "Checking Scryfall rulings…" else "No cached rulings available offline.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("card_rulings_list"),
                contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rulings) { ruling ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "${ruling.publishedAt} • ${ruling.source.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(ruling.comment, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardInspectorArtwork(card: CardEntity, frontUri: String?, backUri: String?, isOnline: Boolean) {
    val context = LocalContext.current
    val front = OfflineArtworkRepository.localFile(context, card.scryfallId)
        ?: (card.imageUri ?: frontUri)?.takeIf { it.startsWith("https://") }
    val back = OfflineArtworkRepository.localFile(context, card.scryfallId, ArtworkSide.BACK)
        ?: backUri?.takeIf { it.startsWith("https://") }
    var showBack by remember(card.id) { mutableStateOf(false) }
    var enlarged by remember(card.id) { mutableStateOf(false) }
    val source = (if (showBack) back else front) ?: front ?: back ?: return
    val description = if (back == null) card.name else "${card.name} · ${if (showBack || front == null) "back" else "front"}"
    val request = remember(context, source, isOnline) {
        ImageRequest.Builder(context)
            .data(source)
            .networkCachePolicy(if (isOnline) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .build()
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = request,
            contentDescription = description,
            contentScale = ContentScale.Fit,
            modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth()
                .aspectRatio(488f / 680f).testTag("sheet_card_art")
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClickLabel = "Enlarge card image") { enlarged = true }
        )
    }
    if (front != null && back != null) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(onClick = { showBack = !showBack }) {
                Text(if (showBack) "Show front" else "Show back")
            }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))

    if (enlarged) {
        Dialog(
            onDismissRequest = { enlarged = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.safeDrawingPadding().padding(12.dp)) {
                    IconButton(
                        onClick = { enlarged = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close enlarged image")
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = description,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.weight(1f).fillMaxWidth().testTag("sheet_enlarged_art")
                    )
                }
            }
        }
    }
}
