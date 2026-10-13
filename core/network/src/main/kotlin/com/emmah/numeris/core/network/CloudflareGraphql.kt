package com.emmah.numeris.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.cancellation.CancellationException

/** Cloudflaren GraphQL Analytics -kutsut yhteisesti Zone Analyticsille ja Web Analyticsille. */
internal class CloudflareGraphql(
    private val http: NumerisHttpClient,
    private val provider: String,
) {
    /** Suorittaa kyselyn. GraphQL-virhe muutetaan Cloudflaren omaksi, redaktoiduksi viestiksi. */
    suspend fun query(
        token: CharArray,
        query: String,
        variables: JsonObject,
    ): JsonObject {
        val root = Json.parseToJsonElement(raw(token, query, variables)).jsonObject
        CloudflareQueryWindow.graphqlFailure(root, provider)?.let { throw it }
        return root
    }

    /** Datasettien rajat settings-solmusta. Epäonnistuminen palauttaa tyhjän kartan, jolloin aikaväliä ei rajata. */
    suspend fun limits(
        token: CharArray,
        query: String,
        variables: JsonObject,
        scope: String,
        datasets: List<String>,
    ): Map<String, CloudflareDatasetLimit?> {
        val raw =
            try {
                raw(token, query, variables)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        return raw?.let { CloudflareQueryWindow.parseLimits(it, scope, datasets) }.orEmpty()
    }

    private suspend fun raw(
        token: CharArray,
        query: String,
        variables: JsonObject,
    ): String {
        val body =
            buildJsonObject {
                put("query", query)
                put("variables", variables)
            }.toString()
        val request =
            Request
                .Builder()
                .url(ENDPOINT)
                .header("Authorization", "Bearer ${token.concatToString().removePrefix("Bearer ").trim()}")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        return http.execute(provider, request).body
    }

    private companion object {
        const val ENDPOINT = "https://api.cloudflare.com/client/v4/graphql"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
