@file:Suppress("ktlint:standard:function-naming", "MaxLineLength")

package com.emmah.numeris.feature.sources

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emmah.numeris.core.designsystem.NumerisColors
import com.emmah.numeris.core.designsystem.NumerisSpacing
import com.emmah.numeris.core.model.Connection
import com.emmah.numeris.core.model.ConnectionId
import com.emmah.numeris.core.model.SaveConnectionCommand
import com.emmah.numeris.core.model.SecretKey
import com.emmah.numeris.core.model.SourceId

private data class ConnectionSetup(
    val source: SourceId,
    val title: String,
    val fields: List<String>,
    val secretField: String? = null,
)

private val connectionSetups =
    listOf(
        ConnectionSetup(SourceId.CloudflareZone, "Cloudflare Zone Analytics", listOf("domain", "zoneId"), "API token"),
        ConnectionSetup(SourceId.CloudflareWebAnalytics, "Cloudflare Web Analytics", listOf("accountId"), "API token"),
        ConnectionSetup(SourceId.SearchConsole, "Google Search Console", listOf("sites (comma separated)")),
        ConnectionSetup(SourceId.GoogleAnalytics, "Google Analytics", listOf("domain", "propertyId")),
        ConnectionSetup(SourceId.Crux, "Chrome UX Report", emptyList(), "API key"),
        ConnectionSetup(SourceId.PageSpeed, "PageSpeed Insights", emptyList(), "API key"),
        ConnectionSetup(SourceId.Bing, "Bing Webmaster", listOf("sites (comma separated)"), "API key"),
        ConnectionSetup(SourceId.Uptime, "Uptime", emptyList()),
        ConnectionSetup(SourceId.Sitemap, "Sitemap", emptyList()),
    )

@Composable
@Suppress("FunctionNaming", "LongMethod")
fun SourcesScreen(
    viewModel: SourcesViewModel,
    requireBiometric: (() -> Unit) -> Unit,
    authorizeGoogle: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var passphrase by remember { mutableStateOf("") }
    var setup by remember { mutableStateOf<ConnectionSetup?>(null) }
    var setupValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var setupSecret by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Connection?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> selectedUri = uri }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(NumerisSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Md),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Xs)) {
                Text("Sources", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text("Connections, credentials, and one-time Windows import", color = NumerisColors.SecondaryText)
                state.message?.let { Text(it, color = NumerisColors.Copper) }
            }
        }
        items(state.connections, key = { it.id.value }) { connection ->
            ConnectionCard(
                connection = connection,
                busy = state.busyConnection == connection.id,
                onTest = { viewModel.test(connection.id) },
                onDelete = { deleteTarget = connection },
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = NumerisColors.Card)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(NumerisSpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm),
                ) {
                    Text("Add or update a source", fontWeight = FontWeight.SemiBold)
                    Text("Secrets are encrypted with Android Keystore and never stored in Room.", color = NumerisColors.SecondaryText)
                    connectionSetups.forEach { item ->
                        TextButton(
                            onClick = {
                                setup = item
                                setupValues = emptyMap()
                                setupSecret = ""
                            },
                        ) { Text("Configure ${item.title}") }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = NumerisColors.Card)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(NumerisSpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Md),
                ) {
                    Text("Google", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Authorize Search Console and Analytics with read-only access. No client secret is stored.",
                        color = NumerisColors.SecondaryText,
                    )
                    Button(onClick = authorizeGoogle) { Text("Authorize Google") }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = NumerisColors.Card)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(NumerisSpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Md),
                ) {
                    Text("Import from Windows", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Choose a .numeris-export file. Existing Android data is changed only after preview and confirmation.",
                        color = NumerisColors.SecondaryText,
                    )
                    Button(onClick = { picker.launch(arrayOf("application/octet-stream", "application/zip")) }) {
                        Text("Choose export file")
                    }
                }
            }
        }
    }

    selectedUri?.let { uri ->
        AlertDialog(
            onDismissRequest = {
                selectedUri = null
                passphrase = ""
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
                    enabled = passphrase.length >= 12 && !state.importing,
                    onClick = {
                        val secret = passphrase.toCharArray()
                        passphrase = ""
                        selectedUri = null
                        viewModel.inspectImport(uri, secret)
                    },
                ) { Text("Inspect") }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedUri = null
                    passphrase = ""
                }) { Text("Cancel") }
            },
        )
    }

    state.importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text("Replace Android data?") },
            text = {
                Text(
                    "${preview.connections} connections, ${preview.rowCounts.values.sum()} rows, " +
                        "domains: ${preview.domains.joinToString().ifBlank { "none" }}",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !state.importing,
                    onClick = { requireBiometric { viewModel.replaceImport() } },
                ) { Text("Replace Android data") }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text("Cancel") } },
        )
    }

    setup?.let { selected ->
        val valid = selected.fields.all { setupValues[it].orEmpty().isNotBlank() }
        AlertDialog(
            onDismissRequest = { setup = null },
            title = { Text(selected.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm)) {
                    selected.fields.forEach { field ->
                        OutlinedTextField(
                            value = setupValues[field].orEmpty(),
                            onValueChange = { value -> setupValues = setupValues + (field to value) },
                            label = { Text(field) },
                            singleLine = true,
                        )
                    }
                    selected.secretField?.let { label ->
                        OutlinedTextField(
                            value = setupSecret,
                            onValueChange = { setupSecret = it },
                            label = { Text(label) },
                            supportingText = { Text("Leave empty to keep an existing secret.") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = valid,
                    onClick = {
                        viewModel.save(selected.toCommand(setupValues, setupSecret))
                        setupSecret = ""
                        setup = null
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { setup = null }) { Text("Cancel") } },
        )
    }

    deleteTarget?.let { connection ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Disconnect ${connection.displayName}?") },
            text = { Text("Cached report data is preserved. The connection metadata and its stored secret are removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        viewModel.delete(connection.id)
                    },
                ) { Text("Disconnect") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
@Suppress("FunctionNaming")
private fun ConnectionCard(
    connection: Connection,
    busy: Boolean,
    onTest: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = NumerisColors.Card)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(NumerisSpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(connection.displayName, fontWeight = FontWeight.SemiBold)
                Text(if (connection.configured) "Configured" else "Disconnected", color = NumerisColors.SecondaryText)
                Text(
                    connection.lastSuccessfulSync?.let { "Last sync $it" } ?: "Not synced yet",
                    color = NumerisColors.TertiaryText,
                )
                connection.lastError?.let { Text(it, color = NumerisColors.Copper) }
            }
            Column {
                TextButton(enabled = !busy, onClick = onTest) { Text(if (busy) "Testing…" else "Test") }
                TextButton(enabled = !busy, onClick = onDelete) { Text("Disconnect") }
            }
        }
    }
}

private fun ConnectionSetup.toCommand(
    values: Map<String, String>,
    secret: String,
): SaveConnectionCommand {
    val normalized = values.mapKeys { (key, _) -> key.substringBefore(' ') }
    val identity = normalized["domain"] ?: normalized["propertyId"] ?: normalized["accountId"] ?: source.name.lowercase()
    val id = ConnectionId("${source.storageName}:$identity")
    val secrets =
        if (secret.isBlank()) {
            emptyMap()
        } else {
            mapOf(secretKey(id) to secret.toCharArray())
        }
    return SaveConnectionCommand(id, source, title, normalized, secrets)
}

private fun ConnectionSetup.secretKey(id: ConnectionId): SecretKey =
    when (source) {
        SourceId.CloudflareZone -> SecretKey("cloudflare:${id.value}:api_token")
        SourceId.CloudflareWebAnalytics -> SecretKey("web_analytics:${id.value}:api_token")
        SourceId.Crux -> SecretKey("crux:crux:api_key")
        SourceId.PageSpeed -> SecretKey("pagespeed:pagespeed:api_key")
        SourceId.Bing -> SecretKey("bing:bing:api_key")
        else -> SecretKey("unused:${id.value}")
    }

private val SourceId.storageName: String
    get() =
        when (this) {
            SourceId.CloudflareZone -> "cloudflare"
            SourceId.CloudflareWebAnalytics -> "web_analytics"
            SourceId.SearchConsole -> "search_console"
            SourceId.GoogleAnalytics -> "google_analytics"
            SourceId.Crux -> "crux"
            SourceId.PageSpeed -> "pagespeed"
            SourceId.Bing -> "bing_webmaster"
            SourceId.Uptime -> "uptime"
            SourceId.Sitemap -> "sitemap"
        }
