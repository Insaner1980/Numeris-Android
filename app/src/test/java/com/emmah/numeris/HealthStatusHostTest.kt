package com.emmah.numeris

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.emmah.numeris.core.designsystem.NumerisTheme
import com.emmah.numeris.core.model.DateRange
import com.emmah.numeris.core.model.HealthReport
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.model.Probe
import com.emmah.numeris.feature.health.HealthReportScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class HealthStatusHostTest {
    @get:Rule val compose = createComposeRule()

    @Test fun distinguishesStatusAtNormalWidthAndFont() = checkStatus(411, 1f)

    @Test fun distinguishesStatusAt320Dp() = checkStatus(320, 1f)

    @Test fun distinguishesStatusAt200PercentFont() = checkStatus(411, 2f)

    @Test fun distinguishesStatusAt320DpAnd200PercentFont() = checkStatus(320, 2f)

    private fun checkStatus(
        width: Int,
        fontScale: Float,
    ) {
        val end = LocalDate.parse("2026-10-11")
        val probe = Probe("example.com", "2026-10-11T12:00:00Z", "pending", null, null, null)
        val report = mutableStateOf(HealthReport(DateRange(end.minusDays(6), end, 7), listOf(probe)))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                NumerisTheme {
                    Box(Modifier.width(width.dp).fillMaxHeight()) {
                        HealthReportScreen(
                            report.value,
                            "example.com",
                            listOf("example.com"),
                            Period.Last7Days,
                            "Uptime",
                            false,
                            {},
                            {},
                            {},
                            {},
                            {},
                        )
                    }
                }
            }
        }
        for (
        (status, label) in
        listOf("pending" to "Pending", "UP" to "Up", "DOWN" to "Down", "unknown" to "Pending")
        ) {
            compose.runOnIdle { report.value = report.value.copy(probes = listOf(probe.copy(status = status))) }
            val siteRow = hasText("example.com") and hasText("Success rate", substring = true)
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(siteRow)
            compose.onNode(siteRow).assertIsDisplayed().assert(hasText(label))
            compose
                .onAllNodes(hasText(label), useUnmergedTree = true)
                .onFirst()
                .fetchSemanticsNode()
                .assertTextFits("Clipped status: $label")
        }
        compose.runOnIdle { report.value = report.value.copy(probes = emptyList()) }
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("No cached checks"))
        compose.onNodeWithText("No cached checks").assertIsDisplayed()
    }
}
