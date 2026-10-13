package com.emmah.numeris.core.designsystem

import androidx.compose.ui.graphics.Color

object NumerisColors {
    // Hiusviivatyyli: lähes musta pohja, sisältö erotetaan ohuilla viivoilla eikä täytetyillä laatikoilla.
    // Oranssi on ainoa korostusväri; tilavärit näkyvät vain tekstinä, pisteenä tai hillittynä taustana.
    val Background = Color(0xFF08090A)
    val Surface = Color(0xFF08090A)

    /** Kelluvat pinnat (alapaneelit, valikot, dialogit). Raporttisisältö ei käytä täytettyjä kortteja. */
    val Card = Color(0xFF111214)
    val Control = Color(0xFF18191C)
    val ChartPanel = Background

    /** Hiusviiva korttien reunoissa ja rivien välissä. */
    val Divider = Color(0xFF1D1F23)

    /** Kontrollien (sirut, tekstikentät) reunaviiva; hieman hiusviivaa näkyvämpi. */
    val ControlBorder = Color(0xFF2A2C31)
    val NavigationSurface = Color(0xFF0C0D0F)
    val PrimaryText = Color(0xFFFAFAFA)
    val SecondaryText = Color(0xFFD4D4D8)
    val TertiaryText = Color(0xFF8B8B94)
    val Accent = Color(0xFFFF7A1A)
    val AccentForeground = Color(0xFF0B0B0C)
    val Copper = Color(0xFF7DB7C2)
    val WarmPlatinum = Color(0xFFA1A1AA)
    val PreviousSeries = Color(0xFF3F3F46)
    val Outline = Color(0xFF3F3F46)
    val OutlineVariant = Divider
    val Error = Color(0xFFF87171)
    val Positive = Color(0xFF4ADE80)
    val PositiveContainer = Color(0xFF0E2016)
    val Negative = Color(0xFFF87171)
    val NegativeContainer = Color(0xFF261213)
    val Warning = Color(0xFFFBBF24)
    val WarningContainer = Color(0xFF241D0C)

    /** Sivustojen tunnusvärit. Sama sivusto saa saman värin kaikissa näkymissä (ks. [siteColor]). */
    val SitePalette =
        listOf(
            Color(0xFFFF9F5A),
            Color(0xFF6FD1C6),
            Color(0xFFB4A8F5),
            Color(0xFFE9B44C),
            Color(0xFF7FB2EE),
            Color(0xFFEE8FB0),
            Color(0xFF9CCB6B),
            Color(0xFFD7A07A),
        )
}
