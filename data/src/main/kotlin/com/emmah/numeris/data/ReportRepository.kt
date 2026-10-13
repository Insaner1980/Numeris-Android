package com.emmah.numeris.data

import androidx.room3.withReadTransaction
import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.database.PerformanceUrlEntity
import com.emmah.numeris.core.model.DateRange
import com.emmah.numeris.core.model.HealthReport
import com.emmah.numeris.core.model.LabRun
import com.emmah.numeris.core.model.MetricWindow
import com.emmah.numeris.core.model.PageDetailReport
import com.emmah.numeris.core.model.PerformanceReport
import com.emmah.numeris.core.model.PerformanceUrl
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.model.PortfolioReport
import com.emmah.numeris.core.model.ProviderDate
import com.emmah.numeris.core.model.ReportMath
import com.emmah.numeris.core.model.ReportSeries
import com.emmah.numeris.core.model.SearchObservation
import com.emmah.numeris.core.model.SearchReport
import com.emmah.numeris.core.model.TrafficDay
import com.emmah.numeris.core.model.TrafficReport
import com.emmah.numeris.core.model.contains
import com.emmah.numeris.core.model.previous
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReportRepository(
    private val database: NumerisDatabase,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val reader = ReportReader(database, clock)
    private val dao get() = database.reportSnapshotDao()

    private fun <T> observe(read: suspend () -> T): Flow<T> =
        dao
            .observeChanges()
            .mapLatest {
                database.withReadTransaction { read() }
            }.flowOn(Dispatchers.IO)

    fun observeDomains(): Flow<List<String>> = observe { reader.domains() }

    fun observeTraffic(
        domain: String,
        period: Period,
    ): Flow<TrafficReport> = observe { reader.traffic(domain, period.toDateRange(clock)) }

    fun observeSearch(
        domain: String,
        period: Period,
        provider: String,
    ): Flow<SearchReport> =
        observe {
            reader.search(domain, period.toDateRange(clock), provider)
        }

    fun observePerformance(domain: String): Flow<PerformanceReport> = observe { reader.performance(domain) }

    fun observeHealth(
        domain: String,
        period: Period,
    ): Flow<HealthReport> = observe { reader.health(domain, period.toDateRange(clock)) }

    fun observePortfolio(
        domain: String,
        period: Period,
    ): Flow<PortfolioReport> =
        observe {
            reader.portfolio(domain, period.toDateRange(clock))
        }

    fun observePage(
        domain: String,
        path: String,
        period: Period,
    ): Flow<PageDetailReport> = observe { reader.page(domain, path, period.toDateRange(clock)) }

    suspend fun addUrl(value: String) {
        val url = PerformanceUrl.normalizePageUrl(value)
        if (dao.readPerformanceUrls("all").none { it.url == url }) {
            database.performanceDao().upsertUrls(
                listOf(
                    PerformanceUrlEntity(
                        url = url,
                        origin = PerformanceUrl.normalizeOrigin(url),
                        createdAt = Instant.now(clock).toString(),
                    ),
                ),
            )
        }
    }

    suspend fun setUrlEnabled(
        id: Long,
        enabled: Boolean,
    ) = database.performanceDao().setUrlEnabled(id, enabled)

    suspend fun deleteUrl(id: Long) = database.performanceDao().deleteUrl(id)
}

fun trafficWindow(
    rows: List<TrafficDay>,
    range: DateRange,
    expectedSites: Set<String> =
        rows
            .map {
                it.domain
            }.toSet(),
    value: (TrafficDay) -> Number,
): MetricWindow =
    ReportMath.window(
        rows.map {
            SearchObservation(it.domain, it.date, "daily", "", value(it).toLong(), null)
        },
        range,
        expectedSites,
    ) { it.clicks }

fun cacheWindow(
    rows: List<TrafficDay>,
    range: DateRange,
    expectedSites: Set<String> = rows.map { it.domain }.toSet(),
): MetricWindow {
    fun ratio(period: DateRange): Double? =
        rows.filter { it.date in period }.takeIf { it.isNotEmpty() }?.let {
            ReportMath.ratio(
                it.sumOf { row ->
                    row.cachedRequests
                },
                it.sumOf { row -> row.requests },
            )
        }
    val current = ratio(range)
    val previous = ratio(range.previous())
    return trafficWindow(rows, range, expectedSites) { it.requests }.copy(
        current = current ?: 0.0,
        previous = previous ?: 0.0,
        hasCurrent =
            current != null,
        hasPrevious = previous != null,
    )
}

fun comparisonSeries(
    label: String,
    rows: List<SearchObservation>,
    range: DateRange,
): List<ReportSeries> {
    fun points(period: DateRange): Map<LocalDate, Double> =
        rows
            .filter { it.date in period }
            .groupBy { it.date }
            .mapNotNull { (date, records) ->
                records.mapNotNull { it.clicks }.takeIf { it.isNotEmpty() }?.let { date to it.sum().toDouble() }
            }.toMap()
    val previous = ReportMath.series("Previous $label", "count", points(range.previous()), range.previous())
    return listOf(ReportMath.series(label, "count", points(range), range), previous.copy(previous = true))
}

internal fun mobileScoreWindow(
    runs: List<LabRun>,
    range: DateRange,
    domain: String,
): MetricWindow {
    if (domain == "all") return MetricWindow(0.0, 0.0, false, false, false)
    val home = runs.filter { it.device == "mobile" && it.url == "https://$domain/" && it.performance != null }

    fun latest(period: DateRange) =
        home
            .firstOrNull {
                ProviderDate.normalize(it.time)?.let(LocalDate::parse)?.let { date -> date in period } ==
                    true
            }?.performance
    val current = latest(range)
    val previous = latest(range.previous())
    return MetricWindow(
        current ?: 0.0,
        previous ?: 0.0,
        current != null,
        previous != null,
        current != null && previous != null,
    )
}
