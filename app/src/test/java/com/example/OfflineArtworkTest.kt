package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LatticeDatabase
import com.example.data.local.StarterCards
import com.example.data.remote.ScryfallCardDto
import com.example.data.remote.ScryfallImageUrisDto
import com.example.data.repository.ArtworkCard
import com.example.data.repository.ArtworkSelection
import com.example.data.repository.OfflineArtworkRepository
import com.example.data.repository.ArtworkQuality
import com.example.data.repository.ArtworkSide
import com.example.domain.CardFaceInfo
import com.example.domain.CardJsonCodec
import com.example.data.repository.OfflineCatalogRepository
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.artworkImageTargets
import com.example.data.repository.artworkTargets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflineArtworkTest {
    private val englishId = "158b311e-2c41-4e09-a560-147a17ec6c94"
    private val alternateId = "258b311e-2c41-4e09-a560-147a17ec6c94"

    @Test
    fun allCardsKeepsOnePrintingPerCardWhileOtherSelectionsKeepDistinctPrintings() {
        val english = ArtworkCard("english", englishId, "same-card", "FDN", "777", "en", null)
        val alternate = english.copy(id = "alternate", scryfallId = alternateId, lang = "fr", setCode = "AFR")
        val cards = listOf(alternate, english, english, english.copy(scryfallId = "unresolved"))
        assertEquals(listOf(alternate, english), artworkTargets(cards, representative = false))
        assertEquals(listOf(english), artworkTargets(cards, representative = true))
    }

    @Test
    fun physicalBackIsAnImageTargetButSharedArtworkIsNotDuplicated() {
        val front = CardFaceInfo("Front", imageUri = "https://cards.scryfall.io/normal/front/1/5/$englishId.jpg")
        val back = CardFaceInfo("Back", imageUri = "https://cards.scryfall.io/normal/back/1/5/$englishId.jpg")
        val card = ArtworkCard("two", englishId, "two", "MID", "1", "en", front.imageUri)
        val both = card.copy(cardFacesJson = CardJsonCodec.encodeFaces(listOf(front, back)))
        val shared = card.copy(cardFacesJson = CardJsonCodec.encodeFaces(listOf(
            front.copy(imageUri = null), back.copy(imageUri = null)
        )))
        assertEquals(listOf(ArtworkSide.FRONT, ArtworkSide.BACK), artworkImageTargets(listOf(both)).map { it.side })
        assertEquals(listOf(ArtworkSide.FRONT), artworkImageTargets(listOf(shared)).map { it.side })
    }

    @Test
    fun singleDownloadUsesSelectedQualityAndExplainsMissingImages() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, LatticeDatabase::class.java).build()
        val preferences = PreferencesRepository(context)
        val catalog = OfflineCatalogRepository(context, database, preferences)
        val sd = File(context.filesDir, "offline-card-art/$alternateId.jpg")
        val hd = File(context.filesDir, "offline-card-art/$alternateId-hd.jpg")
        val image = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpeg", it)
        }.toByteArray()
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request.url.encodedPath
            val missing = "/back/" in request.url.encodedPath
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (missing) 404 else 200).message(if (missing) "Not found" else "OK")
                .body(image.toResponseBody("image/jpeg".toMediaType())).build()
        }.build()
        val repository = OfflineArtworkRepository(context, database, preferences, catalog, client) { id ->
            ScryfallCardDto(id = id, name = "Prepared card", imageUris = ScryfallImageUrisDto(normal = "front image"))
        }
        val split = StarterCards.cards.first().copy(
            id = "sf-$alternateId", scryfallId = alternateId, imageUri = null,
            cardFacesJson = CardJsonCodec.encodeFaces(listOf(
                CardFaceInfo("Front"),
                CardFaceInfo("Back", imageUri = "https://cards.scryfall.io/normal/back/2/5/$alternateId.jpg")
            ))
        )
        sd.delete()
        hd.delete()
        ShadowStatFs.registerStats(context.filesDir, 10_000, 9_000, 9_000)
        try {
            preferences.setOfflineOnlyMode(false)
            repository.setDefaultQuality(ArtworkQuality.SD)
            assertEquals(ArtworkQuality.SD, repository.defaultQuality.first())
            assertEquals("Back image unavailable. Other images are saved.", repository.downloadCard(split, ArtworkQuality.SD))
            assertEquals(listOf("/normal/front/2/5/$alternateId.jpg", "/normal/back/2/5/$alternateId.jpg"), requests)
            assertArrayEquals(image, sd.readBytes())
            assertEquals(false, hd.exists())

            val inferredBack = split.copy(cardFacesJson = CardJsonCodec.encodeFaces(
                listOf(CardFaceInfo("Creature"), CardFaceInfo("Prepared spell"))
            ))
            database.cardDao().upsertAllPreservingState(listOf(inferredBack))
            database.vaultCollectionDao().saveSnapshot("Prepared", mapOf(inferredBack.id to 1), replace = false, complete = true)
            repository.prepare(ArtworkSelection.Collection)
            val planned = withTimeout(10_000) { repository.state.first { !it.busy } }
            assertEquals(2, planned.total)
            assertEquals(1, planned.remaining)
            repository.download()
            val finished = withTimeout(10_000) { repository.state.first { !it.busy } }
            assertEquals("Artwork download complete.", finished.message)
            assertEquals(1, finished.total)
            assertEquals(1, finished.onDevice)
            assertEquals(0, finished.unavailable)
            assertEquals("Available offline.", repository.downloadCard(inferredBack, ArtworkQuality.SD))
            assertEquals(3, requests.size)

            val singleFace = split.copy(cardFacesJson = "")
            assertEquals("Available offline.", repository.downloadCard(singleFace, ArtworkQuality.HD))
            assertEquals("/large/front/2/5/$alternateId.jpg", requests.last())
            assertArrayEquals(image, hd.readBytes())
            assertEquals(false, sd.exists())
            assertEquals(ArtworkQuality.SD, repository.defaultQuality.first())
        } finally {
            sd.delete()
            hd.delete()
            ShadowStatFs.unregisterStats(context.filesDir)
            database.close()
        }
    }

    @Test
    fun collectionDownloadSavesBothSidesAndUpgradesThemToHd() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, LatticeDatabase::class.java).build()
        val preferences = PreferencesRepository(context)
        val jpeg = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpeg", jpeg)
        val image = jpeg.toByteArray()
        val path = File(context.filesDir, "offline-card-art/$englishId.jpg")
        val hdPath = File(context.filesDir, "offline-card-art/$englishId-hd.jpg")
        val sdBack = File(context.filesDir, "offline-card-art/$englishId-back.jpg")
        val hdBack = File(context.filesDir, "offline-card-art/$englishId-back-hd.jpg")
        val files = listOf(path, hdPath, sdBack, hdBack)
        files.forEach { it.delete() }
        ShadowStatFs.registerStats(context.filesDir, 10_000, 0, 0)
        try {
            preferences.setOfflineOnlyMode(false)
            val card = StarterCards.cards.first().copy(
                id = "sf-$englishId", scryfallId = englishId, imageUri = null,
                cardFacesJson = CardJsonCodec.encodeFaces(listOf(CardFaceInfo("Front"), CardFaceInfo("Back")))
            )
            database.cardDao().upsertAllPreservingState(listOf(card))
            database.vaultCollectionDao().saveSnapshot("Binder", mapOf(card.id to 4), replace = false, complete = true)
            database.vaultCollectionDao().saveSnapshot("Other list", mapOf(card.id to 1), replace = false, complete = true)

            var requests = 0
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                val size = if (requests < 2) "normal" else "large"
                val side = if (requests % 2 == 0) "front" else "back"
                assertEquals("https://cards.scryfall.io/$size/$side/1/5/$englishId.jpg", request.url.toString())
                requests++
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(image.toResponseBody("image/jpeg".toMediaType())).build()
            }.build()
            val catalog = OfflineCatalogRepository(context, database, preferences)
            val repository = OfflineArtworkRepository(context, database, preferences, catalog, client)

            repository.prepare(ArtworkSelection.Collection)
            val preview = withTimeout(10_000) { repository.state.first { !it.busy } }
            assertEquals(preview.message, 2, preview.remaining)
            repository.download()
            val fullDevice = withTimeout(10_000) { repository.state.first { !it.busy } }
            assertEquals(2, fullDevice.remaining)
            assertEquals(0, requests)

            ShadowStatFs.registerStats(context.filesDir, 10_000, 9_000, 9_000)
            repository.download()
            val done = withTimeout(10_000) { repository.state.first { !it.busy } }
            assertEquals(done.message, 2, done.onDevice)
            assertArrayEquals(image, OfflineArtworkRepository.localFile(context, englishId)?.readBytes())
            assertArrayEquals(image, OfflineArtworkRepository.localFile(context, englishId, ArtworkSide.BACK)?.readBytes())

            repository.prepare(ArtworkSelection.Collection)
            val repeat = withTimeout(10_000) { repository.state.first { !it.busy } }
            assertEquals(2, repeat.onDevice)
            assertEquals(0, repeat.remaining)
            assertEquals(2, requests)

            repository.prepare(ArtworkSelection.Collection, ArtworkQuality.HD)
            val upgrade = withTimeout(10_000) { repository.state.first { !it.busy } }
            assertEquals(2, upgrade.remaining)
            repository.download()
            val upgraded = withTimeout(10_000) { repository.state.first { !it.busy } }
            assertEquals(upgraded.message, 2, upgraded.onDevice)
            assertEquals(hdPath, OfflineArtworkRepository.localFile(context, englishId))
            assertEquals(hdBack, OfflineArtworkRepository.localFile(context, englishId, ArtworkSide.BACK))
            assertEquals(false, path.exists())
            assertEquals(false, sdBack.exists())
            assertEquals(4, requests)
        } finally {
            ShadowStatFs.unregisterStats(context.filesDir)
            files.forEach { it.delete() }
            database.close()
        }
    }
}
