package com.emmah.numeris

import android.app.Activity
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.numeris.data.GoogleAuthorizationManager
import com.emmah.numeris.data.GoogleAuthorizationState
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.common.api.Status
import com.google.android.gms.common.internal.safeparcel.SafeParcelableSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAuthorizationResultTest {
    private fun manager() = GoogleAuthorizationManager(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test fun canceledResultRetainsGoogleErrorCodeWithoutProviderMessage() {
        val manager = manager()
        val intent = Intent()
        SafeParcelableSerializer.serializeToIntentExtra(Status(10, "synthetic-private-response"), intent, "status")
        val state = manager.completeAuthorization(intent, Activity.RESULT_CANCELED)
        assertTrue(state is GoogleAuthorizationState.Failure)
        assertEquals("Google authorization was not completed (error 10).", manager.authorizationFeedback.value?.message)
        assertFalse(manager.authorizationFeedback.value?.authorized == true)
    }

    @Test fun nullCanceledResultDoesNotSuggestAnotherLoginAttempt() {
        val manager = manager()
        manager.completeAuthorization(null, Activity.RESULT_CANCELED)
        assertEquals(
            "Google authorization was canceled before a result was returned.",
            manager.authorizationFeedback.value?.message,
        )
    }

    @Test fun successfulGooglePayloadStillRequiresSuccessfulActivityResult() {
        val intent = Intent()
        SafeParcelableSerializer.serializeToIntentExtra(Status.RESULT_SUCCESS, intent, "status")
        SafeParcelableSerializer.serializeToIntentExtra(
            AuthorizationResult(null, "synthetic-access-token", null, emptyList(), null, null),
            intent,
            "authorization_result",
        )
        val manager = manager()
        assertTrue(manager.completeAuthorization(intent, Activity.RESULT_CANCELED) is GoogleAuthorizationState.Failure)
        assertFalse(manager.authorizationFeedback.value?.authorized == true)
        assertTrue(manager.completeAuthorization(intent, Activity.RESULT_OK) is GoogleAuthorizationState.Authorized)
        assertTrue(manager.authorizationFeedback.value?.authorized == true)
        assertTrue(
            manager.authorizationFeedback.value
                ?.message
                .orEmpty()
                .startsWith("Google access confirmed at "),
        )
    }
}
