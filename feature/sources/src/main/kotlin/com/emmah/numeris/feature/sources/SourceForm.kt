@file:Suppress("ktlint:standard:function-naming", "MaxLineLength")

package com.emmah.numeris.feature.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.emmah.numeris.core.designsystem.ListRow
import com.emmah.numeris.core.designsystem.NumerisColors
import com.emmah.numeris.core.designsystem.NumerisSpacing
import com.emmah.numeris.core.designsystem.NumerisTopBar
import com.emmah.numeris.core.model.SourceId

@Composable
@Suppress("FunctionNaming")
internal fun SourceForm(
    selected: ConnectionSetup,
    setupValues: Map<String, String>,
    setupSecret: String,
    hasExistingConnection: Boolean,
    state: SourcesUiState,
    onValues: (Map<String, String>) -> Unit,
    onSecret: (String) -> Unit,
    onDiscover: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = NumerisColors.Background) {
            Column(
                Modifier
                    .safeDrawingPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(NumerisSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Md),
            ) {
                NumerisTopBar(selected.title, onDismiss)
                Text(
                    selected.description,
                    color = NumerisColors.TertiaryText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                selected.fields.forEach { field ->
                    OutlinedTextField(
                        setupValues[field].orEmpty(),
                        { value -> onValues(setupValues + (field to value)) },
                        Modifier.fillMaxWidth(),
                        label = { Text(fieldLabel(field, selected.source)) },
                    )
                }
                if (selected.source in discoverableSources) {
                    DiscoverTargets(selected, setupValues, state, onValues, onDiscover)
                }
                selected.secretField?.let { label ->
                    OutlinedTextField(
                        setupSecret,
                        onSecret,
                        Modifier.fillMaxWidth(),
                        label = { Text(label) },
                        supportingText = {
                            Text("Secrets must be entered again after rotation. Leave empty to keep an existing secret.")
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                }
                Button(
                    onSave,
                    Modifier.fillMaxWidth(),
                    enabled =
                        selected.canSave(setupValues, setupSecret, hasExistingConnection) &&
                            state.busyConnection == null,
                ) {
                    Text("Save")
                }
            }
        }
    }
}

private val discoverableSources =
    setOf(SourceId.CloudflareZone, SourceId.CloudflareWebAnalytics, SourceId.SearchConsole, SourceId.Bing)

/** Kaikki kentät täytetty ja salaisuus annettu, ellei olemassa olevan yhteyden salaisuutta säilytetä. */
private fun ConnectionSetup.canSave(
    values: Map<String, String>,
    secret: String,
    hasExistingConnection: Boolean,
): Boolean =
    fields.all { values[it].orEmpty().isNotBlank() } &&
        (secretField == null || secret.isNotBlank() || hasExistingConnection)

@Composable
@Suppress("FunctionNaming")
private fun DiscoverTargets(
    selected: ConnectionSetup,
    setupValues: Map<String, String>,
    state: SourcesUiState,
    onValues: (Map<String, String>) -> Unit,
    onDiscover: () -> Unit,
) {
    Column {
        FilledTonalButton(enabled = !state.discovering, onClick = {
            onDiscover()
        }) { Text(if (state.discovering) "Discovering targets…" else "Discover available targets") }
        state.message?.let { Text(it, color = NumerisColors.SecondaryText) }
        state.targets.forEach { target ->
            ListRow(target.domain, supporting = target.id, divider = true, onClick = {
                onValues(
                    when (selected.source) {
                        SourceId.CloudflareZone ->
                            setupValues +
                                mapOf("domain" to target.domain, "zoneId" to target.id)
                        SourceId.CloudflareWebAnalytics ->
                            setupValues +
                                (
                                    "siteMappings" to
                                        (
                                            setupValues["siteMappings"].orEmpty().lines().filter(
                                                String::isNotBlank,
                                            ) +
                                                "${target.domain}=${target.id}"
                                        ).distinct().joinToString("\n")
                                )
                        else ->
                            setupValues +
                                (
                                    "sites (comma separated)" to
                                        (
                                            setupValues["sites (comma separated)"]
                                                .orEmpty()
                                                .split(
                                                    ',',
                                                ).filter(String::isNotBlank) +
                                                target.id
                                        ).distinct().joinToString(",")
                                )
                    },
                )
            })
        }
    }
}
