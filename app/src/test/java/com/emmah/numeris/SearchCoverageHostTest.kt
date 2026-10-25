package com.emmah.numeris

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.emmah.numeris.core.designsystem.NumerisTheme
import com.emmah.numeris.core.model.DateRange
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.model.SearchObservation
import com.emmah.numeris.core.model.SearchReport
import com.emmah.numeris.feature.search.SearchReportScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class SearchCoverageHostTest {
    @get:Rule val compose = createComposeRule()

    @Test fun discoveriesAtNormalWidthAndFont() = checkDiscoveries(411, 1f)

    @Test fun discoveriesAt320Dp() = checkDiscoveries(320, 1f)

    @Test fun discoveriesAt200PercentFont() = checkDiscoveries(411, 2f)

    @Test fun discoveriesAt320DpAnd200PercentFont() = checkDiscoveries(320, 2f)

    private fun checkDiscoveries(
        width: Int,
        fontScale: Float,
    ) {
        val end = LocalDate.parse("2026-10-11")
        val daily =
            (0L..13L).map {
                SearchObservation("example.com", end.minusDays(it), "daily", "total", 10, 100)
            }
        val complete =
            SearchReport(
                DateRange(end.minusDays(6), end, 7),
                observations =
                    daily +
                        listOf(
                            SearchObservation("example.com", end, "query", "new query", 3, 30),
                            SearchObservation("example.com", end, "page", "/declining", 2, 30),
                            SearchObservation("example.com", end.minusDays(7), "page", "/declining", 8, 40),
                            SearchObservation("example.com", end, "legacy_query", "legacy query", 1, 20),
                        ),
                expectedSites = setOf("example.com"),
            )
        val report = mutableStateOf(complete)
        val tab = mutableStateOf("Queries")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                NumerisTheme {
                    Box(Modifier.width(width.dp).fillMaxHeight()) {
                        SearchReportScreen(
                            report.value,
                            "example.com",
                            listOf("example.com"),
                            Period.Last7Days,
                            "Google",
                            tab.value,
                            false,
                            {},
                            {},
                            {},
                            { tab.value = it },
                            {},
                        )
                    }
                }
            }
        }
        scrollTo("New queries")
        scrollTo("new query")
        scrollTo("Filter queries")
        compose.onNodeWithText("Filter queries").performTextInput("missing")
        scrollTo("No matching cached queries")
        compose.runOnIdle { tab.value = "Pages" }
        scrollTo("Declining pages")
        scrollTo("/declining")
        compose.runOnIdle { report.value = complete.copy(observations = daily) }
        scrollTo("No declining pages in the stored comparison")
        compose.runOnIdle { tab.value = "Queries" }
        scrollTo("No new queries in the stored comparison")
        compose.runOnIdle { report.value = complete.copy(observations = emptyList()) }
        scrollTo("A complete current and previous period is required.")
    }

    private fun scrollTo(text: String) {
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))
        compose.onAllNodes(hasText(text)).onFirst().assertIsDisplayed()
        val nodes = compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes()
        nodes.forEach { it.assertTextFits("Clipped text: $text") }
    }
}
