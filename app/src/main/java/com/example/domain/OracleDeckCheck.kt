package com.example.domain

import com.example.data.local.CardEntity
import com.example.data.local.CardMetadataStatus
import com.example.data.local.DeckWithEntries

internal data class OracleDeckCheck(
    val commander: Boolean,
    val cards: Int,
    val lands: Int,
    val copyWarning: Boolean,
    val identity: String,
    val offColorNames: List<String>
) {
    companion object {
        fun analyze(deck: DeckWithEntries): OracleDeckCheck {
            val summary = DeckAnalytics.analyze(deck)
            val commander = deck.deck.format.trim().equals("Commander", ignoreCase = true)
            val leaders = deck.commanderEntries.map { it.card }
            val canCheck = commander && leaders.isNotEmpty() && leaders.all { it.hasVerifiedIdentity() }
            val allowedColors = leaders.flatMap { it.identityColors() }.toSet()
            val mainboard = deck.mainboardEntries.map { it.card }
            val offColor = if (canCheck) {
                mainboard.filter { it.hasVerifiedIdentity() && !allowedColors.containsAll(it.identityColors()) }
                    .map { it.name }.distinct()
            } else emptyList()
            val identity = when {
                !commander -> "not_applicable"
                offColor.isNotEmpty() -> "flagged"
                !canCheck || mainboard.any { !it.hasVerifiedIdentity() } -> "incomplete"
                else -> "no_mismatch"
            }
            return OracleDeckCheck(
                commander, summary.playableCount, summary.landCount,
                summary.validationNotes.any { it.copyLimitExceeded }, identity, offColor
            )
        }
    }
}

private fun CardEntity.identityColors() = colorIdentity.split(",")
    .map { it.trim() }.filter { it.isNotEmpty() }

private fun CardEntity.hasVerifiedIdentity() =
    !isCustom && metadataStatus == CardMetadataStatus.VERIFIED &&
        oracleId.isNotBlank() && identityColors().all { it in setOf("W", "U", "B", "R", "G") }
