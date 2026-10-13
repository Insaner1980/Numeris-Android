package com.emmah.numeris.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

object ProviderDate {
    private const val ISO_DAY_LENGTH = 10
    private val bingDate = Regex("/Date\\((-?\\d+)(?:[+-]\\d{4})?\\)/")

    fun normalize(value: String?): String? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val milliseconds =
            bingDate
                .matchEntire(text)
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull()
        return runCatching {
            if (milliseconds != null) {
                Instant
                    .ofEpochMilli(milliseconds)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                    .toString()
            } else {
                LocalDate.parse(text.take(ISO_DAY_LENGTH)).toString()
            }
        }.getOrNull()
    }
}
