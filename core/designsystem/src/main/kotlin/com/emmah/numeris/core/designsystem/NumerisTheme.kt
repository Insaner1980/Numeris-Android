@file:Suppress("ktlint:standard:function-naming")

package com.emmah.numeris.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val NumerisDarkColorScheme =
    darkColorScheme(
        primary = NumerisColors.Accent,
        onPrimary = NumerisColors.AccentForeground,
        secondary = NumerisColors.Copper,
        tertiary = NumerisColors.WarmPlatinum,
        background = NumerisColors.Background,
        onBackground = NumerisColors.PrimaryText,
        surface = NumerisColors.Surface,
        onSurface = NumerisColors.PrimaryText,
        surfaceVariant = NumerisColors.Control,
        onSurfaceVariant = NumerisColors.SecondaryText,
        outline = NumerisColors.Outline,
        outlineVariant = NumerisColors.OutlineVariant,
        error = NumerisColors.Error,
    )

private val NumerisTypography =
    Typography(
        headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    )

private val NumerisMaterialShapes =
    Shapes(
        small =
            androidx.compose.foundation.shape
                .RoundedCornerShape(NumerisShapes.ControlRadius),
        medium =
            androidx.compose.foundation.shape
                .RoundedCornerShape(NumerisShapes.CardRadius),
        large =
            androidx.compose.foundation.shape
                .RoundedCornerShape(NumerisShapes.HeroRadius),
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
