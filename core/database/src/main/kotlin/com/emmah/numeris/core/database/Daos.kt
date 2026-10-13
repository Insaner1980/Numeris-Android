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

    @Query("SELECT DISTINCT source FROM connections WHERE status != 'disconnected'")
    suspend fun configuredSourceNames(): List<String>

    @Query("DELETE FROM connections WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface CloudflareDao {
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
}

@Dao
interface WebAnalyticsDao {
    @Query("DELETE FROM web_analytics_daily WHERE domain = :domain AND date BETWEEN :start AND :end")
    suspend fun deleteWebDaily(
        domain: String,
        start: String,
        end: String,
    )

    @Query("DELETE FROM web_analytics_pages WHERE domain = :domain AND date BETWEEN :start AND :end")
    suspend fun deleteWebPages(
        domain: String,
        start: String,
        end: String,
    )

    @Query("DELETE FROM web_analytics_referrers WHERE domain = :domain AND date BETWEEN :start AND :end")
    suspend fun deleteWebReferrers(
        domain: String,
        start: String,
        end: String,
    )

    @Query("DELETE FROM web_analytics_countries WHERE domain = :domain AND date BETWEEN :start AND :end")
    suspend fun deleteWebCountries(
        domain: String,
        start: String,
        end: String,
    )

    @Query("SELECT * FROM web_analytics_sites ORDER BY domain")
    suspend fun webAnalyticsSites(): List<WebAnalyticsSiteEntity>

    @Upsert suspend fun upsertWebAnalytics(rows: List<WebAnalyticsDailyEntity>)

    @Upsert suspend fun upsertWebAnalyticsSites(rows: List<WebAnalyticsSiteEntity>)

    @Upsert suspend fun upsertWebAnalyticsReferrers(rows: List<WebAnalyticsReferrerEntity>)

    @Upsert suspend fun upsertWebAnalyticsPages(rows: List<WebAnalyticsPageEntity>)

    @Upsert suspend fun upsertWebAnalyticsCountries(rows: List<WebAnalyticsCountryEntity>)

    @Query("DELETE FROM web_analytics_sites WHERE domain IN (:domains)")
    suspend fun deleteSites(domains: List<String>)
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
fun interface SyncRunDao {
    @Upsert suspend fun upsert(row: SyncRunEntity)
}

@Dao
@Suppress("TooManyFunctions")
interface ReportSnapshotDao {
    @Query(
        "SELECT * FROM cloudflare_traffic WHERE (:domain = 'all' OR domain = :domain) " +
            "AND date BETWEEN :start AND :end ORDER BY date",
    )
    suspend fun readCloudflareTraffic(
        domain: String,
        start: String,
        end: String,
    ): List<CloudflareTrafficEntity>

    @Query(
        "SELECT * FROM cloudflare_countries WHERE (:domain = 'all' OR domain = :domain) " +
            "AND date BETWEEN :start AND :end ORDER BY visitors DESC",
    )
    suspend fun readCloudflareCountries(
        domain: String,
        start: String,
        end: String,
    ): List<CloudflareCountryEntity>

    @Query(
        "SELECT * FROM cloudflare_pages WHERE (:domain = 'all' OR domain = :domain) " +
            "AND date BETWEEN :start AND :end ORDER BY requests DESC",
    )
    suspend fun readCloudflarePages(
        domain: String,
        start: String,
        end: String,
    ): List<CloudflarePageEntity>

    @Query(
        "SELECT * FROM cloudflare_status_codes WHERE (:domain = 'all' OR domain = :domain) " +
            "AND date BETWEEN :start AND :end ORDER BY requests DESC",
    )
    suspend fun readCloudflareStatuses(
        domain: String,
        start: String,
        end: String,
    ): List<CloudflareStatusEntity>

    @Query(
        "SELECT * FROM web_analytics_daily WHERE (:domain = 'all' OR domain = :domain) " +
            "AND date BETWEEN :start AND :end ORDER BY date",
    )
    suspend fun readWebAnalytics(
        domain: String,
        start: String,
        end: String,
    ): List<WebAnalyticsDailyEntity>

    @Query(
        "SELECT * FROM search_console WHERE (:domain = 'all' OR siteUrl LIKE '%' || :domain || '%') " +
            "AND date BETWEEN :start AND :end ORDER BY clicks DESC",
    )
    suspend fun readSearchConsole(
        domain: String,
        start: String,
        end: String,
    ): List<SearchConsoleEntity>

    @Query(
        "SELECT * FROM search_devices WHERE (:domain = 'all' OR siteUrl LIKE '%' || :domain || '%') " +
            "AND date BETWEEN :start AND :end ORDER BY clicks DESC",
    )
    suspend fun readSearchDevices(
        domain: String,
        start: String,
        end: String,
    ): List<SearchDeviceEntity>

    @Query(
        "SELECT * FROM bing_rank_traffic WHERE (:domain = 'all' OR siteUrl LIKE '%' || :domain || '%') " +
            "AND date BETWEEN :start AND :end ORDER BY date",
    )
    suspend fun readBingTraffic(
        domain: String,
        start: String,
        end: String,
    ): List<BingRankTrafficEntity>

    @Query(
        "SELECT * FROM bing_query_stats WHERE (:domain = 'all' OR siteUrl LIKE '%' || :domain || '%') " +
            "AND date BETWEEN :start AND :end ORDER BY clicks DESC",
    )
    suspend fun readBingQueries(
        domain: String,
        start: String,
        end: String,
    ): List<BingQueryStatEntity>

    @Query(
        "SELECT * FROM bing_page_stats WHERE (:domain = 'all' OR siteUrl LIKE '%' || :domain || '%') " +
            "AND date BETWEEN :start AND :end ORDER BY clicks DESC",
    )
    suspend fun readBingPages(
        domain: String,
        start: String,
        end: String,
    ): List<BingPageStatEntity>

    @Query(
        "SELECT * FROM bing_raw_items WHERE (:domain = 'all' OR siteUrl LIKE '%' || :domain || '%') " +
            "ORDER BY fetchedAt DESC",
    )
    suspend fun readBingRaw(domain: String): List<BingRawItemEntity>

    @Query("SELECT * FROM sitemap_urls WHERE (:domain = 'all' OR domain = :domain) ORDER BY url")
    suspend fun readSitemap(domain: String): List<SitemapUrlEntity>

    @Query("SELECT * FROM performance_urls WHERE (:domain = 'all' OR origin LIKE '%' || :domain || '%') ORDER BY url")
    suspend fun readPerformanceUrls(domain: String): List<PerformanceUrlEntity>

    @Query(
        "SELECT * FROM crux_metric_points WHERE (:domain = 'all' OR target LIKE '%' || :domain || '%') " +
            "ORDER BY collectionEnd DESC, metric",
    )
    suspend fun readCrux(domain: String): List<CruxMetricPointEntity>

    @Query(
        "SELECT * FROM pagespeed_runs WHERE (:domain = 'all' OR url LIKE '%' || :domain || '%') " +
            "ORDER BY analysisUtc DESC",
    )
    suspend fun readPageSpeedRuns(domain: String): List<PageSpeedRunEntity>

    @Query(
        "SELECT * FROM pagespeed_audits WHERE (:domain = 'all' OR url LIKE '%' || :domain || '%') " +
            "ORDER BY analysisUtc DESC, score ASC",
    )
    suspend fun readPageSpeedAudits(domain: String): List<PageSpeedAuditEntity>

    @Query("SELECT * FROM uptime_checks WHERE (:domain = 'all' OR domain = :domain) ORDER BY checkedAt DESC")
    suspend fun readAllUptime(domain: String): List<UptimeCheckEntity>

    @Query("SELECT * FROM connections ORDER BY source, id")
    suspend fun readConnections(): List<ConnectionEntity>

    @Query("SELECT * FROM search_page_queries ORDER BY periodEnd DESC, clicks DESC")
    suspend fun readPageQueries(): List<SearchPageQueryEntity>

    @Query("SELECT * FROM web_analytics_pages WHERE date BETWEEN :start AND :end")
    suspend fun readWebPages(
        start: String,
        end: String,
    ): List<WebAnalyticsPageEntity>

    @Query("SELECT * FROM web_analytics_referrers WHERE date BETWEEN :start AND :end")
    suspend fun readWebReferrers(
        start: String,
        end: String,
    ): List<WebAnalyticsReferrerEntity>

    @Query("SELECT * FROM web_analytics_countries WHERE date BETWEEN :start AND :end")
    suspend fun readWebCountries(
        start: String,
        end: String,
    ): List<WebAnalyticsCountryEntity>

    @Query(
        "SELECT siteUrl FROM search_console UNION SELECT siteUrl FROM search_devices UNION SELECT siteUrl " +
            "FROM search_page_queries UNION SELECT siteUrl FROM bing_rank_traffic UNION SELECT siteUrl " +
            "FROM bing_query_stats UNION SELECT siteUrl FROM bing_page_stats UNION SELECT siteUrl FROM " +
            "bing_sites UNION SELECT url FROM performance_urls UNION SELECT domain FROM " +
            "web_analytics_sites UNION SELECT target FROM crux_metric_points UNION SELECT url FROM " +
            "pagespeed_runs UNION SELECT domain FROM cloudflare_traffic UNION SELECT domain FROM " +
            "web_analytics_daily UNION SELECT domain FROM uptime_checks UNION SELECT domain FROM " +
            "sitemap_urls ORDER BY 1",
    )
    suspend fun readIdentities(): List<String>

    @Query(
        "SELECT (SELECT COUNT(*) FROM connections) + (SELECT COUNT(*) FROM cloudflare_traffic) + (SELECT " +
            "COUNT(*) FROM cloudflare_countries) + (SELECT COUNT(*) FROM cloudflare_pages) + (SELECT " +
            "COUNT(*) FROM cloudflare_status_codes) + (SELECT COUNT(*) FROM web_analytics_daily) + " +
            "(SELECT COUNT(*) FROM web_analytics_sites) + (SELECT COUNT(*) FROM web_analytics_pages) + " +
            "(SELECT COUNT(*) FROM web_analytics_referrers) + (SELECT COUNT(*) FROM " +
            "web_analytics_countries) + (SELECT COUNT(*) FROM search_console) + (SELECT COUNT(*) FROM " +
            "search_devices) + (SELECT COUNT(*) FROM search_page_queries) + (SELECT COUNT(*) FROM " +
            "sitemap_urls) + (SELECT COUNT(*) FROM bing_sites) + (SELECT COUNT(*) FROM bing_rank_traffic) " +
            "+ (SELECT COUNT(*) FROM bing_query_stats) + (SELECT COUNT(*) FROM bing_page_stats) + (SELECT " +
            "COUNT(*) FROM bing_raw_items) + (SELECT COUNT(*) FROM performance_urls) + (SELECT COUNT(*) " +
            "FROM crux_metric_points) + (SELECT COUNT(*) FROM pagespeed_runs) + (SELECT COUNT(*) FROM " +
            "pagespeed_audits) + (SELECT COUNT(*) FROM uptime_checks)",
    )
    fun observeChanges(): Flow<Long>
}
