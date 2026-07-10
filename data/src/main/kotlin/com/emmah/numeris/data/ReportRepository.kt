package com.emmah.numeris.data

import com.emmah.numeris.core.database.BingPageStatEntity
import com.emmah.numeris.core.database.BingQueryStatEntity
import com.emmah.numeris.core.database.BingRankTrafficEntity
import com.emmah.numeris.core.database.BingRawItemEntity
import com.emmah.numeris.core.database.CloudflareCountryEntity
import com.emmah.numeris.core.database.CloudflarePageEntity
import com.emmah.numeris.core.database.CloudflareStatusEntity
import com.emmah.numeris.core.database.CloudflareTrafficEntity
import com.emmah.numeris.core.database.CruxMetricPointEntity
import com.emmah.numeris.core.database.GoogleAnalyticsDailyEntity
import com.emmah.numeris.core.database.GoogleAnalyticsDeviceEntity
import com.emmah.numeris.core.database.GoogleAnalyticsEventEntity
import com.emmah.numeris.core.database.GoogleAnalyticsPageEntity
import com.emmah.numeris.core.database.GoogleAnalyticsSourceEntity
import com.emmah.numeris.core.database.PageSpeedAuditEntity
import com.emmah.numeris.core.database.PageSpeedRunEntity
import com.emmah.numeris.core.database.PerformanceUrlEntity
import com.emmah.numeris.core.database.ReportDao
import com.emmah.numeris.core.database.SearchConsoleEntity
import com.emmah.numeris.core.database.SearchDeviceEntity
import com.emmah.numeris.core.database.SitemapUrlEntity
import com.emmah.numeris.core.database.UptimeCheckEntity
import com.emmah.numeris.core.database.WebAnalyticsDailyEntity
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
class ReportRepository(
    private val dao: ReportDao,
) {
    fun observeDomains(): Flow<List<String>> = dao.observeDomains()

    fun observeCloudflareTraffic(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<CloudflareTrafficEntity>> = dao.observeCloudflareTraffic(domain, start, end)

    fun observeCloudflareCountries(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<CloudflareCountryEntity>> = dao.observeCloudflareCountries(domain, start, end)

    fun observeCloudflarePages(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<CloudflarePageEntity>> = dao.observeCloudflarePages(domain, start, end)

    fun observeCloudflareStatuses(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<CloudflareStatusEntity>> = dao.observeCloudflareStatuses(domain, start, end)

    fun observeWebAnalytics(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<WebAnalyticsDailyEntity>> = dao.observeWebAnalytics(domain, start, end)

    fun observeAnalyticsDaily(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<GoogleAnalyticsDailyEntity>> = dao.observeAnalyticsDaily(domain, start, end)

    fun observeAnalyticsPages(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<GoogleAnalyticsPageEntity>> = dao.observeAnalyticsPages(domain, start, end)

    fun observeAnalyticsSources(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<GoogleAnalyticsSourceEntity>> = dao.observeAnalyticsSources(domain, start, end)

    fun observeAnalyticsEvents(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<GoogleAnalyticsEventEntity>> = dao.observeAnalyticsEvents(domain, start, end)

    fun observeAnalyticsDevices(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<GoogleAnalyticsDeviceEntity>> = dao.observeAnalyticsDevices(domain, start, end)

    fun observeSearchConsole(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<SearchConsoleEntity>> = dao.observeSearchConsole(domain, start, end)

    fun observeSearchDevices(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<SearchDeviceEntity>> = dao.observeSearchDevices(domain, start, end)

    fun observeBingTraffic(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<BingRankTrafficEntity>> = dao.observeBingTraffic(domain, start, end)

    fun observeBingQueries(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<BingQueryStatEntity>> = dao.observeBingQueries(domain, start, end)

    fun observeBingPages(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<BingPageStatEntity>> = dao.observeBingPages(domain, start, end)

    fun observeBingRaw(domain: String): Flow<List<BingRawItemEntity>> = dao.observeBingRaw(domain)

    fun observeSitemap(domain: String): Flow<List<SitemapUrlEntity>> = dao.observeSitemap(domain)

    fun observePerformanceUrls(domain: String): Flow<List<PerformanceUrlEntity>> = dao.observePerformanceUrls(domain)

    fun observeCrux(domain: String): Flow<List<CruxMetricPointEntity>> = dao.observeCrux(domain)

    fun observePageSpeedRuns(domain: String): Flow<List<PageSpeedRunEntity>> = dao.observePageSpeedRuns(domain)

    fun observePageSpeedAudits(domain: String): Flow<List<PageSpeedAuditEntity>> = dao.observePageSpeedAudits(domain)

    fun observeAllUptime(domain: String): Flow<List<UptimeCheckEntity>> = dao.observeAllUptime(domain)

    suspend fun upsertUrls(rows: List<PerformanceUrlEntity>) = dao.upsertUrls(rows)

    suspend fun setUrlEnabled(
        id: Long,
        enabled: Boolean,
    ) = dao.setUrlEnabled(id, enabled)

    suspend fun deleteUrl(id: Long) = dao.deleteUrl(id)
}
