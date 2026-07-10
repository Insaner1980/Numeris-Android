package com.emmah.numeris.core.network

import com.emmah.numeris.core.model.SiteIdentity
import okhttp3.Request
import kotlin.time.TimeSource

class UptimeClient(
    private val http: NumerisHttpClient,
) {
    suspend fun probe(domainOrUrl: String): UptimeProbeResult {
        val site = SiteIdentity.fromDomainOrUrl(domainOrUrl)
        val started = TimeSource.Monotonic.markNow()
        return runCatching {
            val result =
                http.execute(
                    "Uptime",
                    Request
                        .Builder()
                        .url(site.originUrl)
                        .get()
                        .build(),
                )
            UptimeProbeResult(site.domain, "up", result.status, started.elapsedNow().inWholeMilliseconds, null)
        }.getOrElse { failure ->
            UptimeProbeResult(site.domain, "down", null, null, failure.message ?: "Uptime check failed.")
        }
    }
}

data class UptimeProbeResult(
    val domain: String,
    val status: String,
    val statusCode: Int?,
    val responseMs: Long?,
    val safeError: String?,
)
