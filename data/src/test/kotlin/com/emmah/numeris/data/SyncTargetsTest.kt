package com.emmah.numeris.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTargetsTest {
    @Test fun failureDoesNotPreventOtherTargetsAndIsReported() =
        runBlocking {
            val visited = mutableListOf<String>()
            val failure =
                runCatching {
                    syncTargets(listOf("first", "failed", "last")) {
                        visited += it
                        check(it != "failed") { "Fixture failure" }
                        1
                    }
                }.exceptionOrNull()
            assertEquals(listOf("first", "failed", "last"), visited)
            assertEquals("Fixture failure", failure?.message)
        }

    @Test fun cancellationStopsRemainingTargets() =
        runBlocking {
            val visited = mutableListOf<String>()
            val failure =
                runCatching {
                    syncTargets(listOf("cancel", "never")) {
                        visited += it
                        throw CancellationException("Fixture cancellation")
                    }
                }.exceptionOrNull()
            assertTrue(failure is CancellationException)
            assertEquals(listOf("cancel"), visited)
        }
}
