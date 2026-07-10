@file:Suppress("MaxLineLength")

package com.emmah.numeris.core.network

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

data class SearchAnalyticsRowWire(
    val keys: List<String>,
    val clicks: Long,
    val impressions: Long,
    val ctr: Double,
    val position: Double,
)

data class GoogleAnalyticsRowWire(
    val dimensions: List<String>,
    val metrics: List<String>,
)

class SearchConsoleApiClient(
    private val http: NumerisHttpClient,
) {
    suspend fun query(
        accessToken: String,
        propertyUrl: String,
        startDate: String,
        endDate: String,
        dimensions: List<String>,
        rowLimit: Int = DEFAULT_ROW_LIMIT,
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
                put("rowLimit", rowLimit)
                put("dimensions", buildJsonArray { dimensions.forEach { add(JsonPrimitive(it)) } })
            }.toString()
        val raw =
            http
                .execute(
                    "Google Search Console",
                    authorizedRequest(url.toString(), accessToken).post(body.toRequestBody(JSON_MEDIA_TYPE)).build(),
                ).body
        return Json.parseToJsonElement(raw).jsonObject["rows"]?.jsonArray.orEmpty().map { value ->
            val row = value.jsonObject
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
                    authorizedRequest(INSPECTION_ENDPOINT, accessToken).post(body.toRequestBody(JSON_MEDIA_TYPE)).build(),
                ).body
        return Json.parseToJsonElement(raw).jsonObject
    }

    private companion object {
        const val SITES_ENDPOINT = "https://www.googleapis.com/webmasters/v3/sites"
        const val INSPECTION_ENDPOINT = "https://searchconsole.googleapis.com/v1/urlInspection/index:inspect"
        const val DEFAULT_ROW_LIMIT = 25_000
    }
}

class GoogleAnalyticsApiClient(
    private val http: NumerisHttpClient,
) {
    suspend fun runReport(
        accessToken: String,
        propertyId: String,
        startDate: String,
        endDate: String,
        dimensions: List<String>,
        metrics: List<String>,
        limit: Int = DEFAULT_LIMIT,
    ): List<GoogleAnalyticsRowWire> {
        val body =
            buildJsonObject {
                put(
                    "dateRanges",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("startDate", startDate)
                                put("endDate", endDate)
                            },
                        )
                    },
                )
                put("dimensions", buildJsonArray { dimensions.forEach { name -> add(buildJsonObject { put("name", name) }) } })
                put("metrics", buildJsonArray { metrics.forEach { name -> add(buildJsonObject { put("name", name) }) } })
                put("limit", limit.toString())
                put("returnPropertyQuota", true)
            }.toString()
        val raw =
            http
                .execute(
                    "Google Analytics",
                    authorizedRequest("$BASE_ENDPOINT/properties/${propertyId.trim()}:runReport", accessToken)
                        .post(body.toRequestBody(JSON_MEDIA_TYPE))
                        .build(),
                ).body
        return Json.parseToJsonElement(raw).jsonObject["rows"]?.jsonArray.orEmpty().map { value ->
            val row = value.jsonObject
            GoogleAnalyticsRowWire(
                dimensions = row.values("dimensionValues"),
                metrics = row.values("metricValues"),
            )
        }
    }

    private fun JsonObject.values(name: String): List<String> =
        get(name)?.jsonArray.orEmpty().map {
            it.jsonObject["value"]
                ?.jsonPrimitive
                ?.contentOrNull
                .orEmpty()
        }

    private companion object {
        const val BASE_ENDPOINT = "https://analyticsdata.googleapis.com/v1beta"
        const val DEFAULT_LIMIT = 100_000
    }
}

private fun authorizedRequest(
    url: String,
    accessToken: String,
): Request.Builder = Request.Builder().url(url).header("Authorization", "Bearer $accessToken")

private val JSON_MEDIA_TYPE = "application/json".toMediaType()

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
