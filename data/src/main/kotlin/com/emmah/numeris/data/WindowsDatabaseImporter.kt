@file:Suppress("MaxLineLength", "ktlint:standard:max-line-length")

package com.emmah.numeris.data

import android.database.sqlite.SQLiteDatabase
import androidx.room3.executeSQL
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.security.ImportManifest
import java.io.File

data class SnapshotPreview(
    val domains: List<String>,
    val connections: Int,
    val rowCounts: Map<String, Long>,
)

class WindowsDatabaseImporter(
    private val database: NumerisDatabase,
) {
    fun validateAndPreview(
        snapshot: File,
        manifest: ImportManifest,
    ): SnapshotPreview {
        val source = SQLiteDatabase.openDatabase(snapshot.path, null, SQLiteDatabase.OPEN_READONLY)
        return source.use { db ->
            val actualVersion =
                db.rawQuery("PRAGMA user_version", null).use { cursor ->
                    cursor.moveToFirst()
                    cursor.getInt(0)
                }
            require(actualVersion == manifest.windowsSchemaVersion) { "Snapshot schema version does not match the manifest" }
            manifest.rowCounts.forEach { (table, expected) ->
                require(table in importedTables) { "Import manifest contains an unsupported table" }
                val actual =
                    db.rawQuery("SELECT COUNT(*) FROM \"$table\"", null).use { cursor ->
                        cursor.moveToFirst()
                        cursor.getLong(0)
                    }
                require(actual == expected) { "Snapshot row count does not match the manifest" }
            }
            SnapshotPreview(
                domains = readDomains(db),
                connections = manifest.rowCounts["connections"]?.toInt() ?: 0,
                rowCounts = manifest.rowCounts,
            )
        }
    }

    suspend fun replaceFromSnapshot(snapshot: File) {
        val escapedPath = snapshot.canonicalPath.replace("'", "''")
        database.useWriterConnection { connection ->
            connection.executeSQL("ATTACH DATABASE '$escapedPath' AS imported")
            try {
                connection.immediateTransaction {
                    deleteStatements.forEach { executeSQL(it) }
                    insertStatements.forEach { executeSQL(it) }
                    executeSQL(
                        "INSERT OR REPLACE INTO main.meta (`key`, value) VALUES " +
                            "('android_import_source', 'windows'), ('android_import_format', '1')",
                    )
                    executeSQL(
                        "UPDATE main.connections SET status = 'disconnected', lastSync = NULL " +
                            "WHERE source IN ('search_console', 'google_analytics')",
                    )
                }
            } finally {
                connection.executeSQL("DETACH DATABASE imported")
            }
        }
    }

    private fun readDomains(database: SQLiteDatabase): List<String> =
        database
            .rawQuery(
                """
                SELECT domain FROM cloudflare_traffic
                UNION SELECT domain FROM web_analytics_daily
                UNION SELECT domain FROM google_analytics_daily
                UNION SELECT domain FROM uptime_checks
                ORDER BY domain
                """.trimIndent(),
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }

    private companion object {
        val importedTables =
            setOf(
                "connections",
                "cloudflare_traffic",
                "cloudflare_countries",
                "cloudflare_pages",
                "cloudflare_status_codes",
                "web_analytics_sites",
                "web_analytics_daily",
                "web_analytics_referrers",
                "web_analytics_pages",
                "web_analytics_countries",
                "search_console",
                "search_devices",
                "search_page_queries",
                "sitemap_urls",
                "performance_urls",
                "crux_metric_points",
                "pagespeed_runs",
                "pagespeed_audits",
                "bing_sites",
                "bing_rank_traffic",
                "bing_query_stats",
                "bing_page_stats",
                "bing_raw_items",
                "google_analytics_daily",
                "google_analytics_pages",
                "google_analytics_sources",
                "google_analytics_events",
                "google_analytics_devices",
                "uptime_checks",
                "meta",
            )

        val deleteStatements =
            (importedTables - "meta").map { "DELETE FROM main.$it" } +
                listOf("DELETE FROM main.sync_runs", "DELETE FROM main.meta")

        val insertStatements =
            listOf(
                "INSERT INTO main.connections (id,source,status,config,lastSync,lastError) SELECT id,source,status,config,last_sync,NULL FROM imported.connections",
                "INSERT INTO main.cloudflare_traffic (domain,date,pageviews,uniqueVisitors,requests,cachedRequests,cachedBytes,totalBytes,threats,topCountry,topPath,fetchedAt) SELECT domain,date,pageviews,unique_visitors,requests,cached_requests,cached_bytes,total_bytes,threats,top_country,top_path,fetched_at FROM imported.cloudflare_traffic",
                "INSERT INTO main.cloudflare_countries (domain,date,country,visitors) SELECT domain,date,country,visitors FROM imported.cloudflare_countries",
                "INSERT INTO main.cloudflare_pages (domain,date,path,requests) SELECT domain,date,path,requests FROM imported.cloudflare_pages",
                "INSERT INTO main.cloudflare_status_codes (domain,date,statusCode,requests) SELECT domain,date,status_code,requests FROM imported.cloudflare_status_codes",
                "INSERT INTO main.web_analytics_sites (domain,siteTag,discoveredAt) SELECT domain,site_tag,discovered_at FROM imported.web_analytics_sites",
                "INSERT INTO main.web_analytics_daily (domain,date,visits,pageViews,fetchedAt) SELECT domain,date,visits,page_views,fetched_at FROM imported.web_analytics_daily",
                "INSERT INTO main.web_analytics_referrers (domain,date,referrer,visits) SELECT domain,date,referrer,visits FROM imported.web_analytics_referrers",
                "INSERT INTO main.web_analytics_pages (domain,date,path,pageViews) SELECT domain,date,path,page_views FROM imported.web_analytics_pages",
                "INSERT INTO main.web_analytics_countries (domain,date,country,visits) SELECT domain,date,country,visits FROM imported.web_analytics_countries",
                "INSERT INTO main.search_console (id,siteUrl,date,kind,query,page,clicks,impressions,ctr,position,country,fetchedAt) SELECT id,site_url,date,kind,query,page,clicks,impressions,ctr,position,country,fetched_at FROM imported.search_console",
                "INSERT INTO main.search_devices (siteUrl,date,device,clicks,impressions,ctr,position) SELECT site_url,date,device,clicks,impressions,ctr,position FROM imported.search_devices",
                "INSERT INTO main.search_page_queries (siteUrl,periodStart,periodEnd,page,query,clicks,impressions,ctr,position) SELECT site_url,period_start,period_end,page,query,clicks,impressions,ctr,position FROM imported.search_page_queries",
                "INSERT INTO main.sitemap_urls (domain,url,discoveredAt,lastSeenAt,removedAt,lastInspectedAt,verdict,coverageState,indexingState,robotsTxtState,pageFetchState,crawledAs,lastCrawlTime) SELECT domain,url,discovered_at,last_seen_at,removed_at,last_inspected_at,verdict,coverage_state,indexing_state,robots_txt_state,page_fetch_state,crawled_as,last_crawl_time FROM imported.sitemap_urls",
                "INSERT INTO main.performance_urls (id,url,origin,source,enabled,createdAt) SELECT id,url,origin,source,enabled,created_at FROM imported.performance_urls",
                "INSERT INTO main.crux_metric_points (targetType,target,formFactor,collectionStart,collectionEnd,metric,p75,goodDensity,needsImprovementDensity,poorDensity,rawJson,fetchedAt) SELECT target_type,target,form_factor,collection_start,collection_end,metric,p75,good_density,needs_improvement_density,poor_density,raw_json,fetched_at FROM imported.crux_metric_points",
                "INSERT INTO main.pagespeed_runs (url,strategy,analysisUtc,finalUrl,performanceScore,accessibilityScore,bestPracticesScore,seoScore,lighthouseVersion,runtimeError,warningsJson,rawJson,fetchedAt) SELECT url,strategy,analysis_utc,final_url,performance_score,accessibility_score,best_practices_score,seo_score,lighthouse_version,runtime_error,warnings_json,raw_json,fetched_at FROM imported.pagespeed_runs",
                "INSERT INTO main.pagespeed_audits (url,strategy,analysisUtc,auditId,title,score,numericValue,numericUnit,displayValue,scoreDisplayMode,detailsJson) SELECT url,strategy,analysis_utc,audit_id,title,score,numeric_value,numeric_unit,display_value,score_display_mode,details_json FROM imported.pagespeed_audits",
                "INSERT INTO main.bing_sites (siteUrl,source,enabled,discoveredAt) SELECT site_url,source,enabled,discovered_at FROM imported.bing_sites",
                "INSERT INTO main.bing_rank_traffic (siteUrl,date,clicks,impressions,rawJson,fetchedAt) SELECT site_url,date,clicks,impressions,raw_json,fetched_at FROM imported.bing_rank_traffic",
                "INSERT INTO main.bing_query_stats (siteUrl,query,date,clicks,impressions,avgClickPosition,avgImpressionPosition,rawJson,fetchedAt) SELECT site_url,query,date,clicks,impressions,avg_click_position,avg_impression_position,raw_json,fetched_at FROM imported.bing_query_stats",
                "INSERT INTO main.bing_page_stats (siteUrl,pageUrl,date,clicks,impressions,rawJson,fetchedAt) SELECT site_url,page_url,date,clicks,impressions,raw_json,fetched_at FROM imported.bing_page_stats",
                "INSERT INTO main.bing_raw_items (method,siteUrl,itemKey,rawJson,fetchedAt) SELECT method,site_url,item_key,raw_json,fetched_at FROM imported.bing_raw_items",
                "INSERT INTO main.google_analytics_daily (domain,propertyId,date,activeUsers,sessions,pageViews,engagedSessions,eventCount,engagementRate,fetchedAt) SELECT domain,property_id,date,active_users,sessions,page_views,engaged_sessions,event_count,engagement_rate,fetched_at FROM imported.google_analytics_daily",
                "INSERT INTO main.google_analytics_pages (domain,propertyId,periodStart,periodEnd,pagePath,activeUsers,sessions,pageViews,engagedSessions,engagementRate,fetchedAt) SELECT domain,property_id,period_start,period_end,page_path,active_users,sessions,page_views,engaged_sessions,engagement_rate,fetched_at FROM imported.google_analytics_pages",
                "INSERT INTO main.google_analytics_sources (domain,propertyId,periodStart,periodEnd,sourceMedium,sessions,activeUsers,keyEvents,fetchedAt) SELECT domain,property_id,period_start,period_end,source_medium,sessions,active_users,key_events,fetched_at FROM imported.google_analytics_sources",
                "INSERT INTO main.google_analytics_events (domain,propertyId,periodStart,periodEnd,eventName,eventCount,keyEvents,fetchedAt) SELECT domain,property_id,period_start,period_end,event_name,event_count,key_events,fetched_at FROM imported.google_analytics_events",
                "INSERT INTO main.google_analytics_devices (domain,propertyId,date,deviceCategory,sessions,activeUsers,fetchedAt) SELECT domain,property_id,date,device_category,sessions,active_users,fetched_at FROM imported.google_analytics_devices",
                "INSERT INTO main.uptime_checks (domain,checkedAt,status,statusCode,responseMs,errorMessage) SELECT domain,checked_at,status,status_code,response_ms,error_message FROM imported.uptime_checks",
                "INSERT INTO main.meta (`key`,value) SELECT `key`,value FROM imported.meta",
            )
    }
}
