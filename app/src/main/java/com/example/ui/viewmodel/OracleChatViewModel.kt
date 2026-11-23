package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChatDao
import com.example.data.local.ChatMessageEntity
import com.example.data.local.DeckWithEntries
import com.example.data.repository.CardRepository
import com.example.data.repository.DeckRepository
import com.example.data.repository.NetworkMonitor
import com.example.data.repository.PreferencesRepository
import com.example.domain.DeckAnalytics
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val TAG = "OracleChatViewModel"

sealed interface OracleReplyState {
    data object Idle : OracleReplyState
    data object Loading : OracleReplyState
    data class Streaming(val partialText: String) : OracleReplyState
    data class Error(val message: String) : OracleReplyState
}

data class DeckContextPreview(
    val deckName: String,
    val format: String,
    val includedMainboardSlots: Int,
    val totalMainboardSlots: Int,
    val includedMainboardCopies: Int,
    val totalMainboardCopies: Int,
    val omittedSideboardCount: Int,
    val omittedMaybeboardCount: Int,
    val isPartialContext: Boolean,
    val scopeSummary: String
)

data class OracleChatUiState(
    val messages: List<ChatMessageEntity> = emptyList(),
    val decks: List<DeckWithEntries> = emptyList(),
    val selectedContextDeckId: Long? = null,
    val vaultCardCount: Int = 0,
    val replyState: OracleReplyState = OracleReplyState.Idle,
    val isOnline: Boolean = false,
    val offlineOnlyMode: Boolean = false
) {
    val selectedContextDeck: DeckWithEntries?
        get() = decks.firstOrNull { it.deck.id == selectedContextDeckId }

    val deckContextPreview: DeckContextPreview?
        get() = selectedContextDeck?.let(OracleChatViewModel::buildDeckContextPreview)
}

class OracleChatViewModel(
    private val chatDao: ChatDao,
    deckRepository: DeckRepository,
    cardRepository: CardRepository,
    networkMonitor: NetworkMonitor,
    preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val selectedContextDeckId = MutableStateFlow<Long?>(null)
    private val replyState = MutableStateFlow<OracleReplyState>(OracleReplyState.Idle)

    // Lazy initialization per Firebase AI Logic contract (evaluated only on user query)
    private val generativeModel by lazy {
        val systemPrompt = content {
            text(
                """
                You are Lattice Oracle, the dedicated Magic: The Gathering (MTG) expert assistant inside the Lattice MTG Android app.

                STRICT DOMAIN RULE:
                - Your SOLE purpose is Magic: The Gathering: Comprehensive Rules (the stack, priority, layers, state-based actions, timestamps, replacement effects), card interactions, combos, deckbuilding, mana curves, format legality (Commander, Modern, Standard, Pioneer, Pauper, Legacy, Vintage), sideboard strategy, card evaluations, and MTG lore/sets.
                - You MUST NOT engage in random or off-topic conversations unrelated to Magic: The Gathering (such as general trivia, non-MTG programming, cooking, politics, dating, general small talk, or unrelated advice).
                - If a user asks anything outside Magic: The Gathering, politely decline in 1-2 sentences and invite them to ask an MTG rules, combo, or deckbuilding question instead.

                BEHAVIOR & CONTEXT:
                - Remember prior turns in this conversation and build upon earlier answers.
                - When an [ACTIVE DECK CONTEXT] block is provided, tailor your advice directly to the user's exact cards, commander, format, mana curve, and synergy tags.
                - Respect [DECK CONTEXT COVERAGE]: if only a partial decklist is included, explicitly acknowledge what was omitted and NEVER claim to have validated the entire deck.
                - Keep responses clear, well-structured, accurate to official MTG rules, and practical for players at the table.
                """.trimIndent()
            )
        }

        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.4f
            },
            systemInstruction = systemPrompt
        )
    }

    private var activeChatSession: Chat? = null

    val uiState: StateFlow<OracleChatUiState> = combine(
        chatDao.observeMessages(),
        deckRepository.allDecks,
        selectedContextDeckId,
        cardRepository.cardCount,
        replyState,
        networkMonitor.isOnline,
        preferencesRepository.offlineOnlyMode
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val msgs = values[0] as List<ChatMessageEntity>
        @Suppress("UNCHECKED_CAST")
        val decks = values[1] as List<DeckWithEntries>
        val ctxId = values[2] as Long?
        val cardCount = values[3] as Int
        val rState = values[4] as OracleReplyState
        val online = values[5] as Boolean
        val offlineForced = values[6] as Boolean

        val resolvedCtxId = if (ctxId != null && decks.any { it.deck.id == ctxId }) {
            ctxId
        } else {
            ctxId
        }

        OracleChatUiState(
            messages = msgs,
            decks = decks,
            selectedContextDeckId = resolvedCtxId,
            vaultCardCount = cardCount,
            replyState = rState,
            isOnline = online,
            offlineOnlyMode = offlineForced
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OracleChatUiState()
    )

    fun selectContextDeck(deckId: Long?) {
        selectedContextDeckId.value = deckId
    }

    fun dismissError() {
        if (replyState.value is OracleReplyState.Error) {
            replyState.value = OracleReplyState.Idle
        }
    }

    fun clearConversation() {
        viewModelScope.launch {
            chatDao.clearConversation()
            activeChatSession = null
            replyState.value = OracleReplyState.Idle
        }
    }

    fun sendMessage(rawQuestion: String) {
        val question = rawQuestion.trim()
        if (question.isEmpty() || replyState.value is OracleReplyState.Loading || replyState.value is OracleReplyState.Streaming) {
            return
        }

        val currentState = uiState.value
        if (currentState.offlineOnlyMode) {
            replyState.value = OracleReplyState.Error(
                "Offline-Only Mode is enabled. Disable it in Tabletop settings to consult Lattice Oracle via Gemini."
            )
            return
        }
        if (!currentState.isOnline) {
            replyState.value = OracleReplyState.Error(
                "No internet connection. Your saved conversation history remains readable offline; reconnect to ask Lattice Oracle new questions."
            )
            return
        }

        val contextDeck = currentState.selectedContextDeck
        val contextPreview = contextDeck?.let(::buildDeckContextPreview)

        viewModelScope.launch {
            replyState.value = OracleReplyState.Loading
            try {
                // Ensure activeChatSession is initialized with persisted Room history so memory persists across restarts
                val chat = activeChatSession ?: run {
                    val savedHistory = chatDao.getMessagesOnce().map { msg ->
                        content(role = msg.role) { text(msg.content) }
                    }
                    generativeModel.startChat(history = savedHistory).also {
                        activeChatSession = it
                    }
                }

                // Persist user question in Room immediately
                chatDao.insertMessage(
                    ChatMessageEntity(
                        role = "user",
                        content = question,
                        contextDeckName = contextDeck?.deck?.name,
                        isPartialContext = contextPreview?.isPartialContext == true,
                        contextScopeNote = contextPreview?.scopeSummary
                    )
                )

                val promptWithContext = buildContextualPrompt(
                    question = question,
                    deck = contextDeck,
                    vaultCount = currentState.vaultCardCount,
                    preview = contextPreview
                )

                val accumulated = StringBuilder()
                withTimeout(60_000L) {
                    chat.sendMessageStream(promptWithContext).collect { chunk ->
                        val chunkText = chunk.text.orEmpty()
                        if (chunkText.isNotEmpty()) {
                            accumulated.append(chunkText)
                            replyState.value = OracleReplyState.Streaming(accumulated.toString())
                        }
                    }
                }

                val finalReply = accumulated.toString().trim()
                if (finalReply.isNotEmpty()) {
                    chatDao.insertMessage(
                        ChatMessageEntity(
                            role = "model",
                            content = finalReply,
                            contextDeckName = contextDeck?.deck?.name,
                            isPartialContext = contextPreview?.isPartialContext == true,
                            contextScopeNote = contextPreview?.scopeSummary
                        )
                    )
                    replyState.value = OracleReplyState.Idle
                } else {
                    replyState.value = OracleReplyState.Error("Lattice Oracle returned an empty response. Please try again.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Oracle chat timed out", e)
                replyState.value = OracleReplyState.Error(
                    "Request timed out after 60 seconds. Please check your connection and try again."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI chat failed", e)
                replyState.value = OracleReplyState.Error(
                    e.localizedMessage ?: "Failed to reach Lattice Oracle."
                )
            }
        }
    }

    private fun buildContextualPrompt(
        question: String,
        deck: DeckWithEntries?,
        vaultCount: Int,
        preview: DeckContextPreview?
    ): String {
        if (deck == null || preview == null) {
            return "[APP CONTEXT: User's local Room Card Vault has $vaultCount cached MTG cards. No specific deck selected.]\n\nUser Question: $question"
        }

        val analytics = DeckAnalytics.analyze(deck)
        val commanderNames = deck.commanderEntries.joinToString(", ") { it.card.name }.ifEmpty { "None" }
        val mainList = deck.mainboardEntries
            .sortedBy { it.card.cmc }
            .take(MAX_CONTEXT_MAINBOARD_SLOTS)
            .joinToString("; ") { "${it.entry.quantity}x ${it.card.name} (${it.card.manaCost.ifEmpty { "Land" }})" }
        val topTags = analytics.synergyClusters.take(5).joinToString(", ") { "${it.tag} (${it.totalCopies})" }
        val coverageTag = if (preview.isPartialContext) "PARTIAL" else "COMPLETE"
        val partialInstruction = if (preview.isPartialContext) {
            "\nContext Constraint: Only part of the decklist is attached. Do NOT claim to validate the full deck or format legality."
        } else ""

        return """
            [ACTIVE DECK CONTEXT]
            [DECK CONTEXT COVERAGE: $coverageTag]
            Scope: ${preview.scopeSummary}$partialInstruction
            Deck Name: ${deck.deck.name}
            Format: ${deck.deck.format}
            Color Identity: ${deck.resolvedColorIdentity.joinToString(",").ifEmpty { "Colorless" }}
            Commander: $commanderNames
            Playable Cards: ${analytics.playableCount} (${analytics.landCount} lands, Avg Non-Land CMC: ${"%.2f".format(analytics.averageCmc)})
            Top Synergy Clusters: ${topTags.ifEmpty { "None" }}
            Included Mainboard Cards: $mainList

            User Question: $question
        """.trimIndent()
    }

    companion object {
        const val MAX_CONTEXT_MAINBOARD_SLOTS = 35

        fun buildDeckContextPreview(deck: DeckWithEntries): DeckContextPreview {
            val sortedMain = deck.mainboardEntries.sortedBy { it.card.cmc }
            val includedMain = sortedMain.take(MAX_CONTEXT_MAINBOARD_SLOTS)
            val totalMainCopies = sortedMain.sumOf { it.entry.quantity }
            val includedMainCopies = includedMain.sumOf { it.entry.quantity }
            val omittedMainSlots = (sortedMain.size - includedMain.size).coerceAtLeast(0)
            val omittedSideboardCount = deck.sideboardCount
            val omittedMaybeboardCount = deck.maybeboardEntries.sumOf { it.entry.quantity }

            val isPartial = omittedMainSlots > 0 || omittedSideboardCount > 0 || omittedMaybeboardCount > 0
            val excludedParts = buildList {
                if (omittedMainSlots > 0) add("$omittedMainSlots remaining mainboard entries")
                if (omittedSideboardCount > 0) add("$omittedSideboardCount sideboard cards")
                if (omittedMaybeboardCount > 0) add("$omittedMaybeboardCount maybeboard cards")
            }

            val scopeSummary = if (isPartial) {
                "Partial decklist sent (${includedMain.size}/${sortedMain.size} mainboard entries, $includedMainCopies/$totalMainCopies copies; omitted ${excludedParts.joinToString(", ")}). Replies are based only on included cards and do not validate the entire deck."
            } else {
                "Complete active mainboard sent (${sortedMain.size} entries, $totalMainCopies copies${if (deck.commanderEntries.isNotEmpty()) " + commander" else ""})."
            }

            return DeckContextPreview(
                deckName = deck.deck.name,
                format = deck.deck.format,
                includedMainboardSlots = includedMain.size,
                totalMainboardSlots = sortedMain.size,
                includedMainboardCopies = includedMainCopies,
                totalMainboardCopies = totalMainCopies,
                omittedSideboardCount = omittedSideboardCount,
                omittedMaybeboardCount = omittedMaybeboardCount,
                isPartialContext = isPartial,
                scopeSummary = scopeSummary
            )
        }
    }

    class Factory(
        private val chatDao: ChatDao,
        private val deckRepository: DeckRepository,
        private val cardRepository: CardRepository,
        private val networkMonitor: NetworkMonitor,
        private val preferencesRepository: PreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OracleChatViewModel(
                chatDao = chatDao,
                deckRepository = deckRepository,
                cardRepository = cardRepository,
                networkMonitor = networkMonitor,
                preferencesRepository = preferencesRepository
            ) as T
        }
    }
}
