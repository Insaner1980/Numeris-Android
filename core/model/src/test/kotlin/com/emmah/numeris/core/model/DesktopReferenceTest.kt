package com.emmah.numeris.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopReferenceTest {
    @Test fun currentDesktopCalculationsMatchAndroid() {
        val rows =
            javaClass.getResourceAsStream("/desktop-reference.csv")!!.bufferedReader(Charsets.UTF_8).use {
                it.readLines()
            }
        assertEquals(28, rows.size)
        rows.forEach { line ->
            val fields = line.split('|')
            when (fields[0]) {
                "trend" -> {
                    val metric =
                        MetricWindow(fields[1].toDouble(), fields[2].toDouble(), hasComparison = fields[3].toBoolean())
                    assertEquals(line, fields[4], Trend.classify(metric).name)
                    assertEquals(line, fields[5], Trend.formatRatio(Trend.changeRatio(metric)))
                    assertEquals(line, fields[6].takeUnless { it == "missing" }?.toDouble(), ReportMath.change(metric))
                }
                "number" -> assertEquals(line, fields[2], DisplayFormat.number(fields[1].toDouble()))
                "path" -> assertEquals(line, fields[2], SiteIdentity.pagePath(fields[1]))
                "http" -> {
                    val card =
                        InsightEngine
                            .generate(
                                InsightMetrics(
                                    httpStatus = StatusCodeSummary(fields[1].toLong(), 0, fields[2].toLong()),
                                ),
                            ).firstOrNull()
                    assertEquals(line, fields[3], card?.title ?: "none")
                    assertEquals(line, fields[4], card?.severity?.name ?: "none")
                    assertEquals(line, fields[5], card?.priority?.toString() ?: "none")
                    assertEquals(line, fields[6], card?.whyShown ?: "none")
                }
                "freshness" ->
                    assertEquals(
                        line,
                        fields[3],
                        InsightEngine
                            .generate(
                                InsightMetrics(
                                    freshness = SourceFreshnessSummary(5, fields[1].toInt(), fields[2].toInt()),
                                ),
                            ).single()
                            .whyShown,
                    )
                else -> error("Unknown fixture contract")
            }
        }
    }
}
