package com.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

private data class GoldfishCheckpoint(val turn: Int, val runs: List<GoldfishSnapshot>)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PreviewAutoPlaytest() {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf(6) }
    var batch by remember { mutableStateOf<GoldfishBatch?>(null) }
    var results by remember { mutableStateOf(emptyList<GoldfishCheckpoint>()) }
    var expandedRun by remember { mutableStateOf<Int?>(null) }

    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text("Automatic playtest · Preview")
    }
    if (!open) return

    val latest = results.lastOrNull()
    val lastTurn = latest?.turn ?: 0
    val canContinue = latest?.runs?.any { it.outcome == GoldfishOutcome.ACTIVE } == true
    val lastRunComplete = latest != null && (!canContinue || lastTurn == goldfishCheckpoints.last())

    AlertDialog(
        onDismissRequest = { if (!busy) open = false },
        title = { Text("Automatic playtest") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Preview Lifegain · Fixed 60-card deck. Five runs on the play and five on the draw. The opponent passes and never blocks.",
                    style = MaterialTheme.typography.bodySmall
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (turn in goldfishCheckpoints) {
                        FilterChip(
                            selected = target == turn,
                            enabled = !busy && turn > lastTurn && !lastRunComplete,
                            onClick = { target = turn },
                            label = { Text("Turn $turn") }
                        )
                    }
                }
                if (busy) Text("Playing ten hands…")
                for (checkpoint in results) {
                    val runs = checkpoint.runs
                    val lethal = runs.count { it.outcome == GoldfishOutcome.WON }
                    val gained = runs.count { it.firstLifeGainTurn != null }
                    val drained = runs.count { it.firstDrainTurn != null }
                    Text(
                        "By turn ${checkpoint.turn}: $lethal/10 lethal · $gained/10 gained life · $drained/10 drained",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (latest != null) {
                    HorizontalDivider()
                    Text("Runs at turn ${latest.turn}", style = MaterialTheme.typography.titleSmall)
                    latest.runs.forEachIndexed { index, run ->
                        val status = when (run.outcome) {
                            GoldfishOutcome.ACTIVE -> "Paused on turn ${run.turn}"
                            GoldfishOutcome.WON -> "Lethal on turn ${run.turn}"
                            GoldfishOutcome.LOST_ON_DRAW -> "Library exhausted on turn ${run.turn}"
                        }
                        TextButton(onClick = { expandedRun = if (expandedRun == index) null else index }) {
                            Text("Run ${index + 1} · ${if (run.onPlay) "Play" else "Draw"} · $status")
                        }
                        if (expandedRun == index) {
                            Text(
                                "Life ${run.life} / ${run.opponentLife} · gained ${run.lifeGained} in ${run.lifeGainEvents} events · " +
                                    "${run.tokens} tokens · ${run.opponentLifeLostToTriggers} drained · ${run.missedLandDrops} missed land drops",
                                style = MaterialTheme.typography.bodySmall
                            )
                            run.firstLifeGainSource?.let {
                                Text("First life gain: $it on turn ${run.firstLifeGainTurn}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(run.actionLog.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (!canContinue) Text("All ten runs have ended.")
                    TextButton(enabled = !busy, onClick = {
                        batch = null
                        results = emptyList()
                        target = 6
                        expandedRun = null
                    }) { Text("Set up new runs") }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && target > lastTurn && !lastRunComplete,
                onClick = {
                    val active = batch ?: GoldfishBatch(Random.nextInt()).also { batch = it }
                    val requestedTurn = target
                    busy = true
                    expandedRun = null
                    scope.launch {
                        try {
                            val newResults = withContext(Dispatchers.Default) {
                                goldfishCheckpoints.filter { it > lastTurn && it <= requestedTurn }.map { turn ->
                                    GoldfishCheckpoint(turn, active.advanceTo(turn))
                                }
                            }
                            results = results + newResults
                            target = goldfishCheckpoints.firstOrNull { it > requestedTurn } ?: requestedTurn
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text(if (latest == null) "Run 10" else "Continue to $target") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { open = false }) { Text("Close") } }
    )
}
