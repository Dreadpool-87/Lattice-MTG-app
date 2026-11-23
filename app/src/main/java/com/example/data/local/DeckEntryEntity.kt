package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "deck_entries",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["deckId"]),
        Index(value = ["cardId"]),
        Index(value = ["deckId", "cardId", "zone"], unique = true)
    ]
)
data class DeckEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val deckId: Long,
    val cardId: String,
    val quantity: Int = 1,
    val zone: String = DeckZones.MAINBOARD
)

object DeckZones {
    const val COMMANDER = "COMMANDER"
    const val MAINBOARD = "MAINBOARD"
    const val SIDEBOARD = "SIDEBOARD"
    const val MAYBEBOARD = "MAYBEBOARD"

    val allZones = listOf(COMMANDER, MAINBOARD, SIDEBOARD, MAYBEBOARD)
}
