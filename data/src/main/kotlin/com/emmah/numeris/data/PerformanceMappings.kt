package com.emmah.numeris.data

import com.emmah.numeris.core.database.CruxMetricPointEntity
import com.emmah.numeris.core.database.PageSpeedAuditEntity
import com.emmah.numeris.core.database.PageSpeedRunEntity
import com.emmah.numeris.core.model.DisplayFormat
import com.emmah.numeris.core.model.FieldMetric
import com.emmah.numeris.core.model.LabAudit
import com.emmah.numeris.core.model.LabRun
import com.emmah.numeris.core.model.contains
import java.time.LocalDate

internal fun fieldMetric(it: CruxMetricPointEntity) =
    FieldMetric(
        it.target,
        it.targetType,
        it.formFactor,
        it.metric,
        LocalDate.parse(it.collectionStart),
        LocalDate.parse(it.collectionEnd),
        it.p75,
        it.goodDensity,
        it.needsImprovementDensity,
        it.poorDensity,
    )

internal fun labRun(it: PageSpeedRunEntity) =
    LabRun(
        it.url,
        it.strategy,
        it.analysisUtc,
        it.performanceScore,
        it.accessibilityScore,
        it.bestPracticesScore,
        it.seoScore,
        it.finalUrl,
        it.lighthouseVersion,
        it.runtimeError,
        it.warningsJson,
    )

internal fun labAudit(it: PageSpeedAuditEntity) =
    LabAudit(
        it.url,
        it.strategy,
        it.analysisUtc,
        it.auditId,
        it.title ?: DisplayFormat.humanize(it.auditId),
        it.score,
        it.displayValue,
        it.detailsJson,
    )
