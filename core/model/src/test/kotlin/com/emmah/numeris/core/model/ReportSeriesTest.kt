package com.emmah.numeris.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReportSeriesTest {
    @Test fun valuesKeepMissingSamplesAndSelectOnlyTheFirstMatchingSeries() {
        val date = LocalDate.of(2024, 3, 1)
        val current = ReportSeries("Visits", "count", listOf(DatedValue(date, null), DatedValue(date.plusDays(1), 0.0)))
        val previous = current.copy(previous = true, points = listOf(DatedValue(date, 7.0)))
        val series = listOf(previous, current, current.copy(points = listOf(DatedValue(date, 99.0))))
        assertEquals(listOf(null, 0.0), series.values(previous = false))
        assertEquals(listOf(7.0), series.values(previous = true))
        assertEquals(emptyList<Double?>(), listOf(current).values(previous = true))
        assertEquals(emptyList<Double?>(), emptyList<ReportSeries>().values(previous = false))
    }

    @Test fun combinedRangeIncludesBothCompletePeriodsAcrossLeapDay() {
        val range = DateRange(LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 7), 7)
        val combined = range.withPrevious()
        assertEquals(LocalDate.of(2024, 2, 23), combined.start)
        assertEquals(range.end, combined.end)
        assertEquals(14, combined.days)
        assertEquals(ReportMath.dates(range.previous()) + ReportMath.dates(range), ReportMath.dates(combined))
    }
}
