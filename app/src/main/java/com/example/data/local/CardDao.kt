package com.example.data.local

import androidx.room.Dao
import com.example.data.repository.ArtworkCard
import com.example.data.repository.ArtworkOption
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT id, scryfallId, oracleId, setCode, collectorNumber, lang, imageUri, cardFacesJson, name FROM cards WHERE scryfallId != ''")
    suspend fun getArtworkCards(): List<ArtworkCard>

    @Query("""
        SELECT LOWER(setCode) AS id, MIN(setName) || ' (' || UPPER(setCode) || ')' AS label
        FROM cards WHERE setCode != '' AND scryfallId != ''
        GROUP BY LOWER(setCode) ORDER BY label COLLATE NOCASE
    """)
    suspend fun getArtworkSets(): List<ArtworkOption>

    @Query("UPDATE cards SET imageUri = :uri, cardFacesJson = :faces WHERE id = :cardId")
    suspend fun updateArtwork(cardId: String, uri: String, faces: String)

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
        AND (:type = 'All' OR instr(LOWER(typeLine), LOWER(:type)) > 0)
        AND (:rarity = 'All' OR rarity = :rarity COLLATE NOCASE)
        AND (:bookmarkedOnly = 0 OR isBookmarked = 1)
        AND (:maxCmc IS NULL OR cmc <= :maxCmc)
        AND (:commanderOnly = 0 OR (isCustom = 0 AND scryfallId != ''
            AND instr(',' || lower(replace(legalFormats, ' ', '')) || ',', ',commander,') > 0))
        AND (:multiColorOnly = 0 OR (
            (instr(',' || replace(cards.colors, ' ', '') || ',', ',W,') > 0) +
            (instr(',' || replace(cards.colors, ' ', '') || ',', ',U,') > 0) +
            (instr(',' || replace(cards.colors, ' ', '') || ',', ',B,') > 0) +
            (instr(',' || replace(cards.colors, ' ', '') || ',', ',R,') > 0) +
            (instr(',' || replace(cards.colors, ' ', '') || ',', ',G,') > 0)
        ) >= 2)
        AND (:colors = '' OR EXISTS (
            SELECT 1 FROM (
                SELECT 'W' AS symbol UNION ALL SELECT 'U' UNION ALL SELECT 'B'
                UNION ALL SELECT 'R' UNION ALL SELECT 'G' UNION ALL SELECT 'C'
            ) AS mana
            WHERE instr(:colors, mana.symbol) > 0
              AND ((mana.symbol = 'C' AND trim(replace(colorIdentity, ',', '')) = '')
                   OR (mana.symbol != 'C' AND
                       instr(',' || replace(colorIdentity, ' ', '') || ',', ',' || mana.symbol || ',') > 0))
        ))
        ORDER BY isBookmarked DESC, name ASC, id ASC
        LIMIT :limit
        """
    )
    fun searchCards(
        query: String,
        type: String,
        rarity: String,
        bookmarkedOnly: Boolean,
        maxCmc: Int?,
        colors: String,
        limit: Int,
        multiColorOnly: Boolean = false,
        commanderOnly: Boolean = false
    ): Flow<List<CardEntity>>

    @Query("SELECT COUNT(*) FROM cards")
    fun observeCardCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun getCardCount(): Int

    @Query("SELECT * FROM cards WHERE id = :id LIMIT 1")
    suspend fun getCardById(id: String): CardEntity?

    @Query("SELECT * FROM cards WHERE id = :id LIMIT 1")
    fun observeCardById(id: String): Flow<CardEntity?>

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

    @Query("""
        DELETE FROM cards WHERE id = :cardId AND isCustom = 1
        AND NOT EXISTS (SELECT 1 FROM deck_entries WHERE cardId = :cardId)
        AND NOT EXISTS (SELECT 1 FROM vault_collection_entries WHERE cardId = :cardId)
    """)
    suspend fun deleteUnusedCustomCard(cardId: String): Int
}
