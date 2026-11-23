package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object CardMetadataStatus {
    const val VERIFIED = "VERIFIED"
    const val UNRESOLVED = "UNRESOLVED"
    const val CUSTOM = "CUSTOM"
}

@Entity(
    tableName = "cards",
    indices = [
        Index(value = ["name"]),
        Index(value = ["typeLine"]),
        Index(value = ["colorIdentity"]),
        Index(value = ["cmc"]),
        Index(value = ["oracleId"]),
        Index(value = ["scryfallId"]),
        Index(value = ["setCode", "collectorNumber", "lang"])
    ]
)
data class CardEntity(
    @PrimaryKey val id: String,
    val name: String,
    val manaCost: String,
    val cmc: Double,
    val typeLine: String,
    val oracleText: String,
    val colors: String,          // e.g. "U,R" or ""
    val colorIdentity: String,   // e.g. "W,U,B,R,G" or ""
    val rarity: String,          // common, uncommon, rare, mythic
    val setCode: String,
    val setName: String,
    val power: String? = null,
    val toughness: String? = null,
    val loyalty: String? = null,
    val keywords: String = "",
    val synergyTags: String = "",
    val legalFormats: String = "Commander,Legacy,Vintage",
    val imageUri: String? = null,
    val isCustom: Boolean = false,
    val isBookmarked: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis(),
    val oracleId: String = "",
    val scryfallId: String = "",
    val collectorNumber: String = "",
    val lang: String = "en",
    val cardFacesJson: String = "",
    val rulingsJson: String = "",
    val metadataStatus: String = CardMetadataStatus.VERIFIED
)
