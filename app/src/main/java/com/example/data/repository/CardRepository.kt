package com.example.data.repository

import com.example.data.local.CardDao
import com.example.data.local.CardEntity
import com.example.data.local.StarterCards
import com.example.data.remote.ScryfallApi
import kotlinx.coroutines.flow.Flow
import java.util.UUID

sealed class SyncResult {
    data class Success(val addedOrUpdated: Int, val queryLabel: String) : SyncResult()
    data class OfflineOrError(val message: String) : SyncResult()
}

class CardRepository(
    private val cardDao: CardDao,
    private val scryfallApi: ScryfallApi,
    private val networkMonitor: NetworkMonitor
) {
    val allCards: Flow<List<CardEntity>> = cardDao.observeAllCards()
    val cardCount: Flow<Int> = cardDao.observeCardCount()

    fun searchLocalCards(query: String): Flow<List<CardEntity>> =
        cardDao.searchCards(query.trim())

    suspend fun toggleBookmark(cardId: String, current: Boolean) {
        cardDao.updateBookmark(cardId, !current)
    }

    suspend fun createCustomCard(
        name: String,
        manaCost: String,
        cmc: Double,
        typeLine: String,
        oracleText: String,
        colors: List<String>,
        rarity: String,
        power: String?,
        toughness: String?,
        synergyTags: List<String>
    ): CardEntity {
        val cleanColors = colors.filter { it in listOf("W", "U", "B", "R", "G") }.joinToString(",")
        val card = CardEntity(
            id = "custom-${UUID.randomUUID()}",
            name = name.trim(),
            manaCost = manaCost.trim(),
            cmc = cmc,
            typeLine = typeLine.trim().ifEmpty { "Artifact" },
            oracleText = oracleText.trim(),
            colors = cleanColors,
            colorIdentity = cleanColors,
            rarity = rarity.lowercase(),
            setCode = "CUSTOM",
            setName = "Lattice Offline Forge",
            power = power?.trim()?.takeIf { it.isNotEmpty() },
            toughness = toughness?.trim()?.takeIf { it.isNotEmpty() },
            keywords = "",
            synergyTags = synergyTags.joinToString(","),
            legalFormats = "Commander,Casual",
            isCustom = true,
            isBookmarked = true,
            cachedAt = System.currentTimeMillis()
        )
        cardDao.upsertCard(card)
        return card
    }

    suspend fun deleteCustomCard(cardId: String) {
        cardDao.deleteCustomCard(cardId)
    }

    suspend fun restoreStarterCatalog() {
        cardDao.insertAll(StarterCards.cards)
    }

    suspend fun clearUnusedRemoteCache(): Int {
        return cardDao.clearUnusedRemoteCache()
    }

    suspend fun syncFromScryfall(query: String, offlineOnlyMode: Boolean): SyncResult {
        if (offlineOnlyMode) {
            return SyncResult.OfflineOrError("Offline-only mode is enabled. Disable it in Tabletop settings to sync Scryfall.")
        }
        if (!networkMonitor.isCurrentlyOnline()) {
            return SyncResult.OfflineOrError("Device is offline. Searching local Room vault instead.")
        }
        val cleanQuery = query.trim().ifEmpty { "f:commander order:edhrec" }
        return try {
            val response = scryfallApi.searchCards(query = cleanQuery)
            val entities = response.data.take(40).map { it.toCardEntity() }
            if (entities.isEmpty()) {
                SyncResult.OfflineOrError("No Scryfall cards matched \"$cleanQuery\".")
            } else {
                cardDao.insertAll(entities)
                SyncResult.Success(entities.size, cleanQuery)
            }
        } catch (e: Exception) {
            SyncResult.OfflineOrError("Could not reach Scryfall (${e.localizedMessage ?: "network error"}). Using local Room cache.")
        }
    }

    suspend fun resolveOrCreateCardByName(name: String, offlineOnlyMode: Boolean): CardEntity {
        val existing = cardDao.findByExactNameIgnoreCase(name.trim())
        if (existing != null) return existing

        if (!offlineOnlyMode && networkMonitor.isCurrentlyOnline()) {
            val fetched = runCatching { scryfallApi.getCardNamed(name.trim()).toCardEntity() }.getOrNull()
            if (fetched != null) {
                cardDao.upsertCard(fetched)
                return fetched
            }
        }

        // Offline fallback stub so imported decklists never lose cards even without internet!
        val offlineCard = CardEntity(
            id = "offline-${name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-")}",
            name = name.trim(),
            manaCost = "",
            cmc = 0.0,
            typeLine = "Imported Card (Offline Entry)",
            oracleText = "Imported offline. Tap Sync Scryfall when online to populate full oracle rules and art.",
            colors = "",
            colorIdentity = "",
            rarity = "common",
            setCode = "LOCAL",
            setName = "Offline Import",
            synergyTags = "Imported",
            legalFormats = "Commander,Casual",
            isCustom = true,
            isBookmarked = false
        )
        cardDao.upsertCard(offlineCard)
        return offlineCard
    }
}
