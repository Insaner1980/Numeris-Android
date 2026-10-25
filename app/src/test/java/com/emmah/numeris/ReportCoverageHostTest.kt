package com.emmah.numeris

import com.emmah.numeris.core.database.ConnectionEntity
import com.emmah.numeris.core.database.SearchConsoleEntity
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.data.ReportRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ReportCoverageHostTest {
    @Test fun googleCoverageRequiresEverySearchSiteButIgnoresOtherProviders() =
        runBlocking {
            val database = testDatabase()
            try {
                val clock = Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC)
                val reports = ReportRepository(database, clock)
                val connection =
                    ConnectionEntity(
                        "google",
                        "search_console",
                        "configured",
                        """{"sites":["sc-domain:example.com"]}""",
                    )
                database.connectionDao().upsert(
                    listOf(
                        connection,
                        ConnectionEntity("cf", "cloudflare", "configured", """{"domain":"other.com"}"""),
                    ),
                )
                database.searchDao().upsertSearchConsole(
                    (0L..13L).map { day ->
                        SearchConsoleEntity(
                            siteUrl = "sc-domain:example.com",
                            date = LocalDate.parse("2026-10-11").minusDays(day).toString(),
                            kind = "daily",
                            query = "",
                            clicks = 1,
                            impressions = 10,
                            fetchedAt = clock.instant().toString(),
                        )
                    },
                )
                val complete = reports.observeSearch("all", Period.Last7Days, "Google").first()
                assertEquals(setOf("example.com"), complete.expectedSites)
                assertTrue(complete.comparisonReady)
                assertTrue(
                    reports
                        .observePortfolio("all", Period.Last7Days)
                        .first()
                        .google.comparisonReady,
                )

                database.connectionDao().upsert(
                    listOf(connection.copy(config = """{"sites":["sc-domain:example.com","https://missing.com/"]}""")),
                )
                assertFalse(reports.observeSearch("all", Period.Last7Days, "Google").first().comparisonReady)
                assertFalse(
                    reports
                        .observePortfolio("all", Period.Last7Days)
                        .first()
                        .google.comparisonReady,
                )
                assertTrue(reports.observeSearch("example.com", Period.Last7Days, "Google").first().comparisonReady)
                database.searchDao().deleteSearchRows("sc-domain:example.com", "daily", "2026-10-01", "2026-10-01")
                assertFalse(reports.observeSearch("example.com", Period.Last7Days, "Google").first().comparisonReady)
            } finally {
                database.close()
            }
        }
}
