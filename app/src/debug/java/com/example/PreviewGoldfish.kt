package com.example

import com.example.domain.PlaytestMana
import kotlin.random.Random

// Fixed Preview deck. Card actions are transcribed from the matching Forge scripts.
internal enum class GoldfishCard(
    val label: String,
    val cost: String = "",
    val power: Int? = null,
    val manaColors: List<String> = emptyList()
) {
    PLAINS("Plains", manaColors = listOf("W")),
    SWAMP("Swamp", manaColors = listOf("B")),
    BARRENS("Scoured Barrens", manaColors = listOf("W", "B")),
    WARDEN("Soul Warden", "{W}", 1),
    WELCOME("Ajani's Welcome", "{W}"),
    ORATOR("Impassioned Orator", "{1}{W}", 2),
    PRIDEMATE("Ajani's Pridemate", "{1}{W}", 2),
    PRIEST("Marauding Blight-Priest", "{2}{B}", 3),
    ALARM("Raise the Alarm", "{1}{W}"),
    EXHIBITION("Servo Exhibition", "{1}{W}"),
    REVITALIZE("Revitalize", "{1}{W}"),
    OVERSEER("Inspiring Overseer", "{2}{W}", 2),
    SOLDIER("Soldier token", power = 1),
    SERVO("Servo token", power = 1);

    val isLand get() = manaColors.isNotEmpty()
}

internal val goldfishCheckpoints = listOf(6, 12, 18, 24)

internal val previewGoldfishDeck = buildList {
    repeat(14) { add(GoldfishCard.PLAINS) }
    repeat(6) { add(GoldfishCard.SWAMP) }
    repeat(4) { add(GoldfishCard.BARRENS) }
    for (card in GoldfishCard.entries.subList(GoldfishCard.WARDEN.ordinal, GoldfishCard.SOLDIER.ordinal)) {
        repeat(4) { add(card) }
    }
}

internal enum class GoldfishOutcome { ACTIVE, WON, LOST_ON_DRAW }

internal data class GoldfishSnapshot(
    val onPlay: Boolean,
    val turn: Int,
    val outcome: GoldfishOutcome,
    val mulligans: Int,
    val life: Int,
    val opponentLife: Int,
    val lifeGained: Int,
    val lifeGainEvents: Int,
    val opponentLifeLostToTriggers: Int,
    val tokens: Int,
    val missedLandDrops: Int,
    val firstLifeGainTurn: Int?,
    val firstLifeGainSource: String?,
    val firstDrainTurn: Int?,
    val actionLog: List<String>
)

internal class GoldfishBatch(seed: Int) {
    private val random = Random(seed)
    private val games = List(10) { index ->
        GoldfishGame.fromSeed(random.nextInt(), onPlay = index < 5)
    }
    var checkpoint = 0
        private set

    fun advanceTo(target: Int): List<GoldfishSnapshot> {
        require(target in goldfishCheckpoints && target > checkpoint)
        games.forEach { it.advanceTo(target) }
        checkpoint = target
        return games.map { it.snapshot() }
    }
}

/**
 * Scripted 1v1 playtest for [previewGoldfishDeck]. Opponent passes and never blocks.
 * Own main phase uses one land drop, pays for spells, then attacks with eligible creatures.
 * The single active player orders their triggers in battlefield order.
 */
internal class GoldfishGame(
    deckOrder: List<GoldfishCard>,
    private val onPlay: Boolean,
    private val mulligans: Int = 0
) {
    private data class Permanent(
        val id: Long,
        val card: GoldfishCard,
        val enteredOn: Int,
        var tapped: Boolean,
        var counters: Int = 0
    )

    private sealed interface Trigger {
        data class Gain(val amount: Int, val source: GoldfishCard, val draw: Boolean = false) : Trigger
        data class Counter(val permanent: Permanent) : Trigger
        data object Drain : Trigger
    }

    private val hand = deckOrder.take(7).toMutableList()
    private val library = ArrayDeque(deckOrder.drop(7))
    private val battlefield = mutableListOf<Permanent>()
    private val log = mutableListOf<String>()
    private val triggers = ArrayDeque<Trigger>()
    private var nextId = 1L
    private var turn = 0
    private var outcome = GoldfishOutcome.ACTIVE
    private var life = 20
    private var opponentLife = 20
    private var lifeGainEvents = 0
    private var lifeLostToTriggers = 0
    private var tokens = 0
    private var missedLandDrops = 0
    private var firstLifeGainTurn: Int? = null
    private var firstLifeGainSource: String? = null
    private var firstDrainTurn: Int? = null

    init {
        // A London mulligan draws seven again, then puts one card per mulligan on the bottom.
        repeat(mulligans) { library.addLast(hand.removeAt(discardIndex())) }
        log += "Opening hand (${hand.size} cards, $mulligans mulligans): ${hand.joinToString { it.label }}"
    }

    fun advanceTo(target: Int) {
        while (turn < target && outcome == GoldfishOutcome.ACTIVE) {
            turn++
            battlefield.forEach { it.tapped = false }
            if (!onPlay || turn > 1) draw()
            if (outcome != GoldfishOutcome.ACTIVE) continue
            playLand()
            castAvailableSpells()
            if (outcome != GoldfishOutcome.ACTIVE) continue
            attack()
            while (hand.size > 7 && outcome == GoldfishOutcome.ACTIVE) {
                record("Discarded ${hand.removeAt(discardIndex()).label} during cleanup.")
            }
        }
    }

    fun snapshot() = GoldfishSnapshot(
        onPlay, turn, outcome, mulligans, life, opponentLife, life - 20, lifeGainEvents,
        lifeLostToTriggers, tokens, missedLandDrops, firstLifeGainTurn, firstLifeGainSource,
        firstDrainTurn, log.toList()
    )

    private fun discardIndex(): Int {
        val landCount = hand.count { it.isLand }
        return if (landCount >= 4) {
            hand.indexOfLast { it.isLand }
        } else {
            hand.indexOfLast { !it.isLand }.takeIf { it >= 0 } ?: hand.lastIndex
        }
    }

    private fun playLand() {
        // Keep an untapped play available when possible.
        val firstUntapped = hand.indexOfFirst { it.isLand && it != GoldfishCard.BARRENS }
        val index = firstUntapped.takeIf { it >= 0 } ?: hand.indexOf(GoldfishCard.BARRENS)
        if (index < 0) {
            missedLandDrops++
            record("Missed land drop.")
            return
        }
        val land = hand.removeAt(index)
        record("Played ${land.label}.")
        enter(listOf(land))
    }

    private fun castAvailableSpells() {
        while (outcome == GoldfishOutcome.ACTIVE) {
            val sources = battlefield.filter { !it.tapped && it.card.isLand }.map { land ->
                PlaytestMana.Source(land.id, land.card.manaColors.map(::listOf))
            }
            val cast = hand.withIndex().filterNot { it.value.isLand }.sortedBy { it.value.ordinal }
                .firstNotNullOfOrNull { (index, card) ->
                    val cost = checkNotNull(PlaytestMana.cost(card.cost))
                    PlaytestMana.pay(cost, emptyList(), sources)?.let { index to it }
                } ?: return
            battlefield.filter { it.id in cast.second.sources }.forEach { it.tapped = true }
            val card = hand.removeAt(cast.first)
            record("Cast ${card.label}.")
            when (card) {
                GoldfishCard.ALARM -> createTokens(GoldfishCard.SOLDIER)
                GoldfishCard.EXHIBITION -> createTokens(GoldfishCard.SERVO)
                GoldfishCard.REVITALIZE -> {
                    gain(Trigger.Gain(3, card, draw = true))
                    resolveTriggers()
                }
                else -> enter(listOf(card))
            }
        }
    }

    private fun createTokens(token: GoldfishCard) {
        tokens += 2
        record("Created 2 ${token.label}s.")
        enter(List(2) { token })
    }

    private fun enter(cards: List<GoldfishCard>) {
        val entered = cards.map { card ->
            Permanent(nextId++, card, turn, tapped = card == GoldfishCard.BARRENS)
        }
        battlefield += entered
        for (permanent in entered) {
            when (permanent.card) {
                GoldfishCard.BARRENS -> triggers.addLast(Trigger.Gain(1, permanent.card))
                GoldfishCard.OVERSEER -> triggers.addLast(Trigger.Gain(1, permanent.card, draw = true))
                else -> Unit
            }
            if (permanent.card.power == null) continue
            for (watcher in battlefield) {
                val observes = when (watcher.card) {
                    GoldfishCard.WARDEN, GoldfishCard.ORATOR -> watcher != permanent
                    GoldfishCard.WELCOME -> true
                    else -> false
                }
                if (observes) triggers.addLast(Trigger.Gain(1, watcher.card))
            }
        }
        resolveTriggers()
    }

    private fun resolveTriggers() {
        while (outcome == GoldfishOutcome.ACTIVE) {
            when (val trigger = triggers.removeFirstOrNull() ?: return) {
                is Trigger.Gain -> gain(trigger)
                is Trigger.Counter -> {
                    trigger.permanent.counters++
                    record("${trigger.permanent.card.label} received a +1/+1 counter.")
                }
                Trigger.Drain -> {
                    lifeLostToTriggers++
                    if (firstDrainTurn == null) firstDrainTurn = turn
                    loseOpponentLife(1, "Marauding Blight-Priest")
                }
            }
        }
        triggers.clear()
    }

    private fun gain(effect: Trigger.Gain) {
        life += effect.amount
        lifeGainEvents++
        if (firstLifeGainTurn == null) {
            firstLifeGainTurn = turn
            firstLifeGainSource = effect.source.label
        }
        record("${effect.source.label} gained ${effect.amount} life ($life total).")
        // Finish resolving the current ability before resolving its life-gain triggers.
        if (effect.draw) draw()
        if (outcome != GoldfishOutcome.ACTIVE) return
        for (permanent in battlefield.asReversed()) {
            when (permanent.card) {
                GoldfishCard.PRIDEMATE -> triggers.addFirst(Trigger.Counter(permanent))
                GoldfishCard.PRIEST -> triggers.addFirst(Trigger.Drain)
                else -> Unit
            }
        }
    }

    private fun draw() {
        val card = library.removeFirstOrNull()
        if (card == null) {
            outcome = GoldfishOutcome.LOST_ON_DRAW
            record("Lost trying to draw from an empty library.")
            return
        }
        hand += card
        record("Drew ${card.label}.")
    }

    private fun attack() {
        val attackers = battlefield.filter { it.card.power != null && !it.tapped && it.enteredOn < turn }
        if (attackers.isEmpty()) return
        attackers.forEach { it.tapped = true }
        val damage = attackers.sumOf { checkNotNull(it.card.power) + it.counters }
        loseOpponentLife(damage, "Combat")
    }

    private fun loseOpponentLife(amount: Int, source: String) {
        opponentLife -= amount
        record("$source took $amount life from opponent ($opponentLife left).")
        if (opponentLife <= 0) outcome = GoldfishOutcome.WON
    }

    private fun record(message: String) {
        log += "Turn $turn · $message"
    }

    companion object {
        fun fromSeed(seed: Int, onPlay: Boolean): GoldfishGame {
            val random = Random(seed)
            for (mulligans in 0..2) {
                val order = previewGoldfishDeck.shuffled(random)
                val lands = order.take(7).count { it.isLand }
                if (lands in 2..5 || mulligans == 2) return GoldfishGame(order, onPlay, mulligans)
            }
            error("Mulligan limit should always produce an opening hand")
        }
    }
}
