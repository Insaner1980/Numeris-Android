@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.emmah.numeris.core.model.DisplayFormat
import com.emmah.numeris.core.model.Metric

/** Tunnusluvut ruudukkona. Kaksi saraketta, yksi sarake suurella fonttikoolla tai kapealla näytöllä. */
@Composable
fun KpiGrid(
    metrics: List<Metric>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 280.dp && LocalDensity.current.fontScale < 1.5f) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm)) {
            metrics.chunked(columns).forEach { values ->
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm),
                ) {
                    values.forEach { metric -> KpiTile(metric, Modifier.weight(1f).fillMaxHeight()) }
                    repeat(columns - values.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun KpiTile(
    metric: Metric,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .hairlineSurface(NumerisShapes.TileRadius)
            .padding(NumerisSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Xs),
    ) {
        Text(metric.label, color = NumerisColors.TertiaryText, style = MaterialTheme.typography.labelMedium)
        Text(DisplayFormat.metric(metric.value, metric.unit), style = MaterialTheme.typography.displaySmall)
        metric.change?.let { ChangeText(it, higherIsBetter = metric.higherIsBetter) }
        metric.detail?.let {
            Text(it, color = NumerisColors.TertiaryText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Huomiota vaativa asia hiusviivakortissa. Tila näkyy kuvakkeen värinä, ei reunapalkkina. */
@Composable
fun NoticeCard(
    title: String,
    message: String,
    tone: PillTone,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val icon =
        when (tone) {
            PillTone.Negative -> R.drawable.ic_numeris_error
            PillTone.Warning -> R.drawable.ic_numeris_warning
            PillTone.Positive -> R.drawable.ic_numeris_check_circle
            PillTone.Neutral -> R.drawable.ic_numeris_info
        }
    Row(
        modifier
            .fillMaxWidth()
            .hairlineSurface()
            .padding(horizontal = NumerisSpacing.Lg)
            .padding(top = NumerisSpacing.Md, bottom = NumerisSpacing.Xs),
        horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Md),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.padding(top = NumerisSpacing.Xxs).size(NumerisSizes.SmallIcon),
            tint = tone.contentColor(),
        )
        Column(Modifier.weight(1f).padding(bottom = NumerisSpacing.Sm)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(message, color = NumerisColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction, modifier = Modifier.padding(top = NumerisSpacing.Xxs)) {
                    Text(actionLabel)
                }
            }
        }
    }
}
