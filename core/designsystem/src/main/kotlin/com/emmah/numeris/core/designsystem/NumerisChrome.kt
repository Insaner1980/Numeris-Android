@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun RefreshButton(
    syncing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onRefresh, enabled = !syncing, modifier = modifier.size(NumerisSizes.TouchTarget)) {
        if (syncing) {
            CircularProgressIndicator(Modifier.size(NumerisSizes.SmallIcon), strokeWidth = 2.dp)
        } else {
            Icon(
                painterResource(R.drawable.ic_numeris_refresh),
                contentDescription = "Refresh data",
                tint = NumerisColors.SecondaryText,
            )
        }
    }
}

/** Alanäkymien yläpalkki: takaisin-nuoli, otsikko ja valinnaiset toiminnot oikealla. */
@Composable
fun NumerisTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = NumerisSizes.TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_numeris_arrow_back), contentDescription = "Back")
        }
        Text(
            title,
            Modifier.weight(1f).padding(start = NumerisSpacing.Xs).semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions?.invoke()
    }
}

/** Näkymän alasivut välilehtinä. Korvaa aiemman "View:"-pudotusvalikon. */
@Composable
fun NumerisTabs(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Xs),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Column(
                    Modifier
                        .width(IntrinsicSize.Max)
                        .clip(RoundedCornerShape(NumerisShapes.ControlRadius))
                        .clickable { onSelect(option) }
                        .semantics {
                            role = Role.Tab
                            this.selected = isSelected
                        }.padding(horizontal = NumerisSpacing.Sm),
                ) {
                    Text(
                        option,
                        Modifier.padding(vertical = NumerisSpacing.Md),
                        color = if (isSelected) NumerisColors.PrimaryText else NumerisColors.TertiaryText,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(NumerisSizes.TabIndicator)
                            .background(if (isSelected) NumerisColors.Accent else Color.Transparent),
                    )
                }
            }
        }
        HorizontalDivider(color = NumerisColors.Divider)
    }
}

/** Hiusviivareunainen valintasiru suodattimille. Avaa valikon tai alapaneelin. */
@Composable
fun NumerisSelectChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .heightIn(min = NumerisSizes.TouchTarget)
            .clip(RoundedCornerShape(NumerisShapes.ControlRadius))
            .clickable(onClick = onClick)
            .semantics { role = Role.DropdownList }
            .padding(vertical = NumerisSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .hairlineSurface(NumerisShapes.ControlRadius, NumerisColors.ControlBorder)
                .padding(start = NumerisSpacing.Md, end = NumerisSpacing.Sm)
                .padding(vertical = NumerisSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Xs),
        ) {
            Text(
                label,
                Modifier.weight(1f, fill = false),
                color = NumerisColors.SecondaryText,
                style = MaterialTheme.typography.labelLarge,
            )
            Icon(
                painterResource(R.drawable.ic_numeris_expand_more),
                contentDescription = null,
                modifier = Modifier.size(NumerisSizes.SmallIcon),
                tint = NumerisColors.TertiaryText,
            )
        }
    }
}
