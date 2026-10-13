@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun NumerisSiteSelector(
    selected: String,
    domains: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    Box(modifier) {
        NumerisSelectChip(if (selected == "all") "All sites" else selected, {
            query = ""
            expanded = true
        })
        if (expanded) {
            ModalBottomSheet(
                onDismissRequest = { expanded = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = NumerisColors.Card,
            ) {
                LazyColumn(
                    Modifier.fillMaxWidth().imePadding().padding(horizontal = NumerisSpacing.Lg),
                    contentPadding = PaddingValues(bottom = NumerisSpacing.Lg),
                ) {
                    item { Text("Select a site", style = MaterialTheme.typography.titleLarge) }
                    item {
                        OutlinedTextField(
                            query,
                            { query = it },
                            Modifier.fillMaxWidth().padding(vertical = NumerisSpacing.Md),
                            label = { Text("Find a site") },
                            singleLine = true,
                        )
                    }
                    item {
                        SiteOption("all", "All sites", selected, domains) {
                            onSelect("all")
                            expanded = false
                        }
                    }
                    val matches = domains.distinct().filter { it.contains(query, ignoreCase = true) }
                    items(matches, key = { it }) { domain ->
                        SiteOption(domain, domain, selected, domains) {
                            onSelect(domain)
                            expanded = false
                        }
                    }
                    if (matches.isEmpty()) {
                        item { Text("No matching sites", color = NumerisColors.TertiaryText) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SiteOption(
    value: String,
    label: String,
    selected: String,
    domains: List<String>,
    onClick: () -> Unit,
) {
    val isSelected = value == selected
    ListRow(
        label,
        Modifier.semantics { stateDescription = if (isSelected) "Selected" else "Not selected" },
        divider = true,
        onClick = onClick,
        leading = if (value == "all") null else ({ SiteAvatar(value, domains) }),
        trailing =
            if (isSelected) {
                {
                    Icon(
                        painterResource(R.drawable.ic_numeris_check_circle),
                        contentDescription = null,
                        modifier = Modifier.size(NumerisSizes.SmallIcon),
                        tint = NumerisColors.Accent,
                    )
                }
            } else {
                null
            },
    )
}
