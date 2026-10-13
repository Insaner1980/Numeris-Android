package com.emmah.numeris

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.numeris.core.database.BingPageStatEntity
import com.emmah.numeris.core.database.BingQueryStatEntity
import com.emmah.numeris.core.database.BingRankTrafficEntity
import com.emmah.numeris.core.database.BingRawItemEntity
import com.emmah.numeris.core.database.CloudflareCountryEntity
import com.emmah.numeris.core.database.CloudflarePageEntity
import com.emmah.numeris.core.database.CloudflareStatusEntity
import com.emmah.numeris.core.database.CloudflareTrafficEntity
import com.emmah.numeris.core.database.CruxMetricPointEntity
import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.database.PageSpeedAuditEntity
import com.emmah.numeris.core.database.PageSpeedRunEntity
import com.emmah.numeris.core.database.PerformanceUrlEntity
import com.emmah.numeris.core.database.SearchConsoleEntity
import com.emmah.numeris.core.database.SearchDeviceEntity
import com.emmah.numeris.core.database.SearchPageQueryEntity
import com.emmah.numeris.core.database.SitemapUrlEntity
import com.emmah.numeris.core.database.UptimeCheckEntity
import com.emmah.numeris.core.database.WebAnalyticsCountryEntity
import com.emmah.numeris.core.database.WebAnalyticsDailyEntity
import com.emmah.numeris.core.database.WebAnalyticsPageEntity
import com.emmah.numeris.core.database.WebAnalyticsReferrerEntity
import com.emmah.numeris.core.database.writeTransaction
import com.emmah.numeris.core.designsystem.NumerisTheme
import com.emmah.numeris.core.model.HealthReport
import com.emmah.numeris.core.model.PageDetailReport
import com.emmah.numeris.core.model.PerformanceReport
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.model.PortfolioReport
import com.emmah.numeris.core.model.SearchReport
import com.emmah.numeris.core.model.SecretKey
import com.emmah.numeris.core.model.SecretStore
import com.emmah.numeris.core.model.TrafficReport
import com.emmah.numeris.core.model.contains
import com.emmah.numeris.core.network.BingApiClient
import com.emmah.numeris.core.network.NumerisHttpClient
import com.emmah.numeris.core.network.SourceDiscoveryClient
import com.emmah.numeris.data.AndroidImportRepository
import com.emmah.numeris.data.DefaultSyncOrchestrator
import com.emmah.numeris.data.GoogleAuthorizationFeedback
import com.emmah.numeris.data.GoogleAuthorizationManager
import com.emmah.numeris.data.ReportRepository
import com.emmah.numeris.data.RoomConnectionRepository
import com.emmah.numeris.data.SourceDiscovery
import com.emmah.numeris.data.WindowsDatabaseImporter
import com.emmah.numeris.feature.health.HealthReportScreen
import com.emmah.numeris.feature.overview.OverviewScreen
import com.emmah.numeris.feature.overview.PageDetailScreen
import com.emmah.numeris.feature.performance.PerformanceReportScreen
import com.emmah.numeris.feature.search.SearchReportScreen
import com.emmah.numeris.feature.sources.SourcesScreen
import com.emmah.numeris.feature.traffic.TrafficReportScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

class MobileReportsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: NumerisDatabase
    private lateinit var reports: ReportRepository
    private lateinit var portfolio: PortfolioReport
    private lateinit var page: PageDetailReport
    private val screen = mutableStateOf("Overview")
    private val tab = mutableStateOf("Overview")
    private val provider = mutableStateOf("Google")
    private val authorizationFeedback = mutableStateOf<GoogleAuthorizationFeedback?>(null)
    private var calibrationClipped = false
    private val domain = "a-very-long-but-valid-example-domain.com"
    private val secondaryDomain = "secondary.example"

    @Before fun start() =
        runBlocking {
            database =
                Room
                    .inMemoryDatabaseBuilder(
                        context,
                        NumerisDatabase::class.java,
                    ).setDriver(AndroidSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            val now = Instant.now().toString()
            val days = (0L..364L).map { LocalDate.now().minusDays(it).toString() }
            database.writeTransaction {
                seedProviderDetails(days.first(), now)
                cloudflareDao().upsertTraffic(
                    days.map {
                        CloudflareTrafficEntity(
                            domain,
                            it,
                            20000,
                            10000,
                            100000,
                            70000,
                            100000000,
                            200000000,
                            15,
                            fetchedAt = now,
                        )
                    } + days.map { CloudflareTrafficEntity(secondaryDomain, it, uniqueVisitors = 5, fetchedAt = now) },
                )
                webAnalyticsDao().upsertWebAnalytics(
                    days.map { WebAnalyticsDailyEntity(domain, it, 10000, 20000, now) },
                )
                webAnalyticsDao().upsertWebAnalyticsPages(
                    listOf(WebAnalyticsPageEntity(domain, days.first(), "/long-page-path/", 20000)),
                )
                searchDao().upsertSearchConsole(
                    days.map {
                        SearchConsoleEntity(
                            siteUrl = domain,
                            date = it,
                            kind = "daily",
                            query = "",
                            clicks = 100,
                            impressions = 1000,
                            position = 2.3,
                            fetchedAt = now,
                        )
                    } +
                        (0..999).map {
                            SearchConsoleEntity(
                                siteUrl = domain,
                                date = days.first(),
                                kind = "query_daily",
                                query = "query ${it.toString().padStart(4, '0')}",
                                clicks = 10,
                                impressions = 100,
                                position = 3.0,
                                fetchedAt = now,
                            )
                        } +
                        (0..999).map {
                            SearchConsoleEntity(
                                siteUrl = secondaryDomain,
                                date = days.first(),
                                kind = "page_daily",
                                query = "",
                                page = "https://$secondaryDomain/page-${it.toString().padStart(4, '0')}/",
                                clicks = 1,
                                impressions = 10,
                                position = 3.0,
                                fetchedAt = now,
                            )
                        } +
                        listOf(
                            SearchConsoleEntity(
                                siteUrl = domain,
                                date = days.first(),
                                kind = "page_daily",
                                query = "",
                                page = "https://$domain/long-page-path/",
                                clicks = 100,
                                impressions = 1000,
                                position = 2.3,
                                fetchedAt = now,
                            ),
                        ),
                )
                searchDao().upsertSearchDevices(
                    days.map { SearchDeviceEntity(domain, it, "mobile", 80, 800, 0.1, 2.5) },
                )
                searchDao().upsertSitemapUrls(
                    listOf(
                        SitemapUrlEntity(
                            domain,
                            "https://$domain/long-page-path/",
                            now,
                            now,
                            lastInspectedAt = now,
                            verdict = "PASS",
                            coverageState = "Submitted and indexed",
                            indexingState = "INDEXING_ALLOWED",
                        ),
                    ),
                )
                healthDao().upsertChecks(
                    listOf(
                        UptimeCheckEntity(domain, now, "up", 200, 245),
                        UptimeCheckEntity(
                            domain,
                            Instant.now().minusSeconds(7200).toString(),
                            "timeout",
                            errorMessage = "Fixture timeout",
                        ),
                    ),
                )
                performanceDao().upsertUrls(
                    listOf(
                        PerformanceUrlEntity(
                            url = "https://$domain/long-page-path/",
                            origin = "https://$domain",
                            createdAt = now,
                        ),
                    ),
                )
                performanceDao().upsertPageSpeedRuns(
                    listOf(
                        PageSpeedRunEntity(
                            "https://$domain/long-page-path/",
                            "mobile",
                            now,
                            performanceScore = 0.64,
                            accessibilityScore = 0.92,
                            bestPracticesScore = 0.95,
                            seoScore = 0.99,
                            rawJson = "{}",
                            fetchedAt = now,
                        ),
                    ),
                )
                performanceDao().upsertCruxMetrics(
                    (
                        listOf(
                            "largest_contentful_paint" to 2450.0,
                            "interaction_to_next_paint" to 190.0,
                            "cumulative_layout_shift" to 0.08,
                        )
                    ).map { (metric, value) ->
                        CruxMetricPointEntity(
                            "origin",
                            "https://$domain",
                            "PHONE",
                            days[27],
                            days.first(),
                            metric,
                            value,
                            0.8,
                            0.15,
                            0.05,
                            "{}",
                            now,
                        )
                    },
                )
            }
            reports = ReportRepository(database)
            portfolio = reports.observePortfolio("all", Period.All).first()
            page = reports.observePage(domain, "/long-page-path/", Period.All).first()
            assertEquals(8, page.queries.size)
            val secrets =
                object : SecretStore {
                    override suspend fun put(
                        key: SecretKey,
                        value: CharArray,
                    ) = Unit

                    override suspend fun get(key: SecretKey): CharArray? = null

                    override suspend fun contains(key: SecretKey) = false

                    override suspend fun delete(key: SecretKey) = Unit

                    override suspend fun clearAll() = Unit
                }
            val connections = RoomConnectionRepository(database, secrets) { emptyMap() }
            val imports =
                AndroidImportRepository(
                    context,
                    WindowsDatabaseImporter(database),
                    DefaultSyncOrchestrator(emptyList()),
                )
            val http = NumerisHttpClient()
            val discovery =
                SourceDiscovery(
                    secrets,
                    GoogleAuthorizationManager(context),
                    SourceDiscoveryClient(http),
                    BingApiClient(http),
                )
            compose.setContent {
                NumerisTheme {
                    val measured =
                        rememberTextMeasurer().measure(
                            AnnotatedString("Deliberately clipped fixture text"),
                            softWrap = false,
                            constraints = Constraints(maxWidth = 20),
                        )
                    calibrationClipped = measured.getLineRight(0) > measured.size.width + 1
                    Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                        when (screen.value) {
                            "Overview" ->
                                OverviewScreen(
                                    portfolio,
                                    "all",
                                    listOf(domain, secondaryDomain),
                                    Period.All,
                                    false,
                                    {},
                                    {},
                                    {},
                                    {},
                                    {},
                                    {},
                                )
                            "Traffic" ->
                                TrafficReportScreen(
                                    portfolio.traffic,
                                    "all",
                                    listOf(
                                        domain,
                                    ),
                                    Period.All,
                                    tab.value,
                                    false,
                                    {},
                                    {},
                                    {
                                        tab.value =
                                            it
                                    },
                                    {},
                                )
                            "Search" ->
                                SearchReportScreen(
                                    if (provider.value ==
                                        "Google"
                                    ) {
                                        portfolio.google
                                    } else {
                                        portfolio.bing
                                    },
                                    "all",
                                    listOf(domain, secondaryDomain),
                                    Period.All,
                                    provider.value,
                                    tab.value,
                                    false,
                                    {},
                                    {},
                                    {
                                        provider.value =
                                            it
                                    },
                                    { tab.value = it },
                                    {},
                                )
                            "Performance" ->
                                PerformanceReportScreen(
                                    portfolio.performance,
                                    "all",
                                    listOf(
                                        domain,
                                    ),
                                    Period.All,
                                    tab.value,
                                    false,
                                    {},
                                    {},
                                    {
                                        tab.value =
                                            it
                                    },
                                    {},
                                    {},
                                )
                            "Health" ->
                                HealthReportScreen(
                                    portfolio.health,
                                    "all",
                                    listOf(
                                        domain,
                                    ),
                                    Period.All,
                                    tab.value,
                                    false,
                                    {},
                                    {},
                                    {
                                        tab.value =
                                            it
                                    },
                                    {},
                                    {},
                                )
                            "Page" -> PageDetailScreen(page, {}, {})
                            "Sources" ->
                                SourcesScreen(
                                    connections,
                                    imports,
                                    {},
                                    reports = reports,
                                    discovery = discovery,
                                    onBack = {},
                                    googleAuthorizationFeedback = authorizationFeedback.value,
                                )
                            "Empty" ->
                                OverviewScreen(
                                    portfolio.copy(
                                        hasCache = false,
                                        sites = emptyList(),
                                        pages = emptyList(),
                                        traffic = TrafficReport(portfolio.range),
                                        google = SearchReport(portfolio.range),
                                        bing = SearchReport(portfolio.range),
                                        health = HealthReport(portfolio.range),
                                        performance = PerformanceReport(),
                                        insights = emptyList(),
                                    ),
                                    "all",
                                    emptyList(),
                                    Period.All,
                                    false,
                                    {
                                    },
                                    {},
                                    {},
                                    {},
                                    {},
                                    {},
                                )
                            else -> Text("Fixture")
                        }
                    }
                }
            }
        }

    @After fun close() {
        compose.activityRule.scenario.close()
        database.close()
    }

    private suspend fun seedProviderDetails(
        date: String,
        now: String,
    ) {
        database.cloudflareDao().upsertCountries(listOf(CloudflareCountryEntity(domain, date, "FI", 10000)))
        database.cloudflareDao().upsertPages(listOf(CloudflarePageEntity(domain, date, "/long-page-path/", 20000)))
        database.cloudflareDao().upsertStatuses(
            listOf(CloudflareStatusEntity(domain, date, 200, 900), CloudflareStatusEntity(domain, date, 503, 12)),
        )
        database.webAnalyticsDao().upsertWebAnalyticsCountries(
            listOf(WebAnalyticsCountryEntity(domain, date, "FI", 800)),
        )
        database.webAnalyticsDao().upsertWebAnalyticsReferrers(
            listOf(WebAnalyticsReferrerEntity(domain, date, "https://search.example/very-long-referrer-path/", 800)),
        )
        database.searchDao().upsertSearchPageQueries(
            (0..9).map {
                SearchPageQueryEntity(
                    domain,
                    LocalDate.parse(date).minusDays(27).toString(),
                    date,
                    "https://$domain/long-page-path/",
                    "stored page query $it",
                    10,
                    100,
                    0.1,
                    2.5,
                )
            },
        )
        database.bingDao().upsertTraffic(
            listOf(BingRankTrafficEntity(domain, date, 12, 120, "{}", now)),
        )
        database.bingDao().upsertQueries(
            listOf(domain, secondaryDomain).map {
                BingQueryStatEntity(it, "shared long Bing query for both configured sites", date, 12, 120, 2.0, 3.0, "{}", now)
            },
        )
        database.bingDao().upsertPages(
            listOf(domain, secondaryDomain).map {
                BingPageStatEntity(it, "https://$it/long-page-path/", date, 12, 120, "{}", now)
            },
        )
        database.bingDao().upsertRawItems(
            listOf(
                BingRawItemEntity("GetCrawlStats", domain, "stats", "{\"CrawledPages\":10,\"Errors\":2}", now),
                BingRawItemEntity("GetCrawlIssues", domain, "issue", "{\"HttpCode\":404,\"Url\":\"https://$domain/missing/\"}", now),
            ),
        )
        database.performanceDao().upsertPageSpeedAudits(
            listOf(
                PageSpeedAuditEntity(
                    "https://$domain/long-page-path/",
                    "mobile",
                    now,
                    "render-blocking-resources",
                    title = "Eliminate render-blocking resources",
                    score = 0.0,
                    numericValue = 1200.0,
                    numericUnit = "millisecond",
                    displayValue = "Potential savings of 1,200 ms",
                ),
            ),
        )
    }

    @Test fun clippingDetectorRejectsAnActuallyClippedLayout() {
        compose.waitForIdle()
        assertTrue("The clipping detector must reject a narrow non-wrapping layout", calibrationClipped)
    }

    @Test fun everyReportTabAndDetailHasReachableContentAtCurrentDeviceConfiguration() {
        val cases =
            listOf(
                "Overview" to listOf("Overview"),
                "Traffic" to listOf("Traffic", "Web Analytics"),
                "Search" to listOf("Overview", "Queries", "Pages", "Devices", "Indexing"),
                "Performance" to listOf("Overview", "CrUX", "PageSpeed", "URLs"),
                "Health" to listOf("Uptime", "Sitemap"),
                "Page" to listOf("Page"),
            )
        for ((family, tabs) in cases) {
            for (view in tabs) {
                show(family, view)
                capture("$family-$view")
                assertVisibleTextFits()
                compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp() }
                compose.waitForIdle()
                assertVisibleTextFits()
                capture("$family-$view-scrolled")
            }
        }
        for (view in listOf("Overview", "Queries", "Pages", "Crawl")) {
            show("Search", view, "Bing")
            capture("Bing-$view")
            assertVisibleTextFits()
            compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp() }
            compose.waitForIdle()
            assertVisibleTextFits()
            capture("Bing-$view-scrolled")
        }
        show("Empty", "Overview")
        compose.onNodeWithText("Build your first dashboard").assertExists()
    }

    @Test fun fullQueryListAndSourceFormsRemainReachable() {
        show("Search", "Queries")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("query 0999", substring = true))
        compose.onNodeWithText("query 0999", substring = true).assertIsDisplayed()
        assertVisibleTextFits()
        show("Search", "Pages")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(
            hasText("https://$secondaryDomain/page-0999/", substring = true),
        )
        compose.onNodeWithText("https://$secondaryDomain/page-0999/", substring = true).assertIsDisplayed()
        assertVisibleTextFits()
        show("Sources", "Sources")
        for (label in listOf(
            "Cloudflare Zone Analytics",
            "Cloudflare Web Analytics",
            "Google Search Console",
            "Chrome UX Report",
            "PageSpeed Insights",
            "Bing Webmaster",
            "Uptime",
            "Sitemap",
        )) {
            openSourceForm(label)
            if (label in listOf("Chrome UX Report", "PageSpeed Insights")) {
                compose.onNodeWithText("Save").assertIsNotEnabled()
            }
            assertVisibleTextFits()
            capture("Form-$label")
            compose.onAllNodes(hasContentDescription("Back")).onLast().performClick()
        }
    }

    @Test fun googleAuthorizationResultRemainsVisible() {
        show("Sources", "Sources")
        for (feedback in listOf(
            GoogleAuthorizationFeedback(false, "Google authorization is unavailable (error 10)."),
            GoogleAuthorizationFeedback(true, "Google authorized for Search Console."),
        )) {
            compose.runOnIdle { authorizationFeedback.value = feedback }
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(feedback.message))
            compose.onNodeWithText(feedback.message).assertIsDisplayed()
            val label = if (feedback.authorized) "Authorize Google again" else "Authorize Google"
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(label))
            compose.onNodeWithText(label).assertIsDisplayed()
            assertVisibleTextFits()
        }
    }

    @Test fun selectedChartPointHasAccessibleDatedValues() {
        show("Search", "Overview")
        val chart = hasContentDescription("Select a chart point")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(chart)
        drawFrame()
        compose.onNode(chart).performTouchInput { click(center) }
        drawFrame()
        compose.waitForIdle()
        val selectedValue = hasText("This period, ", substring = true)
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(selectedValue)
        compose.onNodeWithText("Selected day").assertIsDisplayed()
        compose.onNode(selectedValue).assertIsDisplayed()
        assertVisibleTextFits()
        capture("Selected-samples")
    }

    @Test fun selectorsAndMeasuredUrlConfirmationRemainUsable() {
        show("Overview", "Overview")
        compose.onNodeWithText("All sites").performClick()
        compose.onAllNodes(hasText(domain)).onLast().assertIsDisplayed()
        assertVisibleTextFits()
        capture("Site-selector")
        compose.onNodeWithText("Find a site").performTextInput("secondary")
        compose.onAllNodes(hasText(secondaryDomain)).onLast().assertIsDisplayed()
        // Overview-näkymän sivustolista jää alapaneelin taakse; paneelista suodattui pois toinen sivusto.
        assertEquals(1, compose.onAllNodes(hasText(domain) and hasClickAction()).fetchSemanticsNodes().size)
        compose.onAllNodes(hasText("All sites") and hasClickAction()).onLast().assertIsDisplayed()
        compose.onNodeWithText("Find a site").performTextInput("-no-match")
        compose.onNodeWithText("No matching sites").assertIsDisplayed()
        compose.onAllNodes(hasText("All sites") and hasClickAction()).onLast().performClick()
        compose.onNodeWithText("All sites").performClick()
        compose.onAllNodes(hasText(domain)).onLast().performClick()
        compose.onNode(hasText("All time") and hasClickAction()).performClick()
        compose.onNodeWithText("Last 7 days").assertIsDisplayed()
        assertVisibleTextFits()
        capture("Period-selector")
        compose.onNodeWithText("Last 7 days").performClick()
        show("Sources", "Sources")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Measured URLs"))
        val removeUrl = hasContentDescription("Remove measured URL")
        compose.waitUntil(5000) {
            compose.onAllNodes(removeUrl).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(removeUrl)
        compose.onNode(removeUrl).performClick()
        compose.onNodeWithText("Remove measured URL?").assertIsDisplayed()
        compose.onNodeWithText("Remove").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        assertVisibleTextFits()
        capture("Remove-confirmation")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(
            1,
            runBlocking {
                reports
                    .observePerformance("all")
                    .first()
                    .urls.size
            },
        )
        compose.onNode(removeUrl).performClick()
        compose.onNodeWithText("Remove").performClick()
        compose.waitUntil(5000) {
            runBlocking {
                reports
                    .observePerformance("all")
                    .first()
                    .urls
                    .isEmpty()
            }
        }
        assertEquals(
            1,
            runBlocking {
                reports
                    .observePerformance("all")
                    .first()
                    .runs.size
            },
        )
        openSourceForm("Cloudflare Zone Analytics")
        compose.onNodeWithText("Site domain").performTextInput("ime-fixture.com")
        compose.onNodeWithText("ime-fixture.com").assertIsDisplayed()
        assertVisibleTextFits()
        capture("Form-IME")
    }

    /** Lähteen lomake avataan yläpalkin Add source -alapaneelista. */
    private fun openSourceForm(label: String) {
        compose.onAllNodes(hasText("Add source") and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasText(label) and hasClickAction()).onLast().performClick()
        compose.waitForIdle()
    }

    private fun drawFrame() {
        // Chart hit targets and marker callbacks are calculated during drawing.
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
            } finally {
                bitmap.recycle()
            }
        }
        compose.waitForIdle()
    }

    private fun show(
        family: String,
        view: String,
        searchProvider: String = "Google",
    ) {
        compose.runOnIdle { screen.value = "Fixture" }
        compose.waitForIdle()
        compose.runOnIdle {
            tab.value = view
            provider.value = searchProvider
            screen.value = family
        }
        compose.waitForIdle()
    }

    private fun assertVisibleTextFits() {
        val nodes = compose.onAllNodes(hasText("", substring = true), useUnmergedTree = true).fetchSemanticsNodes()
        for (node in nodes) {
            val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: continue
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            action(layouts)
            layouts.forEach {
                val widthExcess =
                    (0 until it.lineCount).maxOfOrNull { line ->
                        maxOf(it.getLineRight(line) - it.size.width, -it.getLineLeft(line))
                    } ?: 0f
                val heightExcess = it.multiParagraph.height - it.size.height
                // Check glyph line bounds; paragraph width can include unused constraint space.
                val clipped = widthExcess > 1f || heightExcess > 1f || it.multiParagraph.didExceedMaxLines
                if (it.hasVisualOverflow) {
                    File(context.filesDir, "text-bounds.txt").appendText(
                        "${screen.value}/${tab.value}; ${it.layoutInput.text}; size=${it.size}; " +
                            "widthExcess=$widthExcess; heightExcess=$heightExcess\n",
                    )
                }
                if (clipped) capture("overflow-${screen.value}-${tab.value}")
                assertFalse(
                    "Clipped text: ${it.layoutInput.text}; size=${it.size}; widthExcess=$widthExcess; " +
                        "heightExcess=$heightExcess",
                    clipped,
                )
            }
        }
    }

    private fun capture(name: String) {
        android.os.SystemClock.sleep(250) // Let the Android dialog window transition finish before capturing.
        val config = context.resources.configuration
        val directory =
            File(
                context.filesDir,
                "parity-${config.screenWidthDp}-${config.fontScale}",
            ).apply {
                mkdirs()
            }
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory, name.replace(Regex("[^A-Za-z0-9]"), "_") + ".png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
