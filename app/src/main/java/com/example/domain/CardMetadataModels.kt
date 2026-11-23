package com.example.domain

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@JsonClass(generateAdapter = true)
data class CardFaceInfo(
    val name: String,
    val manaCost: String = "",
    val typeLine: String = "",
    val oracleText: String = "",
    val power: String? = null,
    val toughness: String? = null,
    val loyalty: String? = null,
    val imageUri: String? = null
)

@JsonClass(generateAdapter = true)
data class CardRulingInfo(
    val publishedAt: String,
    val source: String = "wotc",
    val comment: String
)

object CardJsonCodec {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val facesAdapter = moshi.adapter<List<CardFaceInfo>>(
        Types.newParameterizedType(List::class.java, CardFaceInfo::class.java)
    )

    private val rulingsAdapter = moshi.adapter<List<CardRulingInfo>>(
        Types.newParameterizedType(List::class.java, CardRulingInfo::class.java)
    )

    fun encodeFaces(faces: List<CardFaceInfo>): String {
        if (faces.isEmpty()) return ""
        return facesAdapter.toJson(faces)
    }

    fun decodeFaces(json: String): List<CardFaceInfo> {
        if (json.isBlank()) return emptyList()
        return runCatching { facesAdapter.fromJson(json).orEmpty() }.getOrDefault(emptyList())
    }

    fun encodeRulings(rulings: List<CardRulingInfo>): String {
        if (rulings.isEmpty()) return ""
        return rulingsAdapter.toJson(rulings)
    }

    fun decodeRulings(json: String): List<CardRulingInfo> {
        if (json.isBlank()) return emptyList()
        return runCatching { rulingsAdapter.fromJson(json).orEmpty() }.getOrDefault(emptyList())
    }
}
