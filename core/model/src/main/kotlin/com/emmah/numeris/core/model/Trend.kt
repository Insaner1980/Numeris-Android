package com.emmah.numeris.core.model

import java.math.RoundingMode

data class MetricWindow(
    val current: Double,
    val previous: Double,
)

enum class TrendState { NoData, NewActivity, Up, Down, Flat }

object Trend {
    fun changeRatio(metric: MetricWindow): Double =
        if (metric.previous == 0.0) 0.0 else (metric.current - metric.previous) / metric.previous

    fun classify(
        metric: MetricWindow,
        upThreshold: Double = 0.25,
        downThreshold: Double = 0.10,
    ): TrendState =
        when {
            metric.current == 0.0 && metric.previous == 0.0 -> TrendState.NoData
            metric.previous == 0.0 && metric.current > 0.0 -> TrendState.NewActivity
            changeRatio(metric) >= upThreshold -> TrendState.Up
            changeRatio(metric) <= -downThreshold -> TrendState.Down
            else -> TrendState.Flat
        }

    fun formatRatio(ratio: Double): String {
        val percent =
            ratio
                .toBigDecimal()
                .multiply(100.toBigDecimal())
                .setScale(0, RoundingMode.HALF_UP)
                .toInt()
        return when {
            percent > 0 -> "+$percent%"
            percent < 0 -> "$percent%"
            else -> "0%"
        }
    }
}
