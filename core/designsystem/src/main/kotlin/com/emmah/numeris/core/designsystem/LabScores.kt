@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.emmah.numeris.core.model.CompactFormat
import com.emmah.numeris.core.model.DisplayFormat
import com.emmah.numeris.core.model.DisplayLabels
import com.emmah.numeris.core.model.LabRun

/** Yhden PageSpeed-ajon neljä Lighthouse-pistettä värikoodattuina ruutuina. */
@Composable
fun LabScores(
    run: LabRun,
    modifier: Modifier = Modifier,
) {
    val scores =
        listOf(
            "Performance" to run.performance,
            "Accessibility" to run.accessibility,
            "Best practices" to run.bestPractices,
            "SEO" to run.seo,
        )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm)) {
        Column {
            Text(DisplayLabels.device(run.device), style = MaterialTheme.typography.titleSmall)
            Text(
                "${CompactFormat.url(run.url)}\nRun ${DisplayFormat.timestamp(run.time)}",
                color = NumerisColors.TertiaryText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 360.dp && LocalDensity.current.fontScale < 1.3f) 4 else 2
            Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm)) {
                scores.chunked(columns).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm),
                    ) {
                        row.forEach { (label, score) -> ScoreTile(label, score, Modifier.weight(1f).fillMaxHeight()) }
                    }
                }
            }
        }
        run.error?.let { Text("Runtime error: $it", color = NumerisColors.Negative) }
        run.warnings?.takeUnless { it == "[]" }?.let {
            Text("Warnings: $it", color = NumerisColors.Warning, style = MaterialTheme.typography.bodySmall)
        }
        run.finalUrl?.takeIf { it != run.url }?.let {
            Text("Redirected to $it", color = NumerisColors.TertiaryText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ScoreTile(
    label: String,
    score: Double?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(NumerisShapes.TileRadius))
            .background(NumerisColors.Control)
            .padding(NumerisSpacing.Sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            DisplayFormat.score(score),
            color = labScoreTone(score).contentColor(),
            style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = TABULAR_NUMBERS),
        )
        Text(label, color = NumerisColors.TertiaryText, style = MaterialTheme.typography.bodySmall)
    }
}
