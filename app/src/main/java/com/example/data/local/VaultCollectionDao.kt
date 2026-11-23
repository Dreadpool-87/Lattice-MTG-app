package com.example.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "vault_collection_entries",
    primaryKeys = ["listName", "cardId"],
    foreignKeys = [
        ForeignKey(entity = CardEntity::class, parentColumns = ["id"], childColumns = ["cardId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("cardId")]
)
data class VaultCollectionEntry(val listName: String, val cardId: String, val quantity: Int)

data class VaultCollectionCard(
    @Embedded val entry: VaultCollectionEntry,
    @Relation(parentColumn = "cardId", entityColumn = "id") val card: CardEntity
)

@Dao
interface VaultCollectionDao {
    @Transaction
    @Query("SELECT * FROM vault_collection_entries ORDER BY listName COLLATE NOCASE, listName, cardId")
    fun observeCards(): Flow<List<VaultCollectionCard>>

    @Query("SELECT listName FROM vault_collection_entries WHERE listName = :name COLLATE NOCASE LIMIT 1")
    suspend fun findListName(name: String): String?

    @Query("DELETE FROM vault_collection_entries WHERE listName = :name")
    suspend fun deleteEntries(name: String)

    @Insert
    suspend fun insertEntries(entries: List<VaultCollectionEntry>)

    @Transaction
    suspend fun saveSnapshot(name: String, quantities: Map<String, Int>, replace: Boolean, complete: Boolean): Boolean {
        if (quantities.isEmpty()) return false
        val existing = findListName(name)
        if (existing != null && (!replace || !complete)) return false
        if (existing != null) deleteEntries(existing)
        insertEntries(quantities.map { (id, quantity) -> VaultCollectionEntry(existing ?: name, id, quantity) })
        return true
    }
}
