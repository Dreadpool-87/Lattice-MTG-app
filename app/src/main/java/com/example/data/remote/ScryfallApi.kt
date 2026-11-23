package com.example.data.remote

import com.example.data.local.CardEntity
import com.example.data.local.CardMetadataStatus
import com.example.domain.CardFaceInfo
import com.example.domain.CardJsonCodec
import com.example.domain.CardRulingInfo
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface ScryfallApi {
    @GET("cards/search")
    suspend fun searchCards(
        @Query("q") query: String,
        @Query("order") order: String = "edhrec"
    ): ScryfallSearchResponse

    @GET("cards/named")
    suspend fun getCardNamed(
        @Query("fuzzy") fuzzyName: String
    ): ScryfallCardDto

    @GET("cards/named")
    suspend fun getCardNamedExact(
        @Query("exact") exactName: String,
        @Query("set") setCode: String? = null
    ): ScryfallCardDto

    @GET("cards/{id}")
    suspend fun getCardByScryfallId(
        @Path("id") scryfallId: String
    ): ScryfallCardDto

    @GET("cards/{code}/{number}/{lang}")
    suspend fun getCardBySetNumberLang(
        @Path("code") setCode: String,
        @Path("number") collectorNumber: String,
        @Path("lang") lang: String
    ): ScryfallCardDto

    @GET("cards/{code}/{number}")
    suspend fun getCardBySetNumber(
        @Path("code") setCode: String,
        @Path("number") collectorNumber: String
    ): ScryfallCardDto

    @GET("cards/{id}/rulings")
    suspend fun getCardRulings(
        @Path("id") scryfallId: String
    ): ScryfallRulingsResponse

    companion object {
        private const val BASE_URL = "https://api.scryfall.com/"

        fun create(): ScryfallApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "LatticeMTG-Android/1.0")
                        .header("Accept", "application/json")
                        .build()
                    chain.proceed(request)
                }
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(ScryfallApi::class.java)
        }
    }
}

@JsonClass(generateAdapter = true)
data class ScryfallSearchResponse(
    val data: List<ScryfallCardDto> = emptyList(),
    @Json(name = "has_more") val hasMore: Boolean = false,
    @Json(name = "total_cards") val totalCards: Int = 0
)

@JsonClass(generateAdapter = true)
data class ScryfallRulingsResponse(
    val data: List<ScryfallRulingDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ScryfallRulingDto(
    val source: String? = null,
    @Json(name = "published_at") val publishedAt: String? = null,
    val comment: String? = null
) {
    fun toCardRulingInfo(): CardRulingInfo? {
        val text = comment?.trim().orEmpty()
        if (text.isEmpty()) return null
        return CardRulingInfo(
            publishedAt = publishedAt.orEmpty(),
            source = source ?: "wotc",
            comment = text
        )
    }
}

@JsonClass(generateAdapter = true)
data class ScryfallImageUrisDto(
    val small: String? = null,
    val normal: String? = null,
    @Json(name = "art_crop") val artCrop: String? = null
)

@JsonClass(generateAdapter = true)
data class ScryfallCardFaceDto(
    val name: String? = null,
    @Json(name = "mana_cost") val manaCost: String? = null,
    @Json(name = "type_line") val typeLine: String? = null,
    @Json(name = "oracle_text") val oracleText: String? = null,
    val power: String? = null,
    val toughness: String? = null,
    val loyalty: String? = null,
    @Json(name = "image_uris") val imageUris: ScryfallImageUrisDto? = null
)

@JsonClass(generateAdapter = true)
data class ScryfallCardDto(
    val id: String,
    @Json(name = "oracle_id") val oracleId: String? = null,
    val name: String,
    val lang: String? = null,
    @Json(name = "mana_cost") val manaCost: String? = null,
    val cmc: Double? = null,
    @Json(name = "type_line") val typeLine: String? = null,
    @Json(name = "oracle_text") val oracleText: String? = null,
    val colors: List<String>? = null,
    @Json(name = "color_identity") val colorIdentity: List<String>? = null,
    val rarity: String? = null,
    val set: String? = null,
    @Json(name = "set_name") val setName: String? = null,
    @Json(name = "collector_number") val collectorNumber: String? = null,
    val power: String? = null,
    val toughness: String? = null,
    val loyalty: String? = null,
    val keywords: List<String>? = null,
    val legalities: Map<String, String>? = null,
    @Json(name = "image_uris") val imageUris: ScryfallImageUrisDto? = null,
    @Json(name = "card_faces") val cardFaces: List<ScryfallCardFaceDto>? = null
) {
    fun toCardEntity(rulings: List<CardRulingInfo> = emptyList()): CardEntity {
        val frontFace = cardFaces?.firstOrNull()
        val resolvedMana = manaCost ?: frontFace?.manaCost.orEmpty()
        val resolvedType = typeLine ?: frontFace?.typeLine.orEmpty()
        val resolvedOracle = oracleText ?: cardFaces?.mapNotNull { it.oracleText }?.joinToString("\n//\n").orEmpty()
        val resolvedImage = imageUris?.normal ?: imageUris?.artCrop ?: frontFace?.imageUris?.normal

        val faceInfos = cardFaces.orEmpty().map { face ->
            CardFaceInfo(
                name = face.name ?: name,
                manaCost = face.manaCost.orEmpty(),
                typeLine = face.typeLine.orEmpty(),
                oracleText = face.oracleText.orEmpty(),
                power = face.power,
                toughness = face.toughness,
                loyalty = face.loyalty,
                imageUri = face.imageUris?.normal ?: face.imageUris?.artCrop
            )
        }

        val trackedFormats = setOf("Commander", "Standard", "Modern", "Pioneer", "Pauper", "Legacy", "Vintage")
        val legalList = legalities
            ?.filterValues { it == "legal" }
            ?.keys
            ?.map { it.replaceFirstChar { c -> c.uppercase() } }
            ?.filter { it in trackedFormats }
            .orEmpty()

        val resolvedLegalFormats = when {
            legalities.isNullOrEmpty() -> "Unknown"
            legalList.isEmpty() -> "None"
            else -> legalList.joinToString(",")
        }

        val inferredTags = inferSynergyTags(resolvedType, resolvedOracle, keywords.orEmpty())

        return CardEntity(
            id = "sf-$id",
            name = name,
            manaCost = resolvedMana,
            cmc = cmc ?: 0.0,
            typeLine = resolvedType,
            oracleText = resolvedOracle,
            colors = colors.orEmpty().joinToString(","),
            colorIdentity = colorIdentity.orEmpty().joinToString(","),
            rarity = rarity ?: "common",
            setCode = (set ?: "").uppercase(),
            setName = setName ?: "",
            power = power ?: frontFace?.power,
            toughness = toughness ?: frontFace?.toughness,
            loyalty = loyalty ?: frontFace?.loyalty,
            keywords = keywords.orEmpty().joinToString(","),
            synergyTags = inferredTags.joinToString(","),
            legalFormats = resolvedLegalFormats,
            imageUri = resolvedImage,
            isCustom = false,
            isBookmarked = false,
            cachedAt = System.currentTimeMillis(),
            oracleId = oracleId.orEmpty(),
            scryfallId = id,
            collectorNumber = collectorNumber.orEmpty(),
            lang = (lang ?: "en").lowercase(),
            cardFacesJson = CardJsonCodec.encodeFaces(faceInfos),
            rulingsJson = CardJsonCodec.encodeRulings(rulings),
            metadataStatus = CardMetadataStatus.VERIFIED
        )
    }

    private fun inferSynergyTags(type: String, oracle: String, kw: List<String>): List<String> {
        val tags = mutableListOf<String>()
        val lowerOracle = oracle.lowercase()
        val lowerType = type.lowercase()
        if ("artifact" in lowerType || "artifact" in lowerOracle) tags.add("Artifact")
        if ("land" in lowerType) tags.add("Land")
        if ("draw" in lowerOracle && "card" in lowerOracle) tags.add("Card Draw")
        if ("add {" in lowerOracle || "search your library for a" in lowerOracle && "land" in lowerOracle || "treasure" in lowerOracle) tags.add("Ramp")
        if ("destroy target" in lowerOracle || "exile target" in lowerOracle || "damage to any target" in lowerOracle) tags.add("Removal")
        if ("counter target" in lowerOracle) tags.add("Countermagic")
        if ("create a" in lowerOracle && "token" in lowerOracle) tags.add("Tokens")
        if ("graveyard" in lowerOracle || "delve" in kw.map { it.lowercase() }) tags.add("Graveyard")
        if ("search your library" in lowerOracle && "land" !in lowerOracle) tags.add("Tutor")
        if ("indestructible" in lowerOracle || "hexproof" in lowerOracle || "protection" in lowerOracle) tags.add("Protection")
        if ("instant" in lowerType || "sorcery" in lowerType) tags.add("Spellslinger")
        return tags.distinct()
    }
}
