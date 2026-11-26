package com.example.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.local.CardEntity
import com.example.data.local.CardMetadataStatus
import com.example.data.repository.ArtworkQuality
import com.example.data.repository.ArtworkSide
import com.example.data.repository.artworkId
import com.example.data.repository.offlineArtworkImages
import com.example.data.repository.OfflineArtworkRepository
import com.example.data.local.DeckWithEntries
import com.example.data.local.DeckZones
import com.example.domain.CardJsonCodec
import com.example.domain.CardRulingInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class CardInspectorPage { DETAILS, RULINGS, ADD_TO_DECK }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardInspectorSheet(
    card: CardEntity,
    decks: List<DeckWithEntries>,
    isOnline: Boolean,
    showCardArt: Boolean,
    onDismiss: () -> Unit,
    onToggleBookmark: () -> Unit,
    onAddToDeck: ((deckId: Long, zone: String) -> Unit)? = null,
    onDeleteCustomCard: (() -> Unit)? = null,
    printingVariants: List<CardEntity> = emptyList(),
    rulings: List<CardRulingInfo> = emptyList(),
    isLoadingRulings: Boolean = false,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    offlineArtwork: OfflineArtworkRepository? = null
) {
    val previousAction by rememberUpdatedState(onPrevious)
    val nextAction by rememberUpdatedState(onNext)
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scrollState = remember(card.id) { ScrollState(0) }
    var page by remember(card.id) { mutableStateOf(CardInspectorPage.DETAILS) }
    val backToDetails = { page = CardInspectorPage.DETAILS }

    val decodedFaces = remember(card.cardFacesJson) {
        CardJsonCodec.decodeFaces(card.cardFacesJson)
    }
    val context = LocalContext.current
    val imageRevision = offlineArtwork?.localImageRevision?.collectAsStateWithLifecycle()?.value
    val hasBackSide = remember(context, offlineArtwork, card, imageRevision) {
        offlineArtwork?.hasBackSide(card) ?: (
            OfflineArtworkRepository.localFile(context, card.scryfallId, ArtworkSide.BACK) != null ||
                card.offlineArtworkImages().any { it.side == ArtworkSide.BACK && !it.inferredBack }
            )
    }
    val effectiveRulings = remember(rulings, card.rulingsJson) {
        if (rulings.isNotEmpty()) rulings else CardJsonCodec.decodeRulings(card.rulingsJson)
    }
    val isUnresolved = card.metadataStatus == CardMetadataStatus.UNRESOLVED ||
        card.legalFormats.equals("Unknown", ignoreCase = true) ||
        card.legalFormats.isBlank()

    Dialog(
        onDismissRequest = { if (page == CardInspectorPage.DETAILS) onDismiss() else backToDetails() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        if (page == CardInspectorPage.RULINGS) {
            CardRulings(
                cardName = card.name,
                rulings = effectiveRulings,
                isLoading = isLoadingRulings,
                onBack = backToDetails
            )
            return@Dialog
        }

        if (page == CardInspectorPage.ADD_TO_DECK && onAddToDeck != null) {
            CardDeckPicker(
                cardName = card.name,
                decks = decks,
                onBack = backToDetails,
                onAdd = { deckId, zone ->
                    onAddToDeck(deckId, zone)
                    onDismiss()
                }
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
                onClick = { page = CardInspectorPage.RULINGS },
                modifier = Modifier.fillMaxWidth().testTag("sheet_open_rulings")
            ) {
                Text("Official Rulings (${effectiveRulings.size})")
            }
            if (onAddToDeck != null && decks.isNotEmpty()) {
                OutlinedButton(
                    onClick = { page = CardInspectorPage.ADD_TO_DECK },
                    modifier = Modifier.fillMaxWidth().testTag("sheet_open_deck_picker")
                ) {
                    Text("Add to Offline Deck")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (showCardArt || offlineArtwork != null) {
                CardArtworkActions(card, showCardArt, isOnline, offlineArtwork, hasBackSide, onPrevious, onNext)
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
                                text = if (hasBackSide) "Card sides" else "Rules text",
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
                                                text = when {
                                                    hasBackSide && index == 0 -> "Front · ${face.name}"
                                                    hasBackSide && index == 1 -> "Back · ${face.name}"
                                                    else -> face.name
                                                },
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CardDeckPicker(
    cardName: String,
    decks: List<DeckWithEntries>,
    onBack: () -> Unit,
    onAdd: (Long, String) -> Unit
) {
    var selectedDeckId by remember { mutableStateOf(decks.firstOrNull()?.deck?.id) }
    var selectedZone by remember { mutableStateOf(DeckZones.MAINBOARD) }
    var deckMenuExpanded by remember { mutableStateOf(false) }
    val selectedDeck = decks.firstOrNull { it.deck.id == selectedDeckId }?.deck

    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp, bottom = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to card details")
                }
                Column {
                    Text("Add to Offline Deck", style = MaterialTheme.typography.titleLarge)
                    Text(cardName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            val selectedDeckName = selectedDeck?.name ?: "Select Deck"
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(DeckZones.MAINBOARD, DeckZones.SIDEBOARD, DeckZones.COMMANDER).forEach { z ->
                    val active = selectedZone == z
                    FilterChip(
                        selected = active,
                        onClick = { selectedZone = z },
                        label = {
                            Text(
                                text = z.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
        }

        Button(
            onClick = { selectedDeck?.let { onAdd(it.id, selectedZone) } },
            enabled = selectedDeck != null,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                .testTag("sheet_add_to_deck_button")
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add 1x to ${selectedZone.lowercase().replaceFirstChar { it.uppercase() }}")
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
private fun CardArtworkActions(
    card: CardEntity,
    showCardArt: Boolean,
    isOnline: Boolean,
    repository: OfflineArtworkRepository?,
    hasBackSide: Boolean,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?
) {
    val scope = rememberCoroutineScope()
    var showDownload by remember(card.id) { mutableStateOf(false) }
    var downloading by remember(card.id) { mutableStateOf(false) }
    var result by remember(card.id) { mutableStateOf<String?>(null) }
    var artworkRevision by remember(card.id) { mutableStateOf(0) }
    if (repository != null) {
        val hasPrinting = artworkId(card.scryfallId) != null
        val defaultQuality by repository.defaultQuality.collectAsStateWithLifecycle(initialValue = ArtworkQuality.SD)
        OutlinedButton(
            onClick = { showDownload = true },
            enabled = hasPrinting && isOnline && !downloading,
            modifier = Modifier.fillMaxWidth().testTag("sheet_download_card_image")
        ) { Text(if (downloading) "Downloading image…" else "Download card image") }
        val message = result ?: if (!hasPrinting) "No verified printing ID for this card." else null
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        if (showDownload) {
            var overrideQuality by remember { mutableStateOf<ArtworkQuality?>(null) }
            val quality = overrideQuality ?: defaultQuality
            AlertDialog(
                onDismissRequest = { showDownload = false },
                title = { Text("Download card image") },
                text = {
                    Column {
                        Text(card.name)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ArtworkQuality.entries.forEach { value ->
                                FilterChip(
                                    selected = quality == value,
                                    onClick = { overrideQuality = value },
                                    label = { Text(value.label) }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDownload = false
                        downloading = true
                        result = null
                        scope.launch {
                            try {
                                result = repository.downloadCard(card, quality)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                result = error.message ?: "Could not download the card image."
                            } finally {
                                artworkRevision++
                                downloading = false
                            }
                        }
                    }) { Text("Download ${quality.label}") }
                },
                dismissButton = { TextButton(onClick = { showDownload = false }) { Text("Cancel") } }
            )
        }
    }
    if (showCardArt) {
        CardInspectorArtwork(card, isOnline, artworkRevision, hasBackSide, onPrevious, onNext)
    }
}

@Composable
private fun CardInspectorArtwork(
    card: CardEntity,
    isOnline: Boolean,
    artworkRevision: Int,
    hasBackSide: Boolean,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?
) {
    val context = LocalContext.current
    val sources = remember(context, card, isOnline, artworkRevision, hasBackSide) {
        card.offlineArtworkImages().filter { it.side == ArtworkSide.FRONT || hasBackSide }.associate { image ->
            image.side to (OfflineArtworkRepository.localFile(context, card.scryfallId, image.side)
                ?: if (isOnline) image.url(ArtworkQuality.SD) else null)
        }
    }
    val front = sources[ArtworkSide.FRONT]
    val back = sources[ArtworkSide.BACK]
    var showBack by remember(card.id) { mutableStateOf(false) }
    var enlarged by remember { mutableStateOf(false) }
    val source = (if (showBack) back else front) ?: front ?: back
    val description = if (!hasBackSide) card.name else "${card.name} · ${if (showBack || front == null) "back" else "front"}"
    val request = remember(context, source, isOnline) {
        if (source == null) return@remember null
        ImageRequest.Builder(context)
            .data(source)
            .networkCachePolicy(if (isOnline) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .build()
    }
    if (request != null) {
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
    }

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
                    if (front != null && back != null) {
                        TextButton(
                            onClick = { showBack = !showBack },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(if (showBack) "Show front" else "Show back")
                        }
                    }
                    ZoomableCardArtwork(
                        cardId = "${card.id}-$showBack",
                        request = request,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        description = description,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
        }
    }
}


@Composable
private fun ZoomableCardArtwork(
    cardId: String,
    request: ImageRequest?,
    description: String,
    modifier: Modifier,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?
) {
    var zoom by remember(cardId, request) { mutableStateOf(1f) }
    var offset by remember(cardId, request) { mutableStateOf(Offset.Zero) }
    val previousAction by rememberUpdatedState(onPrevious)
    val nextAction by rememberUpdatedState(onNext)
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val cardAspect = 488f / 680f
        val fittedWidth = minOf(maxWidth, maxHeight * cardAspect)
        val fittedHeight = fittedWidth / cardAspect
        val widthPx = with(LocalDensity.current) { fittedWidth.toPx() }
        val heightPx = with(LocalDensity.current) { fittedHeight.toPx() }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
                .clipToBounds()
                .testTag("sheet_enlarged_art")
                .semantics { stateDescription = "${(zoom * 100).roundToInt()}% zoom" }
                .pointerInput(cardId, request, isRtl) {
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        if (zoom > 1f) return@awaitEachGesture
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.id != first.id && it.pressed }) break
                            val change = event.changes.firstOrNull { it.id == first.id } ?: break
                            if (!change.pressed) {
                                val movement = change.position - first.position
                                val horizontal = if (isRtl) -movement.x else movement.x
                                if (zoom <= 1f && abs(horizontal) >= 56.dp.toPx() &&
                                    abs(horizontal) > abs(movement.y) * 1.25f
                                ) {
                                    if (horizontal < 0f) nextAction?.invoke() else previousAction?.invoke()
                                }
                                break
                            }
                        } while (true)
                    }
                }
                .pointerInput(cardId, request, widthPx, heightPx) {
                    detectTransformGestures { centroid, pan, zoomChange, _ ->
                        val nextZoom = (zoom * zoomChange).coerceIn(1f, 4f)
                        val viewportCenter = Offset(size.width / 2f, size.height / 2f)
                        val anchor = centroid - viewportCenter
                        val nextOffset = anchor + (offset - anchor) * (nextZoom / zoom) + pan
                        val maxX = ((widthPx * nextZoom - size.width) / 2f).coerceAtLeast(0f)
                        val maxY = ((heightPx * nextZoom - size.height) / 2f).coerceAtLeast(0f)
                        offset = Offset(nextOffset.x.coerceIn(-maxX, maxX), nextOffset.y.coerceIn(-maxY, maxY))
                        zoom = nextZoom
                    }
                }
        ) {
            if (request == null) {
                Text("$description · No image available", style = MaterialTheme.typography.bodyMedium)
            } else {
                AsyncImage(
                    model = request,
                    contentDescription = description,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(fittedWidth, fittedHeight)
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = offset.x
                            translationY = offset.y
                        }
                )
            }
        }
    }
}
