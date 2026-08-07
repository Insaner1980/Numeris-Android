@file:Suppress("ktlint:standard:function-naming", "FunctionNaming", "TooManyFunctions")

package com.emmah.numeris

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emmah.numeris.core.database.PerformanceUrlEntity
import com.emmah.numeris.core.designsystem.NumerisChoiceRow
import com.emmah.numeris.core.designsystem.NumerisColors
import com.emmah.numeris.core.designsystem.NumerisDropdownSelector
import com.emmah.numeris.core.designsystem.NumerisEqualChoiceRow
import com.emmah.numeris.core.designsystem.NumerisScreenHeader
import com.emmah.numeris.core.designsystem.NumerisSpacing
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.model.SiteIdentity
import com.emmah.numeris.data.ReportRepository
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

private data class ReportRow(
    val key: String,
    val title: String,
    val value: String,
    val detail: String,
)

private const val TOP_ITEM_LIMIT = 100
private const val SCORE_SCALE = 100
private const val AUDIT_PASS_SCORE = 0.9

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun TrafficReportScreen(
    dao: ReportRepository,
    domain: String,
    domains: List<String>,
    period: Period,
    syncing: Boolean,
    onSelectDomain: (String) -> Unit,
    onSelectPeriod: (Period) -> Unit,
    onRefresh: () -> Unit,
) {
    val range = period.toDateRange()
    val start = range.start.toString()
    val end = range.end.toString()
    val cloudflare by remember(dao, domain, start, end) {
        dao.observeCloudflareTraffic(domain, start, end)
    }.collectAsStateWithLifecycle(emptyList())
    val countries by remember(dao, domain, start, end) {
        dao.observeCloudflareCountries(domain, start, end)
    }.collectAsStateWithLifecycle(emptyList())
    val pages by remember(dao, domain, start, end) {
        dao.observeCloudflarePages(domain, start, end)
    }.collectAsStateWithLifecycle(emptyList())
    val statuses by remember(dao, domain, start, end) {
        dao.observeCloudflareStatuses(domain, start, end)
    }.collectAsStateWithLifecycle(emptyList())
    val webAnalytics by remember(dao, domain, start, end) {
        dao.observeWebAnalytics(domain, start, end)
    }.collectAsStateWithLifecycle(emptyList())
    val analytics by remember(dao, domain, start, end) {
        dao.observeAnalyticsDaily(domain, start, end)
    }.collectAsStateWithLifecycle(emptyList())
    val analyticsPages by remember(dao, domain, start, end) {
        dao.observeAnalyticsPages(domain, start, end)
    }.collectAsStateWithLifecycle(emptyList())
    val analyticsSources by remember(dao, domain, start, end) {
        dao.observeAnalyticsSources(domain, start, end)
    }.collectAsStateWithLifecycle(emptyList())
    val analyticsEvents by remember(dao, domain, start, end) {
        dao.observeAnalyticsEvents(domain, start, end)
    }.collectAsStateWithLifecycle(emptyList())
    val analyticsDevices by remember(dao, domain, start, end) {
        dao.observeAnalyticsDevices(domain, start, end)
    }.collectAsStateWithLifecycle(emptyList())
    var source by remember { mutableStateOf("Cloudflare") }
    var tab by remember(source) { mutableStateOf(if (source == "Cloudflare") "Traffic" else "Overview") }
    val tabs =
        if (source == "Cloudflare") {
            listOf("Traffic", "Cache", "Security", "Status", "Web Analytics")
        } else {
            listOf("Overview", "Pages", "Acquisition", "Events", "Devices")
        }
    val rows =
        when (source to tab) {
            "Cloudflare" to "Traffic" ->
                cloudflare.map {
                    ReportRow(
                        it.date,
                        it.date,
                        count(it.uniqueVisitors),
                        "${count(it.pageviews)} pageviews · ${count(it.requests)} requests",
                    )
                }
            "Cloudflare" to "Cache" ->
                cloudflare.map {
                    val ratio = if (it.requests == 0L) 0 else (100.0 * it.cachedRequests / it.requests).roundToInt()
                    ReportRow(
                        it.date,
                        it.date,
                        "$ratio%",
                        "${count(it.cachedRequests)} cached requests · ${count(it.cachedBytes)} bytes",
                    )
                }
            "Cloudflare" to "Security" ->
                countries
                    .take(
                        100,
                    ).map { ReportRow("${it.date}:${it.country}", it.country, count(it.visitors), it.date) }
            "Cloudflare" to "Status" ->
                statuses.take(TOP_ITEM_LIMIT).map {
                    ReportRow("${it.date}:${it.statusCode}", "HTTP ${it.statusCode}", count(it.requests), it.date)
                }
            "Cloudflare" to "Web Analytics" ->
                webAnalytics.map {
                    ReportRow(
                        it.date,
                        it.date,
                        count(it.visits),
                        "${count(it.pageViews)} page views",
                    )
                }
            "Analytics" to "Overview" ->
                analytics.map {
                    ReportRow(
                        "${it.propertyId}:${it.date}",
                        it.date,
                        count(it.activeUsers),
                        "${count(it.sessions)} sessions · ${percent(it.engagementRate)} engagement",
                    )
                }
            "Analytics" to "Pages" ->
                analyticsPages.take(TOP_ITEM_LIMIT).map {
                    ReportRow(
                        it.pagePath,
                        it.pagePath,
                        count(it.pageViews),
                        "${count(it.activeUsers)} users · ${count(it.engagedSessions)} engaged",
                    )
                }
            "Analytics" to "Acquisition" ->
                analyticsSources.take(TOP_ITEM_LIMIT).map {
                    ReportRow(
                        it.sourceMedium,
                        it.sourceMedium,
                        count(it.sessions),
                        "${count(it.activeUsers)} users · ${count(it.keyEvents)} key events",
                    )
                }
            "Analytics" to "Events" ->
                analyticsEvents.take(TOP_ITEM_LIMIT).map {
                    ReportRow(it.eventName, it.eventName, count(it.eventCount), "${count(it.keyEvents)} key events")
                }
            else ->
                analyticsDevices.take(TOP_ITEM_LIMIT).map {
                    ReportRow(
                        "${it.date}:${it.deviceCategory}",
                        it.deviceCategory,
                        count(it.sessions),
                        "${count(it.activeUsers)} users · ${it.date}",
                    )
                }
        }
    val trend =
        if (source == "Cloudflare") {
            cloudflare.map { it.uniqueVisitors.toDouble() }
        } else {
            analytics.map { it.activeUsers.toDouble() }
        }
    OfflineReport(
        title = "Traffic",
        domain = domain,
        period = period,
        sources = listOf("Cloudflare", "Analytics"),
        selectedSource = source,
        onSelectSource = { source = it },
        tabs = tabs,
        selectedTab = tab,
        onSelectTab = { tab = it },
        rows = rows,
        trend = trend,
        syncing = syncing,
        onRefresh = onRefresh,
        domains = domains,
        onSelectDomain = onSelectDomain,
        onSelectPeriod = onSelectPeriod,
    )
}

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun SearchReportScreen(
    dao: ReportRepository,
    domain: String,
    domains: List<String>,
    period: Period,
    syncing: Boolean,
    onSelectDomain: (String) -> Unit,
    onSelectPeriod: (Period) -> Unit,
    onRefreshIndexing: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val range = period.toDateRange()
    val start = range.start.toString()
    val end = range.end.toString()
    val google by remember(dao, domain, start, end) {
        dao.observeSearchConsole(domain, start, end)
    }.collectAsStateWithLifecycle(emptyList())
    val devices by remember(dao, domain, start, end) {
        dao.observeSearchDevices(domain, start, end)
    }.collectAsStateWithLifecycle(emptyList())
    val bingTraffic by remember(dao, domain, start, end) {
        dao.observeBingTraffic(domain, start, end)
    }.collectAsStateWithLifecycle(emptyList())
    val bingQueries by remember(dao, domain, start, end) {
        dao.observeBingQueries(domain, start, end)
    }.collectAsStateWithLifecycle(emptyList())
    val bingPages by remember(dao, domain, start, end) {
        dao.observeBingPages(domain, start, end)
    }.collectAsStateWithLifecycle(emptyList())
    val bingRaw by remember(dao, domain) { dao.observeBingRaw(domain) }.collectAsStateWithLifecycle(emptyList())
    val sitemap by remember(dao, domain) { dao.observeSitemap(domain) }.collectAsStateWithLifecycle(emptyList())
    var source by remember { mutableStateOf("Google") }
    var tab by remember(source) { mutableStateOf("Overview") }
    val tabs =
        if (source ==
            "Google"
        ) {
            listOf("Overview", "Queries", "Pages", "Devices", "Indexing")
        } else {
            listOf("Overview", "Queries", "Pages", "Crawl")
        }
    val rows =
        when (source to tab) {
            "Google" to "Overview" -> google.filter { it.kind == "daily" }.map { it.toReportRow() }
            "Google" to "Queries" -> google.filter { it.kind == "query" }.take(TOP_ITEM_LIMIT).map { it.toReportRow() }
            "Google" to "Pages" -> google.filter { it.kind == "page" }.take(TOP_ITEM_LIMIT).map { it.toReportRow() }
            "Google" to "Devices" ->
                devices.take(TOP_ITEM_LIMIT).map {
                    ReportRow(
                        "${it.date}:${it.device}",
                        it.device,
                        count(it.clicks),
                        "${count(it.impressions)} impressions · ${percent(it.ctr)} CTR",
                    )
                }
            "Google" to "Indexing" ->
                sitemap.map {
                    ReportRow(
                        it.url,
                        it.url,
                        it.verdict ?: "Not inspected",
                        it.coverageState ?: "Last seen ${it.lastSeenAt}",
                    )
                }
            "Bing" to "Overview" ->
                bingTraffic.map {
                    ReportRow(
                        it.date,
                        it.date,
                        count(it.clicks ?: 0),
                        "${count(it.impressions ?: 0)} impressions",
                    )
                }
            "Bing" to "Queries" ->
                bingQueries.take(TOP_ITEM_LIMIT).map {
                    ReportRow(
                        "${it.date}:${it.query}",
                        it.query,
                        count(it.clicks ?: 0),
                        "${count(
                            it.impressions ?: 0,
                        )} impressions · ${it.date}",
                    )
                }
            "Bing" to "Pages" ->
                bingPages.take(TOP_ITEM_LIMIT).map {
                    ReportRow(
                        "${it.date}:${it.pageUrl}",
                        it.pageUrl,
                        count(it.clicks ?: 0),
                        "${count(
                            it.impressions ?: 0,
                        )} impressions · ${it.date}",
                    )
                }
            else ->
                bingRaw.take(TOP_ITEM_LIMIT).map {
                    ReportRow("${it.method}:${it.itemKey}", it.method, it.itemKey, "Fetched ${it.fetchedAt}")
                }
        }
    val trend =
        if (source ==
            "Google"
        ) {
            google.filter { it.kind == "daily" }.map { it.clicks.toDouble() }
        } else {
            bingTraffic.map { (it.clicks ?: 0).toDouble() }
        }
    OfflineReport(
        "Search",
        domain,
        period,
        listOf("Google", "Bing"),
        source,
        { source = it },
        tabs,
        tab,
        { tab = it },
        rows,
        trend,
        syncing,
        if (source == "Google" && tab == "Indexing" && domain != "all") {
            { onRefreshIndexing(domain) }
        } else {
            onRefresh
        },
        domains = domains,
        onSelectDomain = onSelectDomain,
        onSelectPeriod = onSelectPeriod,
    )
}

@Composable
@Suppress("LongMethod")
fun PerformanceReportScreen(
    dao: ReportRepository,
    domain: String,
    domains: List<String>,
    period: Period,
    syncing: Boolean,
    onSelectDomain: (String) -> Unit,
    onSelectPeriod: (Period) -> Unit,
    onRefresh: () -> Unit,
) {
    val urls by remember(dao, domain) { dao.observePerformanceUrls(domain) }.collectAsStateWithLifecycle(emptyList())
    val crux by remember(dao, domain) { dao.observeCrux(domain) }.collectAsStateWithLifecycle(emptyList())
    val runs by remember(dao, domain) { dao.observePageSpeedRuns(domain) }.collectAsStateWithLifecycle(emptyList())
    val audits by remember(dao, domain) { dao.observePageSpeedAudits(domain) }.collectAsStateWithLifecycle(emptyList())
    var tab by remember { mutableStateOf("Overview") }
    var newUrl by remember { mutableStateOf("") }
    var urlError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val rows =
        when (tab) {
            "Overview" ->
                runs
                    .distinctBy {
                        it.url to it.strategy
                    }.map {
                        ReportRow(
                            "${it.url}:${it.strategy}",
                            "${it.strategy.replaceFirstChar(Char::uppercase)} · ${it.url}",
                            score(it.performanceScore),
                            "Accessibility ${score(it.accessibilityScore)} · SEO ${score(it.seoScore)}",
                        )
                    }
            "CrUX" ->
                crux.take(TOP_ITEM_LIMIT).map {
                    ReportRow(
                        "${it.target}:${it.formFactor}:${it.metric}",
                        "${it.metric} · ${it.formFactor}",
                        it.p75?.toString() ?: "—",
                        "${it.targetType}: ${it.target} · ${it.collectionEnd}",
                    )
                }
            "PageSpeed" ->
                audits.filter { (it.score ?: 1.0) < AUDIT_PASS_SCORE }.take(TOP_ITEM_LIMIT).map {
                    ReportRow(
                        "${it.url}:${it.strategy}:${it.auditId}",
                        it.title ?: it.auditId,
                        score(it.score),
                        "${it.strategy} · ${it.displayValue.orEmpty()}",
                    )
                }
            else ->
                urls.map {
                    ReportRow(
                        it.url,
                        it.url,
                        if (it.enabled) "Enabled" else "Disabled",
                        "${it.source} · ${it.origin}",
                    )
                }
        }
    OfflineReport(
        "Performance",
        domain,
        period,
        emptyList(),
        "",
        {},
        listOf("Overview", "CrUX", "PageSpeed", "URLs"),
        tab,
        { tab = it },
        rows,
        runs.sortedBy { it.analysisUtc }.map {
            (
                it.performanceScore
                    ?: 0.0
            ) *
                SCORE_SCALE
        },
        syncing,
        onRefresh,
        management =
            if (tab == "URLs") {
                {
                    UrlManagement(
                        urls = urls,
                        value = newUrl,
                        error = urlError,
                        onValueChange = {
                            newUrl = it
                            urlError = null
                        },
                        onAdd = {
                            val site = runCatching { SiteIdentity.fromDomainOrUrl(newUrl) }.getOrNull()
                            if (site == null) {
                                urlError = "Enter a valid HTTPS URL or domain."
                            } else {
                                scope.launch {
                                    dao.upsertUrls(
                                        listOf(
                                            PerformanceUrlEntity(
                                                url = site.homePageUrl,
                                                origin = site.originUrl,
                                                source = "manual",
                                                createdAt = Instant.now().toString(),
                                            ),
                                        ),
                                    )
                                }
                                newUrl = ""
                            }
                        },
                        onToggle = { id, enabled -> scope.launch { dao.setUrlEnabled(id, enabled) } },
                        onDelete = { id -> scope.launch { dao.deleteUrl(id) } },
                    )
                }
            } else {
                null
            },
        domains = domains,
        onSelectDomain = onSelectDomain,
        onSelectPeriod = onSelectPeriod,
    )
}

@Composable
fun HealthReportScreen(
    dao: ReportRepository,
    domain: String,
    domains: List<String>,
    period: Period,
    syncing: Boolean,
    onSelectDomain: (String) -> Unit,
    onSelectPeriod: (Period) -> Unit,
    onRefresh: () -> Unit,
) {
    val uptime by remember(dao, domain) { dao.observeAllUptime(domain) }.collectAsStateWithLifecycle(emptyList())
    val sitemap by remember(dao, domain) { dao.observeSitemap(domain) }.collectAsStateWithLifecycle(emptyList())
    var tab by remember { mutableStateOf("Uptime") }
    val rows =
        if (tab == "Uptime") {
            uptime.map {
                ReportRow(
                    "${it.domain}:${it.checkedAt}",
                    it.domain,
                    it.status,
                    "${it.responseMs?.let { value ->
                        "$value ms"
                    } ?: "No response"} · ${it.checkedAt}",
                )
            }
        } else {
            sitemap.map { ReportRow(it.url, it.url, it.verdict ?: "Discovered", "Last seen ${it.lastSeenAt}") }
        }
    OfflineReport(
        "Health",
        domain,
        period,
        emptyList(),
        "",
        {},
        listOf("Uptime", "Sitemap"),
        tab,
        { tab = it },
        rows,
        uptime.sortedBy { it.checkedAt }.map {
            (
                it.responseMs
                    ?: 0
            ).toDouble()
        },
        syncing,
        onRefresh,
        domains = domains,
        onSelectDomain = onSelectDomain,
        onSelectPeriod = onSelectPeriod,
    )
}

@Composable
private fun OfflineReport(
    title: String,
    domain: String,
    period: Period,
    sources: List<String>,
    selectedSource: String,
    onSelectSource: (String) -> Unit,
    tabs: List<String>,
    selectedTab: String,
    onSelectTab: (String) -> Unit,
    rows: List<ReportRow>,
    trend: List<Double>,
    syncing: Boolean,
    onRefresh: () -> Unit,
    management: (@Composable () -> Unit)? = null,
    domains: List<String> = emptyList(),
    onSelectDomain: (String) -> Unit = {},
    onSelectPeriod: (Period) -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(NumerisSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Md),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Lg)) {
                NumerisScreenHeader(
                    title = title,
                    subtitle = period.displayLabel,
                    syncing = syncing,
                    onRefresh = onRefresh,
                )
                NumerisDropdownSelector(
                    selected = domain,
                    options = listOf("all") + domains,
                    onSelect = onSelectDomain,
                    label = { value -> if (value == "all") "All domains" else value },
                )
                NumerisEqualChoiceRow(
                    options = Period.entries.map(Period::shortLabel),
                    selected = period.shortLabel,
                    onSelect = { label -> Period.entries.firstOrNull { it.shortLabel == label }?.let(onSelectPeriod) },
                )
                if (sources.isNotEmpty()) {
                    ReportChoiceGroup("Data source") {
                        NumerisChoiceRow(sources, selectedSource, onSelectSource)
                    }
                }
                ReportChoiceGroup("View") {
                    NumerisChoiceRow(tabs, selectedTab, onSelectTab)
                }
            }
        }
        if (trend.isNotEmpty()) {
            item { TrendCard(title = "$selectedTab trend", values = trend, period = period.displayLabel) }
        }
        management?.let { content -> item { content() } }
        if (rows.isEmpty() && management == null) {
            item {
                EmptyCard(
                    "No cached $selectedTab data",
                    "Connect the source, then refresh when a network connection is available.",
                    syncing,
                    onRefresh,
                )
            }
        } else {
            items(rows, key = ReportRow::key) { row -> ReportListRow(row) }
        }
    }
}

@Composable
private fun UrlManagement(
    urls: List<PerformanceUrlEntity>,
    value: String,
    error: String?,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = NumerisColors.Card)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(NumerisSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm),
        ) {
            Text("Measured URLs", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = value,
                onValueChange = onValueChange,
                label = { Text("HTTPS URL or domain") },
                isError = error != null,
                supportingText = if (error != null) ({ Text(error) }) else null,
                singleLine = true,
            )
            Button(onClick = onAdd, enabled = value.isNotBlank()) { Text("Add URL") }
            urls.forEach { url ->
                HorizontalDivider(color = NumerisColors.Control)
                Text(url.url, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(NumerisSpacing.Sm)) {
                    TextButton(onClick = { onToggle(url.id, !url.enabled) }) {
                        Text(if (url.enabled) "Disable" else "Enable")
                    }
                    TextButton(onClick = { onDelete(url.id) }) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun ReportChoiceGroup(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Xs)) {
        Text(label, color = NumerisColors.TertiaryText, style = MaterialTheme.typography.labelMedium)
        content()
    }
}

@Composable
private fun TrendCard(
    title: String,
    values: List<Double>,
    period: String,
) {
    val summary =
        if (values.isEmpty()) {
            "$title, $period, no cached values"
        } else {
            val direction = if (values.last() >= values.first()) "up" else "down"
            "$title, $period, ${values.size} points, minimum ${values.min()}, maximum ${values.max()}, trend $direction"
        }
    Card(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = summary },
        colors = CardDefaults.cardColors(containerColor = NumerisColors.ChartPanel),
    ) {
        Column(modifier = Modifier.padding(NumerisSpacing.Lg)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(summary.substringAfter("$title, "), color = NumerisColors.SecondaryText)
            if (values.size > 1) {
                NumerisLineChart(values)
            } else {
                Text("Chart appears when at least two cached points are available.", color = NumerisColors.TertiaryText)
            }
        }
    }
}

@Composable
private fun NumerisLineChart(values: List<Double>) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values) {
        modelProducer.runTransaction { lineModel { series(values) } }
    }
    CartesianChartHost(
        chart =
            rememberCartesianChart(
                rememberLineCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(guideline = null),
            ),
        modelProducer = modelProducer,
        modifier = Modifier.fillMaxWidth().height(144.dp).padding(top = NumerisSpacing.Md),
    )
}

@Composable
private fun ReportListRow(row: ReportRow) {
    Card(colors = CardDefaults.cardColors(containerColor = NumerisColors.Card)) {
        Column(modifier = Modifier.fillMaxWidth().padding(NumerisSpacing.Lg)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    row.title,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(row.value, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = NumerisSpacing.Sm), color = NumerisColors.Control)
            Text(row.detail, color = NumerisColors.SecondaryText)
        }
    }
}

@Composable
private fun EmptyCard(
    title: String,
    detail: String,
    syncing: Boolean,
    onRefresh: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = NumerisColors.Card)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(NumerisSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(NumerisSpacing.Md),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(detail, color = NumerisColors.SecondaryText, style = MaterialTheme.typography.bodyLarge)
            Button(enabled = !syncing, onClick = onRefresh) {
                Text(if (syncing) "Refreshing…" else "Refresh data")
            }
        }
    }
}

private fun com.emmah.numeris.core.database.SearchConsoleEntity.toReportRow(): ReportRow =
    ReportRow(
        key = "$date:$kind:$query:${page.orEmpty()}",
        title = page ?: query.ifBlank { date },
        value = count(clicks),
        detail =
            "${count(impressions)} impressions · ${percent(ctr)} CTR · " +
                "position ${"%.1f".format(Locale.US, position)}",
    )

private fun count(value: Long): String = NumberFormat.getIntegerInstance(Locale.US).format(value)

private fun percent(value: Double): String = "%.1f%%".format(Locale.US, value * SCORE_SCALE)

private fun score(value: Double?): String = value?.let { "${(it * SCORE_SCALE).roundToInt()}" } ?: "—"
