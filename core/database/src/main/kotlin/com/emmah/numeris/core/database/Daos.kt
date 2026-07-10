package com.emmah.numeris.core.database

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionDao {
    @Query("SELECT * FROM connections ORDER BY source, id")
    fun observeConnections(): Flow<List<ConnectionEntity>>

    @Upsert suspend fun upsert(rows: List<ConnectionEntity>)

    @Query("SELECT * FROM connections WHERE id = :id")
    suspend fun find(id: String): ConnectionEntity?

    @Query("SELECT * FROM connections WHERE source = :source ORDER BY id")
    suspend fun bySource(source: String): List<ConnectionEntity>

    @Query("SELECT DISTINCT source FROM connections WHERE status = 'configured'")
    suspend fun configuredSourceNames(): List<String>

    @Query("DELETE FROM connections WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface CloudflareDao {
    @Query("SELECT * FROM web_analytics_sites ORDER BY domain")
    suspend fun webAnalyticsSites(): List<WebAnalyticsSiteEntity>

    @Query("SELECT * FROM cloudflare_traffic WHERE domain = :domain AND date BETWEEN :start AND :end ORDER BY date")
    fun observeTraffic(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<CloudflareTrafficEntity>>

    @Upsert suspend fun upsertTraffic(rows: List<CloudflareTrafficEntity>)

    @Upsert suspend fun upsertCountries(rows: List<CloudflareCountryEntity>)

    @Upsert suspend fun upsertPages(rows: List<CloudflarePageEntity>)

    @Upsert suspend fun upsertStatuses(rows: List<CloudflareStatusEntity>)

    @Upsert suspend fun upsertWebAnalytics(rows: List<WebAnalyticsDailyEntity>)

    @Upsert suspend fun upsertWebAnalyticsSites(rows: List<WebAnalyticsSiteEntity>)

    @Upsert suspend fun upsertWebAnalyticsReferrers(rows: List<WebAnalyticsReferrerEntity>)

    @Upsert suspend fun upsertWebAnalyticsPages(rows: List<WebAnalyticsPageEntity>)

    @Upsert suspend fun upsertWebAnalyticsCountries(rows: List<WebAnalyticsCountryEntity>)
}

@Dao
interface SearchDao {
    @Query("DELETE FROM search_console WHERE siteUrl = :siteUrl AND kind = :kind AND date BETWEEN :start AND :end")
    suspend fun deleteSearchRows(
        siteUrl: String,
        kind: String,
        start: String,
        end: String,
    )

    @Upsert suspend fun upsertSearchConsole(rows: List<SearchConsoleEntity>)

    @Upsert suspend fun upsertSearchDevices(rows: List<SearchDeviceEntity>)

    @Upsert suspend fun upsertSearchPageQueries(rows: List<SearchPageQueryEntity>)

    @Query("SELECT * FROM sitemap_urls WHERE domain = :domain")
    suspend fun sitemapUrls(domain: String): List<SitemapUrlEntity>

    @Upsert suspend fun upsertSitemapUrls(rows: List<SitemapUrlEntity>)
}

@Dao
interface PerformanceDao {
    @Query("SELECT * FROM performance_urls WHERE enabled = 1 ORDER BY url")
    suspend fun enabledUrls(): List<PerformanceUrlEntity>

    @Upsert suspend fun upsertUrls(rows: List<PerformanceUrlEntity>)

    @Query("UPDATE performance_urls SET enabled = :enabled WHERE id = :id")
    suspend fun setUrlEnabled(
        id: Long,
        enabled: Boolean,
    )

    @Query("DELETE FROM performance_urls WHERE id = :id")
    suspend fun deleteUrl(id: Long)

    @Upsert suspend fun upsertCruxMetrics(rows: List<CruxMetricPointEntity>)

    @Upsert suspend fun upsertPageSpeedRuns(rows: List<PageSpeedRunEntity>)

    @Upsert suspend fun upsertPageSpeedAudits(rows: List<PageSpeedAuditEntity>)

    @Query("DELETE FROM pagespeed_runs WHERE analysisUtc < :cutoff")
    suspend fun deleteOldPageSpeedRuns(cutoff: String): Int

    @Query(
        "DELETE FROM pagespeed_runs AS old WHERE (SELECT COUNT(*) FROM pagespeed_runs AS newer " +
            "WHERE newer.url = old.url AND newer.strategy = old.strategy " +
            "AND newer.analysisUtc >= old.analysisUtc) > :keepPerTarget",
    )
    suspend fun retainLatestPageSpeedRuns(keepPerTarget: Int): Int

    @Query(
        "DELETE FROM pagespeed_audits WHERE NOT EXISTS (SELECT 1 FROM pagespeed_runs run " +
            "WHERE run.url = pagespeed_audits.url AND run.strategy = pagespeed_audits.strategy " +
            "AND run.analysisUtc = pagespeed_audits.analysisUtc)",
    )
    suspend fun deleteOrphanedPageSpeedAudits(): Int
}

@Dao
interface GoogleAnalyticsDao {
    @Query("SELECT * FROM google_analytics_daily WHERE domain = :domain AND date BETWEEN :start AND :end ORDER BY date")
    fun observeGoogleAnalytics(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<GoogleAnalyticsDailyEntity>>

    @Upsert suspend fun upsertGoogleAnalytics(rows: List<GoogleAnalyticsDailyEntity>)

    @Upsert suspend fun upsertGoogleAnalyticsPages(rows: List<GoogleAnalyticsPageEntity>)

    @Upsert suspend fun upsertGoogleAnalyticsSources(rows: List<GoogleAnalyticsSourceEntity>)

    @Upsert suspend fun upsertGoogleAnalyticsEvents(rows: List<GoogleAnalyticsEventEntity>)

    @Upsert suspend fun upsertGoogleAnalyticsDevices(rows: List<GoogleAnalyticsDeviceEntity>)
}

@Dao
interface BingDao {
    @Query("SELECT * FROM bing_sites WHERE enabled = 1 ORDER BY siteUrl")
    suspend fun enabledSites(): List<BingSiteEntity>

    @Upsert suspend fun upsertTraffic(rows: List<BingRankTrafficEntity>)

    @Upsert suspend fun upsertSites(rows: List<BingSiteEntity>)

    @Query("DELETE FROM bing_sites")
    suspend fun deleteAllSites()

    @Upsert suspend fun upsertQueries(rows: List<BingQueryStatEntity>)

    @Upsert suspend fun upsertPages(rows: List<BingPageStatEntity>)

    @Upsert suspend fun upsertRawItems(rows: List<BingRawItemEntity>)

    @Query("DELETE FROM bing_rank_traffic WHERE date < :cutoff")
    suspend fun deleteOldTraffic(cutoff: String): Int

    @Query("DELETE FROM bing_raw_items WHERE fetchedAt < :cutoff")
    suspend fun deleteOldRawItems(cutoff: String): Int
}

@Dao
interface HealthDao {
    @Query("SELECT * FROM uptime_checks WHERE domain = :domain ORDER BY checkedAt DESC LIMIT :limit")
    fun observeUptime(
        domain: String,
        limit: Int,
    ): Flow<List<UptimeCheckEntity>>

    @Upsert suspend fun upsertChecks(rows: List<UptimeCheckEntity>)
}

@Dao
interface SyncRunDao {
    @Upsert suspend fun upsert(row: SyncRunEntity)
}

@Dao
interface OverviewDao {
    @Query(
        "SELECT COALESCE(SUM(uniqueVisitors), 0) FROM cloudflare_traffic " +
            "WHERE (:domain = 'all' OR domain = :domain) AND date BETWEEN :start AND :end",
    )
    fun observeCloudflareVisitors(
        domain: String,
        start: String,
        end: String,
    ): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(clicks), 0) FROM search_console WHERE kind = 'daily' " +
            "AND (:domain = 'all' OR siteUrl LIKE '%' || :domain || '%') AND date BETWEEN :start AND :end",
    )
    fun observeGoogleClicks(
        domain: String,
        start: String,
        end: String,
    ): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(clicks), 0) FROM bing_rank_traffic " +
            "WHERE (:domain = 'all' OR siteUrl LIKE '%' || :domain || '%') AND date BETWEEN :start AND :end",
    )
    fun observeBingClicks(
        domain: String,
        start: String,
        end: String,
    ): Flow<Long>

    @Query(
        "SELECT performanceScore FROM pagespeed_runs WHERE strategy = 'mobile' " +
            "AND (:domain = 'all' OR url LIKE '%' || :domain || '%') ORDER BY analysisUtc DESC LIMIT 1",
    )
    fun observePageSpeedMobile(domain: String): Flow<Double?>

    @Query("SELECT COUNT(*) FROM connections WHERE status != 'disconnected'")
    fun observeConfiguredConnections(): Flow<Int>

    @Query("SELECT COUNT(*) FROM connections")
    fun observeConnectionCount(): Flow<Int>

    @Query("SELECT completedAt FROM sync_runs WHERE state = 'success' ORDER BY completedAt DESC LIMIT 1")
    fun observeLatestSync(): Flow<String?>
}

@Dao
@Suppress("TooManyFunctions")
interface ReportDao {
    @Query(
        "SELECT domain FROM cloudflare_traffic UNION SELECT domain FROM web_analytics_daily " +
            "UNION SELECT domain FROM google_analytics_daily UNION SELECT domain FROM sitemap_urls " +
            "UNION SELECT domain FROM uptime_checks ORDER BY domain",
    )
    fun observeDomains(): Flow<List<String>>

    @Upsert
    suspend fun upsertUrls(rows: List<PerformanceUrlEntity>)

    @Query("UPDATE performance_urls SET enabled = :enabled WHERE id = :id")
    suspend fun setUrlEnabled(
        id: Long,
        enabled: Boolean,
    )

    @Query("DELETE FROM performance_urls WHERE id = :id")
    suspend fun deleteUrl(id: Long)

    @Query(
        "SELECT * FROM cloudflare_traffic WHERE (:domain = 'all' OR domain = :domain) " +
            "AND date BETWEEN :start AND :end ORDER BY date",
    )
    fun observeCloudflareTraffic(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<CloudflareTrafficEntity>>

    @Query(
        "SELECT * FROM cloudflare_countries WHERE (:domain = 'all' OR domain = :domain) " +
            "AND date BETWEEN :start AND :end ORDER BY visitors DESC",
    )
    fun observeCloudflareCountries(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<CloudflareCountryEntity>>

    @Query(
        "SELECT * FROM cloudflare_pages WHERE (:domain = 'all' OR domain = :domain) " +
            "AND date BETWEEN :start AND :end ORDER BY requests DESC",
    )
    fun observeCloudflarePages(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<CloudflarePageEntity>>

    @Query(
        "SELECT * FROM cloudflare_status_codes WHERE (:domain = 'all' OR domain = :domain) " +
            "AND date BETWEEN :start AND :end ORDER BY requests DESC",
    )
    fun observeCloudflareStatuses(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<CloudflareStatusEntity>>

    @Query(
        "SELECT * FROM web_analytics_daily WHERE (:domain = 'all' OR domain = :domain) " +
            "AND date BETWEEN :start AND :end ORDER BY date",
    )
    fun observeWebAnalytics(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<WebAnalyticsDailyEntity>>

    @Query(
        "SELECT * FROM google_analytics_daily WHERE (:domain = 'all' OR domain = :domain) " +
            "AND date BETWEEN :start AND :end ORDER BY date",
    )
    fun observeAnalyticsDaily(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<GoogleAnalyticsDailyEntity>>

    @Query(
        "SELECT * FROM google_analytics_pages WHERE (:domain = 'all' OR domain = :domain) " +
            "AND periodEnd >= :start AND periodStart <= :end ORDER BY pageViews DESC",
    )
    fun observeAnalyticsPages(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<GoogleAnalyticsPageEntity>>

    @Query(
        "SELECT * FROM google_analytics_sources WHERE (:domain = 'all' OR domain = :domain) " +
            "AND periodEnd >= :start AND periodStart <= :end ORDER BY sessions DESC",
    )
    fun observeAnalyticsSources(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<GoogleAnalyticsSourceEntity>>

    @Query(
        "SELECT * FROM google_analytics_events WHERE (:domain = 'all' OR domain = :domain) " +
            "AND periodEnd >= :start AND periodStart <= :end ORDER BY eventCount DESC",
    )
    fun observeAnalyticsEvents(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<GoogleAnalyticsEventEntity>>

    @Query(
        "SELECT * FROM google_analytics_devices WHERE (:domain = 'all' OR domain = :domain) " +
            "AND date BETWEEN :start AND :end ORDER BY sessions DESC",
    )
    fun observeAnalyticsDevices(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<GoogleAnalyticsDeviceEntity>>

    @Query(
        "SELECT * FROM search_console WHERE (:domain = 'all' OR siteUrl LIKE '%' || :domain || '%') " +
            "AND date BETWEEN :start AND :end ORDER BY clicks DESC",
    )
    fun observeSearchConsole(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<SearchConsoleEntity>>

    @Query(
        "SELECT * FROM search_devices WHERE (:domain = 'all' OR siteUrl LIKE '%' || :domain || '%') " +
            "AND date BETWEEN :start AND :end ORDER BY clicks DESC",
    )
    fun observeSearchDevices(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<SearchDeviceEntity>>

    @Query(
        "SELECT * FROM bing_rank_traffic WHERE (:domain = 'all' OR siteUrl LIKE '%' || :domain || '%') " +
            "AND date BETWEEN :start AND :end ORDER BY date",
    )
    fun observeBingTraffic(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<BingRankTrafficEntity>>

    @Query(
        "SELECT * FROM bing_query_stats WHERE (:domain = 'all' OR siteUrl LIKE '%' || :domain || '%') " +
            "AND date BETWEEN :start AND :end ORDER BY clicks DESC",
    )
    fun observeBingQueries(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<BingQueryStatEntity>>

    @Query(
        "SELECT * FROM bing_page_stats WHERE (:domain = 'all' OR siteUrl LIKE '%' || :domain || '%') " +
            "AND date BETWEEN :start AND :end ORDER BY clicks DESC",
    )
    fun observeBingPages(
        domain: String,
        start: String,
        end: String,
    ): Flow<List<BingPageStatEntity>>

    @Query(
        "SELECT * FROM bing_raw_items WHERE (:domain = 'all' OR siteUrl LIKE '%' || :domain || '%') " +
            "ORDER BY fetchedAt DESC",
    )
    fun observeBingRaw(domain: String): Flow<List<BingRawItemEntity>>

    @Query("SELECT * FROM sitemap_urls WHERE (:domain = 'all' OR domain = :domain) ORDER BY url")
    fun observeSitemap(domain: String): Flow<List<SitemapUrlEntity>>

    @Query("SELECT * FROM performance_urls WHERE (:domain = 'all' OR origin LIKE '%' || :domain || '%') ORDER BY url")
    fun observePerformanceUrls(domain: String): Flow<List<PerformanceUrlEntity>>

    @Query(
        "SELECT * FROM crux_metric_points WHERE (:domain = 'all' OR target LIKE '%' || :domain || '%') " +
            "ORDER BY collectionEnd DESC, metric",
    )
    fun observeCrux(domain: String): Flow<List<CruxMetricPointEntity>>

    @Query(
        "SELECT * FROM pagespeed_runs WHERE (:domain = 'all' OR url LIKE '%' || :domain || '%') " +
            "ORDER BY analysisUtc DESC",
    )
    fun observePageSpeedRuns(domain: String): Flow<List<PageSpeedRunEntity>>

    @Query(
        "SELECT * FROM pagespeed_audits WHERE (:domain = 'all' OR url LIKE '%' || :domain || '%') " +
            "ORDER BY analysisUtc DESC, score ASC",
    )
    fun observePageSpeedAudits(domain: String): Flow<List<PageSpeedAuditEntity>>

    @Query("SELECT * FROM uptime_checks WHERE (:domain = 'all' OR domain = :domain) ORDER BY checkedAt DESC LIMIT 500")
    fun observeAllUptime(domain: String): Flow<List<UptimeCheckEntity>>
}
