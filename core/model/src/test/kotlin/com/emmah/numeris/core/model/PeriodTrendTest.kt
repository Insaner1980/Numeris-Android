package com.emmah.numeris.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PeriodTrendTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-10T12:00:00Z"), ZoneOffset.UTC)

    @Test fun `all means 365 inclusive days`() {
        val range = Period.All.toDateRange(clock)
        assertEquals("2025-07-11", range.start.toString())
        assertEquals("2026-07-10", range.end.toString())
        assertEquals(365, range.days)
    }

    @Test fun `zero baseline with activity is new activity`() {
        assertEquals(TrendState.NewActivity, Trend.classify(MetricWindow(current = 12.0, previous = 0.0)))
        assertEquals("+25%", Trend.formatRatio(0.25))
    }
}
