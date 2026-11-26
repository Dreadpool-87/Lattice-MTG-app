package com.example

import com.example.GoldfishCard.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewGoldfishTest {
    @Test
    fun twoSoldiersTriggerSoulWardenTwice() {
        // Forge: Raise the Alarm creates two tokens; Soul Warden triggers for each other creature.
        val game = GoldfishGame(listOf(PLAINS, PLAINS, WARDEN, ALARM) + List(12) { PLAINS }, onPlay = true)
        game.advanceTo(1)
        assertEquals(20, game.snapshot().life)
        game.advanceTo(2)
        val result = game.snapshot()
        assertEquals(2, result.tokens)
        assertEquals(22, result.life)
        assertEquals(2, result.lifeGainEvents)
        assertEquals(2, result.firstLifeGainTurn)
        assertEquals("Soul Warden", result.firstLifeGainSource)
    }

    @Test
    fun lifeGainAmountTriggersPriestAndPridemateOnce() {
        val opening = listOf(PLAINS, PLAINS, SWAMP, PLAINS, PRIDEMATE, PRIEST, PLAINS)
        val game = GoldfishGame(opening + listOf(PLAINS, PLAINS, REVITALIZE, PLAINS), onPlay = true)
        game.advanceTo(4)
        val result = game.snapshot()
        assertEquals(23, result.life)
        assertEquals(1, result.lifeGainEvents)
        assertEquals(1, result.opponentLifeLostToTriggers)
        assertEquals(1, result.actionLog.count { "received a +1/+1 counter" in it })
        assertEquals(4, result.firstDrainTurn)
    }

    @Test
    fun tappedLandStillGainsLifeAndCannotPayOnFirstTurn() {
        val game = GoldfishGame(listOf(BARRENS, WARDEN, PLAINS) + List(10) { SWAMP }, onPlay = true)
        game.advanceTo(1)
        assertEquals(21, game.snapshot().life)
        assertFalse(game.snapshot().actionLog.any { "Cast Soul Warden" in it })
        game.advanceTo(2)
        assertTrue(game.snapshot().actionLog.any { "Turn 2 · Cast Soul Warden" in it })
    }

    @Test
    fun firstTurnDrawDependsOnSeatAndEmptyLibraryEndsTheRun() {
        val opening = List(7) { PLAINS }
        val starting = GoldfishGame(opening, onPlay = true)
        val drawing = GoldfishGame(opening, onPlay = false)
        starting.advanceTo(1)
        assertEquals(GoldfishOutcome.ACTIVE, starting.snapshot().outcome)
        drawing.advanceTo(24)
        assertEquals(GoldfishOutcome.LOST_ON_DRAW, drawing.snapshot().outcome)
        assertEquals(1, drawing.snapshot().turn)
        starting.advanceTo(24)
        assertEquals(GoldfishOutcome.LOST_ON_DRAW, starting.snapshot().outcome)
        assertEquals(2, starting.snapshot().turn)
    }

    @Test
    fun continuationPreservesRunsAndMatchesAnUninterruptedBatch() {
        assertEquals(60, previewGoldfishDeck.size)
        val batch = GoldfishBatch(421)
        val atSix = batch.advanceTo(6)
        assertEquals(10, atSix.size)
        assertEquals(5, atSix.count { it.onPlay })
        batch.advanceTo(12)
        batch.advanceTo(18)
        val atTwentyFour = batch.advanceTo(24)
        assertEquals(GoldfishBatch(421).advanceTo(24), atTwentyFour)
        atSix.zip(atTwentyFour).forEach { (early, late) ->
            assertEquals(early.actionLog, late.actionLog.take(early.actionLog.size))
            if (early.outcome != GoldfishOutcome.ACTIVE) assertEquals(early, late)
        }
        assertTrue(atTwentyFour.any { it.tokens > 0 })
        assertTrue(atTwentyFour.any { it.opponentLifeLostToTriggers > 0 })
    }
}
