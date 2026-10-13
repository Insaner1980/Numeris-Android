@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    divider: Boolean = false,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .heightIn(min = NumerisSizes.TouchTarget)
                .padding(vertical = NumerisSpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Xxs)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                supporting?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = NumerisColors.TertiaryText, style = MaterialTheme.typography.bodyMedium)
                }
            }
            trailing?.invoke()
        }
        if (divider) HorizontalDivider(color = NumerisColors.Divider)
    }
}

/** Hiusviivakortti listariveille. Rivit erotetaan ohuilla viivoilla, ei erillisinä kortteina. */
@Composable
fun ListCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .hairlineSurface()
            .padding(horizontal = NumerisSpacing.Lg),
    ) {
        content()
    }
}

/** Oikean reunan arvo listarivillä: luku ylhäällä, yksikkö pienellä alla. */
@Composable
fun TrailingValue(
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TABULAR_NUMBERS))
        caption?.let { Text(it, color = NumerisColors.TertiaryText, style = MaterialTheme.typography.bodySmall) }
    }
}

/** Sivuston kirjainmerkki. Värillisenä se toistaa sivuston tunnusvärin (ks. [siteColor]). */
@Composable
fun LetterAvatar(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    Box(
        modifier
            .size(NumerisSizes.Avatar)
            .clip(RoundedCornerShape(NumerisShapes.ControlRadius))
            .background(color?.copy(alpha = AVATAR_TINT_ALPHA) ?: NumerisColors.Control),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.firstOrNull()?.uppercase().orEmpty(),
            color = color ?: NumerisColors.SecondaryText,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

private const val AVATAR_TINT_ALPHA = 0.2f

/** "See all" -painike listan loppuun, kun listaa on lyhennetty. */
@Composable
fun ShowMoreButton(
    expanded: Boolean,
    total: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InlineTextButton(if (expanded) "Show fewer" else "See all $total", onToggle, modifier)
}

/** Hillitty tekstipainike, joka linjautuu sisällön vasempaan reunaan (esim. "See all", "Show data table"). */
@Composable
fun InlineTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick,
        modifier,
        colors = ButtonDefaults.textButtonColors(contentColor = NumerisColors.SecondaryText),
        contentPadding = PaddingValues(vertical = NumerisSpacing.Sm),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(top = NumerisSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            supporting?.let {
                Text(it, color = NumerisColors.TertiaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
        action?.invoke()
    }
}

/** Selite ja arvo samalla rivillä eri reunoissa. */
@Composable
fun DetailPairs(
    pairs: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Xs)) {
        pairs.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Md)) {
                Text(
                    label,
                    Modifier.weight(1f),
                    color = NumerisColors.TertiaryText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR_NUMBERS))
            }
        }
    }
}
