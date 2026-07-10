@file:Suppress("ktlint:standard:function-naming")

package com.emmah.numeris

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.emmah.numeris.feature.sources.SourcesViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

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
    val symbol: String,
)

private val primaryDestinations =
    listOf(
        PrimaryDestination(OverviewRoute, "Overview", "O"),
        PrimaryDestination(TrafficRoute, "Traffic", "T"),
        PrimaryDestination(SearchRoute, "Search", "S"),
        PrimaryDestination(PerformanceRoute, "Performance", "P"),
        PrimaryDestination(MoreRoute, "More", "M"),
    )

private val backgroundSyncIntervals = listOf(6, 12, 24)

@Composable
@Suppress("FunctionNaming", "LongMethod")
fun NumerisShell(
    container: AppContainer,
    requireBiometric: (() -> Unit) -> Unit,
    authorizeGoogle: () -> Unit,
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
    val sourcesViewModel: SourcesViewModel =
        viewModel(
            factory =
                remember(container) {
                    SourcesViewModel.factory(container.connectionRepository, container.importRepository)
                },
        )
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
                    icon = { Text(destination.symbol, fontWeight = FontWeight.Bold) },
                    label = { Text(destination.label) },
                )
            }
        },
    ) {
        NavDisplay(
            modifier = Modifier.fillMaxSize(),
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
                                onRefresh = { refreshGroup(ReportingGroup.Health) },
                            )
                        }
                    SourcesRoute ->
                        NavEntry(route) {
                            SourcesScreen(
                                viewModel = sourcesViewModel,
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
    Column(
        modifier = Modifier.fillMaxSize().padding(NumerisSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Md),
    ) {
        Text("More", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        MoreCard("Health", "Uptime, incidents, and sitemap coverage", openHealth)
        MoreCard("Sources", "Connections, credentials, sync, and Windows import", openSources)
        Card(colors = CardDefaults.cardColors(containerColor = NumerisColors.Card)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(NumerisSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Md),
            ) {
                Text("Privacy and background sync", fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Biometric app lock")
                    Switch(
                        checked = preferences.biometricLockEnabled,
                        onCheckedChange = onSetBiometricLock,
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Periodic sync")
                    Switch(
                        checked = preferences.backgroundSyncEnabled,
                        onCheckedChange = { enabled ->
                            onSetBackgroundSync(enabled, preferences.backgroundSyncHours)
                        },
                    )
                }
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
            modifier = Modifier.fillMaxWidth().padding(NumerisSpacing.Lg),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Xs)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, color = NumerisColors.SecondaryText)
            }
            TextButton(onClick = onClick) { Text("Open") }
        }
    }
}
