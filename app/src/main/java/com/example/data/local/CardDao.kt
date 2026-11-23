package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
               OR synergyTags LIKE '%' || :query || '%')
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

    @Query("SELECT * FROM cards WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findByExactNameIgnoreCase(name: String): CardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<CardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCard(card: CardEntity)

    @Query("UPDATE cards SET isBookmarked = :bookmarked WHERE id = :cardId")
    suspend fun updateBookmark(cardId: String, bookmarked: Boolean)

    @Query("DELETE FROM cards WHERE id = :cardId AND isCustom = 1")
    suspend fun deleteCustomCard(cardId: String)

    @Query(
        """
        DELETE FROM cards
        WHERE isCustom = 0
          AND id NOT LIKE 'local-%'
          AND id NOT IN (SELECT DISTINCT cardId FROM deck_entries)
        """
    )
    suspend fun clearUnusedRemoteCache(): Int
}
