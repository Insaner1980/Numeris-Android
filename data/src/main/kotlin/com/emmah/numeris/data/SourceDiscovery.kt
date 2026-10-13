package com.emmah.numeris.data

import com.emmah.numeris.core.model.ConnectionId
import com.emmah.numeris.core.model.SecretKey
import com.emmah.numeris.core.model.SecretStore
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.core.model.reportDomain
import com.emmah.numeris.core.network.BingApiClient
import com.emmah.numeris.core.network.SourceDiscoveryClient
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class DiscoveredTarget(
    val domain: String,
    val id: String,
)

class SourceDiscovery(
    private val secrets: SecretStore,
    private val google: GoogleAuthorizationManager,
    private val client: SourceDiscoveryClient,
    private val bing: BingApiClient,
) {
    suspend fun discover(
        source: SourceId,
        id: ConnectionId?,
        metadata: Map<String, String>,
        enteredSecret: CharArray,
    ): List<DiscoveredTarget> {
        if (source == SourceId.SearchConsole) {
            enteredSecret.fill('\u0000')
            val token =
                (google.authorize() as? GoogleAuthorizationState.Authorized)?.accessToken
                    ?: error("Authorize Google before discovering properties.")
            return client.google(token).mapNotNull { value -> reportDomain(value)?.let { DiscoveredTarget(it, value) } }
        }
        val key = storedKey(source, id)
        val token =
            if (enteredSecret.isNotEmpty()) {
                enteredSecret
            } else {
                key?.let { secrets.get(it) }
                    ?: error("Enter a provider token before discovering targets.")
            }
        try {
            return when (source) {
                SourceId.CloudflareZone, SourceId.CloudflareWebAnalytics ->
                    client
                        .cloudflare(
                            token,
                            if (source == SourceId.CloudflareWebAnalytics) metadata["accountId"] else null,
                        ).mapNotNull { row -> reportDomain(row.domain)?.let { DiscoveredTarget(it, row.id) } }
                SourceId.Bing ->
                    discoverBing(token)
                else -> emptyList()
            }.distinct().sortedWith(compareBy({ it.domain }, { it.id }))
        } finally {
            token.fill('\u0000')
            enteredSecret.fill('\u0000')
        }
    }

    private suspend fun discoverBing(token: CharArray): List<DiscoveredTarget> =
        bing.call(token, "GetUserSites").mapNotNull { row ->
            val value = row["Url"]?.jsonPrimitive?.contentOrNull ?: row["SiteUrl"]?.jsonPrimitive?.contentOrNull
            value?.let { reportDomain(it)?.let { domain -> DiscoveredTarget(domain, value) } }
        }

    private fun storedKey(
        source: SourceId,
        id: ConnectionId?,
    ): SecretKey? =
        when (source) {
            SourceId.CloudflareZone -> id?.let { SecretKey("cloudflare:${it.value}:api_token") }
            SourceId.CloudflareWebAnalytics -> id?.let { SecretKey("web_analytics:${it.value}:api_token") }
            SourceId.Bing -> SecretKey("bing:bing:api_key")
            else -> null
        }
}
