@file:Suppress("MaxLineLength", "ktlint:standard:max-line-length")

package com.emmah.numeris.core.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

data class WebAnalyticsDailyWire(
    val date: String,
    val visits: Long,
    val pageViews: Long,
)

data class WebAnalyticsBreakdownWire(
    val name: String,
    val value: Long,
    val date: String,
)

data class WebAnalyticsRollupWire(
    val daily: List<WebAnalyticsDailyWire>,
    val referrers: List<WebAnalyticsBreakdownWire>,
    val pages: List<WebAnalyticsBreakdownWire>,
    val countries: List<WebAnalyticsBreakdownWire>,
)

class WebAnalyticsApiClient(
    http: NumerisHttpClient,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val graphql = CloudflareGraphql(http, "Cloudflare Web Analytics")

    suspend fun fetch(
        token: CharArray,
        accountId: String,
        siteTag: String,
        startDate: String,
        endDate: String,
    ): WebAnalyticsRollupWire {
        val limit =
            graphql.limits(
                token,
                SETTINGS_QUERY,
                buildJsonObject { put("accountTag", accountId) },
                "accounts",
                listOf(DATASET),
            )[DATASET]
        val end = LocalDate.parse(endDate)
        val until = end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val requested = LocalDate.parse(startDate).atStartOfDay(ZoneOffset.UTC).toInstant()
        val since = CloudflareQueryWindow.adaptiveStart(requested, until, clock.instant(), limit)
        // Päiväsuodatin alkaa ensimmäisestä kokonaisesta päivästä rajatun alun jälkeen.
        val sinceDay = since.atZone(ZoneOffset.UTC).toLocalDate()
        val sinceDate = if (sinceDay.atStartOfDay(ZoneOffset.UTC).toInstant() == since) sinceDay else sinceDay.plusDays(1)
        val variables =
            buildJsonObject {
                put("accountTag", accountId)
                put("siteTag", siteTag)
                put("since", since.toString())
                put("until", "${endDate}T23:59:59Z")
                put("sinceDate", sinceDate.toString())
                put("untilDate", endDate)
            }
        val root = graphql.query(token, QUERY, variables)
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
            val label = row.dimension(dimension)
            WebAnalyticsBreakdownWire(
                label,
                if (useCount) row.long("count") else row.sum(sumName),
                row.dimension("date"),
            )
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
        const val DATASET = "rumPageloadEventsAdaptiveGroups"
        const val SETTINGS_QUERY = """
            query NumerisWebAnalyticsLimits(${"$"}accountTag: String!) {
              viewer { accounts(filter: { accountTag: ${"$"}accountTag }) { settings {
                rumPageloadEventsAdaptiveGroups { maxDuration notOlderThan }
              } } }
            }
        """
        const val QUERY = """
            query NumerisWebAnalytics(${"$"}accountTag: String!, ${"$"}siteTag: String!, ${"$"}since: Time!, ${"$"}until: Time!, ${"$"}sinceDate: Date!, ${"$"}untilDate: Date!) {
              viewer { accounts(filter: { accountTag: ${"$"}accountTag }) {
                daily: rumPageloadEventsAdaptiveGroups(limit: 10000, filter: { siteTag: ${"$"}siteTag, date_geq: ${"$"}sinceDate, date_leq: ${"$"}untilDate, bot: 0 }, orderBy: [date_ASC]) { dimensions { date } sum { visits } count }
                referrers: rumPageloadEventsAdaptiveGroups(limit: 10000, filter: { siteTag: ${"$"}siteTag, datetime_geq: ${"$"}since, datetime_leq: ${"$"}until, bot: 0 }, orderBy: [sum_visits_DESC]) { dimensions { date refererHost } sum { visits } }
                pages: rumPageloadEventsAdaptiveGroups(limit: 10000, filter: { siteTag: ${"$"}siteTag, datetime_geq: ${"$"}since, datetime_leq: ${"$"}until, bot: 0 }, orderBy: [count_DESC]) { dimensions { date requestPath } count }
                countries: rumPageloadEventsAdaptiveGroups(limit: 10000, filter: { siteTag: ${"$"}siteTag, datetime_geq: ${"$"}since, datetime_leq: ${"$"}until, bot: 0 }, orderBy: [sum_visits_DESC]) { dimensions { date countryName } sum { visits } }
              } }
            }
        """
    }
}
