package com.emmah.numeris.data

import com.emmah.numeris.core.model.SyncResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GoogleSyncAuthorizationTest {
    @Test fun authorizedTokenReachesTheSyncWithoutInvokingFailureHandler() {
        val token = GoogleAuthorizationState.Authorized("synthetic-token").syncToken { error("Unexpected failure") }
        assertEquals("synthetic-token", token)
    }

    @Test fun failureReturnsTheSafeMessageWithoutContinuingTheSync() {
        fun sync(): SyncResult {
            GoogleAuthorizationState.Failure("Safe failure").syncToken { return it }
            error("Sync must not run without authorization")
        }
        val failure = sync() as SyncResult.Failure
        assertEquals("Safe failure", failure.safeMessage)
        assertFalse(failure.retryable)
    }
}
