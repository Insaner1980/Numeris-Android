package com.emmah.numeris.core.model

import java.time.LocalDate
import kotlin.math.round

object ReportMath {
    private const val PERCENT_TENTHS = 1000
    private const val TENTHS = 10

    fun ratio(
        part: Number?,
        total: Number?,
    ): Double? = if (part != null && total != null && total.toDouble() > 0) part.toDouble() / total.toDouble() else null

    fun dates(range: DateRange): List<LocalDate> = (0 until range.days).map { range.start.plusDays(it.toLong()) }

    fun series(
        label: String,
        unit: String,
        values: Map<LocalDate, Double>,
        range: DateRange,
    ): ReportSeries = ReportSeries(label, unit, dates(range).map { DatedValue(it, values[it]) })

    fun complete(
        rows: List<SearchObservation>,
        range: DateRange,
        expectedSites: Set<String> = rows.map { it.domain }.toSet(),
    ): Boolean {
        val sites = expectedSites + rows.map { it.domain }
        return sites.isNotEmpty() &&
            sites.all { site ->
                val dates = rows.filter { it.domain == site }.map { it.date }.toSet()
                dates(range).all { it in dates } && dates(range.previous()).all { it in dates }
            }
    }

    fun window(
        rows: List<SearchObservation>,
        range: DateRange,
        expectedSites: Set<String> =
            rows
                .map {
                    it.domain
                }.toSet(),
        value: (SearchObservation) -> Number?,
    ): MetricWindow {
        fun sum(period: DateRange): Double? {
            val values = rows.filter { it.date in period }.mapNotNull(value)
            return values.takeIf { it.isNotEmpty() }?.sumOf { it.toDouble() }
        }
        val current = sum(range)
        val previous = sum(range.previous())
        return MetricWindow(
            current ?: 0.0,
            previous ?: 0.0,
            current != null,
            previous != null,
            complete(rows, range, expectedSites),
        )
    }

    fun change(window: MetricWindow): Double? =
        if (window.hasCurrent && window.hasPrevious && window.hasComparison) {
            if (window.previous <=
                0
            ) {
                null
            } else {
                round((window.current - window.previous) / window.previous * PERCENT_TENTHS) / TENTHS
            }
        } else {
            null
        }

    fun searchRows(
        rows: List<SearchObservation>,
        range: DateRange,
        comparison: Boolean,
    ): List<SearchRow> {
        val previous = rows.filter { it.date in range.previous() }.groupBy { it.domain to it.identity }
        return rows
            .filter { it.date in range }
            .groupBy { it.domain to it.identity }
            .map { (key, records) ->
                val clicks = records.mapNotNull { it.clicks }.takeIf { it.isNotEmpty() }?.sum()
                val impressions = records.mapNotNull { it.impressions }.takeIf { it.isNotEmpty() }?.sum()
                val weighted = records.filter { it.position != null && (it.impressions ?: 0) > 0 }
                SearchRow(
                    key.first,
                    key.second,
                    clicks,
                    impressions,
                    ratio(clicks, impressions),
                    weighted.takeIf { it.isNotEmpty() }?.let { list ->
                        list.sumOf { it.position!! * it.impressions!! } / list.sumOf { it.impressions!! }
                    },
                    if (comparison) previous[key]?.mapNotNull { it.clicks }?.sum() ?: 0 else null,
                )
            }.sortedWith(compareByDescending<SearchRow> { it.clicks }.thenBy { it.domain }.thenBy { it.identity })
    }

    fun uptime(probes: List<Probe>): Double? =
        probes
            .groupBy { it.domain }
            .values
            .map { rows -> rows.count { it.status.equals("up", true) }.toDouble() / rows.size }
            .takeIf { it.isNotEmpty() }
            ?.average()

    fun indexing(pages: List<SitemapPage>): IndexingSummary {
        val active = pages.filter { it.removedAt == null }
        val inspected = active.filter { it.inspectedAt != null }
        return IndexingSummary(active.size, inspected.size, inspected.count { it.verdict == "PASS" })
    }

    fun vitals(metrics: List<FieldMetric>): String {
        val required = listOf("largest_contentful_paint", "interaction_to_next_paint", "cumulative_layout_shift")
        val ratings =
            required.map { metric ->
                metrics
                    .filter { it.metric == metric }
                    .maxByOrNull { it.end }
                    ?.p75
                    ?.let { vitalRating(metric, it) }
            }
        if (ratings.any { it == null }) return "No field data"
        return when {
            VitalRating.Poor in ratings -> "Fail"
            VitalRating.NeedsWork in ratings -> "Needs work"
            else -> "Pass"
        }
    }

    /** Googlen Core Web Vitals -kynnysarvot p75-arvolle. Tuntemattomalle mittarille ei anneta luokitusta. */
    fun vitalRating(
        metric: String,
        p75: Double,
    ): VitalRating? {
        val (good, poor) = vitalThresholds[metric] ?: return null
        return when {
            p75 > poor -> VitalRating.Poor
            p75 > good -> VitalRating.NeedsWork
            else -> VitalRating.Good
        }
    }

    private val vitalThresholds =
        mapOf(
            "largest_contentful_paint" to (2500.0 to 4000.0),
            "interaction_to_next_paint" to (200.0 to 500.0),
            "cumulative_layout_shift" to (0.1 to 0.25),
            "first_contentful_paint" to (1800.0 to 3000.0),
            "experimental_time_to_first_byte" to (800.0 to 1800.0),
        )
}

enum class VitalRating { Good, NeedsWork, Poor }

operator fun DateRange.contains(date: LocalDate): Boolean = date >= start && date <= end

fun DateRange.previous(): DateRange = DateRange(start.minusDays(days.toLong()), start.minusDays(1), days)

fun reportDomain(value: String): String? =
    runCatching {
        SiteIdentity.normalizeDomain(value.removePrefix("sc-domain:"))
    }.getOrNull()

fun DateRange.withPrevious(): DateRange = copy(start = start.minusDays(days.toLong()), days = days * 2)
