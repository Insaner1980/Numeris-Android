package com.emmah.numeris.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class DisplayFreshnessTest {
    @Test fun distinguishesMissingRecentOldAndFutureFetchTimes() {
        val now = Instant.parse("2026-10-11T12:00:00Z")
        assertEquals(DisplayFormat.MISSING, DisplayFormat.relativeTimestamp(null, now))
        assertEquals("Just now", DisplayFormat.relativeTimestamp("2026-10-11T11:59:59Z", now))
        assertEquals("1 min ago", DisplayFormat.relativeTimestamp("2026-10-11T11:59:00Z", now))
        assertEquals("1 h ago", DisplayFormat.relativeTimestamp("2026-10-11T11:00:00Z", now))
        assertEquals("1 d ago", DisplayFormat.relativeTimestamp("2026-10-10T12:00:00Z", now))
        for (value in listOf("2026-10-01T12:00:00Z", "2026-10-12T12:00:00Z", "2026-10-01")) {
            assertEquals(DisplayFormat.timestamp(value), DisplayFormat.relativeTimestamp(value, now))
        }
    }
}
