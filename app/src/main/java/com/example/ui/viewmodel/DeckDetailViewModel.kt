package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.CardEntity
import com.example.data.local.DeckWithEntries
import com.example.data.local.DeckZones
import com.example.data.repository.CardRepository
import com.example.data.repository.DeckRepository
import com.example.domain.DeckAnalytics
import com.example.domain.DeckAnalyticsSummary
import com.example.domain.PlaytestMana
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BattlefieldPermanent(
    val instanceId: Long,
    val card: CardEntity,
    val tapped: Boolean = false,
    val enteredOnTurn: Int = 0
)

enum class PlaytestDestination {
    GRAVEYARD,
    EXILE,
    HAND,
    COMMAND_ZONE
}

data class PlaytestHandState(
    val library: List<CardEntity> = emptyList(),
    val hand: List<CardEntity> = emptyList(),
    val battlefield: List<BattlefieldPermanent> = emptyList(),
    val graveyard: List<CardEntity> = emptyList(),
    val exile: List<CardEntity> = emptyList(),
    val commandZone: List<CardEntity> = emptyList(),
    val commanderIds: Set<String> = emptySet(),
    val commanderColors: Set<String> = emptySet(),
    val commanderCasts: Map<String, Int> = emptyMap(),
    val mulliganCount: Int = 0,
    val bottomedThisMulligan: Int = 0,
    val turnNumber: Int = 1,
    val landsPlayedThisTurn: Int = 0,
    val manaPool: List<String> = emptyList(),
    val scryCard: CardEntity? = null,
    val landsInHand: Int = 0,
    val actionLog: List<String> = emptyList()
)

internal fun PlaytestHandState.commanderTax(commander: CardEntity): Int =
    2 * commanderCasts.getOrDefault(commander.id, 0)

internal fun PlaytestHandState.availableManaSources(): List<PlaytestMana.Source> =
    battlefield.mapNotNull { permanent ->
        val hasHaste = permanent.card.keywords.split(',').any { it.trim().equals("Haste", ignoreCase = true) }
        val summoningSick = permanent.enteredOnTurn == turnNumber &&
            PlaytestMana.isCreature(permanent.card) && !hasHaste
        if (permanent.tapped || summoningSick) return@mapNotNull null
        val choices = PlaytestMana.sourceChoices(permanent.card, commanderColors)
        if (choices.isEmpty()) null else PlaytestMana.Source(permanent.instanceId, choices)
    }

data class DeckDetailUiState(
    val deckWithEntries: DeckWithEntries? = null,
    val analytics: DeckAnalyticsSummary? = null,
    val allVaultCards: List<CardEntity> = emptyList(),
    val cardPickerQuery: String = "",
    val activeZone: String = DeckZones.MAINBOARD,
    val playtest: PlaytestHandState = PlaytestHandState(),
    val selectedSynergyTag: String? = null,
    val snackbarMessage: String? = null
)

class DeckDetailViewModel(
    private val deckId: Long,
    private val deckRepository: DeckRepository,
    cardRepository: CardRepository
) : ViewModel() {

    private var nextInstanceId = 1L
    private val cardPickerQuery = MutableStateFlow("")
    private val activeZone = MutableStateFlow(DeckZones.MAINBOARD)
    private val playtestState = MutableStateFlow(PlaytestHandState())
    private val selectedSynergyTag = MutableStateFlow<String?>(null)
    private val snackbarMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DeckDetailUiState> = combine(
        deckRepository.observeDeck(deckId),
        cardRepository.allCards,
        cardPickerQuery,
        activeZone,
        playtestState,
        selectedSynergyTag,
        snackbarMessage
    ) { values ->
        val deck = values[0] as DeckWithEntries?
        @Suppress("UNCHECKED_CAST")
        val allCards = values[1] as List<CardEntity>
        val pickerQ = values[2] as String
        val zone = values[3] as String
        val playtest = values[4] as PlaytestHandState
        val selectedTag = values[5] as String?
        val snack = values[6] as String?

        val analytics = deck?.let { DeckAnalytics.analyze(it) }
        val filteredCards = if (pickerQ.isBlank()) {
            allCards
        } else {
            allCards.filter {
                it.name.contains(pickerQ, ignoreCase = true) ||
                    it.typeLine.contains(pickerQ, ignoreCase = true) ||
                    it.oracleText.contains(pickerQ, ignoreCase = true) ||
                    it.synergyTags.contains(pickerQ, ignoreCase = true)
            }
        }

        DeckDetailUiState(
            deckWithEntries = deck,
            analytics = analytics,
            allVaultCards = filteredCards,
            cardPickerQuery = pickerQ,
            activeZone = zone,
            playtest = playtest,
            selectedSynergyTag = selectedTag,
            snackbarMessage = snack
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DeckDetailUiState()
    )

    fun setActiveZone(zone: String) {
        activeZone.value = zone
    }

    fun setPickerQuery(q: String) {
        cardPickerQuery.value = q
    }

    fun selectSynergyTag(tag: String?) {
        selectedSynergyTag.value = if (selectedSynergyTag.value == tag) null else tag
    }

    fun clearSnackbar() {
        snackbarMessage.value = null
    }

    fun adjustQuantity(cardId: String, zone: String, delta: Int) {
        viewModelScope.launch {
            deckRepository.addCardToDeck(deckId, cardId, zone, delta)
        }
    }

    fun moveZone(cardId: String, fromZone: String, toZone: String) {
        viewModelScope.launch {
            deckRepository.moveCardZone(deckId, cardId, fromZone, toZone)
            snackbarMessage.value = "Moved card to $toZone."
        }
    }

    fun updateDeckMeta(name: String, format: String, description: String, notes: String) {
        val current = uiState.value.deckWithEntries?.deck ?: return
        viewModelScope.launch {
            deckRepository.updateDeckMeta(current, name, format, description, notes)
            snackbarMessage.value = "Deck details saved offline."
        }
    }

    fun exportDeckText(): String {
        val current = uiState.value.deckWithEntries ?: return ""
        return deckRepository.exportDeckAsText(current)
    }

    // === Offline Goldfish Playtest Simulator ===
    fun startNewTestHand(isMulligan: Boolean = false) {
        val deck = uiState.value.deckWithEntries ?: return
        val expandedPool = deck.mainboardEntries.flatMap { entryWithCard ->
            List(entryWithCard.entry.quantity) { entryWithCard.card }
        }.shuffled()

        if (expandedPool.isEmpty()) {
            snackbarMessage.value = "Add cards to Mainboard before starting a playtest hand."
            return
        }

        val commanders = deck.commanderEntries.flatMap { entryWithCard ->
            List(entryWithCard.entry.quantity) { entryWithCard.card }
        }

        val handSize = minOf(7, expandedPool.size)
        val hand = expandedPool.take(handSize)
        val remaining = expandedPool.drop(handSize)
        val nextMull = if (isMulligan) (playtestState.value.mulliganCount + 1).coerceAtMost(handSize) else 0
        val logEntry = if (isMulligan) "London Mulligan #$nextMull (kept 7, bottom $nextMull)." else "Dealt opening hand of $handSize."

        playtestState.value = PlaytestHandState(
            library = remaining,
            hand = hand,
            battlefield = emptyList(),
            graveyard = emptyList(),
            exile = emptyList(),
            commandZone = commanders,
            commanderIds = commanders.map { it.id }.toSet(),
            commanderColors = commanders.flatMap { it.colorIdentity.split(',') }.map(String::trim).toSet(),
            mulliganCount = nextMull,
            bottomedThisMulligan = 0,
            turnNumber = 1,
            landsPlayedThisTurn = 0,
            scryCard = null,
            landsInHand = hand.count { "land" in it.typeLine.lowercase() },
            actionLog = listOf(logEntry)
        )
    }

    fun drawCard() {
        val current = playtestState.value
        if (current.scryCard != null) return
        if (current.library.isEmpty()) {
            snackbarMessage.value = "Library is empty!"
            return
        }
        val drawn = current.library.first()
        val nextHand = current.hand + drawn
        playtestState.value = current.copy(
            library = current.library.drop(1),
            hand = nextHand,
            landsInHand = nextHand.count { "land" in it.typeLine.lowercase() },
            actionLog = (listOf("Drew ${drawn.name}.") + current.actionLog).take(12)
        )
    }

    fun nextTurn() {
        val current = playtestState.value
        if (current.scryCard != null) return
        val untapped = current.battlefield.map { it.copy(tapped = false) }
        val nextTurnNum = current.turnNumber + 1
        if (current.library.isEmpty()) {
            playtestState.value = current.copy(
                battlefield = untapped,
                turnNumber = nextTurnNum,
                landsPlayedThisTurn = 0,
                manaPool = emptyList(),
                actionLog = (listOf("Turn $nextTurnNum: Untapped all (library empty).") + current.actionLog).take(12)
            )
            snackbarMessage.value = "Library is empty — cannot draw for turn."
            return
        }
        val drawn = current.library.first()
        val nextHand = current.hand + drawn
        playtestState.value = current.copy(
            library = current.library.drop(1),
            hand = nextHand,
            battlefield = untapped,
            turnNumber = nextTurnNum,
            landsPlayedThisTurn = 0,
            manaPool = emptyList(),
            landsInHand = nextHand.count { "land" in it.typeLine.lowercase() },
            actionLog = (listOf("Turn $nextTurnNum: Untapped & drew ${drawn.name}.") + current.actionLog).take(12)
        )
    }

    fun untapAll() {
        val current = playtestState.value
        if (current.battlefield.none { it.tapped }) return
        playtestState.value = current.copy(
            battlefield = current.battlefield.map { it.copy(tapped = false) },
            actionLog = (listOf("Untapped all permanents.") + current.actionLog).take(12)
        )
    }

    fun playCardFromHand(index: Int) {
        val current = playtestState.value
        if (index !in current.hand.indices) return
        val card = current.hand[index]
        val nextHand = current.hand.toMutableList().apply { removeAt(index) }
        val typeLower = card.typeLine.substringBefore(" // ").lowercase()
        val isLand = PlaytestMana.isLand(card)
        if (isLand && current.landsPlayedThisTurn >= 1) {
            snackbarMessage.value = "You already played a land this turn."
            return
        }
        if (!openingHandIsReady(current)) return
        val payment = if (isLand) null else payForCast(current, card) ?: return
        val paidBattlefield = tapPaidSources(current, payment)
        val isInstantOrSorcery = "instant" in typeLower || "sorcery" in typeLower

        if (isInstantOrSorcery) {
            playtestState.value = current.copy(
                hand = nextHand,
                battlefield = paidBattlefield,
                manaPool = payment?.pool ?: current.manaPool,
                graveyard = listOf(card) + current.graveyard,
                landsInHand = nextHand.count { "land" in it.typeLine.lowercase() },
                actionLog = (listOf("Cast ${card.name} → Graveyard.") + current.actionLog).take(12)
            )
        } else {
            val permanent = newPermanent(card, current)
            playtestState.value = current.copy(
                hand = nextHand,
                battlefield = paidBattlefield + permanent,
                manaPool = payment?.pool ?: current.manaPool,
                landsPlayedThisTurn = current.landsPlayedThisTurn + if (isLand) 1 else 0,
                landsInHand = nextHand.count { "land" in it.typeLine.lowercase() },
                actionLog = (listOf("${if (isLand) "Played land" else "Cast"} ${card.name}.") + current.actionLog).take(12)
            )
        }
    }

    fun castCommander(index: Int) {
        val current = playtestState.value
        if (index !in current.commandZone.indices) return
        val commander = current.commandZone[index]
        val nextCommandZone = current.commandZone.toMutableList().apply { removeAt(index) }
        if (!openingHandIsReady(current)) return
        val priorCasts = current.commanderCasts.getOrDefault(commander.id, 0)
        val payment = payForCast(current, commander, tax = current.commanderTax(commander)) ?: return
        val permanent = newPermanent(commander, current)
        val nextTaxCount = priorCasts + 1
        playtestState.value = current.copy(
            commandZone = nextCommandZone,
            battlefield = tapPaidSources(current, payment) + permanent,
            manaPool = payment.pool,
            commanderCasts = current.commanderCasts + (commander.id to nextTaxCount),
            actionLog = (listOf("Cast Commander ${commander.name} (cast #$nextTaxCount).") + current.actionLog).take(12)
        )
    }

    private fun openingHandIsReady(state: PlaytestHandState): Boolean {
        if (state.bottomedThisMulligan >= state.mulliganCount) return true
        snackbarMessage.value = "Finish your mulligan before playing cards."
        return false
    }

    private fun newPermanent(card: CardEntity, state: PlaytestHandState) = BattlefieldPermanent(
        instanceId = nextInstanceId++,
        card = card,
        tapped = PlaytestMana.entersTapped(card, state.battlefield.map { it.card }),
        enteredOnTurn = state.turnNumber
    )

    private fun payForCast(state: PlaytestHandState, card: CardEntity, tax: Int = 0): PlaytestMana.Payment? {
        val cost = PlaytestMana.cost(card.manaCost, tax)
        if (cost == null) {
            snackbarMessage.value = "Playtest cannot pay this card's mana cost automatically."
            return null
        }

        return PlaytestMana.pay(cost, state.manaPool, state.availableManaSources()).also {
            if (it == null) snackbarMessage.value = "Not enough available mana of the required types."
        }
    }

    private fun tapPaidSources(state: PlaytestHandState, payment: PlaytestMana.Payment?): List<BattlefieldPermanent> =
        state.battlefield.map { permanent ->
            if (payment != null && permanent.instanceId in payment.sources) permanent.copy(tapped = true) else permanent
        }

    fun togglePermanentTapped(instanceId: Long) {
        val current = playtestState.value
        playtestState.value = current.copy(
            battlefield = current.battlefield.map { perm ->
                if (perm.instanceId == instanceId) perm.copy(tapped = !perm.tapped) else perm
            }
        )
    }

    fun moveBattlefieldCard(instanceId: Long, destination: PlaytestDestination) {
        val current = playtestState.value
        val target = current.battlefield.firstOrNull { it.instanceId == instanceId } ?: return
        val remainingBoard = current.battlefield.filterNot { it.instanceId == instanceId }
        val card = target.card
        if (destination == PlaytestDestination.COMMAND_ZONE && card.id !in current.commanderIds) return

        playtestState.value = when (destination) {
            PlaytestDestination.GRAVEYARD -> current.copy(
                battlefield = remainingBoard,
                graveyard = listOf(card) + current.graveyard,
                actionLog = (listOf("Moved ${card.name} → Graveyard.") + current.actionLog).take(12)
            )
            PlaytestDestination.EXILE -> current.copy(
                battlefield = remainingBoard,
                exile = listOf(card) + current.exile,
                actionLog = (listOf("Exiled ${card.name}.") + current.actionLog).take(12)
            )
            PlaytestDestination.HAND -> {
                val nextHand = current.hand + card
                current.copy(
                    battlefield = remainingBoard,
                    hand = nextHand,
                    landsInHand = nextHand.count { "land" in it.typeLine.lowercase() },
                    actionLog = (listOf("Returned ${card.name} to hand.") + current.actionLog).take(12)
                )
            }
            PlaytestDestination.COMMAND_ZONE -> current.copy(
                battlefield = remainingBoard,
                commandZone = current.commandZone + card,
                actionLog = (listOf("Returned ${card.name} to Command Zone.") + current.actionLog).take(12)
            )
        }
    }

    fun returnFromGraveyardToHand(index: Int) {
        val current = playtestState.value
        if (index !in current.graveyard.indices) return
        val card = current.graveyard[index]
        val nextGy = current.graveyard.toMutableList().apply { removeAt(index) }
        val nextHand = current.hand + card
        playtestState.value = current.copy(
            graveyard = nextGy,
            hand = nextHand,
            landsInHand = nextHand.count { "land" in it.typeLine.lowercase() },
            actionLog = (listOf("Recurred ${card.name} to hand.") + current.actionLog).take(12)
        )
    }

    fun putOnBottomForMulligan(index: Int) {
        val current = playtestState.value
        if (index !in current.hand.indices || current.bottomedThisMulligan >= current.mulliganCount) return
        val card = current.hand[index]
        val nextHand = current.hand.toMutableList().apply { removeAt(index) }
        playtestState.value = current.copy(
            hand = nextHand,
            library = current.library + card,
            bottomedThisMulligan = current.bottomedThisMulligan + 1,
            landsInHand = nextHand.count { "land" in it.typeLine.lowercase() },
            actionLog = (listOf("Put ${card.name} on bottom of library.") + current.actionLog).take(12)
        )
    }

    fun scryTop() {
        val current = playtestState.value
        if (current.library.isEmpty()) {
            snackbarMessage.value = "Library is empty!"
            return
        }
        if (current.scryCard != null) return
        playtestState.value = current.copy(
            scryCard = current.library.first(),
            library = current.library.drop(1)
        )
    }

    fun resolveScry(keepOnTop: Boolean) {
        val current = playtestState.value
        val card = current.scryCard ?: return
        val nextLibrary = if (keepOnTop) listOf(card) + current.library else current.library + card
        val action = if (keepOnTop) "Scry 1: kept ${card.name} on top." else "Scry 1: sent ${card.name} to bottom."
        playtestState.value = current.copy(
            library = nextLibrary,
            scryCard = null,
            actionLog = (listOf(action) + current.actionLog).take(12)
        )
    }

    fun shuffleLibrary() {
        val current = playtestState.value
        if (current.scryCard != null) return
        if (current.library.size <= 1) return
        playtestState.value = current.copy(
            library = current.library.shuffled(),
            actionLog = (listOf("Shuffled library (${current.library.size} cards).") + current.actionLog).take(12)
        )
    }

    class Factory(
        private val deckId: Long,
        private val deckRepository: DeckRepository,
        private val cardRepository: CardRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DeckDetailViewModel(deckId, deckRepository, cardRepository) as T
        }
    }
}
