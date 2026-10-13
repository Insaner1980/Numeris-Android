@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItemColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.emmah.numeris.core.designsystem.NumerisColors
import com.emmah.numeris.core.designsystem.NumerisSizes
import com.emmah.numeris.core.designsystem.NumerisSpacing
import com.emmah.numeris.core.designsystem.PillTone
import com.emmah.numeris.core.designsystem.StatusPill
import com.emmah.numeris.core.model.InsightTarget
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.model.SyncState
import com.emmah.numeris.core.model.contains
import com.emmah.numeris.core.model.displayName
import com.emmah.numeris.data.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import com.emmah.numeris.core.designsystem.R as DesignSystemR

@Serializable internal data object OverviewRoute : NavKey

@Serializable internal data object TrafficRoute : NavKey

@Serializable internal data object SearchRoute : NavKey

@Serializable internal data object PerformanceRoute : NavKey

@Serializable internal data object MoreRoute : NavKey

@Serializable internal data object HealthRoute : NavKey

@Serializable internal data class SourcesRoute(
    val url: String = "",
) : NavKey

@Serializable internal data class PageRoute(
    val domain: String,
    val path: String,
    val period: Period,
) : NavKey

internal data class PrimaryDestination(
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

private fun routeForPage(page: String): NavKey =
    when (page) {
        "traffic" -> TrafficRoute
        "search" -> SearchRoute
        "performance" -> PerformanceRoute
        "health" -> HealthRoute
        "sources" -> SourcesRoute()
        "more" -> MoreRoute
        else -> OverviewRoute
    }

@Composable
fun NumerisShell(
    container: AppContainer,
    authorizeGoogle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val saved by container.preferences.preferences.collectAsStateWithLifecycle(null)
    val preferences = saved
    if (preferences == null) {
        CircularProgressIndicator(modifier.padding(NumerisSpacing.Lg))
        return
    }
    val initial = remember { routeForPage(preferences.lastPage) }
    val backStack = rememberNavBackStack(initial)
    val syncState by container.syncOrchestrator.observeState().collectAsStateWithLifecycle(SyncState.Idle)
    val domains by remember(container) {
        container.reportRepository.observeDomains()
    }.collectAsStateWithLifecycle(emptyList())
    val tabs = rememberSaveable(saver = ReportTabs.Saver) { ReportTabs() }
    val context =
        ShellContext(
            container,
            preferences,
            domains,
            tabs,
            syncState is SyncState.Running,
            rememberCoroutineScope(),
            backStack,
            authorizeGoogle,
        )
    ShellNavigation(context, syncState, modifier)
}

internal class ReportTabs {
    var trafficTab by mutableStateOf("Traffic")
    var provider by mutableStateOf("Google")
    var googleTab by mutableStateOf("Overview")
    var bingTab by mutableStateOf("Overview")
    var performanceTab by mutableStateOf("Overview")
    var healthTab by mutableStateOf("Uptime")

    companion object {
        val Saver =
            listSaver<ReportTabs, String>(
                save = {
                    listOf(
                        it.trafficTab,
                        it.provider,
                        it.googleTab,
                        it.bingTab,
                        it.performanceTab,
                        it.healthTab,
                    )
                },
                restore = { values ->
                    ReportTabs().apply {
                        trafficTab = values[0]
                        provider = values[1]
                        googleTab = values[2]
                        bingTab = values[3]
                        performanceTab = values[4]
                        healthTab = values[5]
                    }
                },
            )
    }
}

internal class ShellContext(
    val container: AppContainer,
    val preferences: AppPreferences,
    val domains: List<String>,
    val tabs: ReportTabs,
    val syncing: Boolean,
    val scope: CoroutineScope,
    val backStack: MutableList<NavKey>,
    val authorizeGoogle: () -> Unit,
) {
    val domain get() = preferences.selectedDomain
    val period get() = preferences.selectedPeriod
    val onDomain: (String) -> Unit = { scope.launch { container.preferences.selectDomain(it) } }
    val onPeriod: (Period) -> Unit = { scope.launch { container.preferences.selectPeriod(it) } }

    /** Jokainen päivityspainike hakee kaiken: kaikki lähteet ja sivustot, valinnoista riippumatta. */
    val refresh: () -> Unit = { scope.launch { container.syncOrchestrator.syncEverything() } }
    val insight: (InsightTarget) -> Unit = { target ->
        val route =
            when (target.page) {
                "sources" -> SourcesRoute()
                "cloudflare" -> {
                    tabs.trafficTab = target.tab
                    TrafficRoute
                }
                "performance" -> {
                    tabs.performanceTab = target.tab
                    PerformanceRoute
                }
                else -> {
                    tabs.provider = if (target.page == "bing") "Bing" else "Google"
                    if (tabs.provider == "Bing") tabs.bingTab = target.tab else tabs.googleTab = target.tab
                    SearchRoute
                }
            }
        backStack.add(route)
    }

    fun back() {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }

    fun selectRoot(destination: PrimaryDestination) {
        if (backStack.lastOrNull() == destination.route) return
        backStack.clear()
        backStack.add(destination.route)
        scope.launch {
            container.preferences.selectPage(
                if (destination.label ==
                    "Vitals"
                ) {
                    "performance"
                } else {
                    destination.label.lowercase()
                },
            )
        }
    }
}

@Composable
private fun ShellNavigation(
    context: ShellContext,
    syncState: SyncState,
    modifier: Modifier = Modifier,
) {
    val selected = context.backStack.lastOrNull()
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val itemColors = navigationItemColors()
    NavigationSuiteScaffold(modifier = modifier, navigationSuiteItems = {
        primaryDestinations.forEach { destination ->
            item(
                selected = isPrimarySelected(destination.route, selected),
                onClick = { context.selectRoot(destination) },
                icon = { Icon(painterResource(destination.icon), if (largeText) destination.label else null) },
                label =
                    if (largeText) {
                        null
                    } else {
                        { Text(destination.label) }
                    },
                colors = itemColors,
            )
        }
    }) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            ),
        ) {
            SyncNotice(syncState) { context.backStack.add(SourcesRoute()) }
            ShellReports(context, Modifier.weight(1f))
        }
    }
}

/** Valittu kohde: oranssi kuvake ja valkoinen nimi ilman taustapilleriä. */
@Composable
private fun navigationItemColors(): NavigationSuiteItemColors =
    NavigationSuiteDefaults.itemColors(
        navigationBarItemColors =
            NavigationBarItemDefaults.colors(
                selectedIconColor = NumerisColors.Accent,
                selectedTextColor = NumerisColors.PrimaryText,
                indicatorColor = Color.Transparent,
                unselectedIconColor = NumerisColors.TertiaryText,
                unselectedTextColor = NumerisColors.TertiaryText,
            ),
        navigationRailItemColors =
            NavigationRailItemDefaults.colors(
                selectedIconColor = NumerisColors.Accent,
                selectedTextColor = NumerisColors.PrimaryText,
                indicatorColor = Color.Transparent,
                unselectedIconColor = NumerisColors.TertiaryText,
                unselectedTextColor = NumerisColors.TertiaryText,
            ),
    )

private fun isPrimarySelected(
    primary: NavKey,
    selected: NavKey?,
): Boolean =
    primary == selected ||
        (primary == OverviewRoute && selected is PageRoute) ||
        (primary == MoreRoute && (selected == HealthRoute || selected is SourcesRoute))

/**
 * Synkronoinnin tila tiiviinä rivinä. Käynnissä oleva päivitys näkyy pyörivänä ilmaisimena; epäonnistuneet
 * lähteet näkyvät pillerinä, josta pääsee Sourcesiin. Onnistunut päivitys ei vie tilaa.
 */
@Composable
private fun SyncNotice(
    state: SyncState,
    onOpenSources: () -> Unit,
) {
    val (message, tone) =
        when (state) {
            is SyncState.Running ->
                "Refreshing ${state.source?.displayName ?: "all sources"}…" to null
            is SyncState.PartialSuccess ->
                (
                    if (state.failed.size <= 2) {
                        state.failed.joinToString(" and ") { it.displayName } + " failed"
                    } else {
                        "${state.failed.size} sources failed"
                    }
                ) to PillTone.Warning
            is SyncState.AuthRequired -> "Sign in again to ${state.source.displayName}" to PillTone.Warning
            is SyncState.Failed -> "Refresh failed" to PillTone.Negative
            else -> return
        }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (tone != null) Modifier.clickable(onClick = onOpenSources) else Modifier)
            .padding(horizontal = NumerisSpacing.Lg, vertical = NumerisSpacing.Sm),
        horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tone == null) {
            CircularProgressIndicator(Modifier.size(NumerisSizes.SmallIcon), strokeWidth = 2.dp)
            Text(message, color = NumerisColors.SecondaryText, style = MaterialTheme.typography.bodySmall)
        } else {
            StatusPill(message, tone)
            Text("Open Sources", color = NumerisColors.TertiaryText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ShellReports(
    context: ShellContext,
    modifier: Modifier = Modifier,
) {
    val holder = rememberSaveableStateHolder()
    val currentContext by rememberUpdatedState(context)
    NavDisplay(
        modifier = modifier,
        backStack = context.backStack,
        onBack = context::back,
        entryProvider = { route ->
            NavEntry(route) {
                holder.SaveableStateProvider(route.toString()) {
                    Box(Modifier.fillMaxSize()) {
                        ReportDestination(route, currentContext)
                    }
                }
            }
        },
    )
}
