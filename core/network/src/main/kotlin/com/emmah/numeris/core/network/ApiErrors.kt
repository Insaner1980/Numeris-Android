package com.emmah.numeris.core.network

import com.emmah.numeris.core.common.NumerisFailure

object ApiErrors {
    fun fromStatus(
        source: String,
        status: Int,
        retryAfterSeconds: Long? = null,
    ): NumerisFailure =
        when (status) {
            HTTP_UNAUTHORIZED -> NumerisFailure.Authentication("$source authorization is required.")
            HTTP_FORBIDDEN -> NumerisFailure.Authorization("$source denied access to this resource.")
            HTTP_NOT_FOUND -> NumerisFailure.Provider("$source could not find the requested resource.")
            HTTP_RATE_LIMITED ->
                NumerisFailure.RateLimit(
                    if (retryAfterSeconds == null) {
                        "$source rate limit was reached."
                    } else {
                        "$source rate limit was reached; retry later."
                    },
                )
            in HTTP_SERVER_ERROR_MIN..HTTP_SERVER_ERROR_MAX ->
                NumerisFailure.TransientNetwork("$source is temporarily unavailable.")
            else -> NumerisFailure.Provider("$source request failed with HTTP $status.")
        }

    fun fromIOException(source: String): NumerisFailure.TransientNetwork =
        NumerisFailure.TransientNetwork("$source could not be reached. Check the network and try again.")

    private const val HTTP_UNAUTHORIZED = 401
    private const val HTTP_FORBIDDEN = 403
    private const val HTTP_NOT_FOUND = 404
    private const val HTTP_RATE_LIMITED = 429
    private const val HTTP_SERVER_ERROR_MIN = 500
    private const val HTTP_SERVER_ERROR_MAX = 599
}
