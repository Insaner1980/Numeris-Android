@file:Suppress("ktlint:standard:function-naming")

package com.emmah.numeris

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.emmah.numeris.core.designsystem.NumerisColors
import com.emmah.numeris.core.designsystem.NumerisSpacing
import com.emmah.numeris.core.model.ReportingGroup
import com.emmah.numeris.core.model.SyncRequest
import com.emmah.numeris.core.model.SyncState
import com.emmah.numeris.data.AppPreferences
import com.emmah.numeris.data.OverviewSnapshot
import com.emmah.numeris.feature.overview.OverviewScreen
import com.emmah.numeris.feature.sources.SourcesScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import com.emmah.numeris.core.designsystem.R as DesignSystemR

@Serializable private data object OverviewRoute : NavKey

@Serializable private data object TrafficRoute : NavKey

@Serializable private data object SearchRoute : NavKey

@Serializable private data object PerformanceRoute : NavKey

@Serializable private data object MoreRoute : NavKey

@Serializable private data object HealthRoute : NavKey

@Serializable private data object SourcesRoute : NavKey

private data class PrimaryDestination(
    val route: NavKey,
    val label: String,
    val icon: Int,
)

private val primaryDestinations =
    listOf(
        PrimaryDestination(OverviewRoute, "Overview", DesignSystemR.drawable.ic_numeris_dashboard),
        PrimaryDestination(TrafficRoute, "Traffic", DesignSystemR.drawable.ic_numeris_monitoring),
        PrimaryDestination(SearchRoute, "Search", DesignSystemR.drawable.ic_numeris_search),
        PrimaryDestination(PerformanceRoute, "Vitals", DesignSystemR.drawable.ic_numeris_speed),
        PrimaryDestination(MoreRoute, "More", DesignSystemR.drawable.ic_numeris_more),
    )

private val backgroundSyncIntervals = listOf(6, 12, 24)

@Composable
@Suppress("FunctionNaming", "LongMethod")
fun NumerisShell(
    container: AppContainer,
    requireBiometric: (() -> Unit) -> Unit,
    authorizeGoogle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(OverviewRoute)
    val selected = backStack.lastOrNull()
    val preferences by container.preferences.preferences.collectAsStateWithLifecycle(AppPreferences())
    val overviewFlow =
        remember(preferences.selectedDomain, preferences.selectedPeriod) {
            container.overviewRepository.observe(preferences.selectedDomain, preferences.selectedPeriod)
        }
    val overview by overviewFlow.collectAsStateWithLifecycle(OverviewSnapshot(0, 0, 0, null, 0, 0, null))
    val syncState by container.syncOrchestrator.observeState().collectAsStateWithLifecycle(SyncState.Idle)
    val domains by container.reportRepository.observeDomains().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val refreshGroup: (ReportingGroup) -> Unit = { group ->
        scope.launch {
            container.syncOrchestrator.syncReportingGroup(
                group,
                SyncRequest(
                    period = preferences.selectedPeriod,
                    domain = preferences.selectedDomain.takeUnless { it == "all" },
                ),
            )
        }
    }
    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteItems = {
            primaryDestinations.forEach { destination ->
                item(
                    selected =
                        selected == destination.route ||
                            (destination.route == MoreRoute && selected in listOf(HealthRoute, SourcesRoute)),
                    onClick = {
                        backStack.clear()
                        backStack.add(destination.route)
                    },
                    icon = {
                        Icon(
                            painter = painterResource(destination.icon),
                            contentDescription = null,
                        )
                    },
                    label = { Text(destination.label) },
                )
            }
        },
    ) {
        NavDisplay(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    ),
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryProvider = { route ->
                when (route) {
                    OverviewRoute ->
                        NavEntry(route) {
                            OverviewScreen(
                                snapshot = overview,
                                domain = preferences.selectedDomain,
                                domains = domains,
                                period = preferences.selectedPeriod,
                                syncing = syncState is SyncState.Running,
                                onSelectPeriod = { period ->
                                    scope.launch { container.preferences.selectPeriod(period) }
                                },
                                onSelectDomain = { domain ->
                                    scope.launch { container.preferences.selectDomain(domain) }
                                },
                                onRefresh = {
                                    scope.launch {
                                        container.syncOrchestrator.syncAll(
                                            SyncRequest(
                                                period = preferences.selectedPeriod,
                                                domain = preferences.selectedDomain.takeUnless { it == "all" },
                                            ),
                                        )
                                    }
                                },
                                onOpenSources = { backStack.add(SourcesRoute) },
                            )
                        }
                    TrafficRoute ->
                        NavEntry(route) {
                            TrafficReportScreen(
                                dao = container.reportRepository,
                                domain = preferences.selectedDomain,
                                domains = domains,
                                period = preferences.selectedPeriod,
                                syncing = syncState is SyncState.Running,
                                onSelectDomain = { domain ->
                                    scope.launch { container.preferences.selectDomain(domain) }
                                },
                                onSelectPeriod = { period ->
                                    scope.launch { container.preferences.selectPeriod(period) }
                                },
                                onRefresh = { refreshGroup(ReportingGroup.Traffic) },
                            )
                        }
                    SearchRoute ->
                        NavEntry(route) {
                            SearchReportScreen(
                                dao = container.reportRepository,
                                domain = preferences.selectedDomain,
                                domains = domains,
                                period = preferences.selectedPeriod,
                                syncing = syncState is SyncState.Running,
                                onSelectDomain = { domain ->
                                    scope.launch { container.preferences.selectDomain(domain) }
                                },
                                onSelectPeriod = { period ->
                                    scope.launch { container.preferences.selectPeriod(period) }
                                },
                                onRefreshIndexing = { domain ->
                                    scope.launch {
                                        container.syncOrchestrator.syncSource(
                                            com.emmah.numeris.core.model.SourceId.Sitemap,
                                            SyncRequest(preferences.selectedPeriod, domain),
                                        )
                                        container.inspectIndexing(domain)
                                    }
                                },
                                onRefresh = { refreshGroup(ReportingGroup.Search) },
                            )
                        }
                    PerformanceRoute ->
                        NavEntry(route) {
                            PerformanceReportScreen(
                                dao = container.reportRepository,
                                domain = preferences.selectedDomain,
                                domains = domains,
                                period = preferences.selectedPeriod,
                                syncing = syncState is SyncState.Running,
                                onSelectDomain = { domain ->
                                    scope.launch { container.preferences.selectDomain(domain) }
                                },
                                onSelectPeriod = { period ->
                                    scope.launch { container.preferences.selectPeriod(period) }
                                },
                                onRefresh = { refreshGroup(ReportingGroup.Performance) },
                            )
                        }
                    MoreRoute ->
                        NavEntry(route) {
                            MoreScreen(
                                preferences = preferences,
                                onSetBackgroundSync = { enabled, hours ->
                                    scope.launch { container.preferences.setBackgroundSync(enabled, hours) }
                                },
                                onSetBiometricLock = { enabled ->
                                    scope.launch { container.preferences.setBiometricLock(enabled) }
                                },
                                openHealth = { backStack.add(HealthRoute) },
                                openSources = { backStack.add(SourcesRoute) },
                            )
                        }
                    HealthRoute ->
                        NavEntry(route) {
                            HealthReportScreen(
                                dao = container.reportRepository,
                                domain = preferences.selectedDomain,
                                domains = domains,
                                period = preferences.selectedPeriod,
                                syncing = syncState is SyncState.Running,
                                onSelectDomain = { domain ->
                                    scope.launch { container.preferences.selectDomain(domain) }
                                },
                                onSelectPeriod = { period ->
                                    scope.launch { container.preferences.selectPeriod(period) }
                                },
                                onRefresh = { refreshGroup(ReportingGroup.Health) },
                            )
                        }
                    SourcesRoute ->
                        NavEntry(route) {
                            SourcesScreen(
                                connections = container.connectionRepository,
                                imports = container.importRepository,
                                requireBiometric = requireBiometric,
                                authorizeGoogle = authorizeGoogle,
                            )
                        }
                    else -> error("Unknown navigation route")
                }
            },
        )
    }
}

@Composable
@Suppress("FunctionNaming")
private fun MoreScreen(
    preferences: AppPreferences,
    onSetBackgroundSync: (Boolean, Int) -> Unit,
    onSetBiometricLock: (Boolean) -> Unit,
    openHealth: () -> Unit,
    openSources: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(NumerisSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Md),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Xs)) {
                Text("More", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Settings and data connections",
                    color = NumerisColors.SecondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item { MoreCard("Health", "Uptime, incidents, and sitemap coverage", openHealth) }
        item { MoreCard("Sources", "Connections, credentials, sync, and Windows import", openSources) }
        item {
            PrivacySyncCard(preferences, onSetBackgroundSync, onSetBiometricLock)
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun PrivacySyncCard(
    preferences: AppPreferences,
    onSetBackgroundSync: (Boolean, Int) -> Unit,
    onSetBiometricLock: (Boolean) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = NumerisColors.Card)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(NumerisSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Lg),
        ) {
            Text("Privacy and sync", style = MaterialTheme.typography.titleLarge)
            SettingSwitchRow(
                title = "Biometric app lock",
                detail = "Require your fingerprint or device credential after leaving the app.",
                checked = preferences.biometricLockEnabled,
                onCheckedChange = onSetBiometricLock,
            )
            SettingSwitchRow(
                title = "Periodic sync",
                detail = "Refresh configured sources in the background.",
                checked = preferences.backgroundSyncEnabled,
                onCheckedChange = { enabled ->
                    onSetBackgroundSync(enabled, preferences.backgroundSyncHours)
                },
            )
            Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm)) {
                Text(
                    "Sync interval",
                    color = NumerisColors.SecondaryText,
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm)) {
                    backgroundSyncIntervals.forEach { hours ->
                        FilterChip(
                            selected = preferences.backgroundSyncHours == hours,
                            enabled = preferences.backgroundSyncEnabled,
                            onClick = { onSetBackgroundSync(true, hours) },
                            label = { Text("${hours}h") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("FunctionNaming")
private fun MoreCard(
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NumerisColors.Card),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(NumerisSpacing.Xl),
            horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Lg),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, color = NumerisColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(
                painter = painterResource(DesignSystemR.drawable.ic_numeris_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
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
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Lg),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = NumerisColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
