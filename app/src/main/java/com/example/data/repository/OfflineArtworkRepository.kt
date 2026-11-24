package com.example.data.repository

import android.content.Context
import android.os.StatFs
import com.example.data.local.CardEntity
import com.example.data.local.LatticeDatabase
import com.example.domain.CardJsonCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

sealed interface ArtworkSelection {
    data object Collection : ArtworkSelection
    data class Decks(val ids: Set<Long>) : ArtworkSelection
    data class Sets(val codes: Set<String>) : ArtworkSelection
    data object AllCards : ArtworkSelection
}

data class ArtworkOption(val id: String, val label: String)

data class ArtworkCard(
    val id: String,
    val scryfallId: String,
    val oracleId: String,
    val setCode: String,
    val collectorNumber: String,
    val lang: String,
    val imageUri: String?,
    val cardFacesJson: String = ""
)

private fun CardEntity.asArtworkCard() = ArtworkCard(id, scryfallId, oracleId, setCode, collectorNumber, lang, imageUri, cardFacesJson)

private val printingIdPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

internal fun artworkId(value: String): String? = value.lowercase(Locale.ROOT).takeIf(printingIdPattern::matches)

internal fun artworkTargets(cards: List<ArtworkCard>, representative: Boolean): List<ArtworkCard> {
    val valid = cards.filter { artworkId(it.scryfallId) != null }
    val printings = if (representative) valid.sortedWith(
        compareBy<ArtworkCard>({ !it.lang.equals("en", true) }, { it.setCode }, { it.collectorNumber }, { it.scryfallId })
    ).distinctBy { it.oracleId.lowercase(Locale.ROOT).ifBlank { artworkId(it.scryfallId).orEmpty() } } else valid
    return printings.distinctBy { artworkId(it.scryfallId) }
}

enum class ArtworkQuality(val label: String, val sizePath: String, val estimatedImageBytes: Long) {
    SD("SD", "normal", 100_000L),
    HD("HD", "large", 180_000L)
}

enum class ArtworkSide(val path: String) { FRONT("front"), BACK("back") }

internal data class ArtworkImage(val scryfallId: String, val side: ArtworkSide, val imageUri: String?)

internal fun artworkImageTargets(cards: List<ArtworkCard>): List<ArtworkImage> = cards.flatMap { card ->
    val faces = CardJsonCodec.decodeFaces(card.cardFacesJson)
    val front = ArtworkImage(card.scryfallId, ArtworkSide.FRONT, card.imageUri ?: faces.firstOrNull()?.imageUri)
    val back = faces.getOrNull(1)
    if (back != null && (!back.imageUri.isNullOrBlank() || card.imageUri.isNullOrBlank())) {
        listOf(front, ArtworkImage(card.scryfallId, ArtworkSide.BACK, back.imageUri))
    } else listOf(front)
}

data class ArtworkDownloadState(
    val selection: ArtworkSelection? = null,
    val quality: ArtworkQuality = ArtworkQuality.SD,
    val busy: Boolean = false,
    val downloading: Boolean = false,
    val message: String? = null,
    val total: Int = 0,
    val onDevice: Int = 0,
    val remaining: Int = 0,
    val checked: Int = 0,
    val unavailable: Int = 0,
    val freeBytes: Long = 0
) {
    val estimatedBytes: Long get() = remaining * quality.estimatedImageBytes
}

class OfflineArtworkRepository(
    private val context: Context,
    private val database: LatticeDatabase,
    private val preferences: PreferencesRepository,
    private val catalog: OfflineCatalogRepository,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false).build()
) {
    private val directory = File(context.filesDir, "offline-card-art")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ArtworkDownloadState())
    val state = _state.asStateFlow()
    val allCardsReady = combine(catalog.manifest, catalog.installed) { manifest, installed ->
        manifest?.packs?.any { it.id == "all" } == true || "all" in installed
    }
    @Volatile private var pending = emptyList<ArtworkImage>()
    @Volatile private var job: Job? = null

    suspend fun checkAvailability() {
        availableManifest()
    }

    suspend fun deckOptions(): List<ArtworkOption> =
        database.deckDao().observeAllDecksWithEntries().first().map { ArtworkOption(it.deck.id.toString(), it.deck.name) }

    suspend fun setOptions(): List<ArtworkOption> {
        val options = database.cardDao().getArtworkSets().associateBy { it.id }.toMutableMap()
        availableManifest()?.packs?.filter { it.id.startsWith("set-") }?.forEach {
            val code = it.id.removePrefix("set-")
            options[code] = ArtworkOption(code, "${it.name} (${code.uppercase(Locale.ROOT)})")
        }
        return options.values.sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    fun resetSelection() {
        if (_state.value.busy) return
        pending = emptyList()
        _state.value = ArtworkDownloadState()
    }

    fun prepare(selection: ArtworkSelection, quality: ArtworkQuality = ArtworkQuality.SD) {
        if (_state.value.busy) return
        pending = emptyList()
        _state.value = ArtworkDownloadState(selection = selection, quality = quality, busy = true, message = "Preparing the selection…")
        launchDownloadJob("Preparation stopped.") {
            var localSetDataOnly = false
            val cards = when (selection) {
                ArtworkSelection.Collection -> database.vaultCollectionDao().observeCards().first().map { it.card.asArtworkCard() }
                is ArtworkSelection.Decks -> database.deckDao().observeAllDecksWithEntries().first()
                    .filter { it.deck.id in selection.ids }.flatMap { it.entries.map { entry -> entry.card.asArtworkCard() } }
                ArtworkSelection.AllCards -> {
                    if ("all" !in catalog.installed.first()) installPacks(setOf("all"), requireManifest())
                    database.cardDao().getArtworkCards()
                }
                is ArtworkSelection.Sets -> {
                    val needed = selection.codes.map { "set-$it" }.toSet() - catalog.installed.first().keys
                    if (needed.isNotEmpty()) {
                        val manifest = availableManifest()
                        val onlinePacks = manifest?.packs.orEmpty().map { it.id }.toSet()
                        localSetDataOnly = (needed - onlinePacks).isNotEmpty()
                        if (manifest != null) installPacks(needed.intersect(onlinePacks), manifest)
                    }
                    database.cardDao().getArtworkCards().filter { it.setCode.lowercase(Locale.ROOT) in selection.codes }
                }
            }

            val targets = artworkImageTargets(artworkTargets(cards, selection == ArtworkSelection.AllCards))
            pending = targets.filter { localFile(context, it.scryfallId, it.side, quality) == null }
            _state.value = ArtworkDownloadState(
                selection = selection,
                quality = quality,
                busy = true,
                message = when {
                    targets.isEmpty() -> "No downloadable artwork was found for this selection."
                    localSetDataOnly -> "Some set packs are unavailable. This selection includes the matching cards already on this device."
                    else -> null
                },
                total = targets.size,
                onDevice = targets.size - pending.size,
                remaining = pending.size,
                unavailable = cards.filter { artworkId(it.scryfallId) == null }.distinctBy { it.id }.size,
                freeBytes = freeBytes()
            )
        }
    }

    fun download() {
        if (_state.value.busy || pending.isEmpty()) return
        _state.value = _state.value.copy(busy = true, downloading = true, checked = 0, message = null)
        launchDownloadJob("Download stopped. Finished images are still available offline.") {
            val batch = pending
            var checked = 0
            var missing = 0
            try {
                if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Could not open artwork storage.")
                directory.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
                val quality = _state.value.quality
                for (card in batch) {
                    coroutineContext.ensureActive()
                    if (preferences.offlineOnlyMode.first()) throw IOException("Turn off offline-only mode to download artwork.")
                    if (freeBytes() < MAX_IMAGE_BYTES) throw IOException("Not enough free space. Finished images are still available offline.")
                    val saved = localFile(context, card.scryfallId, card.side, quality) != null || fetch(card, quality)
                    checked++
                    if (!saved) missing++
                    val current = _state.value
                    _state.value = current.copy(
                        checked = checked,
                        onDevice = current.onDevice + if (saved) 1 else 0,
                        unavailable = current.unavailable + if (saved) 0 else 1
                    )
                }
                _state.value = _state.value.copy(
                    message = if (missing == 0) "Artwork download complete." else "Artwork download finished. $missing images were unavailable."
                )
            } finally {
                pending = batch.drop(checked)
                _state.value = _state.value.copy(remaining = pending.size, freeBytes = freeBytes())
            }
        }
    }

    fun stop() = job?.cancel()

    private fun launchDownloadJob(cancelMessage: String, work: suspend () -> Unit) {
        val running = scope.launch {
            try {
                work()
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(message = cancelMessage)
                throw cancelled
            } catch (error: Exception) {
                _state.value = _state.value.copy(message = error.message ?: "Artwork download stopped.")
            } finally {
                _state.value = _state.value.copy(busy = false, downloading = false)
            }
        }
        job = running
        running.invokeOnCompletion {
            if (job === running && _state.value.busy) {
                _state.value = _state.value.copy(busy = false, downloading = false, message = cancelMessage)
            }
        }
    }

    private suspend fun availableManifest(): OfflineCatalogManifest? = try {
        catalog.check()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private suspend fun requireManifest(): OfflineCatalogManifest = availableManifest()
        ?: throw IOException("The offline card catalog is unavailable. Try again once the card data packs are online.")

    private suspend fun installPacks(ids: Set<String>, manifest: OfflineCatalogManifest) {
        val needed = ids - catalog.installed.first().keys
        val packs = manifest.packs.filter { it.id in needed }
        if (packs.size != needed.size) throw IOException("The required offline card data is unavailable.")
        packs.forEachIndexed { index, pack ->
            _state.value = _state.value.copy(message = "Downloading card data ${index + 1} of ${packs.size}…")
            catalog.download(pack)
        }
    }

    private suspend fun fetch(card: ArtworkImage, quality: ArtworkQuality): Boolean {
        val id = artworkId(card.scryfallId) ?: return false
        val destination = File(directory, artworkFilename(id, card.side, quality))
        val known = card.imageUri?.takeIf {
            it.startsWith("https://cards.scryfall.io/${quality.sizePath}/${card.side.path}/")
        }
        val uri = known ?: "https://cards.scryfall.io/${quality.sizePath}/${card.side.path}/${id[0]}/${id[1]}/$id.jpg"
        val request = Request.Builder().url(uri)
            .header("Accept", "image/jpeg").header("User-Agent", "Lattice-MTG-Android").build()
        return client.newCall(request).execute().use { response ->
            if (response.code == 404) return false
            val body = response.body
            if (!response.isSuccessful || body == null) throw IOException("Artwork server returned HTTP ${response.code}. Try again later.")
            if (body.contentType()?.toString()?.startsWith("image/jpeg") != true) return false

            val temporary = File.createTempFile("artwork-", ".part", directory)
            try {
                body.byteStream().buffered().use { input ->
                    if (input.read() != 0xFF || input.read() != 0xD8) return false
                    temporary.outputStream().buffered().use { output ->
                        output.write(0xFF)
                        output.write(0xD8)
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var bytes = 2L
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            bytes += count
                            if (bytes > MAX_IMAGE_BYTES) return false
                            output.write(buffer, 0, count)
                        }
                    }
                }
                coroutineContext.ensureActive()
                if (!temporary.renameTo(destination)) throw IOException("Could not save artwork on this device.")
                if (quality == ArtworkQuality.HD) File(directory, artworkFilename(id, card.side, ArtworkQuality.SD)).delete()
                true
            } finally {
                temporary.delete()
            }
        }
    }

    private fun freeBytes() = StatFs(context.filesDir.absolutePath).availableBytes

    companion object {
        private const val MAX_IMAGE_BYTES = 2 * 1024 * 1024L

        internal fun artworkFilename(id: String, side: ArtworkSide, quality: ArtworkQuality): String {
            val back = if (side == ArtworkSide.BACK) "-back" else ""
            val hd = if (quality == ArtworkQuality.HD) "-hd" else ""
            return "$id$back$hd.jpg"
        }

        fun localFile(
            context: Context,
            scryfallId: String,
            side: ArtworkSide = ArtworkSide.FRONT,
            minimumQuality: ArtworkQuality = ArtworkQuality.SD
        ): File? {
            val id = artworkId(scryfallId) ?: return null
            val directory = File(context.filesDir, "offline-card-art")
            val hd = File(directory, artworkFilename(id, side, ArtworkQuality.HD))
            if (hd.isFile && hd.length() > 2) return hd
            if (minimumQuality == ArtworkQuality.HD) return null
            return File(directory, artworkFilename(id, side, ArtworkQuality.SD))
                .takeIf { it.isFile && it.length() > 2 }
        }
    }
}
