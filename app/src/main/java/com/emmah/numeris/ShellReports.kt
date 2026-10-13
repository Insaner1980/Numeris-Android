@file:Suppress("ktlint:standard:function-naming", "FunctionNaming")

package com.emmah.numeris

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.emmah.numeris.core.model.contains
import com.emmah.numeris.feature.health.HealthReportScreen
import com.emmah.numeris.feature.overview.OverviewScreen
import com.emmah.numeris.feature.overview.PageDetailScreen
import com.emmah.numeris.feature.performance.PerformanceReportScreen
import com.emmah.numeris.feature.search.SearchReportScreen
import com.emmah.numeris.feature.sources.SourcesScreen
import com.emmah.numeris.feature.traffic.TrafficReportScreen
import kotlinx.coroutines.launch

@Composable
internal fun ReportDestination(
    route: NavKey,
    context: ShellContext,
) {
    when (route) {
        OverviewRoute -> OverviewDestination(context)
        TrafficRoute -> TrafficDestination(context)
        SearchRoute -> SearchDestination(context)
        PerformanceRoute -> PerformanceDestination(context)
        HealthRoute -> HealthDestination(context)
        is PageRoute -> PageDestination(route, context)
        is SourcesRoute -> SourcesDestination(route, context)
        MoreRoute -> MoreDestination(context)
    }
}

@Composable
internal fun OverviewDestination(context: ShellContext) {
    val reportFlow =
        remember(
            context.domain,
            context.period,
        ) {
            context.container.reportRepository.observePortfolio(
                context.domain,
                context.period,
            )
        }
    CachedReport(reportFlow) {
        OverviewScreen(
            it,
            context.domain,
            context.domains,
            context.period,
            context.syncing,
            context.onPeriod,
            context.onDomain,
            context.refresh,
            { context.backStack.add(SourcesRoute()) },
            { page -> context.backStack.add(PageRoute(page.domain, page.path, context.period)) },
            context.insight,
        )
    }
}

@Composable
internal fun TrafficDestination(context: ShellContext) {
    val reportFlow =
        remember(
            context.domain,
            context.period,
        ) { context.container.reportRepository.observeTraffic(context.domain, context.period) }
    val stateHolder = rememberSaveableStateHolder()
    CachedReport(reportFlow) {
        stateHolder.SaveableStateProvider("${context.domain}:${context.period}:${context.tabs.trafficTab}") {
            TrafficReportScreen(
                it,
                context.domain,
                context.domains,
                context.period,
                context.tabs.trafficTab,
                context.syncing,
                context.onDomain,
                context.onPeriod,
                {
                    context.tabs.trafficTab =
                        it
                },
                context.refresh,
            )
        }
    }
}

@Composable
internal fun SearchDestination(context: ShellContext) {
    val tab =
        if (context.tabs.provider ==
            "Google"
        ) {
            context.tabs.googleTab
        } else {
            context.tabs.bingTab
        }
    val reportFlow =
        remember(
            context.domain,
            context.period,
            context.tabs.provider,
        ) {
            context.container.reportRepository.observeSearch(
                context.domain,
                context.period,
                context.tabs.provider,
            )
        }
    val stateHolder = rememberSaveableStateHolder()
    CachedReport(reportFlow) {
        stateHolder.SaveableStateProvider("${context.domain}:${context.period}:${context.tabs.provider}:$tab") {
            SearchReportScreen(
                it,
                context.domain,
                context.domains,
                context.period,
                context.tabs.provider,
                tab,
                context.syncing,
                context.onDomain,
                context.onPeriod,
                {
                    context.tabs.provider =
                        it
                },
                {
                    if (context.tabs.provider ==
                        "Google"
                    ) {
                        context.tabs.googleTab = it
                    } else {
                        context.tabs.bingTab = it
                    }
                },
                context.refresh,
            )
        }
    }
}

@Composable
internal fun PerformanceDestination(context: ShellContext) {
    val reportFlow =
        remember(
            context.domain,
        ) { context.container.reportRepository.observePerformance(context.domain) }
    val stateHolder = rememberSaveableStateHolder()
    CachedReport(reportFlow) {
        stateHolder.SaveableStateProvider("${context.domain}:${context.period}:${context.tabs.performanceTab}") {
            PerformanceReportScreen(
                it,
                context.domain,
                context.domains,
                context.period,
                context.tabs.performanceTab,
                context.syncing,
                context.onDomain,
                context.onPeriod,
                {
                    context.tabs.performanceTab =
                        it
                },
                context.refresh,
                { context.backStack.add(SourcesRoute()) },
            )
        }
    }
}

@Composable
internal fun HealthDestination(context: ShellContext) {
    val reportFlow =
        remember(
            context.domain,
            context.period,
        ) { context.container.reportRepository.observeHealth(context.domain, context.period) }
    val stateHolder = rememberSaveableStateHolder()
    CachedReport(reportFlow) {
        stateHolder.SaveableStateProvider("${context.domain}:${context.period}:${context.tabs.healthTab}") {
            HealthReportScreen(
                it,
                context.domain,
                context.domains,
                context.period,
                context.tabs.healthTab,
                context.syncing,
                context.onDomain,
                context.onPeriod,
                {
                    context.tabs.healthTab =
                        it
                },
                context.refresh,
                context::back,
            )
        }
    }
}

@Composable
internal fun PageDestination(
    route: PageRoute,
    context: ShellContext,
) {
    val reportFlow =
        remember(
            route,
            context.period,
        ) {
            context.container.reportRepository.observePage(
                route.domain,
                route.path,
                context.period,
            )
        }
    CachedReport(
        reportFlow,
    ) {
        PageDetailScreen(
            it,
            context::back,
            { url -> context.backStack.add(SourcesRoute(url)) },
        )
    }
}

@Composable
internal fun SourcesDestination(
    route: SourcesRoute,
    context: ShellContext,
) {
    val authorizationFeedback by context.container.googleAuthorization.authorizationFeedback
        .collectAsStateWithLifecycle()
    SourcesScreen(
        context.container.connectionRepository,
        context.container.importRepository,
        context.requireBiometric,
        context.authorizeGoogle,
        reports = context.container.reportRepository,
        discovery = context.container.sourceDiscovery,
        onBack = context::back,
        initialUrl = route.url,
        googleAuthorizationFeedback = authorizationFeedback,
    )
}

@Composable
internal fun MoreDestination(context: ShellContext) {
    MoreScreen(
        context.preferences,
        {
            enabled,
            hours,
            ->
            context.scope.launch {
                context.container.preferences.setBackgroundSync(
                    enabled,
                    hours,
                )
            }
        },
        { enabled ->
            context.scope.launch { context.container.preferences.setBiometricLock(enabled) }
        },
        { context.backStack.add(HealthRoute) },
        { context.backStack.add(SourcesRoute()) },
    )
}
