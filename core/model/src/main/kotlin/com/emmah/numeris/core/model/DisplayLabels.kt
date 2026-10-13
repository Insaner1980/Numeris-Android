package com.emmah.numeris.core.model

import java.util.Locale

object DisplayLabels {
    fun device(value: String): String =
        when (value.uppercase()) {
            "PHONE", "MOBILE" -> "Mobile"
            "ALL", "" -> "All devices"
            else -> DisplayFormat.humanize(value)
        }

    fun country(value: String): String =
        if (value.matches(Regex("[A-Za-z]{2}"))) {
            Locale
                .Builder()
                .setRegion(value)
                .build()
                .getDisplayCountry(Locale.US)
        } else {
            value
        }

    fun metricLabel(value: String): String =
        when (value) {
            "largest_contentful_paint" -> "LCP"
            "interaction_to_next_paint" -> "INP"
            "cumulative_layout_shift" -> "CLS"
            "first_contentful_paint" -> "FCP"
            "experimental_time_to_first_byte" -> "TTFB"
            else -> DisplayFormat.humanize(value)
        }

    fun metricName(value: String): String =
        when (value) {
            "largest_contentful_paint" -> "Largest contentful paint"
            "interaction_to_next_paint" -> "Interaction to next paint"
            "cumulative_layout_shift" -> "Cumulative layout shift"
            "first_contentful_paint" -> "First contentful paint"
            "experimental_time_to_first_byte" -> "Time to first byte"
            else -> DisplayFormat.humanize(value)
        }
}
