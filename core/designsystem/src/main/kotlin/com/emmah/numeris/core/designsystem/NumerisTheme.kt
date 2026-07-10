@file:Suppress("ktlint:standard:function-naming")

package com.emmah.numeris.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NumerisDarkColorScheme =
    darkColorScheme(
        primary = NumerisColors.Accent,
        onPrimary = NumerisColors.AccentForeground,
        secondary = NumerisColors.Copper,
        tertiary = NumerisColors.WarmPlatinum,
        background = NumerisColors.Background,
        onBackground = NumerisColors.PrimaryText,
        surface = NumerisColors.Card,
        onSurface = NumerisColors.PrimaryText,
        surfaceVariant = NumerisColors.Control,
        onSurfaceVariant = NumerisColors.SecondaryText,
        error = NumerisColors.Error,
    )

@Composable
@Suppress("FunctionNaming")
fun NumerisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NumerisDarkColorScheme, content = content)
}
