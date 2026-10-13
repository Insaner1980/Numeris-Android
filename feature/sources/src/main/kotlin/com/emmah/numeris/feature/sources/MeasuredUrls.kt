@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris.feature.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emmah.numeris.core.designsystem.NumerisColors
import com.emmah.numeris.core.designsystem.NumerisSpacing
import com.emmah.numeris.core.designsystem.ReportSection
import com.emmah.numeris.core.model.CompactFormat
import com.emmah.numeris.core.model.MeasuredUrl
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.data.ReportRepository
import kotlinx.coroutines.launch
import com.emmah.numeris.core.designsystem.R as DesignSystemR

@Composable
internal fun MeasuredUrls(
    reports: ReportRepository,
    initialUrl: String,
    modifier: Modifier = Modifier,
) {
    val flow = remember(reports) { reports.observePerformance("all") }
    val report by flow.collectAsStateWithLifecycle(null)
    var value by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    var error by remember { mutableStateOf<String?>(null) }
    var remove by remember { mutableStateOf<MeasuredUrl?>(null) }
    val scope = rememberCoroutineScope()
    ReportSection(
        "Measured URLs",
        modifier,
        supporting = "Used by CrUX and PageSpeed. Saving a URL does not fetch a report.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm)) {
            OutlinedTextField(
                value,
                {
                    value = it
                    error = null
                },
                Modifier.fillMaxWidth(),
                label = { Text("Full HTTPS URL") },
                isError = error != null,
                supportingText = error?.let { message -> { Text(message) } },
                singleLine = true,
            )
            FilledTonalButton({
                scope.launch {
                    runCatching { reports.addUrl(value) }.onSuccess { value = "" }.onFailure {
                        error = "Enter a valid HTTPS URL without credentials or secret query parameters."
                    }
                }
            }, enabled = value.isNotBlank()) { Text("Add measured URL") }
            report?.urls?.forEach { url ->
                MeasuredUrlRow(url, { scope.launch { reports.setUrlEnabled(url.id, it) } }) { remove = url }
            }
        }
    }
    remove?.let { url ->
        AlertDialog(
            onDismissRequest = { remove = null },
            title = { Text("Remove measured URL?") },
            text = {
                Text(
                    "${url.url}\nCached performance history is preserved. Future refreshes will not measure this URL.",
                )
            },
            confirmButton = {
                TextButton({
                    scope.launch { reports.deleteUrl(url.id) }
                    remove = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton({ remove = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MeasuredUrlRow(
    url: MeasuredUrl,
    onEnableChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Column {
        HorizontalDivider(color = NumerisColors.Divider)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(CompactFormat.url(url.url), style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (url.enabled) "Measured on refresh" else "Paused",
                    color = NumerisColors.TertiaryText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(
                url.enabled,
                onEnableChange,
                Modifier.semantics { contentDescription = "Measure ${url.url}" },
            )
            IconButton(onRemove) {
                Icon(
                    painterResource(DesignSystemR.drawable.ic_numeris_delete),
                    contentDescription = "Remove measured URL",
                    tint = NumerisColors.TertiaryText,
                )
            }
        }
    }
}

internal fun fieldLabel(
    field: String,
    source: SourceId,
): String =
    when (field.substringBefore(' ')) {
        "domain" -> "Site domain"
        "zoneId" -> "Zone ID"
        "siteMappings" -> "Web Analytics targets: domain=site tag, one per line"
        "accountId" -> "Account ID"
        "sites" ->
            if (source == SourceId.SearchConsole) {
                "Search Console properties, separated by commas"
            } else {
                "Site URLs, separated by commas"
            }
        else -> field
    }
