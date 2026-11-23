package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY isBookmarked DESC, name ASC")
    fun observeAllCards(): Flow<List<CardEntity>>

    @Query(
        """
        SELECT * FROM cards
        WHERE (:query = '' OR name LIKE '%' || :query || '%'
               OR typeLine LIKE '%' || :query || '%'
               OR oracleText LIKE '%' || :query || '%'
               OR keywords LIKE '%' || :query || '%'
               OR synergyTags LIKE '%' || :query || '%'
               OR setCode LIKE '%' || :query || '%'
               OR collectorNumber LIKE '%' || :query || '%')
        ORDER BY isBookmarked DESC, name ASC
        """
    )
    fun searchCards(query: String): Flow<List<CardEntity>>

    @Query("SELECT COUNT(*) FROM cards")
    fun observeCardCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun getCardCount(): Int

    @Query("SELECT * FROM cards WHERE id = :id LIMIT 1")
    suspend fun getCardById(id: String): CardEntity?

    @Query("SELECT * FROM cards WHERE LOWER(scryfallId) = LOWER(:scryfallId) LIMIT 1")
    suspend fun findByScryfallId(scryfallId: String): CardEntity?

    @Query(
        """
        SELECT * FROM cards
        WHERE UPPER(setCode) = UPPER(:setCode)
          AND LOWER(collectorNumber) = LOWER(:collectorNumber)
          AND LOWER(lang) = LOWER(:lang)
        LIMIT 1
        """
    )
    suspend fun findByPrintingExact(setCode: String, collectorNumber: String, lang: String): CardEntity?

    @Query(
        """
        SELECT * FROM cards
        WHERE UPPER(setCode) = UPPER(:setCode)
          AND LOWER(collectorNumber) = LOWER(:collectorNumber)
        """
    )
    suspend fun findBySetAndCollector(setCode: String, collectorNumber: String): List<CardEntity>

    @Query("SELECT * FROM cards WHERE LOWER(oracleId) = LOWER(:oracleId) ORDER BY setCode ASC, collectorNumber ASC")
    suspend fun findByOracleId(oracleId: String): List<CardEntity>

    @Query("SELECT * FROM cards WHERE LOWER(name) = LOWER(:name) ORDER BY CASE WHEN metadataStatus = 'VERIFIED' THEN 0 ELSE 1 END, setCode ASC")
    suspend fun findAllByExactNameIgnoreCase(name: String): List<CardEntity>

    @Query("SELECT * FROM cards WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findByExactNameIgnoreCase(name: String): CardEntity?

    @Query("SELECT * FROM cards WHERE id LIKE 'offline-%' AND metadataStatus = 'UNRESOLVED' AND scryfallId = '' AND LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findOfflinePlaceholderByName(name: String): CardEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(card: CardEntity): Long

    @Update
    suspend fun updateCardInPlace(card: CardEntity)

    @Transaction
    suspend fun upsertPreservingState(card: CardEntity): CardEntity {
        val existing = getCardById(card.id)
            ?: card.scryfallId.takeIf { it.isNotBlank() }?.let { findByScryfallId(it) }
            ?: if (card.metadataStatus == CardMetadataStatus.VERIFIED && card.scryfallId.isNotBlank()) {
                findOfflinePlaceholderByName(card.name)
            } else null

        return if (existing == null) {
            insertIgnore(card)
            card
        } else {
            val isOfflineUpgrade = existing.metadataStatus == CardMetadataStatus.UNRESOLVED
            val merged = card.copy(
                id = existing.id,
                isBookmarked = existing.isBookmarked,
                isCustom = if (isOfflineUpgrade) card.isCustom else existing.isCustom,
                imageUri = card.imageUri ?: existing.imageUri,
                rulingsJson = card.rulingsJson.ifBlank { existing.rulingsJson },
                cardFacesJson = card.cardFacesJson.ifBlank { existing.cardFacesJson },
                oracleId = card.oracleId.ifBlank { existing.oracleId },
                scryfallId = card.scryfallId.ifBlank { existing.scryfallId },
                collectorNumber = card.collectorNumber.ifBlank { existing.collectorNumber },
                lang = card.lang.ifBlank { existing.lang }
            )
            updateCardInPlace(merged)
            merged
        }
    }

    @Transaction
    suspend fun upsertAllPreservingState(cards: List<CardEntity>): List<CardEntity> {
        return cards.map { upsertPreservingState(it) }
    }

    @Query("SELECT * FROM cards WHERE scryfallId = :id LIMIT 1")
    suspend fun findByCanonicalScryfallId(id: String): CardEntity?

    suspend fun storeCatalogPrinting(card: CardEntity) {
        val existing = getCardById(card.id) ?: findByCanonicalScryfallId(card.scryfallId)
        if (existing == null) {
            insertIgnore(card)
        } else {
            updateCardInPlace(card.copy(
                id = existing.id,
                rarity = existing.rarity.takeUnless { it == "unknown" } ?: card.rarity,
                isBookmarked = existing.isBookmarked,
                imageUri = existing.imageUri,
                rulingsJson = existing.rulingsJson,
                cardFacesJson = existing.cardFacesJson.ifBlank { card.cardFacesJson }
            ))
        }
    }

    @Query("UPDATE cards SET isBookmarked = :bookmarked WHERE id = :cardId")
    suspend fun updateBookmark(cardId: String, bookmarked: Boolean)

    @Query("UPDATE cards SET rulingsJson = :rulingsJson WHERE id = :cardId")
    suspend fun updateRulings(cardId: String, rulingsJson: String)

    @Query("DELETE FROM cards WHERE id = :cardId AND isCustom = 1")
    suspend fun deleteCustomCard(cardId: String)
}
