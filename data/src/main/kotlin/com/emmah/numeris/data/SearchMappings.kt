package com.emmah.numeris.data

import com.emmah.numeris.core.database.BingPageStatEntity
import com.emmah.numeris.core.database.BingQueryStatEntity
import com.emmah.numeris.core.database.BingRankTrafficEntity
import com.emmah.numeris.core.database.SearchConsoleEntity
import com.emmah.numeris.core.model.SearchObservation
import com.emmah.numeris.core.model.contains
import com.emmah.numeris.core.model.reportDomain
import java.time.LocalDate

internal fun googleObservation(it: SearchConsoleEntity) =
    SearchObservation(
        reportDomain(it.siteUrl)!!,
        LocalDate.parse(it.date),
        when (it.kind) {
            "query_daily" -> "query"
            "page_daily" -> "page"
            "query", "page" -> "legacy_${it.kind}"
            else -> it.kind
        },
        it.page ?: it.query,
        it.clicks,
        it.impressions,
        it.position,
    )

internal fun bingTraffic(it: BingRankTrafficEntity) =
    SearchObservation(
        reportDomain(it.siteUrl)!!,
        LocalDate.parse(it.date),
        "daily",
        "",
        it.clicks,
        it.impressions,
    )

internal fun bingQuery(it: BingQueryStatEntity) =
    SearchObservation(
        reportDomain(it.siteUrl)!!,
        LocalDate.parse(it.date),
        "query",
        it.query,
        it.clicks,
        it.impressions,
        it.avgImpressionPosition,
    )

internal fun bingPage(it: BingPageStatEntity) =
    SearchObservation(
        reportDomain(it.siteUrl)!!,
        LocalDate.parse(it.date),
        "page",
        it.pageUrl,
        it.clicks,
        it.impressions,
    )
