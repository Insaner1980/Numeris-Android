@file:Suppress("ktlint:standard:function-naming")

package com.emmah.numeris.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val NumerisDarkColorScheme =
    darkColorScheme(
        primary = NumerisColors.Accent,
        onPrimary = NumerisColors.AccentForeground,
        secondary = NumerisColors.Copper,
        secondaryContainer = NumerisColors.Control,
        onSecondaryContainer = NumerisColors.PrimaryText,
        tertiary = NumerisColors.WarmPlatinum,
        background = NumerisColors.Background,
        onBackground = NumerisColors.PrimaryText,
        surface = NumerisColors.Surface,
        onSurface = NumerisColors.PrimaryText,
        surfaceVariant = NumerisColors.Control,
        surfaceContainer = NumerisColors.NavigationSurface,
        surfaceContainerLow = NumerisColors.Card,
        surfaceContainerHigh = NumerisColors.Card,
        surfaceContainerHighest = NumerisColors.Control,
        primaryContainer = NumerisColors.Control,
        onPrimaryContainer = NumerisColors.Accent,
        onSurfaceVariant = NumerisColors.TertiaryText,
        outline = NumerisColors.Outline,
        outlineVariant = NumerisColors.OutlineVariant,
        error = NumerisColors.Error,
    )

/**
 * Sovelluksen ainoa fonttiperhe: Inter 4.1 (SIL OFL, ks. core/designsystem/INTER-LICENSE.txt). Mukana vain
 * painot 400 ja 500; kaikki tekstityylit periytyvät tästä.
 */
val NumerisFontFamily: FontFamily =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
    )

private fun style(
    size: Int,
    line: Int,
    weight: FontWeight,
    tracking: TextUnit = 0.em,
    tabular: Boolean = false,
) = TextStyle(
    fontFamily = NumerisFontFamily,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking,
    fontFeatureSettings = if (tabular) TABULAR_NUMBERS else null,
)

// Isot luvut ovat kevyitä ja tiiviisti välistettyjä; selitteet pieniä ja hillittyjä.
private val NumerisTypography =
    Typography(
        displayMedium = style(40, 44, FontWeight.Normal, (-0.035).em, tabular = true),
        displaySmall = style(28, 32, FontWeight.Normal, (-0.03).em, tabular = true),
        headlineMedium = style(28, 34, FontWeight.Medium, (-0.025).em),
        headlineSmall = style(20, 26, FontWeight.Normal, (-0.02).em, tabular = true),
        titleLarge = style(18, 24, FontWeight.Medium, (-0.015).em),
        titleMedium = style(16, 22, FontWeight.Medium, (-0.01).em),
        titleSmall = style(14, 20, FontWeight.Medium),
        bodyLarge = style(16, 24, FontWeight.Normal, (-0.01).em),
        bodyMedium = style(14, 20, FontWeight.Normal),
        bodySmall = style(12, 16, FontWeight.Normal),
        labelLarge = style(14, 20, FontWeight.Medium),
        labelMedium = style(12, 16, FontWeight.Medium),
        labelSmall = style(11, 14, FontWeight.Medium),
    )

/** Tabulaariset numerot pitävät lukujen leveyden vakiona, joten arvot eivät hypi päivittyessään. */
internal const val TABULAR_NUMBERS = "tnum"

private val NumerisMaterialShapes =
    Shapes(
        small = RoundedCornerShape(NumerisShapes.ControlRadius),
        medium = RoundedCornerShape(NumerisShapes.CardRadius),
        large = RoundedCornerShape(NumerisShapes.HeroRadius),
    )

@Composable
@Suppress("FunctionNaming")
fun NumerisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NumerisDarkColorScheme,
        typography = NumerisTypography,
        shapes = NumerisMaterialShapes,
        content = content,
    )
}
