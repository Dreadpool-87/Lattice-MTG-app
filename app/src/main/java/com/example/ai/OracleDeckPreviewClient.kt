package com.example.ai

import com.example.domain.OracleDeckCheck
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private const val ENDPOINT =
    "https://lattice-oracle-deck-preview.dreadpool.workers.dev/explain"

internal enum class OraclePreviewModel(val id: String, val label: String) {
    STANDARD("120b", "GPT-OSS 120B"),
    ECONOMY("20b", "GPT-OSS 20B")
}

internal data class OracleDeckExplanation(
    val text: String,
    val neurons: Double?,
    val remaining: Int
)

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
            val request = Request.Builder()
                .url(ENDPOINT)
                .header("Authorization", "Bearer ${token.trim()}")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    401 -> throw IOException("That preview code was not accepted.")
                    429 -> throw IOException("The preview limit or Cloudflare quota was reached. Try again later.")
                    422 -> throw IOException("This deck is outside the supported preview limits.")
                }
                if (!response.isSuccessful) throw IOException("Deck explanation is unavailable right now.")
                val json = JSONObject(response.peekBody(8192).string())
                OracleDeckExplanation(
                    json.getString("text"),
                    json.optDouble("neurons").takeIf { it.isFinite() && it >= 0 },
                    json.getInt("remaining")
                )
            }
        }
}
