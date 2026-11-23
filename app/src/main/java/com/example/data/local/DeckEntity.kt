package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val format: String, // Commander, Modern, Standard, Pioneer, Pauper, Legacy, Casual
    val description: String = "",
    val commanderCardId: String? = null,
    val colorIdentity: String = "", // e.g. "U,R"
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
