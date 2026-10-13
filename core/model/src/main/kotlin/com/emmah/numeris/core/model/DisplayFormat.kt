package com.emmah.numeris.core.model

import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DisplayFormat {
    private const val PERCENT_MULTIPLIER = 100
    private const val KIBIBYTE = 1024
    private const val MEBIBYTE = 1048576
    private const val GIBIBYTE = 1073741824
    private const val ISO_DAY_LENGTH = 10
    private const val MINUTES_PER_HOUR = 60
    private const val HOURS_PER_DAY = 24
    private const val DAYS_PER_WEEK = 7
    const val MISSING = "—"

    fun number(value: Number?): String =
        value?.takeIf { it.toDouble().isFinite() }?.let { NumberFormat.getIntegerInstance(Locale.US).format(it) }
            ?: MISSING

    fun decimal(
        value: Double?,
        places: Int = 1,
    ): String =
        value?.takeIf(Double::isFinite)?.let {
            val format = NumberFormat.getNumberInstance(Locale.US)
            format.isGroupingUsed = false
            format.minimumFractionDigits = places
            format.maximumFractionDigits = places
            format.roundingMode = RoundingMode.HALF_EVEN
            format.format(it)
        } ?: MISSING

    fun percent(value: Double?): String = value?.let { decimal(it * PERCENT_MULTIPLIER) + "%" } ?: MISSING

    fun score(value: Double?): String = value?.let { decimal(it * PERCENT_MULTIPLIER, 0) } ?: MISSING

    fun change(value: Double?): String = value?.let { (if (it > 0) "+" else "") + decimal(it) + "%" } ?: MISSING

    fun date(value: LocalDate): String = value.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))

    fun timestamp(value: String?): String =
        value?.let {
            runCatching {
                Instant
                    .parse(
                        it,
                    ).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("MMM d, yyyy, HH:mm", Locale.US))
            }.getOrElse { runCatching { date(LocalDate.parse(value.take(ISO_DAY_LENGTH))) }.getOrDefault(value) }
        } ?: MISSING

    fun relativeTimestamp(
        value: String?,
        now: Instant = Instant.now(),
    ): String {
        val fetched = value?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return timestamp(value)
        val elapsed = Duration.between(fetched, now)
        return when {
            elapsed.isNegative -> timestamp(value)
            elapsed.toMinutes() < 1 -> "Just now"
            elapsed.toMinutes() < MINUTES_PER_HOUR -> "${elapsed.toMinutes()} min ago"
            elapsed.toHours() < HOURS_PER_DAY -> "${elapsed.toHours()} h ago"
            elapsed.toDays() < DAYS_PER_WEEK -> "${elapsed.toDays()} d ago"
            else -> timestamp(value)
        }
    }

    fun bytes(value: Double?): String =
        when {
            value == null || value < 0 || !value.isFinite() -> MISSING
            value >= GIBIBYTE -> decimal(value / GIBIBYTE, 2) + " GB"
            value >= MEBIBYTE -> decimal(value / MEBIBYTE) + " MB"
            value >= KIBIBYTE -> decimal(value / KIBIBYTE) + " KB"
            else -> number(value) + " B"
        }

    fun metric(
        value: Double?,
        unit: String,
    ): String =
        when (unit) {
            "percent" -> percent(value)
            "score" -> score(value)
            "bytes" -> bytes(value)
            "ms" -> number(value) + if (value != null) " ms" else ""
            "decimal" -> decimal(value, 2)
            else -> number(value)
        }

    fun humanize(value: String): String =
        value
            .replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
            .replace('_', ' ')
            .replace('-', ' ')
            .lowercase()
            .replaceFirstChar(Char::uppercase)
}

/** Tiivis muotoilu kaavioihin, listariveihin ja otsikoihin. */
object CompactFormat {
    fun date(value: LocalDate): String = value.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))

    fun dateRange(range: DateRange): String = dateRange(range.start, range.end)

    fun dateRange(
        start: LocalDate,
        end: LocalDate,
    ): String =
        if (start.year == end.year) {
            "${date(start)} – ${DisplayFormat.date(end)}"
        } else {
            "${DisplayFormat.date(start)} – ${DisplayFormat.date(end)}"
        }

    /** Suhteellinen aika lauseen keskelle, esim. "Updated just now" tai "Synced 20 h ago". */
    fun relative(value: String?): String {
        val text = DisplayFormat.relativeTimestamp(value)
        return if (text == "Just now") "just now" else text
    }

    /** URL ilman protokollaa, www-alkua ja loppukauttaviivaa. */
    fun url(value: String): String =
        value
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .removeSuffix("/")
            .ifEmpty { value }
}
