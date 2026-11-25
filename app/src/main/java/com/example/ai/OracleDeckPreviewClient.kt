package com.example.ai

import com.example.data.local.CardEntity
import com.example.data.local.DeckWithEntries
import com.example.domain.CardFaceInfo
import com.example.domain.CardRulingInfo
import com.example.domain.DeckAnalytics
import com.example.domain.OracleDeckCheck
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private const val ENDPOINT =
    "https://lattice-oracle-deck-preview.dreadpool.workers.dev"

internal enum class OraclePreviewModel(val id: String, val label: String) {
    STANDARD("120b", "GPT-OSS 120B"),
    ECONOMY("20b", "GPT-OSS 20B")
}

internal data class OracleDeckExplanation(
    val text: String,
    val neurons: Double?,
    val remaining: Int
)

internal sealed interface OracleQuestionTarget {
    val title: String
    val scope: String
    fun context(): JSONObject

    data class Deck(val deck: DeckWithEntries) : OracleQuestionTarget {
        override val title get() = deck.deck.name
        override val scope get() = "deck"

        override fun context(): JSONObject {
            val analytics = DeckAnalytics.analyze(deck)
            val entries = deck.commanderEntries + deck.mainboardEntries
            val summary = "Playable cards: ${analytics.playableCount}. Lands: ${analytics.landCount}. " +
                "Average mana value: ${analytics.averageCmc}. Curve (6 means 6+): ${analytics.manaCurve}. " +
                "Copy-limit warning: ${analytics.validationNotes.any { it.copyLimitExceeded }}. " +
                "Cached analytics only. Full format legality was not checked. " +
                "Listed: ${minOf(entries.size, 180)} of ${entries.size} entries. Sideboard omitted."
            val cards = entries.take(180).map {
                "${it.entry.zone}: ${it.entry.quantity} × ${it.card.name} (${it.card.manaCost})".take(160)
            }
            return JSONObject()
                .put("format", deck.deck.format.take(80).ifBlank { "Unspecified" })
                .put("analytics", summary)
                .put("cards", JSONArray(cards))
        }
    }

    data class Card(
        val card: CardEntity,
        val faces: List<CardFaceInfo>,
        val rulings: List<CardRulingInfo>
    ) : OracleQuestionTarget {
        override val title get() = card.name
        override val scope get() = "card"

        override fun context(): JSONObject = JSONObject()
            .put("name", card.name.take(250))
            .put("text", buildString {
                appendLine(card.typeLine)
                appendLine(card.manaCost)
                appendLine(card.oracleText)
                faces.forEach { face ->
                    appendLine("${face.name}: ${face.manaCost} ${face.typeLine}")
                    appendLine(face.oracleText)
                }
                rulings.forEach { ruling -> appendLine(ruling.comment) }
            }.take(6000).ifBlank { "No cached rules text available." })
    }
}

internal object OracleDeckPreviewClient {
    private val client = OkHttpClient.Builder()
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun explain(check: OracleDeckCheck, token: String, model: OraclePreviewModel) =
        runInterruptible(Dispatchers.IO) {
            val payload = JSONObject()
                .put("model", model.id)
                .put("commander", check.commander)
                .put("cards", check.cards)
                .put("lands", check.lands)
                .put("copyWarning", check.copyWarning)
                .put("identity", check.identity)
            send("explain", token, payload)
        }

    suspend fun ask(
        target: OracleQuestionTarget,
        question: String,
        token: String,
        model: OraclePreviewModel
    ) = runInterruptible(Dispatchers.IO) {
        send("ask", token, JSONObject()
            .put("model", model.id)
            .put("question", question.trim())
            .put("scope", target.scope)
            .put("context", target.context()))
    }

    private fun send(route: String, token: String, payload: JSONObject): OracleDeckExplanation {
        val request = Request.Builder()
            .url("$ENDPOINT/$route")
            .header("Authorization", "Bearer ${token.trim()}")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return client.newCall(request).execute().use { response ->
            when (response.code) {
                401 -> throw IOException("That preview code was not accepted.")
                429 -> throw IOException("The preview limit or Cloudflare quota was reached. Try again later.")
                422 -> throw IOException("The selected context or question is outside the preview limits.")
            }
            if (!response.isSuccessful) throw IOException("Oracle preview is unavailable right now.")
            val json = JSONObject(response.peekBody(8192).string())
            OracleDeckExplanation(
                json.getString("text"),
                json.optDouble("neurons").takeIf { it.isFinite() && it >= 0 },
                json.getInt("remaining")
            )
        }
    }
}
