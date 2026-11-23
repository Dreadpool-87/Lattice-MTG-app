package com.example.domain

import com.example.data.local.CardDao
import com.example.data.local.CardEntity
import com.example.data.local.CardMetadataStatus
import com.example.data.remote.ScryfallApi
import kotlinx.coroutines.CancellationException

data class DelverCsvRow(
    val rowNumber: Int,
    val rawLine: String,
    val quantity: Int,
    val name: String?,
    val setCode: String?,
    val collectorNumber: String?,
    val language: String?,
    val scryfallId: String?,
    val oracleId: String?
)

enum class CsvRowMatchStatus {
    VERIFIED,
    CONFLICT,
    UNRESOLVED
}

data class DelverRowResolution(
    val row: DelverCsvRow,
    val status: CsvRowMatchStatus,
    val resolvedCard: CardEntity? = null,
    val mismatchReasons: List<String> = emptyList(),
    val statusDetail: String = ""
)

data class DelverImportReport(
    val resolutions: List<DelverRowResolution>,
    val verifiedRows: List<DelverRowResolution>,
    val conflictRows: List<DelverRowResolution>,
    val unresolvedRows: List<DelverRowResolution>,
    val savedCardsCount: Int,
    val totalVerifiedCopies: Int,
    val deckIdCreated: Long? = null
)

object DelverCsvImporter {

    private val parenSetRegex = Regex(""".*\(([A-Za-z0-9]{2,8})\)\s*$""")
    private val trailingSetInNameRegex = Regex("""^(.+?)\s+\(([A-Za-z0-9]{2,8})\)\s*$""")

    private val languageAliases = mapOf(
        "en" to "en",
        "eng" to "en",
        "english" to "en",
        "ja" to "ja",
        "jp" to "ja",
        "jpn" to "ja",
        "japanese" to "ja",
        "de" to "de",
        "deu" to "de",
        "ger" to "de",
        "german" to "de",
        "fr" to "fr",
        "fra" to "fr",
        "fre" to "fr",
        "french" to "fr",
        "es" to "es",
        "spa" to "es",
        "spanish" to "es",
        "it" to "it",
        "ita" to "it",
        "italian" to "it",
        "pt" to "pt",
        "por" to "pt",
        "portuguese" to "pt",
        "ko" to "ko",
        "kor" to "ko",
        "korean" to "ko",
        "ru" to "ru",
        "rus" to "ru",
        "russian" to "ru",
        "zhs" to "zhs",
        "cs" to "zhs",
        "simplified chinese" to "zhs",
        "chinese simplified" to "zhs",
        "zht" to "zht",
        "ct" to "zht",
        "traditional chinese" to "zht",
        "chinese traditional" to "zht",
        "ph" to "ph",
        "phyrexian" to "ph"
    )

    fun normalizeSetCode(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val parenMatch = parenSetRegex.matchEntire(trimmed)
        if (parenMatch != null) {
            return parenMatch.groupValues[1].uppercase()
        }
        val unwrapped = trimmed.removePrefix("(").removeSuffix(")").trim()
        if (unwrapped.matches(Regex("""^[A-Za-z0-9]{2,8}$"""))) {
            return unwrapped.uppercase()
        }
        return unwrapped.uppercase()
    }

    fun normalizeCollectorNumber(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty().removePrefix("#").trim()
        return trimmed.takeIf { it.isNotEmpty() }
    }

    fun normalizeLanguage(raw: String?): String? {
        val trimmed = raw?.trim()?.lowercase().orEmpty()
        if (trimmed.isEmpty()) return null
        return languageAliases[trimmed] ?: trimmed
    }

    fun normalizeUuid(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        return trimmed.takeIf { it.isNotEmpty() }
    }

    fun parseCsv(csvContent: String): List<DelverCsvRow> {
        val cleaned = csvContent.removePrefix("\uFEFF")
        val records = parseRfc4180Records(cleaned)
        if (records.isEmpty()) return emptyList()

        val header = records.first().map { it.trim().lowercase() }
        val colQty = findHeaderIndex(header, setOf("quantity", "qty", "count", "reg qty", "regular quantity", "amount"))
        val colName = findHeaderIndex(header, setOf("name", "card", "card name"))
        val colSet = findHeaderIndex(header, setOf("edition (code)", "edition code", "set code", "edition", "set"))
        val colNumber = findHeaderIndex(header, setOf("collector's number", "collector number", "card number", "number", "cn"))
        val colLang = findHeaderIndex(header, setOf("language", "lang", "language code"))
        val colScryfallId = findHeaderIndex(header, setOf("scryfall id", "scryfall_id", "printing id", "scryfall printing id", "id"))
        val colOracleId = findHeaderIndex(header, setOf("oracle id", "oracle_id"))

        val hasHeaderRow = colName >= 0 || colScryfallId >= 0 || colSet >= 0 || colNumber >= 0
        val dataRecords = if (hasHeaderRow) records.drop(1) else records

        return dataRecords.mapIndexedNotNull { idx, cols ->
            if (cols.all { it.isBlank() }) return@mapIndexedNotNull null
            val rowNum = if (hasHeaderRow) idx + 2 else idx + 1

            val rawQty = colQty.takeIf { it in cols.indices }?.let { cols[it].trim() }
            val qty = rawQty?.toIntOrNull()?.coerceAtLeast(0) ?: 1

            val rawNameField = if (hasHeaderRow) {
                colName.takeIf { it in cols.indices }?.let { cols[it].trim() }
            } else {
                cols.firstOrNull()?.trim()
            }

            val rawSetField = colSet.takeIf { it in cols.indices }?.let { cols[it].trim() }
            var extractedSetFromName: String? = null
            val cleanName = rawNameField?.let { n ->
                val parenInName = trailingSetInNameRegex.matchEntire(n)
                if (parenInName != null && rawSetField.isNullOrBlank()) {
                    extractedSetFromName = parenInName.groupValues[2].uppercase()
                    parenInName.groupValues[1].trim()
                } else {
                    n.takeIf { it.isNotEmpty() }
                }
            }

            val normalizedSet = normalizeSetCode(rawSetField) ?: extractedSetFromName
            val normalizedNum = normalizeCollectorNumber(colNumber.takeIf { it in cols.indices }?.let { cols[it] })
            val normalizedLang = normalizeLanguage(colLang.takeIf { it in cols.indices }?.let { cols[it] })
            val scryfallId = normalizeUuid(colScryfallId.takeIf { it in cols.indices }?.let { cols[it] })
            val oracleId = normalizeUuid(colOracleId.takeIf { it in cols.indices }?.let { cols[it] })

            DelverCsvRow(
                rowNumber = rowNum,
                rawLine = cols.joinToString(","),
                quantity = qty,
                name = cleanName,
                setCode = normalizedSet,
                collectorNumber = normalizedNum,
                language = normalizedLang,
                scryfallId = scryfallId,
                oracleId = oracleId
            )
        }
    }

    suspend fun matchAndImportRows(
        rows: List<DelverCsvRow>,
        cardDao: CardDao,
        scryfallApi: ScryfallApi?,
        canFetchOnline: Boolean
    ): DelverImportReport {
        val resolutions = mutableListOf<DelverRowResolution>()

        for (row in rows) {
            if (row.quantity <= 0) {
                resolutions.add(
                    DelverRowResolution(
                        row = row,
                        status = CsvRowMatchStatus.UNRESOLVED,
                        statusDetail = "Row ${row.rowNumber}: Invalid quantity (${row.quantity})."
                    )
                )
                continue
            }

            val candidate = findExactCandidate(
                row = row,
                cardDao = cardDao,
                scryfallApi = scryfallApi,
                canFetchOnline = canFetchOnline
            )

            if (candidate == null) {
                val hint = when {
                    row.scryfallId != null -> "Scryfall ID ${row.scryfallId}"
                    row.setCode != null && row.collectorNumber != null ->
                        "${row.name ?: "Card"} (${row.setCode}) #${row.collectorNumber}"
                    else -> row.name ?: "Row ${row.rowNumber}"
                }
                val offlineNote = if (!canFetchOnline) " while offline/local-only" else ""
                resolutions.add(
                    DelverRowResolution(
                        row = row,
                        status = CsvRowMatchStatus.UNRESOLVED,
                        statusDetail = "No exact printing match for $hint$offlineNote. Fuzzy substitution disabled."
                    )
                )
                continue
            }

            val mismatches = verifySuppliedFields(row, candidate)
            if (mismatches.isNotEmpty()) {
                resolutions.add(
                    DelverRowResolution(
                        row = row,
                        status = CsvRowMatchStatus.CONFLICT,
                        resolvedCard = candidate,
                        mismatchReasons = mismatches,
                        statusDetail = mismatches.joinToString(" • ")
                    )
                )
            } else {
                val persisted = cardDao.upsertPreservingState(candidate)
                resolutions.add(
                    DelverRowResolution(
                        row = row,
                        status = CsvRowMatchStatus.VERIFIED,
                        resolvedCard = persisted,
                        statusDetail = "${persisted.name} (${persisted.setCode}) #${persisted.collectorNumber.ifBlank { "—" }} [${persisted.lang}] × ${row.quantity}"
                    )
                )
            }
        }

        val verified = resolutions.filter { it.status == CsvRowMatchStatus.VERIFIED }
        val conflicts = resolutions.filter { it.status == CsvRowMatchStatus.CONFLICT }
        val unresolved = resolutions.filter { it.status == CsvRowMatchStatus.UNRESOLVED }

        return DelverImportReport(
            resolutions = resolutions,
            verifiedRows = verified,
            conflictRows = conflicts,
            unresolvedRows = unresolved,
            savedCardsCount = verified.mapNotNull { it.resolvedCard?.id }.distinct().size,
            totalVerifiedCopies = verified.sumOf { it.row.quantity }
        )
    }

    private suspend fun findExactCandidate(
        row: DelverCsvRow,
        cardDao: CardDao,
        scryfallApi: ScryfallApi?,
        canFetchOnline: Boolean
    ): CardEntity? {
        // 1. Exact Scryfall Printing ID (distinct from Oracle ID)
        row.scryfallId?.let { printingId ->
            val localByPrinting = cardDao.findByScryfallId(printingId)
                ?: cardDao.getCardById("sf-$printingId")
            if (localByPrinting != null && localByPrinting.metadataStatus == CardMetadataStatus.VERIFIED) {
                return localByPrinting
            }
            if (canFetchOnline && scryfallApi != null) {
                val remote = fetchRemoteSafe { scryfallApi.getCardByScryfallId(printingId).toCardEntity() }
                if (remote != null) return remote
            }
            if (localByPrinting != null) return localByPrinting
            // Strictly do not fall back to name/fuzzy when an explicit Scryfall printing ID wasn't found.
            return null
        }

        // 2. Exact Set Code + Collector Number (+ Language)
        if (row.setCode != null && row.collectorNumber != null) {
            val lang = row.language ?: "en"
            val localExact = cardDao.findByPrintingExact(row.setCode, row.collectorNumber, lang)
            if (localExact != null) return localExact

            val localSetNum = cardDao.findBySetAndCollector(row.setCode, row.collectorNumber)
            if (localSetNum.isNotEmpty()) {
                return localSetNum.firstOrNull { it.lang.equals(lang, ignoreCase = true) }
                    ?: localSetNum.first()
            }

            if (canFetchOnline && scryfallApi != null) {
                val remote = if (row.language != null) {
                    fetchRemoteSafe {
                        scryfallApi.getCardBySetNumberLang(
                            setCode = row.setCode.lowercase(),
                            collectorNumber = row.collectorNumber,
                            lang = row.language
                        ).toCardEntity()
                    }
                } else {
                    fetchRemoteSafe {
                        scryfallApi.getCardBySetNumber(
                            setCode = row.setCode.lowercase(),
                            collectorNumber = row.collectorNumber
                        ).toCardEntity()
                    }
                }
                if (remote != null) return remote
            }
            return null
        }

        // 3. Exact card name ONLY when no printing ID / collector number was provided (NO fuzzy match)
        row.name?.let { cardName ->
            val localMatches = cardDao.findAllByExactNameIgnoreCase(cardName)
                .filter { it.metadataStatus != CardMetadataStatus.UNRESOLVED }
            if (localMatches.isNotEmpty()) {
                if (row.setCode != null) {
                    return localMatches.firstOrNull { it.setCode.equals(row.setCode, ignoreCase = true) }
                        ?: localMatches.first()
                }
                return localMatches.first()
            }
            if (canFetchOnline && scryfallApi != null) {
                return fetchRemoteSafe { scryfallApi.getCardNamedExact(cardName).toCardEntity() }
            }
        }
        return null
    }

    internal fun verifySuppliedFields(row: DelverCsvRow, candidate: CardEntity): List<String> {
        val mismatches = mutableListOf<String>()

        if (candidate.metadataStatus == CardMetadataStatus.UNRESOLVED) {
            mismatches.add("Candidate record '${candidate.name}' is unresolved and cannot verify printing metadata.")
        }

        row.scryfallId?.let { expectedPrintingId ->
            if (candidate.scryfallId.isBlank()) {
                mismatches.add("Printing Scryfall ID unavailable on local record (expected $expectedPrintingId).")
            } else if (!candidate.scryfallId.equals(expectedPrintingId, ignoreCase = true)) {
                mismatches.add("Scryfall printing ID mismatch: expected $expectedPrintingId, got ${candidate.scryfallId}.")
            }
            if (candidate.oracleId.isNotBlank() &&
                candidate.oracleId.equals(expectedPrintingId, ignoreCase = true) &&
                !candidate.scryfallId.equals(expectedPrintingId, ignoreCase = true)
            ) {
                mismatches.add("Supplied ID matches Oracle ID instead of a Scryfall printing ID.")
            }
        }

        row.oracleId?.let { expectedOracleId ->
            if (candidate.oracleId.isBlank()) {
                mismatches.add("Oracle ID unavailable on candidate '${candidate.name}' (expected $expectedOracleId).")
            } else if (!candidate.oracleId.equals(expectedOracleId, ignoreCase = true)) {
                mismatches.add("Oracle ID mismatch: expected $expectedOracleId, got ${candidate.oracleId}.")
            }
        }

        row.setCode?.let { expectedSet ->
            if (candidate.setCode.isBlank()) {
                mismatches.add("Set code unavailable on candidate (expected $expectedSet).")
            } else if (!candidate.setCode.equals(expectedSet, ignoreCase = true)) {
                mismatches.add("Set mismatch: CSV expected '$expectedSet', printing is '${candidate.setCode}'.")
            }
        }

        row.collectorNumber?.let { expectedNum ->
            if (candidate.collectorNumber.isBlank()) {
                mismatches.add("Collector number unavailable on candidate (expected #$expectedNum).")
            } else if (!candidate.collectorNumber.equals(expectedNum, ignoreCase = true)) {
                mismatches.add("Collector number mismatch: CSV expected '#$expectedNum', printing is '#${candidate.collectorNumber}'.")
            }
        }

        row.language?.let { expectedLang ->
            if (candidate.lang.isBlank()) {
                mismatches.add("Language unavailable on candidate (expected '$expectedLang').")
            } else if (!candidate.lang.equals(expectedLang, ignoreCase = true)) {
                mismatches.add("Language mismatch: CSV expected '$expectedLang', printing is '${candidate.lang}'.")
            }
        }

        row.name?.let { expectedName ->
            val matchesFull = candidate.name.equals(expectedName, ignoreCase = true)
            val matchesFace = candidate.name.split(" // ").any { it.trim().equals(expectedName, ignoreCase = true) }
            if (!matchesFull && !matchesFace) {
                mismatches.add("Name mismatch: CSV expected '$expectedName', printing is '${candidate.name}'.")
            }
        }

        return mismatches
    }

    private suspend fun fetchRemoteSafe(block: suspend () -> CardEntity): CardEntity? {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun findHeaderIndex(headers: List<String>, aliases: Set<String>): Int {
        return headers.indexOfFirst { it in aliases }
    }

    internal fun parseRfc4180Records(input: String): List<List<String>> {
        if (input.isBlank()) return emptyList()
        val records = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < input.length && input[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> {
                        currentRow.add(field.toString())
                        field.setLength(0)
                    }
                    '\r' -> {
                        // handle CRLF cleanly
                        currentRow.add(field.toString())
                        field.setLength(0)
                        records.add(currentRow.toList())
                        currentRow.clear()
                        if (i + 1 < input.length && input[i + 1] == '\n') i++
                    }
                    '\n' -> {
                        currentRow.add(field.toString())
                        field.setLength(0)
                        records.add(currentRow.toList())
                        currentRow.clear()
                    }
                    else -> field.append(ch)
                }
            }
            i++
        }
        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(field.toString())
            records.add(currentRow.toList())
        }
        return records
    }
}
