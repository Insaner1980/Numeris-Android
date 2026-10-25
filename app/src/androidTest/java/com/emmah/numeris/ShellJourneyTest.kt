package com.emmah.numeris

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import com.emmah.numeris.core.database.CloudflareTrafficEntity
import com.emmah.numeris.core.database.SearchConsoleEntity
import com.emmah.numeris.core.database.SitemapUrlEntity
import com.emmah.numeris.core.database.WebAnalyticsPageEntity
import com.emmah.numeris.core.database.writeTransaction
import com.emmah.numeris.core.model.Period
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

open class ShellJourneyTest {
    private val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    private val filterChip = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val container get() = (compose.activity.application as NumerisApplication).container

    @Before fun seed() =
        runBlocking {
            container.preferences.selectDomain("all")
            container.preferences.selectPeriod(Period.Last7Days)
            container.preferences.setBackgroundSync(false, 24)
            val now = Instant.now().toString()
            container.database.writeTransaction {
                cloudflareDao().upsertTraffic(
                    (0L..13L).map {
                        CloudflareTrafficEntity(
                            "journey.com",
                            LocalDate.now().minusDays(it).toString(),
                            uniqueVisitors = 42,
                            fetchedAt = now,
                        )
                    },
                )
                webAnalyticsDao().upsertWebAnalyticsPages(
                    listOf(
                        WebAnalyticsPageEntity("journey.com", LocalDate.now().toString(), "/article/", 100),
                    ),
                )
                searchDao().upsertSearchConsole(
                    listOf(
                        SearchConsoleEntity(
                            siteUrl = "sc-domain:journey.com",
                            date = LocalDate.now().toString(),
                            kind = "query_daily",
                            query = "needle query",
                            clicks = 10,
                            impressions = 100,
                            fetchedAt = now,
                        ),
                    ),
                )
                searchDao().upsertSitemapUrls(
                    (1..5).map { SitemapUrlEntity("journey.com", "https://journey.com/page-$it/", now, now) },
                )
            }
            compose.waitForIdle()
        }

    @Test fun pageBackAndSearchFilterSurviveNavigationAndActivityRecreationWithoutRefresh() {
        val initialSyncState = runBlocking { container.syncOrchestrator.observeState().first() }
        navigate("Overview")
        compose.waitUntil(10000) {
            compose.onAllNodes(hasText("All sites") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText("All sites") and filterChip).performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodes(hasText("journey.com")).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText("journey.com")).onLast().performClick()
        val selectedSite = hasText("journey.com") and filterChip
        waitForDisplayed(selectedSite)
        compose.onNode(hasText("Last 7 days") and filterChip).performScrollTo().performClick()
        compose.onNode(hasText("Last 30 days")).performClick()
        val selectedPeriod = hasText("Last 30 days") and filterChip
        waitForDisplayed(selectedPeriod)
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("/article/"))
        compose.onNode(hasText("/article/") and hasClickAction()).performClick()
        waitForDisplayed(hasText("Page detail"))
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        waitForDisplayed(hasText("Page detail"))
        compose.onNode(hasContentDescription("Back")).performClick()
        waitForDisplayed(hasText("/article/"))
        val restoredPreferences = runBlocking { container.preferences.preferences.first() }
        assertEquals("journey.com", restoredPreferences.selectedDomain)
        assertEquals(Period.Last30Days, restoredPreferences.selectedPeriod)
        navigate("Search")
        compose.onNode(hasText("Queries") and tab).performScrollTo().performClick()
        waitForNode(hasText("Filter queries"))
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Filter queries"))
        compose.onNode(hasText("Filter queries")).performTextInput("needle")
        navigate("Traffic")
        navigate("Search")
        waitForNode(hasText("Filter queries"))
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Filter queries"))
        compose.onNode(hasText("needle")).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        waitForNode(hasText("Filter queries"))
        compose.onNode(hasText("needle")).performScrollTo().assertIsDisplayed()
        assertEquals(initialSyncState, runBlocking { container.syncOrchestrator.observeState().first() })
    }

    @Test fun insightOpensExactIndexingTabAndBackRestoresOverviewWithoutRefresh() {
        val initialSyncState = runBlocking { container.syncOrchestrator.observeState().first() }
        navigate("Overview")
        val attention = hasText("thing", substring = true) and hasClickAction()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(attention)
        compose.onNode(attention).performClick()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Open Google Search, Indexing"))
        compose.onNode(hasText("Open Google Search, Indexing")).performClick()
        waitForNode(hasText("Indexing") and tab and isSelected())
        compose.onNode(hasText("Indexing") and tab and isSelected()).performScrollTo().assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        waitForDisplayed(hasText("Open Google Search, Indexing"))
        assertEquals(initialSyncState, runBlocking { container.syncOrchestrator.observeState().first() })
    }

    @Test fun sourcesAndHealthReturnToMoreAndRetainFormMetadataAcrossRecreation() {
        navigate("More")
        compose.onNode(hasText("Health") and hasClickAction()).performClick()
        waitForDisplayed(hasContentDescription("Back"))
        compose.onNode(hasContentDescription("Back")).performClick()
        compose.onNode(hasText("Sources") and hasClickAction()).performClick()
        waitForNode(hasText("Add source") and hasClickAction())
        compose.onAllNodes(hasText("Add source") and hasClickAction()).onFirst().performClick()
        compose.onAllNodes(hasText("Cloudflare Zone Analytics") and hasClickAction()).onLast().performClick()
        compose.onNode(hasText("Site domain")).performTextInput("saved-form.com")
        compose.onNode(hasText("API token")).performScrollTo().performTextInput("fixture-secret")
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        waitForNode(hasText("saved-form.com"))
        compose.onNode(hasText("saved-form.com")).performScrollTo().assertIsDisplayed()
        assertEquals(
            AnnotatedString(""),
            compose.onNode(hasText("API token")).fetchSemanticsNode().config[SemanticsProperties.EditableText],
        )
        compose
            .onAllNodes(hasContentDescription("Back"))
            .onLast()
            .performClick()
        compose.waitForIdle()
        compose.onNode(hasContentDescription("Back")).performClick()
        compose.onNode(hasText("Sync")).assertIsDisplayed()
    }

    private fun navigate(label: String) {
        val matcher = (hasText(label) or hasContentDescription(label)) and tab
        compose.waitUntil(10000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        // Raporttinäkymien välilehdet ovat myös Tab-rooleja; alanavigaatio on näytöllä alimpana.
        val nodes = compose.onAllNodes(matcher)
        val bottom =
            nodes
                .fetchSemanticsNodes()
                .withIndex()
                .maxBy { it.value.boundsInRoot.top }
                .index
        nodes[bottom].performClick()
        compose.waitForIdle()
        if (label in listOf("Overview", "Traffic", "Search")) {
            waitForNode(hasScrollAction())
        }
    }

    private fun waitForDisplayed(matcher: SemanticsMatcher) {
        compose.waitUntil(10000) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().size == 1 &&
                compose.onNode(matcher).isDisplayed()
        }
        compose.onNode(matcher).assertIsDisplayed()
    }

    private fun waitForNode(matcher: SemanticsMatcher) {
        // Room reloads asynchronously after navigation and activity recreation.
        compose.waitUntil(10000) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
