package com.emmah.numeris.data

import com.emmah.numeris.core.database.CloudflareCountryEntity
import com.emmah.numeris.core.database.CloudflarePageEntity
import com.emmah.numeris.core.database.CloudflareStatusEntity
import com.emmah.numeris.core.database.CloudflareTrafficEntity
import com.emmah.numeris.core.database.WebAnalyticsCountryEntity
import com.emmah.numeris.core.database.WebAnalyticsDailyEntity
import com.emmah.numeris.core.database.WebAnalyticsPageEntity
import com.emmah.numeris.core.database.WebAnalyticsReferrerEntity
import com.emmah.numeris.core.model.Breakdown
import com.emmah.numeris.core.model.DisplayLabels
import com.emmah.numeris.core.model.SearchObservation
import com.emmah.numeris.core.model.TrafficDay
import com.emmah.numeris.core.model.contains
import java.time.LocalDate

internal fun trafficDay(it: CloudflareTrafficEntity) =
    TrafficDay(
        it.domain,
        LocalDate.parse(it.date),
        it.uniqueVisitors,
        it.pageviews,
        it.requests,
        it.cachedRequests,
        it.cachedBytes,
        it.totalBytes,
        it.threats,
    )

internal fun zoneCountry(it: CloudflareCountryEntity) =
    Breakdown(key = it.country, label = DisplayLabels.country(it.country), value = it.visitors)

internal fun zonePage(it: CloudflarePageEntity) = Breakdown("${it.domain}:${it.path}", it.path, it.requests, it.domain)

internal fun zoneStatus(it: CloudflareStatusEntity) =
    Breakdown(key = it.statusCode.toString(), label = "HTTP ${it.statusCode}", value = it.requests)

internal fun webDay(it: WebAnalyticsDailyEntity) =
    SearchObservation(it.domain, LocalDate.parse(it.date), "daily", "", it.visits, it.pageViews)

internal fun webPage(it: WebAnalyticsPageEntity) =
    Breakdown(key = "${it.domain}:${it.path}", label = it.path, value = it.pageViews, domain = it.domain)

internal fun webReferrer(it: WebAnalyticsReferrerEntity) =
    Breakdown(
        it.referrer,
        it.referrer.ifBlank {
            "Direct"
        },
        it.visits,
    )

internal fun webCountry(it: WebAnalyticsCountryEntity) =
    Breakdown(key = it.country, label = DisplayLabels.country(it.country), value = it.visits)
