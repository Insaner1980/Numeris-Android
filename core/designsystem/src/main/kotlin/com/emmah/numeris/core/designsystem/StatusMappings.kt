package com.emmah.numeris.core.designsystem

import com.emmah.numeris.core.model.DisplayFormat
import com.emmah.numeris.core.model.VitalRating

/** URL Inspectionin tulos ihmisluettavana. PASS tarkoittaa, että sivu on indeksoitu. */
fun indexingLabel(verdict: String?): String =
    when (verdict) {
        null -> "Not inspected"
        "PASS" -> "Indexed"
        "FAIL" -> "Not indexed"
        else -> DisplayFormat.humanize(verdict)
    }

fun indexingTone(verdict: String?): PillTone =
    when (verdict) {
        null -> PillTone.Neutral
        "PASS" -> PillTone.Positive
        "FAIL" -> PillTone.Negative
        else -> PillTone.Warning
    }

fun VitalRating.tone(): PillTone =
    when (this) {
        VitalRating.Good -> PillTone.Positive
        VitalRating.NeedsWork -> PillTone.Warning
        VitalRating.Poor -> PillTone.Negative
    }

/** ReportMath.vitals-yhteenvedon sävy: Pass, Needs work, Fail tai ei kenttädataa. */
fun vitalsSummaryTone(summary: String): PillTone =
    when (summary) {
        "Pass" -> PillTone.Positive
        "Needs work" -> PillTone.Warning
        "Fail" -> PillTone.Negative
        else -> PillTone.Neutral
    }

/** Lighthouse-pisteet 0–1: 0,9 tai yli on hyvä, 0,5 tai yli kohtalainen. */
fun labScoreTone(score: Double?): PillTone =
    when {
        score == null -> PillTone.Neutral
        score >= GOOD_LAB_SCORE -> PillTone.Positive
        score >= FAIR_LAB_SCORE -> PillTone.Warning
        else -> PillTone.Negative
    }

private const val GOOD_LAB_SCORE = 0.9
private const val FAIR_LAB_SCORE = 0.5
