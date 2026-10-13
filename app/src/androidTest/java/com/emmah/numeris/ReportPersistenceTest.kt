package com.emmah.numeris

import com.emmah.numeris.core.database.CloudflareTrafficEntity
import com.emmah.numeris.core.database.ConnectionEntity
import com.emmah.numeris.core.database.PageSpeedRunEntity
import com.emmah.numeris.core.database.SearchConsoleEntity
import com.emmah.numeris.core.database.UptimeCheckEntity
import com.emmah.numeris.core.database.writeTransaction
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.model.ReportMath
import com.emmah.numeris.data.ReportRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ReportPersistenceTest {
    @Test fun coherentReportsIncludeConfiguredAndSearchOnlySitesAndAllUptimeHistory() =
        runBlocking {
            val database = testDatabase()
            try {
                val clock = Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC)
                database.writeTransaction {
                    connectionDao().upsert(
                        listOf(
                            ConnectionEntity(
                                "cf:configured",
                                "cloudflare",
                                "configured",
                                "{\"domain\":\"configured.com\",\"zoneId\":\"fake\"}",
                            ),
                        ),
                    )
                    searchDao().upsertSearchConsole(
                        listOf(
                            SearchConsoleEntity(
                                siteUrl = "sc-domain:search.com",
                                date = "2026-10-11",
                                kind = "daily",
                                query = "",
                                clicks = 20,
                                impressions = 100,
                                fetchedAt = "2026-10-11T10:00:00Z",
                            ),
                        ),
                    )
                    healthDao().upsertChecks(
                        (0..600).map { index ->
                            UptimeCheckEntity(
                                domain = "search.com",
                                checkedAt =
                                    Instant
                                        .parse(
                                            "2026-10-11T10:00:00Z",
                                        ).minusSeconds(index.toLong())
                                        .toString(),
                                status =
                                    if (index ==
                                        600
                                    ) {
                                        "timeout"
                                    } else {
                                        "up"
                                    },
                                responseMs = if (index == 600) null else 100,
                            )
                        },
                    )
                }
                val repository = ReportRepository(database, clock)
                assertEquals(listOf("configured.com", "search.com"), repository.observeDomains().first())
                val report = repository.observePortfolio("all", Period.Last7Days).first()
                assertTrue(report.hasCache)
                assertEquals(601, report.health.probes.size)
                assertEquals(600.0 / 601, ReportMath.uptime(report.health.probes)!!, 0.000001)
                assertNull(report.sites.first { it.domain == "configured.com" }.visitors)
                assertEquals(20L, report.sites.first { it.domain == "search.com" }.googleClicks)
                assertFalse(report.google.comparisonReady)
            } finally {
                database.close()
            }
        }

    @Test fun failedTransactionPreservesCacheAndUrlChangesPreserveHistory() =
        runBlocking {
            val database = testDatabase()
            try {
                val date = LocalDate.now().toString()
                database.cloudflareDao().upsertTraffic(
                    listOf(CloudflareTrafficEntity("a.com", date, uniqueVisitors = 5, fetchedAt = "now")),
                )
                try {
                    database.writeTransaction {
                        cloudflareDao().upsertTraffic(
                            listOf(CloudflareTrafficEntity("a.com", date, uniqueVisitors = 99, fetchedAt = "now")),
                        )
                        error("Injected fixture failure")
                    }
                } catch (_: IllegalStateException) {
                    // Expected rollback.
                }
                val repository = ReportRepository(database)
                assertEquals(
                    5L,
                    repository
                        .observeTraffic("a.com", Period.Last7Days)
                        .first()
                        .days
                        .single()
                        .visitors,
                )
                repository.addUrl("https://a.com/path?lang=en#fragment")
                repository.addUrl("https://a.com/path/?lang=en")
                val url =
                    repository
                        .observePerformance("a.com")
                        .first()
                        .urls
                        .single()
                assertEquals("https://a.com/path/?lang=en", url.url)
                database.performanceDao().upsertPageSpeedRuns(
                    listOf(
                        PageSpeedRunEntity(
                            url.url,
                            "mobile",
                            "2026-10-11T10:00:00Z",
                            performanceScore = 0.6,
                            rawJson = "{}",
                            fetchedAt = "now",
                        ),
                    ),
                )
                repository.setUrlEnabled(url.id, false)
                repository.deleteUrl(url.id)
                assertEquals(
                    1,
                    repository
                        .observePerformance("a.com")
                        .first()
                        .runs.size,
                )
            } finally {
                database.close()
            }
        }
}
