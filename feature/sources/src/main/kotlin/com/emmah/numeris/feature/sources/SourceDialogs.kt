@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris.feature.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.emmah.numeris.core.designsystem.NumerisSpacing
import com.emmah.numeris.core.model.Connection
import com.emmah.numeris.core.model.DisplayFormat
import com.emmah.numeris.data.ImportPreview

private const val MIN_PASSPHRASE_LENGTH = 12

/** Salasanan kysely. Salasana tyhjennetään heti, kun se on luovutettu tarkastettavaksi. */
@Composable
internal fun ImportPassphraseDialog(
    importing: Boolean,
    onInspect: (CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {
            passphrase = ""
            onDismiss()
        },
        title = { Text("Open encrypted export") },
        text = {
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text("Passphrase") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = passphrase.length >= MIN_PASSPHRASE_LENGTH && !importing,
                onClick = {
                    val secret = passphrase.toCharArray()
                    passphrase = ""
                    onInspect(secret)
                },
            ) { Text("Inspect") }
        },
        dismissButton = {
            TextButton(onClick = {
                passphrase = ""
                onDismiss()
            }) { Text("Cancel") }
        },
    )
}

@Composable
internal fun ImportPreviewDialog(
    preview: ImportPreview,
    importing: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (preview.includesConfiguration) "Import Windows data?" else "Replace cached report data?") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Md),
            ) {
                Text("Snapshot ${DisplayFormat.timestamp(preview.createdAt)}")
                Text("${DisplayFormat.number(preview.rowCounts.values.sum())} cached records will be replaced.")
                for ((label, tables) in importCategories) {
                    Text("$label: ${DisplayFormat.number(tables.sumOf { preview.rowCounts[it] ?: 0L })} records")
                }
                if (preview.includesConfiguration) {
                    Text(
                        "${preview.connections} connections and ${preview.secrets} API keys will be imported " +
                            "with report preferences.",
                    )
                    Text(
                        "Matching connections are updated. Other Android connections and retired history are " +
                            "preserved. Google sign-in is completed again on this device.",
                    )
                } else {
                    Text(
                        "Connections, credentials, settings, retired history and desktop-only categories are " +
                            "not replaced.",
                    )
                }
                Text("Sites: ${preview.domains.joinToString().ifBlank { "none" }}")
            }
        },
        confirmButton = {
            TextButton(enabled = !importing, onClick = onConfirm) {
                Text(if (preview.includesConfiguration) "Import data" else "Replace report data")
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
internal fun DisconnectDialog(
    connection: Connection,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disconnect ${connectionTitle(connection)}?") },
        text = {
            Text("Cached report data is preserved. The connection metadata and its stored secret are removed.")
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Disconnect") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val importCategories =
    listOf(
        "Traffic" to
            listOf(
                "cloudflare_traffic",
                "cloudflare_countries",
                "cloudflare_pages",
                "cloudflare_status_codes",
                "web_analytics_sites",
                "web_analytics_daily",
                "web_analytics_referrers",
                "web_analytics_pages",
                "web_analytics_countries",
            ),
        "Search" to
            listOf(
                "search_console",
                "search_devices",
                "search_page_queries",
                "bing_sites",
                "bing_rank_traffic",
                "bing_query_stats",
                "bing_page_stats",
                "bing_raw_items",
            ),
        "Performance" to listOf("performance_urls", "crux_metric_points", "pagespeed_runs", "pagespeed_audits"),
        "Health" to listOf("sitemap_urls", "uptime_checks"),
    )
