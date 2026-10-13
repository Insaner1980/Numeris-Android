package com.emmah.numeris

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.numeris.core.database.CruxMetricPointEntity
import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.database.PageSpeedRunEntity
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.model.ReportMath
import com.emmah.numeris.data.ReportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceScopeTest {
    @Test fun pageDetailKeepsExactUrlDeviceAndLatestCollectionWithoutSubstitutingAnotherRun() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val database =
                Room
                    .inMemoryDatabaseBuilder(context, NumerisDatabase::class.java)
                    .setDriver(AndroidSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            val url = "https://fixture.com/leaf/"
            try {
                database.performanceDao().upsertPageSpeedRuns(
                    listOf(
                        PageSpeedRunEntity(url, "mobile", "2026-10-10", performanceScore = 0.2, rawJson = "{}", fetchedAt = "fixture"),
                        PageSpeedRunEntity(url, "mobile", "2026-10-11", performanceScore = 0.9, rawJson = "{}", fetchedAt = "fixture"),
                        PageSpeedRunEntity(url, "desktop", "2026-10-11", performanceScore = 0.5, rawJson = "{}", fetchedAt = "fixture"),
                        PageSpeedRunEntity(
                            "https://fixture.com/",
                            "mobile",
                            "2026-10-12",
                            performanceScore = 0.1,
                            rawJson = "{}",
                            fetchedAt = "fixture",
                        ),
                    ),
                )
                database.performanceDao().upsertCruxMetrics(
                    listOf(
                        point(url, "PHONE", "2026-10-10", "largest_contentful_paint", 8000.0),
                        point(url, "PHONE", "2026-10-11", "largest_contentful_paint", 2000.0),
                        point(url, "PHONE", "2026-10-11", "interaction_to_next_paint", 150.0),
                        point(url, "PHONE", "2026-10-11", "cumulative_layout_shift", 0.08),
                        point(url, "DESKTOP", "2026-10-11", "largest_contentful_paint", 7000.0),
                        point("https://fixture.com/", "PHONE", "2026-10-12", "largest_contentful_paint", 9000.0),
                    ),
                )
                val report = ReportRepository(database).observePage("fixture.com", "/leaf/", Period.All).first()
                assertEquals(3, report.performance.runs.size)
                assertTrue(report.performance.runs.all { it.url == url })
                assertTrue(report.performance.field.all { it.target == url })
                assertEquals(
                    0.9,
                    report.performance.runs
                        .first { it.device == "mobile" }
                        .performance!!,
                    0.0,
                )
                assertEquals(
                    0.5,
                    report.performance.runs
                        .first { it.device == "desktop" }
                        .performance!!,
                    0.0,
                )
                assertEquals("Pass", ReportMath.vitals(report.performance.field.filter { it.device == "PHONE" }))
                assertEquals("No field data", ReportMath.vitals(report.performance.field.filter { it.device == "TABLET" }))
            } finally {
                database.close()
            }
        }

    private fun point(
        url: String,
        device: String,
        end: String,
        metric: String,
        value: Double,
    ) = CruxMetricPointEntity("url", url, device, "2026-09-14", end, metric, value, 0.8, 0.15, 0.05, "{}", "fixture")
}
