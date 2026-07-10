@file:Suppress("TooGenericExceptionCaught")

package com.emmah.numeris.data

import com.emmah.numeris.core.common.NumerisFailure
import com.emmah.numeris.core.database.CruxMetricPointEntity
import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.database.PageSpeedAuditEntity
import com.emmah.numeris.core.database.PageSpeedRunEntity
import com.emmah.numeris.core.database.writeTransaction
import com.emmah.numeris.core.model.ConnectionTestResult
import com.emmah.numeris.core.model.SecretKey
import com.emmah.numeris.core.model.SecretStore
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.core.model.SourceSyncService
import com.emmah.numeris.core.model.SyncRequest
import com.emmah.numeris.core.model.SyncResult
import com.emmah.numeris.core.network.CruxApiClient
import com.emmah.numeris.core.network.PageSpeedApiClient
import com.emmah.numeris.core.network.RawJsonStoragePolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant

class CruxSyncService(
    private val database: NumerisDatabase,
    private val secretStore: SecretStore,
    private val client: CruxApiClient,
    private val clock: Clock = Clock.systemUTC(),
) : SourceSyncService {
    override val source = SourceId.Crux

    override suspend fun testConnection(): ConnectionTestResult =
        secretStore.withTestSecret(CRUX_KEY, "No CrUX API key is configured.") { key ->
            val url =
                database
                    .performanceDao()
                    .enabledUrls()
                    .firstOrNull()
                    ?.url
                    ?: return@withTestSecret ConnectionTestResult.Failure("Add a performance URL first.")
            client.queryHistory(key, "url", url, "PHONE")
            ConnectionTestResult.Success
        }

    override suspend fun sync(request: SyncRequest): SyncResult =
        secretStore.withSyncSecret(CRUX_KEY, "No CrUX API key is configured.") { key ->
            val fetchedAt = Instant.now(clock).toString()
            val urls = database.performanceDao().enabledUrls()
            val points =
                buildList {
                    urls.map { it.origin }.distinct().forEach { origin ->
                        FORM_FACTORS.forEach { factor -> addAll(client.queryHistory(key, "origin", origin, factor)) }
                    }
                    urls.forEach { url ->
                        FORM_FACTORS.forEach { factor -> addAll(client.queryHistory(key, "url", url.url, factor)) }
                    }
                }.map { point ->
                    CruxMetricPointEntity(
                        point.targetType,
                        point.target,
                        point.formFactor,
                        point.collectionStart,
                        point.collectionEnd,
                        point.metric,
                        point.p75,
                        point.goodDensity,
                        point.needsImprovementDensity,
                        point.poorDensity,
                        RawJsonStoragePolicy.redactAndLimit(point.rawJson),
                        fetchedAt,
                    )
                }
            database.writeTransaction { performanceDao().upsertCruxMetrics(points) }
            points.size
        }

    private companion object {
        val CRUX_KEY = SecretKey("crux:crux:api_key")
        val FORM_FACTORS = listOf(null, "PHONE", "DESKTOP", "TABLET")
    }
}

class PageSpeedSyncService(
    private val database: NumerisDatabase,
    private val secretStore: SecretStore,
    private val client: PageSpeedApiClient,
    private val clock: Clock = Clock.systemUTC(),
) : SourceSyncService {
    override val source = SourceId.PageSpeed
    private val limiter = Mutex()

    override suspend fun testConnection(): ConnectionTestResult =
        secretStore.withTestSecret(PAGESPEED_KEY, "No PageSpeed API key is configured.") { key ->
            val url =
                database
                    .performanceDao()
                    .enabledUrls()
                    .firstOrNull()
                    ?.url
                    ?: return@withTestSecret ConnectionTestResult.Failure("Add a performance URL first.")
            client.run(key, url, "mobile")
            ConnectionTestResult.Success
        }

    override suspend fun sync(request: SyncRequest): SyncResult =
        secretStore.withSyncSecret(PAGESPEED_KEY, "No PageSpeed API key is configured.") { key ->
            limiter.withLock {
                val fetchedAt = Instant.now(clock).toString()
                var rows = 0
                database.performanceDao().enabledUrls().forEach { target ->
                    STRATEGIES.forEach { strategy ->
                        val run = retry { client.run(key, target.url, strategy) }
                        val runEntity =
                            PageSpeedRunEntity(
                                run.url,
                                run.strategy,
                                run.analysisUtc,
                                run.finalUrl,
                                run.performanceScore,
                                run.accessibilityScore,
                                run.bestPracticesScore,
                                run.seoScore,
                                run.lighthouseVersion,
                                run.runtimeError,
                                run.warningsJson?.let { RawJsonStoragePolicy.redactAndLimit(it) },
                                RawJsonStoragePolicy.redactAndLimit(run.rawJson),
                                fetchedAt,
                            )
                        val audits =
                            run.audits.map { audit ->
                                PageSpeedAuditEntity(
                                    run.url,
                                    run.strategy,
                                    run.analysisUtc,
                                    audit.id,
                                    audit.title,
                                    audit.score,
                                    audit.numericValue,
                                    audit.numericUnit,
                                    audit.displayValue,
                                    audit.scoreDisplayMode,
                                    audit.detailsJson?.let {
                                        RawJsonStoragePolicy.redactAndLimit(it, RawJsonStoragePolicy.MAX_DETAIL_CHARS)
                                    },
                                )
                            }
                        database.writeTransaction {
                            performanceDao().upsertPageSpeedRuns(listOf(runEntity))
                            performanceDao().upsertPageSpeedAudits(audits)
                        }
                        rows += 1 + audits.size
                        delay(REQUEST_SPACING_MILLIS)
                    }
                }
                database.writeTransaction {
                    performanceDao().retainLatestPageSpeedRuns(RUNS_TO_KEEP)
                    performanceDao().deleteOrphanedPageSpeedAudits()
                }
                rows
            }
        }

    private suspend fun <T> retry(block: suspend () -> T): T {
        var delayMillis = INITIAL_BACKOFF_MILLIS
        repeat(MAX_ATTEMPTS - 1) {
            try {
                return block()
            } catch (failure: NumerisFailure) {
                if (failure !is NumerisFailure.RateLimit && failure !is NumerisFailure.TransientNetwork) throw failure
                delay(delayMillis)
                delayMillis *= 2
            }
        }
        return block()
    }

    private companion object {
        val PAGESPEED_KEY = SecretKey("pagespeed:pagespeed:api_key")
        val STRATEGIES = listOf("mobile", "desktop")
        const val RUNS_TO_KEEP = 30
        const val MAX_ATTEMPTS = 3
        const val INITIAL_BACKOFF_MILLIS = 2_000L
        const val REQUEST_SPACING_MILLIS = 1_000L
    }
}

private suspend fun SecretStore.withTestSecret(
    key: SecretKey,
    missingMessage: String,
    block: suspend (CharArray) -> ConnectionTestResult,
): ConnectionTestResult {
    val secret = get(key) ?: return ConnectionTestResult.Failure(missingMessage)
    return try {
        block(secret)
    } catch (failure: Exception) {
        ConnectionTestResult.Failure(failure.message ?: "Connection test failed.")
    } finally {
        secret.fill('\u0000')
    }
}

private suspend fun SecretStore.withSyncSecret(
    key: SecretKey,
    missingMessage: String,
    block: suspend (CharArray) -> Int,
): SyncResult {
    val secret = get(key) ?: return SyncResult.Failure(missingMessage, false)
    return try {
        SyncResult.Success(block(secret))
    } catch (failure: Exception) {
        SyncResult.Failure(failure.message ?: "Sync failed.", failure is NumerisFailure.TransientNetwork)
    } finally {
        secret.fill('\u0000')
    }
}
