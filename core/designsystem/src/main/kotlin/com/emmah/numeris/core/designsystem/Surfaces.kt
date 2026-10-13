package com.emmah.numeris.core.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * Raporttisisällön pinta: läpinäkyvä tausta ja hiusviivareuna. Korvaa täytetyt kortit, jotta näkymä pysyy
 * kevyenä ja ainoat värit ovat data ja korostus.
 */
fun Modifier.hairlineSurface(
    radius: Dp = NumerisShapes.CardRadius,
    color: Color = NumerisColors.Divider,
): Modifier {
    val shape = RoundedCornerShape(radius)
    return clip(shape).border(NumerisSizes.Hairline, color, shape)
}
