@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.emmah.numeris.core.model.DisplayFormat

/** Tilan merkitys pillerille. Väri kertoo tilan, teksti kertoo saman asian ilman väriä. */
enum class PillTone { Positive, Negative, Warning, Neutral }

/** Tilan tekstiväri, esim. värikoodattu luku ilman pilleriä. */
fun PillTone.contentColor(): Color =
    when (this) {
        PillTone.Positive -> NumerisColors.Positive
        PillTone.Negative -> NumerisColors.Negative
        PillTone.Warning -> NumerisColors.Warning
        PillTone.Neutral -> NumerisColors.SecondaryText
    }

private fun PillTone.containerColor(): Color =
    when (this) {
        PillTone.Positive -> NumerisColors.PositiveContainer
        PillTone.Negative -> NumerisColors.NegativeContainer
        PillTone.Warning -> NumerisColors.WarningContainer
        PillTone.Neutral -> NumerisColors.Control
    }

@Composable
fun StatusPill(
    text: String,
    tone: PillTone,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier
            .clip(RoundedCornerShape(NumerisShapes.PillRadius))
            .background(tone.containerColor())
            .padding(horizontal = NumerisSpacing.Sm, vertical = NumerisSpacing.Xxs),
        color = tone.contentColor(),
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
    )
}

/** Muutos edelliseen jaksoon värikoodattuna tekstinä ilman pilleriä, esim. pääluvun tai ruudun alla. */
@Composable
fun ChangeText(
    change: Double,
    modifier: Modifier = Modifier,
    higherIsBetter: Boolean = true,
    suffix: String = "",
) {
    Text(
        DisplayFormat.change(change) + suffix,
        modifier,
        color = changeTone(change, higherIsBetter).contentColor(),
        style = MaterialTheme.typography.labelLarge,
    )
}

/** Muutos edelliseen jaksoon. Suunta tulkitaan mittarin mukaan: esimerkiksi sijoituksessa pienempi on parempi. */
fun changeTone(
    change: Double,
    higherIsBetter: Boolean = true,
): PillTone =
    when {
        change == 0.0 -> PillTone.Neutral
        (change > 0) == higherIsBetter -> PillTone.Positive
        else -> PillTone.Negative
    }
