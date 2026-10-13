package com.emmah.numeris.core.network

import com.emmah.numeris.core.common.NumerisFailure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Vyöhykkeen datasetin rajat sekunteina Cloudflaren GraphQL settings -solmusta. Null tarkoittaa tuntematonta. */
data class CloudflareDatasetLimit(
    val maxDurationSeconds: Long?,
    val notOlderThanSeconds: Long?,
)

/**
 * Rajaa pyydetyn aikavälin vyöhykkeen plaanin sallimaksi. Liian pitkä tai liian vanha väli saa Cloudflaren
 * hylkäämään koko kyselyn, joten väliä lyhennetään alusta ja uusin data säilyy aina mukana.
 */
object CloudflareQueryWindow {
    private const val SECONDS_PER_DAY = 86_400L

    /** Marginaali, jotta kyselyn lähetyshetken ja Cloudflaren kellon ero ei ylitä ikärajaa. */
    private const val CLOCK_MARGIN_SECONDS = 3_600L

    fun dailyStart(
        requested: LocalDate,
        until: LocalDate,
        now: Instant,
        limit: CloudflareDatasetLimit?,
    ): LocalDate {
        var start = requested
        limit?.notOlderThanSeconds?.let { seconds ->
            start =
                maxOf(
                    start,
                    now
                        .minusSeconds(seconds)
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate()
                        .plusDays(1),
                )
        }
        limit?.maxDurationSeconds?.let { seconds ->
            val days = seconds / SECONDS_PER_DAY
            if (days >= 1) start = maxOf(start, until.minusDays(days - 1))
        }
        return minOf(start, until)
    }

    /**
     * Settings-vastauksesta datasettien rajat vyöhykkeelle (`zones`) tai tilille (`accounts`).
     * Null, jos vastaus ei kelpaa; puuttuva datasetti saa arvon null.
     */
    fun parseLimits(
        raw: String,
        scope: String,
        datasets: List<String>,
    ): Map<String, CloudflareDatasetLimit?>? =
        runCatching {
            Json
                .parseToJsonElement(raw)
                .jsonObject["data"]
                ?.jsonObject
                ?.get("viewer")
                ?.jsonObject
                ?.get(scope)
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("settings")
                ?.jsonObject
        }.getOrNull()?.let { settings -> datasets.associateWith { settings.datasetLimit(it) } }

    private fun JsonObject.datasetLimit(name: String): CloudflareDatasetLimit? =
        (get(name) as? JsonObject)?.let {
            CloudflareDatasetLimit(
                it["maxDuration"]?.jsonPrimitive?.longOrNull,
                it["notOlderThan"]?.jsonPrimitive?.longOrNull,
            )
        }

    /**
     * Cloudflaren GraphQL-virhe käyttäjälle näytettävänä viestinä. Viesti kertoo syyn (esim. aikaväli- tai
     * oikeusraja), ja se redaktoidaan ja lyhennetään ennen tallennusta.
     */
    fun graphqlFailure(
        root: JsonObject,
        provider: String,
    ): NumerisFailure.Provider? =
        root["errors"]?.takeUnless { it is JsonNull }?.jsonArray?.firstOrNull()?.let { first ->
            val detail =
                (first as? JsonObject)
                    ?.get("message")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.let { RawJsonStoragePolicy.redactAndLimit(it, MAX_ERROR_DETAIL_CHARS) }
            NumerisFailure.Provider(if (detail.isNullOrBlank()) "$provider request failed." else "$provider: $detail")
        }

    private const val MAX_ERROR_DETAIL_CHARS = 200

    fun adaptiveStart(
        requested: Instant,
        until: Instant,
        now: Instant,
        limit: CloudflareDatasetLimit?,
    ): Instant {
        var start = requested
        limit?.notOlderThanSeconds?.let { seconds ->
            start = maxOf(start, now.minusSeconds(seconds).plusSeconds(CLOCK_MARGIN_SECONDS))
        }
        limit?.maxDurationSeconds?.let { seconds -> start = maxOf(start, until.minusSeconds(seconds)) }
        return minOf(start, until)
    }
}
