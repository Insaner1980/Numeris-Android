package com.emmah.numeris.core.model

import java.math.RoundingMode

data class MetricWindow(
    val current: Double,
    val previous: Double,
    val hasCurrent: Boolean = true,
    val hasPrevious: Boolean = true,
    val hasComparison: Boolean = true,
)

enum class TrendState { NoData, NewActivity, Up, Down, Flat }

object Trend {
    private const val RATIO_PRECISION = 12
    private const val PERCENT_MULTIPLIER = 100

    fun changeRatio(metric: MetricWindow): Double =
        if (!metric.hasCurrent || !metric.hasPrevious || !metric.hasComparison) {
            0.0
        } else {
            if (metric.previous == 0.0) 0.0 else (metric.current - metric.previous) / metric.previous
        }

    fun classify(
        metric: MetricWindow,
        upThreshold: Double = 0.25,
        downThreshold: Double = 0.10,
    ): TrendState =
        when {
            !metric.hasCurrent || !metric.hasPrevious || !metric.hasComparison -> TrendState.NoData
            metric.current == 0.0 && metric.previous == 0.0 -> TrendState.NoData
            metric.previous == 0.0 && metric.current > 0.0 -> TrendState.NewActivity
            roundedRatio(metric) >= upThreshold -> TrendState.Up
            roundedRatio(metric) <= -downThreshold -> TrendState.Down
            else -> TrendState.Flat
        }

    private fun roundedRatio(metric: MetricWindow): Double =
        changeRatio(metric).toBigDecimal().setScale(RATIO_PRECISION, RoundingMode.HALF_EVEN).toDouble()

    fun formatRatio(ratio: Double): String {
        if (!ratio.isFinite()) return DisplayFormat.MISSING
        val percent =
            ratio
                .toBigDecimal()
                .multiply(PERCENT_MULTIPLIER.toBigDecimal())
                .setScale(0, RoundingMode.HALF_UP)
        return when {
            percent.signum() > 0 -> "+${percent.toPlainString()}%"
            percent.signum() < 0 -> "${percent.toPlainString()}%"
            else -> "0%"
        }
    }
}
