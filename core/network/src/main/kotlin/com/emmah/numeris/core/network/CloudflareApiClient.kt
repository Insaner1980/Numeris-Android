@file:Suppress("MaxLineLength", "ktlint:standard:max-line-length")

package com.emmah.numeris.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class CloudflareDailyWire(
    val date: String,
    val pageViews: Long,
    val uniqueVisitors: Long,
    val requests: Long,
    val cachedRequests: Long,
    val cachedBytes: Long,
    val totalBytes: Long,
    val threats: Long,
)

data class CloudflareStatusWire(
    val date: String,
    val statusCode: Int,
    val requests: Long,
)

data class CloudflareBreakdownWire(
    val date: String,
    val name: String,
    val value: Long,
)

data class CloudflareTrafficWire(
    val daily: List<CloudflareDailyWire>,
    val statuses: List<CloudflareStatusWire>,
    val countries: List<CloudflareBreakdownWire>,
    val pages: List<CloudflareBreakdownWire>,
)

class CloudflareApiClient(
    private val http: NumerisHttpClient,
) {
    suspend fun validateZone(
        token: CharArray,
        zoneId: String,
    ): String {
        val result = http.execute("Cloudflare", authorizedRequest("$API_BASE/zones/${zoneId.trim()}", token).get().build())
        val root = Json.parseToJsonElement(result.body).jsonObject
        require(root["success"]?.jsonPrimitive?.content == "true") { "Cloudflare could not validate this zone." }
        return root
            .getValue("result")
            .jsonObject
            .getValue("name")
            .jsonPrimitive.content
    }

    suspend fun fetchTraffic(
        token: CharArray,
        zoneId: String,
        since: String,
        until: String,
    ): CloudflareTrafficWire {
        val variables =
            buildJsonObject {
                put("zoneTag", zoneId)
                put("since", since)
                put("until", until)
                put("adaptiveSince", "${since}T00:00:00Z")
                put("adaptiveUntil", "${java.time.LocalDate.parse(until).plusDays(1)}T00:00:00Z")
            }
        val body =
            buildJsonObject {
                put("query", DAILY_QUERY)
                put("variables", variables)
            }.toString()
        val response =
            http.execute(
                "Cloudflare",
                authorizedRequest(GRAPHQL_ENDPOINT, token)
                    .post(body.toRequestBody(JSON_MEDIA_TYPE))
                    .build(),
            )
        return parseTraffic(response.body, until)
    }

    private fun parseTraffic(
        raw: String,
        breakdownDate: String,
    ): CloudflareTrafficWire {
        val root = Json.parseToJsonElement(raw).jsonObject
        root["errors"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull?.let {
            error("Cloudflare analytics request failed.")
        }
        val zone =
            root["data"]
                ?.jsonObject
                ?.get("viewer")
                ?.jsonObject
                ?.get("zones")
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?: error("Cloudflare did not return analytics for this zone.")
        val statuses = mutableListOf<CloudflareStatusWire>()
        val daily =
            zone.array("httpRequests1dGroups").map { value ->
                val group = value.jsonObject
                val date = group.objectValue("dimensions").string("date")
                val sum = group.objectValue("sum")
                sum.array("responseStatusMap").forEach { statusValue ->
                    val status = statusValue.jsonObject
                    statuses += CloudflareStatusWire(date, status.long("edgeResponseStatus").toInt(), status.long("requests"))
                }
                CloudflareDailyWire(
                    date,
                    sum.long("pageViews"),
                    group.objectValue("uniq").long("uniques"),
                    sum.long("requests"),
                    sum.long("cachedRequests"),
                    sum.long("cachedBytes"),
                    sum.long("bytes"),
                    sum.long("threats"),
                )
            }
        return CloudflareTrafficWire(
            daily = daily,
            statuses = statuses,
            countries = zone.breakdown("topCountries", "clientCountryName", breakdownDate),
            pages = zone.breakdown("topPages", "clientRequestPath", breakdownDate),
        )
    }

    private fun authorizedRequest(
        url: String,
        token: CharArray,
    ): Request.Builder =
        Request
            .Builder()
            .url(url)
            .header("Authorization", "Bearer ${token.concatToString().removePrefix("Bearer ").trim()}")

    private fun JsonObject.breakdown(
        arrayName: String,
        dimensionName: String,
        date: String,
    ): List<CloudflareBreakdownWire> =
        array(arrayName).mapNotNull { value ->
            val group = value.jsonObject
            val name =
                group
                    .objectValue("dimensions")[dimensionName]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
            val visits =
                group["sum"]
                    ?.jsonObject
                    ?.get("visits")
                    ?.jsonPrimitive
                    ?.longOrNull
            CloudflareBreakdownWire(date, name, visits ?: group.long("count"))
        }

    private fun JsonObject.array(name: String): JsonArray = get(name)?.jsonArray ?: JsonArray(emptyList())

    private fun JsonObject.objectValue(name: String): JsonObject = get(name)?.jsonObject ?: JsonObject(emptyMap())

    private fun JsonObject.string(name: String): String = get(name)?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject.long(name: String): Long = get(name)?.jsonPrimitive?.longOrNull ?: 0

    private companion object {
        const val API_BASE = "https://api.cloudflare.com/client/v4"
        const val GRAPHQL_ENDPOINT = "$API_BASE/graphql"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        const val DAILY_QUERY = """
            query NumerisDailyTraffic(${"$"}zoneTag: String!, ${"$"}since: Date!, ${"$"}until: Date!, ${"$"}adaptiveSince: Time!, ${"$"}adaptiveUntil: Time!) {
              viewer { zones(filter: { zoneTag: ${"$"}zoneTag }) {
                httpRequests1dGroups(limit: 10000, filter: { date_geq: ${"$"}since, date_leq: ${"$"}until }, orderBy: [date_ASC]) {
                  dimensions { date }
                  sum { pageViews requests cachedRequests cachedBytes bytes threats responseStatusMap { edgeResponseStatus requests } }
                  uniq { uniques }
                }
                topCountries: httpRequestsAdaptiveGroups(limit: 10, orderBy: [count_DESC], filter: { datetime_geq: ${"$"}adaptiveSince, datetime_lt: ${"$"}adaptiveUntil, requestSource: "eyeball" }) {
                  count sum { visits } dimensions { clientCountryName }
                }
                topPages: httpRequestsAdaptiveGroups(limit: 10, orderBy: [count_DESC], filter: { datetime_geq: ${"$"}adaptiveSince, datetime_lt: ${"$"}adaptiveUntil, requestSource: "eyeball" }) {
                  count sum { visits } dimensions { clientRequestPath }
                }
              } }
            }
        """
    }
}
