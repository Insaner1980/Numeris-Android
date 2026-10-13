package com.emmah.numeris.core.network

import com.emmah.numeris.core.common.NumerisFailure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object ApiErrors {
    fun fromStatus(
        source: String,
        status: Int,
        retryAfterSeconds: Long? = null,
        errorCode: String? = null,
    ): NumerisFailure {
        // HTTP-tila ja palvelun virheluokka kertovat syyn ilman vastauksen vapaata tekstiä.
        val suffix = " (HTTP $status${errorCode?.let { ", $it" }.orEmpty()})."
        return when (status) {
            HTTP_UNAUTHORIZED -> NumerisFailure.Authentication("$source authorization is required$suffix")
            HTTP_FORBIDDEN -> NumerisFailure.Authorization("$source denied access to this resource$suffix")
            HTTP_NOT_FOUND -> NumerisFailure.NotFound("$source could not find the requested resource$suffix")
            HTTP_RATE_LIMITED ->
                NumerisFailure.RateLimit(
                    if (retryAfterSeconds == null) {
                        "$source rate limit was reached$suffix"
                    } else {
                        "$source rate limit was reached; retry later$suffix"
                    },
                )
            in HTTP_SERVER_ERROR_MIN..HTTP_SERVER_ERROR_MAX ->
                NumerisFailure.TransientNetwork("$source is temporarily unavailable$suffix")
            else -> NumerisFailure.Provider("$source request failed$suffix")
        }
    }

    fun fromIOException(source: String): NumerisFailure.TransientNetwork =
        NumerisFailure.TransientNetwork("$source could not be reached. Check the network and try again.")

    /**
     * Poimii virhevastauksesta vain kiinteän virheluokan (esim. Googlen `PERMISSION_DENIED` tai
     * `SERVICE_DISABLED`). Vapaata tekstiä ei palauteta, joten vastauksen yksityinen sisältö ei vuoda.
     */
    fun errorCode(body: String): String? {
        val error =
            runCatching { Json.parseToJsonElement(body.take(MAX_ERROR_BODY_CHARS)) as? JsonObject }
                .getOrNull()
                ?.get("error") as? JsonObject
                ?: return null
        val status = error["status"].enumValue()
        val reason =
            listOfNotNull(error["details"] as? JsonArray, error["errors"] as? JsonArray)
                .flatten()
                .firstNotNullOfOrNull { (it as? JsonObject)?.get("reason").enumValue() }
        return listOfNotNull(status, reason).distinct().joinToString(", ").ifEmpty { null }
    }

    private fun kotlinx.serialization.json.JsonElement?.enumValue(): String? =
        runCatching { this?.jsonPrimitive?.contentOrNull }.getOrNull()?.takeIf { enumLike.matches(it) }

    private val enumLike = Regex("[A-Za-z][A-Za-z_]{2,47}")
    private const val MAX_ERROR_BODY_CHARS = 16_384
    private const val HTTP_UNAUTHORIZED = 401
    private const val HTTP_FORBIDDEN = 403
    private const val HTTP_NOT_FOUND = 404
    private const val HTTP_RATE_LIMITED = 429
    private const val HTTP_SERVER_ERROR_MIN = 500
    private const val HTTP_SERVER_ERROR_MAX = 599
}
