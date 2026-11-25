package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.LatticeDatabase
import com.example.data.remote.ScryfallCardDto
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.source
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.Properties
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

data class OfflineCatalogPack(
    val id: String,
    val name: String,
    val count: Int,
    val bytes: Long,
    val sha256: String
)

data class OfflineCatalogManifest(val snapshot: String, val packs: List<OfflineCatalogPack>)

class OfflineCatalogRepository(
    private val context: Context,
    private val database: LatticeDatabase,
    private val preferences: PreferencesRepository
) {
    private val mutex = Mutex()
    private val client = OkHttpClient.Builder()
        .followSslRedirects(false)
        .callTimeout(3, TimeUnit.MINUTES)
        .build()
    private val adapter = Moshi.Builder().build().adapter(ScryfallCardDto::class.java)
    private val _manifest = MutableStateFlow<OfflineCatalogManifest?>(null)
    val manifest = _manifest.asStateFlow()
    val installed = preferences.catalogPacks
    private val _downloading = MutableStateFlow<String?>(null)
    val downloading = _downloading.asStateFlow()

    suspend fun check(): OfflineCatalogManifest? = withContext(Dispatchers.IO) {
        if (preferences.offlineOnlyMode.first()) return@withContext null
        client.newCall(Request.Builder().url("$BASE/manifest.json").build()).execute().use { response ->
            val body = response.body
            check(response.isSuccessful && body != null) { "Card data is unavailable." }
            val source = body.source()
            check(!source.request(1024L * 1024 + 1)) { "Card data index is too large." }
            val json = JSONObject(source.readUtf8())
            check(json.getInt("schema") == 1) { "Unsupported card data version." }
            val snapshot = json.getString("snapshot")
            check(snapshot.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
            val records = json.getJSONArray("packs")
            val packs = (0 until records.length()).map { index ->
                val pack = records.getJSONObject(index)
                OfflineCatalogPack(
                    pack.getString("id"), pack.getString("name"), pack.getInt("count"),
                    pack.getLong("bytes"), pack.getString("sha256")
                ).also {
                    check(it.id.matches(Regex("all|set-[a-z0-9]{1,12}")))
                    check(it.bytes in 1..MAX_PACK_BYTES && it.count in 1..100_000)
                    check(it.sha256.matches(Regex("[a-f0-9]{64}")))
                }
            }
            check(packs.firstOrNull()?.id == "all" && packs.map { it.id }.distinct().size == packs.size)
            OfflineCatalogManifest(snapshot, packs).also { _manifest.value = it }
        }
    }

    val bundledCatalogSnapshot: String by lazy { bundledMetadata.getProperty("snapshot") }

    private val bundledMetadata: Properties by lazy {
        Properties().apply { context.assets.open("offline-catalog.properties").use(::load) }
    }

    suspend fun installBundledCatalog() = mutex.withLock {
        if ("all" in installed.first()) return@withLock
        val pack = OfflineCatalogPack(
            "all", "All cards",
            bundledMetadata.getProperty("count").toInt(),
            bundledMetadata.getProperty("bytes").toLong(),
            bundledMetadata.getProperty("sha256")
        )
        _downloading.value = pack.id
        try {
            withContext(Dispatchers.IO) {
                context.assets.open("offline-catalog/all.pack").use { importPack(pack, it) }
            }
        } finally {
            _downloading.value = null
        }
    }

    suspend fun download(pack: OfflineCatalogPack) = mutex.withLock {
        val current = _manifest.value
        check(current?.packs?.contains(pack) == true) { "Check for card data updates again." }
        check(!preferences.offlineOnlyMode.first()) { "Turn off offline-only mode to download cards." }
        _downloading.value = pack.id
        try {
            withContext(Dispatchers.IO) {
                val file = File.createTempFile("offline-cards-", ".gz", context.cacheDir)
                try {
                    fetch(pack, file)
                    file.inputStream().use { importPack(pack, it) }
                } finally {
                    file.delete()
                }
            }
        } finally {
            _downloading.value = null
        }
    }

    private suspend fun importPack(pack: OfflineCatalogPack, compressed: InputStream) {
        database.withTransaction {
            GZIPInputStream(compressed).source().buffer().use { source ->
                val reader = JsonReader.of(source)
                val seen = HashSet<String>()
                reader.beginArray()
                while (reader.hasNext()) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val card = checkNotNull(adapter.fromJson(reader))
                    check(card.id.matches(PRINTING_ID) && seen.add(card.id) && seen.size <= pack.count)
                    check(pack.id == "all" || card.set == pack.id.removePrefix("set-"))
                    database.cardDao().storeCatalogPrinting(card.toCardEntity())
                }
                reader.endArray()
                check(seen.size == pack.count && reader.peek() == JsonReader.Token.END_DOCUMENT)
            }
        }
        preferences.recordCatalogPack(pack.id, pack.sha256)
    }

    private suspend fun fetch(pack: OfflineCatalogPack, file: File) = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        client.newCall(Request.Builder().url("$BASE/${pack.id}.json.gz").build()).execute().use { response ->
            val body = response.body
            check(response.isSuccessful && body != null) { "Card data download failed." }
            body.byteStream().use { input ->
                file.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        check(total <= pack.bytes)
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                    check(total == pack.bytes)
                }
            }
        }
        check(digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == pack.sha256) {
            "Card data did not match the published checksum. Check for updates again."
        }
    }

    private companion object {
        const val BASE = "https://dreadpool-87.github.io/Lattice-MTG/data/android"
        const val MAX_PACK_BYTES = 32L * 1024 * 1024
        val PRINTING_ID = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
    }
}
