@file:Suppress("MaxLineLength")

package com.emmah.numeris.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class SearchAnalyticsRowWire(
    val keys: List<String>,
    val clicks: Long,
    val impressions: Long,
    val ctr: Double,
    val position: Double,
)

class SearchConsoleApiClient(
    private val http: NumerisHttpClient,
) {
    suspend fun sites(accessToken: String): List<String> {
        val raw =
            http
                .execute(
                    "Google Search Console",
                    authorizedRequest(SITES_ENDPOINT, accessToken).get().build(),
                ).body
        return Json
            .parseToJsonElement(raw)
            .jsonObject["siteEntry"]
            ?.jsonArray
            .orEmpty()
            .mapNotNull { it.jsonObject["siteUrl"]?.jsonPrimitive?.contentOrNull }
    }

    /**
     * Fetch every available page, including zero-click impressions at the end of the result.
     * Keep pages small enough for the HTTP response size limit; a short page ends pagination.
     * Consume each page before fetching the next so the caller can stage results with bounded memory.
     */
    suspend fun query(
        accessToken: String,
        propertyUrl: String,
        startDate: String,
        endDate: String,
        dimensions: List<String>,
        onPage: suspend (List<SearchAnalyticsRowWire>) -> Unit,
    ) {
        var startRow = 0
        do {
            val page = queryPage(accessToken, propertyUrl, startDate, endDate, dimensions, startRow)
            onPage(page)
            startRow += PAGE_SIZE
        } while (page.size == PAGE_SIZE)
    }

    private suspend fun queryPage(
        accessToken: String,
        propertyUrl: String,
        startDate: String,
        endDate: String,
        dimensions: List<String>,
        startRow: Int,
    ): List<SearchAnalyticsRowWire> {
        val url =
            SITES_ENDPOINT
                .toHttpUrl()
                .newBuilder()
                .addPathSegment(propertyUrl)
                .addPathSegment("searchAnalytics")
                .addPathSegment("query")
                .build()
        val body =
            buildJsonObject {
                put("startDate", startDate)
                put("endDate", endDate)
                put("rowLimit", PAGE_SIZE)
                put("startRow", startRow)
                put("type", "web")
                put("dimensions", buildJsonArray { dimensions.forEach { add(JsonPrimitive(it)) } })
            }.toString()
        val raw =
            http
                .execute(
                    "Google Search Console",
                    authorizedRequest(url.toString(), accessToken).post(body.toRequestBody(JSON_MEDIA_TYPE)).build(),
                ).body
        return googleRows(raw).map { row ->
            SearchAnalyticsRowWire(
                keys = row["keys"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content },
                clicks =
                    row["clicks"]
                        ?.jsonPrimitive
                        ?.content
                        ?.toDoubleOrNull()
                        ?.toLong() ?: 0,
                impressions =
                    row["impressions"]
                        ?.jsonPrimitive
                        ?.content
                        ?.toDoubleOrNull()
                        ?.toLong() ?: 0,
                ctr = row["ctr"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                position = row["position"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
            )
        }
    }

    suspend fun inspectUrl(
        accessToken: String,
        propertyUrl: String,
        url: String,
    ): JsonObject {
        val body =
            buildJsonObject {
                put("inspectionUrl", url)
                put("siteUrl", propertyUrl)
            }.toString()
        val raw =
            http
                .execute(
                    "Google URL Inspection",
                    authorizedRequest(
                        INSPECTION_ENDPOINT,
                        accessToken,
                    ).post(body.toRequestBody(JSON_MEDIA_TYPE)).build(),
                ).body
        return Json.parseToJsonElement(raw).jsonObject
    }

    private companion object {
        const val SITES_ENDPOINT = "https://www.googleapis.com/webmasters/v3/sites"
        const val INSPECTION_ENDPOINT = "https://searchconsole.googleapis.com/v1/urlInspection/index:inspect"
        const val PAGE_SIZE = 5_000
    }
}

private fun authorizedRequest(
    url: String,
    accessToken: String,
): Request.Builder = Request.Builder().url(url).header("Authorization", "Bearer $accessToken")

private val JSON_MEDIA_TYPE = "application/json".toMediaType()

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

private fun googleRows(raw: String): List<JsonObject> =
    Json
        .parseToJsonElement(raw)
        .jsonObject["rows"]
        ?.jsonArray
        .orEmpty()
        .map { it.jsonObject }
