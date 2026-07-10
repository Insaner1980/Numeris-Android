package com.emmah.numeris.data

import com.emmah.numeris.core.database.OverviewDao
import com.emmah.numeris.core.model.Period
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class OverviewSnapshot(
    val cloudflareVisitors: Long,
    val googleClicks: Long,
    val bingClicks: Long,
    val pageSpeedMobile: Double?,
    val configuredSources: Int,
    val totalSources: Int,
    val latestSuccessfulSync: String?,
)

class OverviewRepository(
    private val dao: OverviewDao,
) {
    fun observe(
        domain: String,
        period: Period,
    ): Flow<OverviewSnapshot> {
        val range = period.toDateRange()
        val start = range.start.toString()
        val end = range.end.toString()
        val reporting =
            combine(
                dao.observeCloudflareVisitors(domain, start, end),
                dao.observeGoogleClicks(domain, start, end),
                dao.observeBingClicks(domain, start, end),
                dao.observePageSpeedMobile(domain),
            ) { cloudflare, google, bing, pageSpeed -> ReportingValues(cloudflare, google, bing, pageSpeed) }
        val sources =
            combine(
                dao.observeConfiguredConnections(),
                dao.observeConnectionCount(),
                dao.observeLatestSync(),
            ) { configured, total, latest -> SourceValues(configured, total, latest) }
        return combine(reporting, sources) { values, sourceValues ->
            OverviewSnapshot(
                cloudflareVisitors = values.cloudflare,
                googleClicks = values.google,
                bingClicks = values.bing,
                pageSpeedMobile = values.pageSpeed,
                configuredSources = sourceValues.configured,
                totalSources = sourceValues.total,
                latestSuccessfulSync = sourceValues.latest,
            )
        }
    }

    private data class ReportingValues(
        val cloudflare: Long,
        val google: Long,
        val bing: Long,
        val pageSpeed: Double?,
    )

    private data class SourceValues(
        val configured: Int,
        val total: Int,
        val latest: String?,
    )
}
