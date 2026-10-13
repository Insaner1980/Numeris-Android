package com.emmah.numeris

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.model.previous
import com.emmah.numeris.data.ReportRepository
import com.emmah.numeris.data.WindowsDatabaseImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DesktopPortfolioTest {
    @Test fun sharedSnapshotMatchesLinkedDesktopPortfolioRepositoryAndPreservesAbsence() =
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext

            fun asset(name: String) =
                testFixture(name)
                    .bufferedReader()
                    .use { it.readText() }
            val database =
                Room
                    .inMemoryDatabaseBuilder(context, NumerisDatabase::class.java)
                    .setDriver(AndroidSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            val file = File(context.cacheDir, "desktop-parity-${System.nanoTime()}.db")
            try {
                val manifest = schema11Fixture(file, asset("portfolio.sql"))
                WindowsDatabaseImporter(database).replaceFromSnapshot(file, manifest)
                val clock = Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC)
                val reports = ReportRepository(database, clock)
                val reference =
                    asset("portfolio-reference.csv")
                        .lineSequence()
                        .filter(String::isNotBlank)
                        .map {
                            it.split('|')
                        }.toList()
                for (scope in listOf("all", "a.com", "b.com")) {
                    val expected = reference.filter { it[0] == "page" && it[1] == scope }.map { it.drop(2) }
                    val actual =
                        reports.observePortfolio(scope, Period.Last7Days).first().pages.map {
                            // Desktop uses zero for an absent aggregate provider.
                            // Android preserves its presence separately.
                            listOf(
                                it.domain,
                                it.path,
                                (it.pageViews ?: 0).toString(),
                                (it.googleClicks ?: 0).toString(),
                                (it.bingClicks ?: 0).toString(),
                            )
                        }
                    assertEquals("Portfolio scope $scope", expected, actual)
                }
                val webOnly =
                    reports
                        .observePortfolio("b.com", Period.Last7Days)
                        .first()
                        .pages
                        .first()
                assertNull(webOnly.googleClicks)
                assertNull(webOnly.bingClicks)
                val detailReference = reference.single { it[0] == "detail" }
                val detail = reports.observePage("a.com", "/shared/", Period.Last7Days).first()
                assertEquals(detailReference[2].toDouble(), detail.page.position!!, 0.000001)
                val googlePage =
                    reports
                        .observeSearch("a.com", Period.Last7Days, "Google")
                        .first()
                        .rows("page")
                        .single()
                assertEquals(detailReference[1].toLong(), googlePage.impressions)
                assertEquals(100.0, detail.metrics.first { it.label == "Page views" }.change!!, 0.000001)
                assertEquals(100.0, detail.metrics.first { it.label == "Google clicks" }.change!!, 0.000001)
                val counts = detail.series.take(4).map { it.points.count { point -> point.value != null } }
                assertEquals(detailReference.drop(3).map(String::toInt), counts)
                assertEquals(
                    "2026-09-28",
                    detail.range
                        .previous()
                        .start
                        .toString(),
                )
            } finally {
                database.close()
                file.delete()
            }
        }
}
