@file:Suppress("MaxLineLength")

package com.emmah.numeris.core.network

import com.emmah.numeris.core.common.NumerisFailure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class CruxMetricPointWire(
    val targetType: String,
    val target: String,
    val formFactor: String,
    val collectionStart: String,
    val collectionEnd: String,
    val metric: String,
    val p75: Double?,
    val goodDensity: Double?,
    val needsImprovementDensity: Double?,
    val poorDensity: Double?,
    val rawJson: String,
)

data class PageSpeedAuditWire(
    val id: String,
    val title: String?,
    val score: Double?,
    val numericValue: Double?,
    val numericUnit: String?,
    val displayValue: String?,
    val scoreDisplayMode: String?,
    val detailsJson: String?,
)

data class PageSpeedRunWire(
    val url: String,
    val strategy: String,
    val analysisUtc: String,
    val finalUrl: String?,
    val performanceScore: Double?,
    val accessibilityScore: Double?,
    val bestPracticesScore: Double?,
    val seoScore: Double?,
    val lighthouseVersion: String?,
    val runtimeError: String?,
    val warningsJson: String?,
    val rawJson: String,
    val audits: List<PageSpeedAuditWire>,
)

class CruxApiClient(
    private val http: NumerisHttpClient,
) {
    suspend fun queryHistory(
        apiKey: CharArray,
        targetType: String,
        target: String,
        formFactor: String?,
    ): List<CruxMetricPointWire> {
        val body =
            buildJsonObject {
                put(if (targetType == "url") "url" else "origin", target)
                if (formFactor != null) put("formFactor", formFactor)
            }.toString()
        val requestUrl =
            ENDPOINT
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("key", apiKey.concatToString())
                .build()
        val response =
            try {
                http.execute(
                    "CrUX",
                    Request
                        .Builder()
                        .url(requestUrl)
                        .post(body.toRequestBody(JSON_MEDIA_TYPE))
                        .build(),
                )
            } catch (_: NumerisFailure.NotFound) {
                // CrUX vastaa 404, kun sivustolla tai sivulla on liian vähän Chrome-käyttäjiä: ei dataa, ei virhe.
                return emptyList()
            }
        return parseCrux(response.body, targetType, target, formFactor)
    }

    @Suppress("ReturnCount")
    private fun parseCrux(
        raw: String,
        targetType: String,
        target: String,
        formFactor: String?,
    ): List<CruxMetricPointWire> {
        val record = Json.parseToJsonElement(raw).jsonObject["record"]?.jsonObject ?: return emptyList()
        val periods = record["collectionPeriods"]?.jsonArray ?: return emptyList()
        val metrics = record["metrics"]?.jsonObject ?: return emptyList()
        return metrics.flatMap { (metricName, metricValue) ->
            val metric = metricValue.jsonObject
            val p75s = metric["percentilesTimeseries"]?.jsonObject?.get("p75s")?.jsonArray ?: JsonArray(emptyList())
            val histograms = metric["histogramTimeseries"]?.jsonArray ?: JsonArray(emptyList())
            periods.mapIndexed { index, periodValue ->
                val histogram =
                    histograms.mapNotNull { it.jsonObject["densities"]?.jsonArray }.map {
                        it.valueAt(
                            index,
                        )
                    }
                val period = periodValue.jsonObject
                CruxMetricPointWire(
                    targetType = targetType,
                    target = target,
                    formFactor = formFactor ?: "ALL",
                    collectionStart = period.getValue("firstDate").jsonObject.isoDate(),
                    collectionEnd = period.getValue("lastDate").jsonObject.isoDate(),
                    metric = metricName,
                    p75 = p75s.valueAt(index),
                    goodDensity = histogram.getOrNull(0),
                    needsImprovementDensity = histogram.getOrNull(1),
                    poorDensity = histogram.getOrNull(2),
                    rawJson = metric.toString(),
                )
            }
        }
    }

    private fun JsonArray.valueAt(index: Int): Double? = getOrNull(index)?.jsonPrimitive?.doubleOrNull

    private fun JsonObject.isoDate(): String =
        "%04d-%02d-%02d".format(
            getValue("year").jsonPrimitive.content.toInt(),
            getValue("month").jsonPrimitive.content.toInt(),
            getValue("day").jsonPrimitive.content.toInt(),
        )

    private companion object {
        const val ENDPOINT = "https://chromeuxreport.googleapis.com/v1/records:queryHistoryRecord"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

class PageSpeedApiClient(
    private val http: NumerisHttpClient,
) {
    suspend fun run(
        apiKey: CharArray,
        targetUrl: String,
        strategy: String,
    ): PageSpeedRunWire {
        val requestUrl =
            ENDPOINT
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("url", targetUrl)
                .addQueryParameter("strategy", strategy.uppercase())
                .apply { CATEGORIES.forEach { addQueryParameter("category", it) } }
                .addQueryParameter("key", apiKey.concatToString())
                .build()
        val raw =
            http
                .execute(
                    "PageSpeed",
                    Request
                        .Builder()
                        .url(requestUrl)
                        .get()
                        .build(),
                ).body
        return parse(raw, targetUrl, strategy)
    }

    private fun parse(
        raw: String,
        targetUrl: String,
        strategy: String,
    ): PageSpeedRunWire {
        val root = Json.parseToJsonElement(raw).jsonObject
        val lighthouse = root["lighthouseResult"]?.jsonObject ?: JsonObject(emptyMap())
        val categories = lighthouse["categories"]?.jsonObject ?: JsonObject(emptyMap())
        val audits = lighthouse["audits"]?.jsonObject ?: JsonObject(emptyMap())
        return PageSpeedRunWire(
            url = targetUrl,
            strategy = strategy,
            analysisUtc =
                root["analysisUTCTimestamp"]?.jsonPrimitive?.contentOrNull ?: java.time.Instant
                    .now()
                    .toString(),
            finalUrl = lighthouse["finalUrl"]?.jsonPrimitive?.contentOrNull,
            performanceScore = categories.score("performance"),
            accessibilityScore = categories.score("accessibility"),
            bestPracticesScore = categories.score("best-practices"),
            seoScore = categories.score("seo"),
            lighthouseVersion = lighthouse["lighthouseVersion"]?.jsonPrimitive?.contentOrNull,
            runtimeError = lighthouse["runtimeError"]?.toString(),
            warningsJson = lighthouse["runWarnings"]?.toString(),
            rawJson = raw,
            audits = audits.map { (id, value) -> value.jsonObject.toAudit(id) },
        )
    }

    private fun JsonObject.score(name: String): Double? =
        get(name)
            ?.jsonObject
            ?.get("score")
            ?.jsonPrimitive
            ?.doubleOrNull

    private fun JsonObject.toAudit(id: String) =
        PageSpeedAuditWire(
            id = id,
            title = get("title")?.jsonPrimitive?.contentOrNull,
            score = get("score")?.jsonPrimitive?.doubleOrNull,
            numericValue = get("numericValue")?.jsonPrimitive?.doubleOrNull,
            numericUnit = get("numericUnit")?.jsonPrimitive?.contentOrNull,
            displayValue = get("displayValue")?.jsonPrimitive?.contentOrNull,
            scoreDisplayMode = get("scoreDisplayMode")?.jsonPrimitive?.contentOrNull,
            detailsJson = get("details")?.toString(),
        )

    private companion object {
        const val ENDPOINT = "https://pagespeedonline.googleapis.com/pagespeedonline/v5/runPagespeed"
        val CATEGORIES = listOf("PERFORMANCE", "ACCESSIBILITY", "BEST_PRACTICES", "SEO")
    }
}
