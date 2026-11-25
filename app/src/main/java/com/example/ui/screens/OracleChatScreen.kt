package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.BuildConfig
import com.example.ai.OracleQuestionTarget
import com.example.data.local.DeckWithEntries
import com.example.data.local.ChatMessageEntity
import com.example.ui.viewmodel.OracleChatViewModel
import com.example.ui.viewmodel.OracleReplyState

private val starterMtgPrompts = listOf(
    "Analyze my selected deck's mana curve and land count",
    "How does Mycosynth Lattice + Karn, the Great Creator work?",
    "Explain replacement effects vs triggered abilities",
    "Suggest 3 budget upgrades for my active deck"
)

@Composable
fun OracleChatScreen(
    viewModel: OracleChatViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (BuildConfig.ORACLE_DECK_PREVIEW) {
        OraclePreviewScreen(
            decks = uiState.decks,
            selectedDeck = uiState.selectedContextDeck,
            selectDeck = { viewModel.selectContextDeck(it) },
            online = uiState.isOnline && !uiState.offlineOnlyMode,
            onOpenSettings = onOpenSettings,
            modifier = modifier
        )
        return
    }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size, uiState.replyState) {
        val totalItems = uiState.messages.size + if (uiState.replyState is OracleReplyState.Streaming) 1 else 0
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
        ) {
            // Header & Deck Context Switcher
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.oracle_title),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.oracle_subtitle),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (uiState.messages.isNotEmpty()) {
                                IconButton(
                                    onClick = viewModel::clearConversation,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("oracle_clear_chat_button")
                                ) {
                                    Icon(
                                        Icons.Filled.DeleteSweep,
                                        contentDescription = "Clear conversation",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = onOpenSettings,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("open_settings_button_oracle")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = stringResource(R.string.action_open_settings),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Active Deck Context Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        FilterChip(
                            selected = uiState.selectedContextDeckId == null,
                            onClick = { viewModel.selectContextDeck(null) },
                            label = { Text("General MTG") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.testTag("oracle_context_general")
                        )
                        uiState.decks.forEach { d ->
                            FilterChip(
                                selected = uiState.selectedContextDeckId == d.deck.id,
                                onClick = { viewModel.selectContextDeck(d.deck.id) },
                                label = { Text("${d.deck.name} (${d.deck.format})") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("oracle_context_deck_${d.deck.id}")
                            )
                        }
                    }

                    uiState.deckContextPreview?.let { preview ->
                        Surface(
                            color = if (preview.isPartialContext) {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (preview.isPartialContext) {
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("oracle_deck_context_disclosure")
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (preview.isPartialContext) Icons.Filled.Warning else Icons.Filled.Info,
                                        contentDescription = null,
                                        tint = if (preview.isPartialContext) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Sending \"${preview.deckName}\" (${preview.format}) deck info to Gemini when you ask.",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = preview.scopeSummary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (preview.isPartialContext) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = if (preview.isPartialContext) {
                                        Modifier.testTag("oracle_partial_deck_notice")
                                    } else {
                                        Modifier
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Conversation Feed
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (uiState.messages.isEmpty() && uiState.replyState !is OracleReplyState.Streaming) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.MenuBook,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Magic: The Gathering Specialist",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Ask rules interactions, stack & layer timing, format legality, or deckbuilding advice tailored to your active Room decklist. Conversation history is saved locally.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                starterMtgPrompts.forEach { prompt ->
                                    AssistChip(
                                        onClick = { viewModel.sendMessage(prompt) },
                                        label = {
                                            Text(
                                                text = prompt,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                items(uiState.messages, key = { it.id }) { msg ->
                    ChatBubble(message = msg)
                }

                when (val state = uiState.replyState) {
                    is OracleReplyState.Loading -> {
                        item(key = "loading_bubble") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Consulting the Lattice Oracle...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                    is OracleReplyState.Streaming -> {
                        item(key = "streaming_bubble") {
                            val preview = uiState.deckContextPreview
                            ChatBubble(
                                message = ChatMessageEntity(
                                    id = -1L,
                                    role = "model",
                                    content = state.partialText,
                                    contextDeckName = uiState.selectedContextDeck?.deck?.name,
                                    isPartialContext = preview?.isPartialContext == true,
                                    contextScopeNote = preview?.scopeSummary
                                )
                            )
                        }
                    }
                    is OracleReplyState.Error -> {
                        item(key = "error_banner") {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.ErrorOutline,
                                        contentDescription = "Error",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = viewModel::dismissError) {
                                        Text("Dismiss")
                                    }
                                }
                            }
                        }
                    }
                    OracleReplyState.Idle -> Unit
                }
            }

            // Message Composer
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                if (uiState.selectedContextDeck != null) {
                                    "Ask about ${uiState.selectedContextDeck?.deck?.name}..."
                                } else {
                                    "Ask any MTG rules or deck question..."
                                }
                            )
                        },
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("oracle_chat_input")
                    )

                    val busy = uiState.replyState is OracleReplyState.Loading ||
                        uiState.replyState is OracleReplyState.Streaming
                    FilledIconButton(
                        onClick = {
                            val toSend = inputText
                            inputText = ""
                            viewModel.sendMessage(toSend)
                        },
                        enabled = inputText.isNotBlank() && !busy,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .size(50.dp)
                            .testTag("oracle_send_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send MTG question")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessageEntity) {
    val isUser = message.role == "user"
    Column(
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (!message.contextDeckName.isNullOrBlank() && isUser) {
            val partialLabel = if (message.isPartialContext) " (Partial list sent)" else ""
            Text(
                text = "Sent to Gemini: ${message.contextDeckName}$partialLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 3.dp, end = 4.dp)
            )
        }
        Surface(
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            border = BorderStroke(
                1.dp,
                if (isUser) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
            ),
            modifier = Modifier.widthIn(max = 560.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = if (isUser) "You" else "Lattice Oracle",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
                if (!isUser && message.isPartialContext) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .semantics(mergeDescendants = true) {}
                            .testTag("oracle_bubble_partial_notice")
                    ) {
                        Text(
                            text = message.contextScopeNote ?: "Partial deck context only — reply is not validation of the entire deck.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun OraclePreviewScreen(
    decks: List<DeckWithEntries>,
    selectedDeck: DeckWithEntries?,
    selectDeck: (Long) -> Unit,
    online: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier
) {
    var questionDeck by remember { mutableStateOf<DeckWithEntries?>(null) }
    var explanationDeck by remember { mutableStateOf<DeckWithEntries?>(null) }
    questionDeck?.let { deck ->
        OracleDeckExplanationDialog(
            online = online,
            onDismiss = { questionDeck = null },
            questionTarget = OracleQuestionTarget.Deck(deck)
        )
    }
    explanationDeck?.let { deck ->
        OracleDeckExplanationDialog(online = online, onDismiss = { explanationDeck = null }, deck = deck)
    }
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.oracle_title), style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("open_settings_button_oracle")) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_open_settings))
            }
        }
        Text(if (decks.isEmpty()) "Create or import a deck first." else "Select the deck you want to ask about.")
        if (!online) Text("An internet connection and online mode are required to ask Oracle.")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            decks.forEach { deck ->
                FilterChip(
                    selected = selectedDeck?.deck?.id == deck.deck.id,
                    onClick = { selectDeck(deck.deck.id) },
                    label = { Text("${deck.deck.name} (${deck.deck.format})") },
                    modifier = Modifier.testTag("oracle_context_deck_${deck.deck.id}")
                )
            }
        }
        if (selectedDeck != null) {
            AssistChip(onClick = { questionDeck = selectedDeck }, label = { Text("Ask about this deck") })
            AssistChip(onClick = { explanationDeck = selectedDeck }, label = { Text("Explain deck check · Preview") })
        }
        Text("For a card question, open the card and choose Ask about this card.")
    }
}
