@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emmah.numeris.core.designsystem.ListCard
import com.emmah.numeris.core.designsystem.ListRow
import com.emmah.numeris.core.designsystem.NumerisColors
import com.emmah.numeris.core.designsystem.NumerisSpacing
import com.emmah.numeris.core.designsystem.SectionHeader
import com.emmah.numeris.core.model.UiState
import com.emmah.numeris.data.AppPreferences
import kotlinx.coroutines.flow.catch
import com.emmah.numeris.core.designsystem.R as DesignSystemR

private val backgroundSyncIntervals = listOf(6, 12, 24)

@Composable
@Suppress("FunctionNaming")
internal fun MoreScreen(
    preferences: AppPreferences,
    onSetBackgroundSync: (Boolean, Int) -> Unit,
    openHealth: () -> Unit,
    openSources: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(NumerisSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Lg),
    ) {
        item { Text("More", style = MaterialTheme.typography.headlineMedium) }
        item {
            ListCard {
                NavigationRow("Health", "Uptime, incidents, and sitemap coverage", openHealth, divider = true)
                NavigationRow("Sources", "Connections, credentials, sync, and Windows import", openSources)
            }
        }
        item { SectionHeader("Sync") }
        item { SyncCard(preferences, onSetBackgroundSync) }
    }
}

@Composable
private fun NavigationRow(
    title: String,
    detail: String,
    onClick: () -> Unit,
    divider: Boolean = false,
) {
    ListRow(
        title,
        supporting = detail,
        divider = divider,
        onClick = onClick,
        trailing = {
            Icon(
                painter = painterResource(DesignSystemR.drawable.ic_numeris_chevron_right),
                contentDescription = null,
                tint = NumerisColors.TertiaryText,
            )
        },
    )
}

@Composable
@Suppress("FunctionNaming")
private fun SyncCard(
    preferences: AppPreferences,
    onSetBackgroundSync: (Boolean, Int) -> Unit,
) {
    ListCard {
        SettingSwitchRow(
            title = "Periodic sync",
            detail = "Refresh configured sources in the background.",
            checked = preferences.backgroundSyncEnabled,
            onCheckedChange = { enabled -> onSetBackgroundSync(enabled, preferences.backgroundSyncHours) },
        )
        if (preferences.backgroundSyncEnabled) {
            HorizontalDivider(color = NumerisColors.Divider)
            Column(
                Modifier.padding(vertical = NumerisSpacing.Md),
                verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm),
            ) {
                Text("Sync interval", style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm)) {
                    backgroundSyncIntervals.forEach { hours ->
                        FilterChip(
                            selected = preferences.backgroundSyncHours == hours,
                            onClick = { onSetBackgroundSync(true, hours) },
                            label = { Text("Every $hours h") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun SettingSwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    divider: Boolean = false,
) {
    ListRow(
        title,
        supporting = detail,
        divider = divider,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.semantics { contentDescription = title },
            )
        },
    )
}

@Composable
internal fun <T> CachedReport(
    source: kotlinx.coroutines.flow.Flow<T>,
    content: @Composable (T) -> Unit,
) {
    var retry by remember { mutableIntStateOf(0) }
    val states =
        remember(source, retry) {
            var cached: T? = null
            kotlinx.coroutines.flow
                .flow<UiState<T>> {
                    emit(UiState.Loading)
                    source.collect { value ->
                        cached = value
                        emit(UiState.Content(value))
                    }
                }.catch { failure ->
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    emit(UiState.Error("Saved reports could not be read. Retry to reopen the local data.", cached))
                }
        }
    val state by states.collectAsStateWithLifecycle(UiState.Loading)
    val value =
        when (val current = state) {
            is UiState.Content -> current.value
            is UiState.Error -> current.cached
            else -> null
        }
    Column {
        when (val current = state) {
            UiState.Loading -> CircularProgressIndicator(Modifier.padding(NumerisSpacing.Lg))
            is UiState.Error -> {
                Text(current.message, Modifier.padding(NumerisSpacing.Lg))
                TextButton({ retry++ }) { Text("Retry") }
            }
            UiState.Empty -> Text("No saved reports")
            else -> Unit
        }
        value?.let { content(it) }
    }
}
