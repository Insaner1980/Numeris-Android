package com.emmah.numeris.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class BingApiClient(
    private val http: NumerisHttpClient,
) {
    suspend fun call(
        apiKey: CharArray,
        method: String,
        siteUrl: String? = null,
    ): List<JsonObject> {
        val url =
            (ENDPOINT + method)
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("apikey", apiKey.concatToString())
                .apply { if (siteUrl != null) addQueryParameter("siteUrl", siteUrl) }
                .build()
        val root =
            Json.parseToJsonElement(
                http
                    .execute(
                        "Bing Webmaster",
                        Request
                            .Builder()
                            .url(url)
                            .get()
                            .build(),
                    ).body,
            )
        return unwrap(root).toObjects()
    }

    @Suppress("ReturnCount")
    private fun unwrap(root: JsonElement): JsonElement {
        if (root !is JsonObject) return root
        root["d"]?.let { return it }
        return root.values.firstOrNull { it is JsonArray || it is JsonObject } ?: root
    }

    private fun JsonElement.toObjects(): List<JsonObject> =
        when (this) {
            is JsonArray -> mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(jsonObject)
            else -> emptyList()
        }

    private companion object {
        const val ENDPOINT = "https://ssl.bing.com/webmaster/api.svc/json/"
    }
}
