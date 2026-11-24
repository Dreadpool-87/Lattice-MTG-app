package com.example.data.repository

import com.example.data.local.CardDao
import com.example.data.local.CardEntity
import com.example.data.local.CardMetadataStatus
import com.example.data.local.StarterCards
import com.example.data.remote.ScryfallApi
import com.example.domain.CardJsonCodec
import com.example.domain.CardRulingInfo
import com.example.domain.DelverCsvImporter
import com.example.domain.DelverImportReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
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
            legalFormats = "Custom (Unchecked)",
            isCustom = true,
            isBookmarked = true,
            cachedAt = System.currentTimeMillis(),
            metadataStatus = CardMetadataStatus.CUSTOM
        )
        cardDao.upsertPreservingState(card)
        return card
    }

    suspend fun deleteCustomCard(cardId: String) {
        cardDao.deleteCustomCard(cardId)
    }

    suspend fun restoreStarterCatalog() {
        cardDao.upsertAllPreservingState(StarterCards.cards)
    }

    suspend fun importDelverCsv(csvContent: String, offlineOnlyMode: Boolean): DelverImportReport {
        val parsedRows = withContext(Dispatchers.Default) { DelverCsvImporter.parseCsv(csvContent) }
        val canFetchOnline = !offlineOnlyMode && networkMonitor.isCurrentlyOnline()
        return DelverCsvImporter.matchAndImportRows(
            rows = parsedRows,
            cardDao = cardDao,
            scryfallApi = scryfallApi,
            canFetchOnline = canFetchOnline
        )
    }

    suspend fun getPrintingVariants(card: CardEntity): List<CardEntity> {
        val byOracle = card.oracleId.takeIf { it.isNotBlank() }?.let { cardDao.findByOracleId(it) }.orEmpty()
        val byName = cardDao.findAllByExactNameIgnoreCase(card.name)
        return (byOracle + byName).distinctBy { it.id }
    }

    suspend fun fetchAndCacheArtwork(card: CardEntity, offlineOnlyMode: Boolean): String? {
        cardDao.getCardById(card.id)?.imageUri?.let { return it }
        if (card.isCustom || offlineOnlyMode || !networkMonitor.isCurrentlyOnline()) return null

        return try {
            val printing = if (card.scryfallId.isNotBlank()) {
                scryfallApi.getCardByScryfallId(card.scryfallId).toCardEntity()
            } else {
                val setCode = card.setCode.lowercase().takeUnless { it.isBlank() || it == "local" }
                scryfallApi.getCardNamedExact(card.name, setCode).toCardEntity()
            }
            val image = printing.imageUri?.takeIf { it.startsWith("https://") }
            if (image != null) cardDao.updateArtwork(card.id, image)
            image
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchAndCacheRulings(card: CardEntity, offlineOnlyMode: Boolean): List<CardRulingInfo> {
        val cached = CardJsonCodec.decodeRulings(card.rulingsJson)
        if (cached.isNotEmpty()) return cached
        val targetScryfallId = card.scryfallId.takeIf { it.isNotBlank() } ?: return emptyList()
        if (offlineOnlyMode || !networkMonitor.isCurrentlyOnline()) return emptyList()
        return runCatching {
            val response = scryfallApi.getCardRulings(targetScryfallId)
            val rulings = response.data.mapNotNull { it.toCardRulingInfo() }
            if (rulings.isNotEmpty()) {
                cardDao.updateRulings(card.id, CardJsonCodec.encodeRulings(rulings))
            }
            rulings
        }.getOrDefault(emptyList())
    }

    suspend fun syncFromScryfall(query: String, offlineOnlyMode: Boolean): SyncResult {
        if (offlineOnlyMode) {
            return SyncResult.OfflineOrError("Offline-only mode is enabled. Disable it in Settings to sync Scryfall.")
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
                cardDao.upsertAllPreservingState(entities)
                SyncResult.Success(entities.size, cleanQuery)
            }
        } catch (e: Exception) {
            SyncResult.OfflineOrError("Could not reach Scryfall (${e.localizedMessage ?: "network error"}). Using local Room cache.")
        }
    }

    suspend fun resolveOrCreateCardByName(name: String, offlineOnlyMode: Boolean): CardEntity {
        val trimmed = name.trim()
        val existing = cardDao.findByExactNameIgnoreCase(trimmed)
        if (existing != null && existing.metadataStatus != CardMetadataStatus.UNRESOLVED) {
            return existing
        }

        if (!offlineOnlyMode && networkMonitor.isCurrentlyOnline()) {
            val fetched = runCatching { scryfallApi.getCardNamedExact(trimmed).toCardEntity() }.getOrNull()
            if (fetched != null) {
                return cardDao.upsertPreservingState(fetched)
            }
        }

        if (existing != null) return existing

        // Unresolved offline placeholder: keep legality & metadata explicitly Unknown.
        val offlineCard = CardEntity(
            id = "offline-${trimmed.lowercase().replace(Regex("[^a-z0-9]+"), "-")}",
            name = trimmed,
            manaCost = "",
            cmc = 0.0,
            typeLine = "Unresolved Card (Offline Entry)",
            oracleText = "Unresolved offline entry. Sync with Scryfall when online to resolve oracle text, printing details, and legality.",
            colors = "",
            colorIdentity = "",
            rarity = "unknown",
            setCode = "LOCAL",
            setName = "Unresolved Offline Import",
            synergyTags = "Unresolved",
            legalFormats = "Unknown",
            isCustom = false,
            isBookmarked = false,
            metadataStatus = CardMetadataStatus.UNRESOLVED
        )
        return cardDao.upsertPreservingState(offlineCard)
    }
}
