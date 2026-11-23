package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {
    @Transaction
    @Query("SELECT * FROM decks ORDER BY updatedAt DESC")
    fun observeAllDecksWithEntries(): Flow<List<DeckWithEntries>>

    @Transaction
    @Query("SELECT * FROM decks WHERE id = :deckId LIMIT 1")
    fun observeDeckWithEntries(deckId: Long): Flow<DeckWithEntries?>

    @Transaction
    @Query("SELECT * FROM decks WHERE id = :deckId LIMIT 1")
    suspend fun getDeckWithEntriesOnce(deckId: Long): DeckWithEntries?

    @Query("SELECT COUNT(*) FROM decks")
    suspend fun getDeckCount(): Int

    @Query("SELECT COUNT(*) FROM decks")
    fun observeDeckCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM deck_entries")
    fun observeTotalDeckCardsCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeck(deck: DeckEntity): Long

    @Update
    suspend fun updateDeck(deck: DeckEntity)

    @Query("UPDATE decks SET updatedAt = :timestamp WHERE id = :deckId")
    suspend fun touchDeck(deckId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM decks WHERE id = :deckId")
    suspend fun deleteDeckById(deckId: Long)

    @Query("SELECT * FROM deck_entries WHERE deckId = :deckId AND cardId = :cardId AND zone = :zone LIMIT 1")
    suspend fun getEntry(deckId: Long, cardId: String, zone: String): DeckEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: DeckEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<DeckEntryEntity>)

    @Query("DELETE FROM deck_entries WHERE deckId = :deckId AND cardId = :cardId AND zone = :zone")
    suspend fun deleteEntry(deckId: Long, cardId: String, zone: String)

    @Query("DELETE FROM deck_entries WHERE id = :entryId")
    suspend fun deleteEntryById(entryId: Long)
}
