@file:Suppress("MaxLineLength", "ktlint:standard:max-line-length")

package com.emmah.numeris.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room3.executeSQL
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import androidx.sqlite.SQLiteStatement
import com.emmah.numeris.core.database.ConnectionEntity
import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.model.SiteIdentity
import com.emmah.numeris.core.model.reportDomain
import com.emmah.numeris.core.network.RawJsonStoragePolicy
import com.emmah.numeris.core.security.ImportManifest
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext

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
            require(actualVersion == SUPPORTED_DESKTOP_SCHEMA) { "Only desktop schema 11 is supported" }
            require(
                actualVersion == manifest.windowsSchemaVersion,
            ) { "Snapshot schema version does not match the manifest" }
            require(manifest.exportVersion == 1 && manifest.rowCounts.keys.containsAll(importedTables)) {
                "Import manifest is incomplete"
            }
            insertStatements.forEach { sql ->
                val table = sql.substringAfter(" FROM imported.").substringBefore(' ')
                val required =
                    sql
                        .substringAfter(
                            " SELECT ",
                        ).substringBefore(" FROM imported.")
                        .split(',')
                        .map(String::trim)
                        .filter {
                            it !=
                                "NULL" &&
                                !it.startsWith("CASE ")
                        }
                val columns =
                    db.rawQuery("PRAGMA table_info('$table')", null).use { cursor ->
                        buildSet {
                            while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                        }
                    }
                require(columns.containsAll(required)) { "Snapshot is missing a required column" }
            }
            manifest.rowCounts.forEach { (table, expected) ->
                require(
                    expected >= 0 && table in importedTables + omittedTables,
                ) { "Import manifest contains an unsupported table" }
                val actual =
                    db.rawQuery("SELECT COUNT(*) FROM \"$table\"", null).use { cursor ->
                        cursor.moveToFirst()
                        cursor.getLong(0)
                    }
                require(actual == expected) { "Snapshot row count does not match the manifest" }
            }
            validateTargets(db)
            validateDatesAndFlags(db)
            SnapshotPreview(
                domains = readDomains(db),
                connections = 0,
                rowCounts = manifest.rowCounts.filterKeys { it in importedTables },
            )
        }
    }

    suspend fun replaceFromSnapshot(
        snapshot: File,
        manifest: ImportManifest,
        connections: List<ConnectionEntity> = emptyList(),
        transferId: String? = null,
    ) {
        validateAndPreview(snapshot, manifest)
        SQLiteDatabase.openDatabase(snapshot.path, null, SQLiteDatabase.OPEN_READONLY).use { source ->
            database.useWriterConnection { connection ->
                connection.immediateTransaction {
                    deleteStatements.forEach { executeSQL(it) }
                    for (sql in insertStatements) {
                        val table = sql.substringAfter(" FROM imported.")
                        val selection = sql.substringAfter(" SELECT ").substringBefore(" FROM imported.")
                        source.rawQuery("SELECT $selection FROM $table", null).use { rows ->
                            val insert =
                                sql.substringBefore(" SELECT ") +
                                    " VALUES (${List(rows.columnCount) { "?" }.joinToString()})"
                            usePrepared(insert) { statement ->
                                while (rows.moveToNext()) {
                                    coroutineContext.ensureActive()
                                    statement.bindRow(rows)
                                    statement.step()
                                    statement.reset()
                                }
                            }
                        }
                    }
                    executeSQL(
                        "INSERT OR REPLACE INTO main.meta (`key`, value) VALUES " +
                            "('android_import_source', 'windows'), ('android_import_format', '1')",
                    )
                    for (row in connections) {
                        usePrepared(
                            "INSERT OR REPLACE INTO connections (id,source,status,config,lastSync,lastError) VALUES (?,?,?,?,?,NULL)",
                        ) { statement ->
                            listOf(row.id, row.source, row.status, row.config, row.lastSync).forEachIndexed { index, value ->
                                if (value == null) statement.bindNull(index + 1) else statement.bindText(index + 1, value)
                            }
                            statement.step()
                        }
                    }
                    if (transferId != null) {
                        usePrepared("INSERT OR REPLACE INTO meta (`key`,value) VALUES ('android_transfer_id',?)") { statement ->
                            statement.bindText(1, transferId)
                            statement.step()
                        }
                    }
                }
            }
        }
    }

    private fun SQLiteStatement.bindRow(rows: Cursor) {
        for (index in 0 until rows.columnCount) {
            when (rows.getType(index)) {
                Cursor.FIELD_TYPE_NULL -> bindNull(index + 1)
                Cursor.FIELD_TYPE_INTEGER -> bindLong(index + 1, rows.getLong(index))
                Cursor.FIELD_TYPE_FLOAT -> bindDouble(index + 1, rows.getDouble(index))
                Cursor.FIELD_TYPE_BLOB -> bindBlob(index + 1, rows.getBlob(index))
                else -> bindText(index + 1, rows.getString(index))
            }
        }
    }

    private fun readDomains(database: SQLiteDatabase): List<String> =
        database
            .rawQuery(
                """
                SELECT domain FROM cloudflare_traffic
                UNION SELECT domain FROM web_analytics_daily
                UNION SELECT site_url FROM search_console UNION SELECT site_url FROM bing_rank_traffic UNION SELECT origin FROM performance_urls
                UNION SELECT domain FROM uptime_checks
                UNION SELECT domain FROM sitemap_urls
                UNION SELECT domain FROM web_analytics_sites
                UNION SELECT site_url FROM bing_sites
                UNION SELECT site_url FROM bing_rank_traffic
                UNION SELECT site_url FROM bing_query_stats
                UNION SELECT site_url FROM bing_page_stats
                UNION SELECT target FROM crux_metric_points
                UNION SELECT url FROM pagespeed_runs
                ORDER BY domain
                """.trimIndent(),
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) reportDomain(cursor.getString(0))?.let(::add)
                }
            }

    private fun validateTargets(db: SQLiteDatabase) {
        for ((table, column) in listOf(
            "cloudflare_traffic" to "domain",
            "web_analytics_daily" to "domain",
            "uptime_checks" to "domain",
            "sitemap_urls" to "domain",
        )) {
            db.rawQuery("SELECT DISTINCT $column FROM $table", null).use { rows ->
                while (rows.moveToNext()) {
                    require(SiteIdentity.normalizeDomain(rows.getString(0)) == rows.getString(0)) {
                        "Snapshot contains a non-normalized domain"
                    }
                }
            }
        }
        db.rawQuery("SELECT url FROM performance_urls", null).use { rows ->
            val normalized = mutableSetOf<String>()
            while (rows.moveToNext()) {
                require(normalized.add(SiteIdentity.normalizeHttpsPageUrl(rows.getString(0)))) {
                    "Snapshot contains duplicate target URLs"
                }
            }
        }
        for (table in listOf(
            "crux_metric_points",
            "pagespeed_runs",
            "bing_rank_traffic",
            "bing_query_stats",
            "bing_page_stats",
            "bing_raw_items",
        )) {
            db.rawQuery("SELECT raw_json FROM $table WHERE raw_json IS NOT NULL", null).use { rows ->
                while (rows.moveToNext()) {
                    require(
                        kotlinx.serialization.json.Json
                            .parseToJsonElement(RawJsonStoragePolicy.redactAndLimit(rows.getString(0))) ==
                            kotlinx.serialization.json.Json
                                .parseToJsonElement(rows.getString(0)),
                    ) { "Snapshot contains unsafe raw data" }
                }
            }
        }
        db.rawQuery("SELECT COUNT(*) FROM search_page_queries WHERE period_start > period_end", null).use { rows ->
            rows.moveToFirst()
            require(rows.getLong(0) == 0L) { "Snapshot contains an invalid query window" }
        }
    }

    private fun validateDatesAndFlags(db: SQLiteDatabase) {
        for (table in listOf("performance_urls", "bing_sites")) {
            db.rawQuery("SELECT COUNT(*) FROM $table WHERE enabled IS NULL OR enabled NOT IN (0,1)", null).use { rows ->
                rows.moveToFirst()
                require(rows.getLong(0) == 0L) { "Snapshot contains an invalid enabled flag" }
            }
        }
        val dated =
            listOf(
                "cloudflare_traffic",
                "cloudflare_countries",
                "cloudflare_pages",
                "cloudflare_status_codes",
                "web_analytics_daily",
                "web_analytics_pages",
                "web_analytics_referrers",
                "web_analytics_countries",
                "search_console",
                "search_devices",
                "bing_rank_traffic",
                "bing_query_stats",
                "bing_page_stats",
            )
        for (table in dated) {
            db.rawQuery("SELECT DISTINCT date FROM $table", null).use { rows ->
                while (rows.moveToNext()) {
                    val date = rows.getString(0)
                    val unknownBingDate = table.startsWith("bing_") && date.isNullOrEmpty()
                    require(
                        unknownBingDate ||
                            runCatching {
                                java.time.LocalDate
                                    .parse(date)
                                    .toString() == date
                            }.getOrDefault(false),
                    ) {
                        "Snapshot contains an invalid historical date"
                    }
                }
            }
        }
        db.rawQuery("SELECT period_start,period_end FROM search_page_queries", null).use { rows ->
            while (rows.moveToNext()) {
                require(
                    runCatching {
                        val start = java.time.LocalDate.parse(rows.getString(0))
                        val end = java.time.LocalDate.parse(rows.getString(1))
                        start <= end
                    }.getOrDefault(false),
                ) { "Snapshot contains an invalid query window" }
            }
        }
    }

    private companion object {
        const val SUPPORTED_DESKTOP_SCHEMA = 11
        val omittedTables =
            setOf(
                "connections",
                "meta",
                "play_apps",
                "play_installs",
                "play_ratings",
                "play_revenue",
                "play_crashes",
                "google_analytics_daily",
                "google_analytics_pages",
                "google_analytics_sources",
                "google_analytics_events",
                "google_analytics_devices",
            )
        val importedTables =
            setOf(
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
                "uptime_checks",
            )

        val deleteStatements =
            importedTables.map { "DELETE FROM main.$it" }

        val insertStatements =
            listOf(
                "INSERT INTO main.cloudflare_traffic (domain,date,pageviews,uniqueVisitors,requests,cachedRequests,cachedBytes,totalBytes,threats,topCountry,topPath,fetchedAt) SELECT domain,date,pageviews,unique_visitors,requests,cached_requests,cached_bytes,total_bytes,threats,top_country,top_path,fetched_at FROM imported.cloudflare_traffic",
                "INSERT INTO main.cloudflare_countries (domain,date,country,visitors) SELECT domain,date,country,visitors FROM imported.cloudflare_countries",
                "INSERT INTO main.cloudflare_pages (domain,date,path,requests) SELECT domain,date,path,requests FROM imported.cloudflare_pages",
                "INSERT INTO main.cloudflare_status_codes (domain,date,statusCode,requests) SELECT domain,date,status_code,requests FROM imported.cloudflare_status_codes",
                "INSERT INTO main.web_analytics_sites (domain,siteTag,discoveredAt) SELECT domain,site_tag,discovered_at FROM imported.web_analytics_sites",
                "INSERT INTO main.web_analytics_daily (domain,date,visits,pageViews,fetchedAt) SELECT domain,date,visits,page_views,fetched_at FROM imported.web_analytics_daily",
                "INSERT INTO main.web_analytics_referrers (domain,date,referrer,visits) SELECT domain,date,referrer,visits FROM imported.web_analytics_referrers",
                "INSERT INTO main.web_analytics_pages (domain,date,path,pageViews) SELECT domain,date,path,page_views FROM imported.web_analytics_pages",
                "INSERT INTO main.web_analytics_countries (domain,date,country,visits) SELECT domain,date,country,visits FROM imported.web_analytics_countries",
                "INSERT INTO main.search_console (id,siteUrl,date,kind,query,page,clicks,impressions,ctr,position,country,fetchedAt) SELECT id,site_url,date,CASE kind WHEN 'query' THEN 'query_daily' WHEN 'page' THEN 'page_daily' ELSE kind END,query,page,clicks,impressions,ctr,position,country,fetched_at FROM imported.search_console",
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
                "INSERT INTO main.uptime_checks (domain,checkedAt,status,statusCode,responseMs,errorMessage) SELECT domain,checked_at,status,status_code,response_ms,error_message FROM imported.uptime_checks",
            )
    }
}
