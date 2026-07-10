package com.emmah.numeris

import android.app.Application
import androidx.work.Configuration
import com.emmah.numeris.core.database.buildNumerisDatabase
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.core.model.SourceSyncService
import com.emmah.numeris.core.network.BingApiClient
import com.emmah.numeris.core.network.CloudflareApiClient
import com.emmah.numeris.core.network.CruxApiClient
import com.emmah.numeris.core.network.GoogleAnalyticsApiClient
import com.emmah.numeris.core.network.NumerisHttpClient
import com.emmah.numeris.core.network.PageSpeedApiClient
import com.emmah.numeris.core.network.SearchConsoleApiClient
import com.emmah.numeris.core.network.SitemapClient
import com.emmah.numeris.core.network.UptimeClient
import com.emmah.numeris.core.network.WebAnalyticsApiClient
import com.emmah.numeris.core.security.AndroidSecretStore
import com.emmah.numeris.data.AndroidImportRepository
import com.emmah.numeris.data.AppPreferencesRepository
import com.emmah.numeris.data.BingSyncService
import com.emmah.numeris.data.CloudflareSyncService
import com.emmah.numeris.data.CruxSyncService
import com.emmah.numeris.data.DefaultSyncOrchestrator
import com.emmah.numeris.data.GoogleAnalyticsSyncService
import com.emmah.numeris.data.GoogleAuthorizationManager
import com.emmah.numeris.data.OverviewRepository
import com.emmah.numeris.data.PageSpeedSyncService
import com.emmah.numeris.data.ReportRepository
import com.emmah.numeris.data.RoomConnectionRepository
import com.emmah.numeris.data.SearchConsoleSyncService
import com.emmah.numeris.data.SitemapSyncService
import com.emmah.numeris.data.UptimeSyncService
import com.emmah.numeris.data.WebAnalyticsSyncService
import com.emmah.numeris.data.WindowsDatabaseImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

class NumerisApplication :
    Application(),
    Configuration.Provider {
    val container: AppContainer by lazy { AppContainer(this) }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(NumerisWorkerFactory { container }).build()

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            container.preferences.preferences.collect { settings ->
                if (settings.backgroundSyncEnabled) {
                    PeriodicSyncScheduler.schedule(this@NumerisApplication, settings.backgroundSyncHours)
                } else {
                    PeriodicSyncScheduler.cancel(this@NumerisApplication)
                }
            }
        }
    }
}

class AppContainer(
    application: Application,
) {
    val database = buildNumerisDatabase(application)
    val secretStore = AndroidSecretStore(application)
    val preferences = AppPreferencesRepository(application)
    private val http = NumerisHttpClient()
    val googleAuthorization = GoogleAuthorizationManager(application)
    private val searchConsoleSyncService =
        SearchConsoleSyncService(database, googleAuthorization, SearchConsoleApiClient(http))
    private val sourceServices: List<SourceSyncService> =
        listOf(
            UptimeSyncService(database, UptimeClient(http)),
            SitemapSyncService(database, SitemapClient(http)),
            CloudflareSyncService(database, secretStore, CloudflareApiClient(http)),
            WebAnalyticsSyncService(database, secretStore, WebAnalyticsApiClient(http)),
            CruxSyncService(database, secretStore, CruxApiClient(http)),
            PageSpeedSyncService(database, secretStore, PageSpeedApiClient(http)),
            BingSyncService(database, secretStore, BingApiClient(http)),
            searchConsoleSyncService,
            GoogleAnalyticsSyncService(database, googleAuthorization, GoogleAnalyticsApiClient(http)),
        )
    val syncOrchestrator =
        DefaultSyncOrchestrator(
            services = sourceServices,
            configuredSources = {
                database.connectionDao().configuredSourceNames().mapNotNullTo(linkedSetOf(), ::sourceIdFromStorage)
            },
            onSourceResult = { source, result ->
                val rows = database.connectionDao().bySource(source.storageName)
                val now = Instant.now().toString()
                database.connectionDao().upsert(
                    rows.map { row ->
                        when (result) {
                            is com.emmah.numeris.core.model.SyncResult.Success ->
                                row.copy(status = "connected", lastSync = now, lastError = null)
                            is com.emmah.numeris.core.model.SyncResult.Failure ->
                                row.copy(status = "error", lastError = result.safeMessage)
                        }
                    },
                )
            },
        )
    val overviewRepository = OverviewRepository(database.overviewDao())
    val reportRepository = ReportRepository(database.reportDao())
    val connectionRepository =
        RoomConnectionRepository(
            database,
            secretStore,
        ) { sourceServices.associateBy { it.source } }
    val importRepository =
        AndroidImportRepository(
            context = application,
            databaseImporter = WindowsDatabaseImporter(database),
            secretStore = secretStore,
            preferencesRepository = preferences,
        )

    fun sourceService(source: SourceId): SourceSyncService? = sourceServices.firstOrNull { it.source == source }

    suspend fun inspectIndexing(domain: String) = searchConsoleSyncService.inspectSitemapUrls(domain)

    private fun sourceIdFromStorage(value: String): SourceId? =
        when (value) {
            "cloudflare" -> SourceId.CloudflareZone
            "web_analytics" -> SourceId.CloudflareWebAnalytics
            "search_console" -> SourceId.SearchConsole
            "google_analytics" -> SourceId.GoogleAnalytics
            "crux" -> SourceId.Crux
            "pagespeed" -> SourceId.PageSpeed
            "bing_webmaster" -> SourceId.Bing
            "uptime" -> SourceId.Uptime
            "sitemap" -> SourceId.Sitemap
            else -> null
        }

    private val SourceId.storageName: String
        get() =
            when (this) {
                SourceId.CloudflareZone -> "cloudflare"
                SourceId.CloudflareWebAnalytics -> "web_analytics"
                SourceId.SearchConsole -> "search_console"
                SourceId.GoogleAnalytics -> "google_analytics"
                SourceId.Crux -> "crux"
                SourceId.PageSpeed -> "pagespeed"
                SourceId.Bing -> "bing_webmaster"
                SourceId.Uptime -> "uptime"
                SourceId.Sitemap -> "sitemap"
            }
}
