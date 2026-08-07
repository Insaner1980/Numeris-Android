@file:Suppress("ktlint:standard:function-naming")

package com.emmah.numeris.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
@Suppress("FunctionNaming")
fun NumerisScreenHeader(
    title: String,
    subtitle: String,
    syncing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Lg),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Xs),
        ) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
            Text(
                subtitle,
                color = NumerisColors.SecondaryText,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
        }
        FilledTonalIconButton(
            onClick = onRefresh,
            enabled = !syncing,
            modifier = Modifier.size(NumerisSizes.TouchTarget),
        ) {
            if (syncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(NumerisSizes.Icon),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_numeris_refresh),
                    contentDescription = "Refresh data",
                )
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming")
fun NumerisDropdownSelector(
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    label: (String) -> String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = NumerisSpacing.Lg, vertical = NumerisSpacing.Md),
        ) {
            Text(
                label(selected),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painter = painterResource(R.drawable.ic_numeris_expand_more),
                contentDescription = null,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(maxWidth),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming")
fun NumerisEqualChoiceRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = {
                    Text(
                        option,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
@Suppress("FunctionNaming")
fun NumerisChoiceRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm),
        contentPadding = PaddingValues(end = NumerisSpacing.Lg),
    ) {
        items(options, key = { it }) { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option, maxLines = 1) },
            )
        }
    }
}
