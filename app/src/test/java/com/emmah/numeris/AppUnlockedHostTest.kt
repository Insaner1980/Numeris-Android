package com.emmah.numeris

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = NumerisApplication::class, qualifiers = "w411dp-h891dp-mdpi")
class AppUnlockedHostTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun opensAndRecreatesWithoutAuthenticationAndHasNoLockSetting() = verifyUnlockedShell()

    @Test
    @Config(qualifiers = "w320dp-h800dp-mdpi")
    fun opensAt320DpWithoutAuthentication() = verifyUnlockedShell()

    @Test fun opensWith200PercentFontWithoutAuthentication() = verifyUnlockedShell(2f)

    @Test
    @Config(qualifiers = "w320dp-h800dp-mdpi")
    fun opensAt320DpWith200PercentFontWithoutAuthentication() = verifyUnlockedShell(2f)

    private fun verifyUnlockedShell(fontScale: Float = 1f) {
        RuntimeEnvironment.setFontScale(fontScale)
        val overview = hasText("Overview") or hasContentDescription("Overview")
        val more = hasText("More") or hasContentDescription("More")
        compose.waitUntil(10_000) {
            compose.onAllNodes(overview).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Unlock").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {
            compose.onAllNodes(more).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(more).onFirst().performClick()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Periodic sync"))
        compose.onNodeWithText("Periodic sync").assertIsDisplayed()
        compose.onNodeWithText("Biometric app lock").assertDoesNotExist()
    }
}
