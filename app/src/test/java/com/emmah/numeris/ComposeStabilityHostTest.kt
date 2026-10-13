package com.emmah.numeris

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.emmah.numeris.core.model.Metric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Modifier

@RunWith(RobolectricTestRunner::class)
class ComposeStabilityHostTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun configuredTypesHaveOnlyFinalImmutableFields() {
        val names =
            requireNotNull(javaClass.getResourceAsStream("/stability.conf"))
                .bufferedReader()
                .use { it.readLines() }
                .map { it.substringBefore("//").trim() }
                .filter { it.isNotEmpty() }
        assertTrue("The stability contract must not be empty", names.isNotEmpty())
        val visited = mutableSetOf<Class<*>>()
        names.forEach { assertImmutable(Class.forName(it), visited) }
    }

    @Test
    fun equalMetricsSkipButChangedValuesRecompose() {
        val frame = mutableIntStateOf(0)
        val value = mutableIntStateOf(10)
        var renders = 0
        val onRender = { renders += 1 }
        compose.setContent {
            Text("Frame ${frame.intValue}")
            MetricProbe(Metric("Views", value.intValue.toDouble()), onRender)
        }
        compose.runOnIdle { assertEquals(1, renders) }
        compose.runOnIdle { frame.intValue += 1 }
        compose.onNodeWithText("Frame 1").assertExists()
        compose.runOnIdle { assertEquals("An equal replacement should be skipped", 1, renders) }
        compose.runOnIdle { value.intValue = 20 }
        compose.onNodeWithText("Views: 20.0").assertExists()
        compose.runOnIdle { assertEquals("Changed values must still recompose", 2, renders) }
    }

    private fun assertImmutable(
        type: Class<*>,
        visited: MutableSet<Class<*>>,
    ) {
        if (type.isPrimitive || type in immutableScalars || !visited.add(type)) return
        assertTrue("Unexpected type in stability contract: ${type.name}", type.name.startsWith("com.emmah.numeris."))
        assertTrue("Open type in stability contract: ${type.name}", Modifier.isFinal(type.modifiers))
        type.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.forEach { field ->
            assertTrue("Mutable field in stability contract: $field", Modifier.isFinal(field.modifiers))
            assertImmutable(field.type, visited)
        }
    }

    private companion object {
        val immutableScalars =
            setOf(
                String::class.java,
                Boolean::class.javaObjectType,
                Int::class.javaObjectType,
                Long::class.javaObjectType,
                Double::class.javaObjectType,
            )
    }
}

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
private fun MetricProbe(
    metric: Metric,
    onRender: () -> Unit,
) {
    SideEffect(onRender)
    Text("${metric.label}: ${metric.value}")
}
