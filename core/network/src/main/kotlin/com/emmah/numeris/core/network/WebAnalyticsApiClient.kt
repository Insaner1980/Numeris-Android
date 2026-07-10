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

data class WebAnalyticsDailyWire(
    val date: String,
    val visits: Long,
    val pageViews: Long,
)

data class WebAnalyticsBreakdownWire(
    val name: String,
    val value: Long,
)

data class WebAnalyticsRollupWire(
    val daily: List<WebAnalyticsDailyWire>,
    val referrers: List<WebAnalyticsBreakdownWire>,
    val pages: List<WebAnalyticsBreakdownWire>,
    val countries: List<WebAnalyticsBreakdownWire>,
)

class WebAnalyticsApiClient(
    private val http: NumerisHttpClient,
) {
    suspend fun fetch(
        token: CharArray,
        accountId: String,
        siteTag: String,
        startDate: String,
        endDate: String,
    ): WebAnalyticsRollupWire {
        val variables =
            buildJsonObject {
                put("accountTag", accountId)
                put("siteTag", siteTag)
                put("since", "${startDate}T00:00:00Z")
                put("until", "${endDate}T23:59:59Z")
                put("sinceDate", startDate)
                put("untilDate", endDate)
            }
        val body =
            buildJsonObject {
                put("query", QUERY)
                put("variables", variables)
            }.toString()
        val request =
            Request
                .Builder()
                .url(ENDPOINT)
                .header("Authorization", "Bearer ${token.concatToString().removePrefix("Bearer ").trim()}")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        val root = Json.parseToJsonElement(http.execute("Cloudflare Web Analytics", request).body).jsonObject
        if (root["errors"]?.jsonArray?.isNotEmpty() == true) error("Cloudflare Web Analytics request failed.")
        val account =
            root["data"]
                ?.jsonObject
                ?.get("viewer")
                ?.jsonObject
                ?.get("accounts")
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?: error("Cloudflare did not return Web Analytics data for this account.")
        return WebAnalyticsRollupWire(
            daily =
                account.array("daily").map { value ->
                    val row = value.jsonObject
                    WebAnalyticsDailyWire(row.dimension("date"), row.sum("visits"), row.long("count"))
                },
            referrers = account.breakdown("referrers", "refererHost", "visits", false),
            pages = account.breakdown("pages", "requestPath", "", true),
            countries = account.breakdown("countries", "countryName", "visits", false),
        )
    }

    private fun JsonObject.breakdown(
        name: String,
        dimension: String,
        sumName: String,
        useCount: Boolean,
    ): List<WebAnalyticsBreakdownWire> =
        array(name).mapNotNull { value ->
            val row = value.jsonObject
            val label = row.dimension(dimension).takeIf(String::isNotBlank) ?: return@mapNotNull null
            WebAnalyticsBreakdownWire(label, if (useCount) row.long("count") else row.sum(sumName))
        }

    private fun JsonObject.array(name: String): JsonArray = get(name)?.jsonArray ?: JsonArray(emptyList())

    private fun JsonObject.dimension(name: String): String =
        get("dimensions")
            ?.jsonObject
            ?.get(name)
            ?.jsonPrimitive
            ?.contentOrNull
            .orEmpty()

    private fun JsonObject.sum(name: String): Long =
        get("sum")
            ?.jsonObject
            ?.get(name)
            ?.jsonPrimitive
            ?.longOrNull ?: 0

    private fun JsonObject.long(name: String): Long = get(name)?.jsonPrimitive?.longOrNull ?: 0

    private companion object {
        const val ENDPOINT = "https://api.cloudflare.com/client/v4/graphql"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        const val QUERY = """
            query NumerisWebAnalytics(${"$"}accountTag: String!, ${"$"}siteTag: String!, ${"$"}since: Time!, ${"$"}until: Time!, ${"$"}sinceDate: Date!, ${"$"}untilDate: Date!) {
              viewer { accounts(filter: { accountTag: ${"$"}accountTag }) {
                daily: rumPageloadEventsAdaptiveGroups(limit: 10000, filter: { siteTag: ${"$"}siteTag, date_geq: ${"$"}sinceDate, date_leq: ${"$"}untilDate, bot: 0 }, orderBy: [date_ASC]) { dimensions { date } sum { visits } count }
                referrers: rumPageloadEventsAdaptiveGroups(limit: 100, filter: { siteTag: ${"$"}siteTag, datetime_geq: ${"$"}since, datetime_leq: ${"$"}until, bot: 0 }, orderBy: [sum_visits_DESC]) { dimensions { refererHost } sum { visits } }
                pages: rumPageloadEventsAdaptiveGroups(limit: 100, filter: { siteTag: ${"$"}siteTag, datetime_geq: ${"$"}since, datetime_leq: ${"$"}until, bot: 0 }, orderBy: [count_DESC]) { dimensions { requestPath } count }
                countries: rumPageloadEventsAdaptiveGroups(limit: 100, filter: { siteTag: ${"$"}siteTag, datetime_geq: ${"$"}since, datetime_leq: ${"$"}until, bot: 0 }, orderBy: [sum_visits_DESC]) { dimensions { countryName } sum { visits } }
              } }
            }
        """
    }
}
