package com.emmah.numeris.core.model

import java.time.Clock
import java.time.LocalDate

enum class Period(
    val days: Int,
    val shortLabel: String,
    val displayLabel: String,
) {
    Last7Days(LAST_7_DAYS, "7d", "Last 7 days"),
    Last30Days(LAST_30_DAYS, "30d", "Last 30 days"),
    Last90Days(LAST_90_DAYS, "90d", "Last 90 days"),
    All(ALL_DAYS, "All", "All time"),
    ;

    fun toDateRange(clock: Clock = Clock.systemDefaultZone()): DateRange {
        val end = LocalDate.now(clock)
        return DateRange(start = end.minusDays(days.toLong() - 1), end = end, days = days)
    }
}

private const val LAST_7_DAYS = 7
private const val LAST_30_DAYS = 30
private const val LAST_90_DAYS = 90
private const val ALL_DAYS = 365

data class DateRange(
    val start: LocalDate,
    val end: LocalDate,
    val days: Int,
)
