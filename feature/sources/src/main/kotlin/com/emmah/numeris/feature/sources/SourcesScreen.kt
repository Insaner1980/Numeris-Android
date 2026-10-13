@file:Suppress("ktlint:standard:function-naming")

package com.emmah.numeris.feature.sources

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emmah.numeris.core.designsystem.NoticeCard
import com.emmah.numeris.core.designsystem.NumerisColors
import com.emmah.numeris.core.designsystem.NumerisSpacing
import com.emmah.numeris.core.designsystem.NumerisTopBar
import com.emmah.numeris.core.designsystem.PillTone
import com.emmah.numeris.core.designsystem.SectionHeader
import com.emmah.numeris.core.model.Connection
import com.emmah.numeris.core.model.ConnectionId
import com.emmah.numeris.core.model.ConnectionRepository
import com.emmah.numeris.core.model.SaveConnectionCommand
import com.emmah.numeris.core.model.SecretKey
import com.emmah.numeris.core.model.SiteIdentity
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.data.AndroidImportRepository
import com.emmah.numeris.data.GoogleAuthorizationFeedback
import com.emmah.numeris.data.ReportRepository
import com.emmah.numeris.data.SourceDiscovery

@Composable
@Suppress("FunctionNaming", "LongMethod")
fun SourcesScreen(
    connections: ConnectionRepository,
    imports: AndroidImportRepository,
    requireBiometric: (() -> Unit) -> Unit,
    authorizeGoogle: () -> Unit,
    reports: ReportRepository,
    discovery: SourceDiscovery,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialUrl: String = "",
    viewModel: SourcesViewModel = viewModel(factory = SourcesViewModel.factory(connections, imports, discovery)),
    googleAuthorizationFeedback: GoogleAuthorizationFeedback? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var setupSource by rememberSaveable { mutableStateOf<SourceId?>(null) }
    val setup = connectionSetups.firstOrNull { it.source == setupSource }
    var setupValues by rememberSaveable { mutableStateOf<Map<String, String>>(emptyMap()) }
    var setupSecret by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Connection?>(null) }
    var editingIdValue by rememberSaveable { mutableStateOf<String?>(null) }
    var choosingSource by rememberSaveable { mutableStateOf(false) }
    val editingId = editingIdValue?.let(::ConnectionId)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> selectedUri = uri }
    val openSetup: (ConnectionSetup, Connection?) -> Unit = { provider, connection ->
        setupSource = provider.source
        setupValues =
            connection?.let { existing ->
                provider.fields.associateWith { existing.metadata[it.substringBefore(' ')].orEmpty() }
            } ?: emptyMap()
        editingIdValue = connection?.id?.value
        setupSecret = ""
        viewModel.clearDiscovery()
    }
    val closeSetup = {
        setupSecret = ""
        setupSource = null
    }
    val actions =
        ConnectionActions(
            busy = state.busyConnection != null,
            onTest = { viewModel.test(it.id) },
            onDelete = { deleteTarget = it },
            onEdit = { connection ->
                connectionSetups.firstOrNull { it.source == connection.source }?.let { openSetup(it, connection) }
            },
        )
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = NumerisSpacing.Lg, end = NumerisSpacing.Lg, bottom = NumerisSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Lg),
    ) {
        item { SourcesHeader(state.connections, onBack) { choosingSource = true } }
        state.message?.let { message -> item { NoticeCard("Sources", message, PillTone.Neutral) } }
        if (state.connections.isEmpty()) {
            item {
                NoticeCard(
                    "No sources connected",
                    "Add a source to start caching private reports on this device.",
                    PillTone.Neutral,
                    actionLabel = "Add source",
                    onAction = { choosingSource = true },
                )
            }
        }
        sourceGroups(state.connections, actions, reports, initialUrl) {
            GoogleAuthorizationRow(googleAuthorizationFeedback, authorizeGoogle)
        }
        item { SectionHeader("Data transfer") }
        item {
            ImportRow(
                enabled = !state.importing,
                onChooseFile = { picker.launch(arrayOf("application/octet-stream", "application/zip")) },
            )
        }
    }

    if (choosingSource) {
        AddSourceSheet(
            onDismiss = { choosingSource = false },
            onChoose = { provider ->
                choosingSource = false
                openSetup(provider, null)
            },
        )
    }
    selectedUri?.let { uri ->
        ImportPassphraseDialog(
            importing = state.importing,
            onInspect = { secret ->
                selectedUri = null
                viewModel.inspectImport(uri, secret)
            },
            onDismiss = { selectedUri = null },
        )
    }
    state.importPreview?.let { preview ->
        ImportPreviewDialog(
            preview,
            state.importing,
            onConfirm = { requireBiometric { viewModel.replaceImport() } },
            onCancel = viewModel::cancelImport,
        )
    }
    setup?.let { selected ->
        SourceForm(
            selected,
            setupValues,
            setupSecret,
            editingId != null,
            state,
            { setupValues = it },
            { setupSecret = it },
            {
                viewModel.discover(
                    selected.source,
                    editingId,
                    setupValues.mapKeys { it.key.substringBefore(' ') },
                    setupSecret.toCharArray(),
                )
            },
            {
                val command = runCatching { selected.toCommand(setupValues, setupSecret, editingId) }.getOrNull()
                if (command == null) {
                    viewModel.showValidationError()
                } else {
                    viewModel.save(command)
                    closeSetup()
                }
            },
            closeSetup,
        )
    }
    deleteTarget?.let { connection ->
        DisconnectDialog(
            connection,
            onConfirm = {
                deleteTarget = null
                viewModel.delete(connection.id)
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

/** Yhteydet ryhmittäin. Search-ryhmässä on aina Google-tilin rivi, Performance-ryhmässä mitatut URL:t. */
private fun LazyListScope.sourceGroups(
    connections: List<Connection>,
    actions: ConnectionActions,
    reports: ReportRepository,
    initialUrl: String,
    googleRow: @Composable () -> Unit,
) {
    SourceGroup.entries.forEach { group ->
        val grouped = connections.filter { it.source.group == group }
        val showsGoogle = group == SourceGroup.Search
        if (grouped.isNotEmpty() || showsGoogle) {
            item(key = "header-${group.name}") { SectionHeader(group.title) }
            item(key = "group-${group.name}") {
                ConnectionGroup(grouped, actions, hasFooter = showsGoogle) { if (showsGoogle) googleRow() }
            }
        }
        if (group == SourceGroup.Performance) {
            item(key = "measured-urls") { MeasuredUrls(reports, initialUrl) }
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun SourcesHeader(
    connections: List<Connection>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
) {
    val attention =
        connections.count {
            it.source != SourceId.GoogleAnalytics && connectionStatus(it).second != PillTone.Positive
        }
    Column {
        NumerisTopBar("Sources", onBack, actions = { AddSourceButton(onAdd) })
        Text(
            when {
                connections.isEmpty() -> "Connections, credentials, and Windows import"
                attention == 0 -> "${connections.size} connected, all up to date"
                else -> "${connections.size} connected, $attention need attention"
            },
            color = NumerisColors.TertiaryText,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun ConnectionSetup.toCommand(
    values: Map<String, String>,
    secret: String,
    existingId: ConnectionId?,
): SaveConnectionCommand {
    require(secretField == null || secret.isNotBlank() || existingId != null) { "A new connection requires a secret" }
    val normalized =
        values.mapKeys { (key, _) -> key.substringBefore(' ') }.mapValues { (key, value) ->
            if (key == "domain") SiteIdentity.normalizeDomain(value) else value.trim()
        }
    val identity =
        normalized["domain"] ?: normalized["propertyId"] ?: normalized["accountId"] ?: source.name.lowercase()
    val id = existingId ?: ConnectionId("${source.storageName}:$identity")
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
