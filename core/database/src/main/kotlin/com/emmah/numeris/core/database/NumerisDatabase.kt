package com.emmah.numeris.core.database

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.withWriteTransaction
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        ConnectionEntity::class,
        CloudflareTrafficEntity::class,
        CloudflareCountryEntity::class,
        CloudflarePageEntity::class,
        CloudflareStatusEntity::class,
        WebAnalyticsSiteEntity::class,
        WebAnalyticsDailyEntity::class,
        WebAnalyticsReferrerEntity::class,
        WebAnalyticsPageEntity::class,
        WebAnalyticsCountryEntity::class,
        SearchConsoleEntity::class,
        SearchDeviceEntity::class,
        SearchPageQueryEntity::class,
        SitemapUrlEntity::class,
        PerformanceUrlEntity::class,
        CruxMetricPointEntity::class,
        PageSpeedRunEntity::class,
        PageSpeedAuditEntity::class,
        BingSiteEntity::class,
        BingRankTrafficEntity::class,
        BingQueryStatEntity::class,
        BingPageStatEntity::class,
        BingRawItemEntity::class,
        GoogleAnalyticsDailyEntity::class,
        GoogleAnalyticsPageEntity::class,
        GoogleAnalyticsSourceEntity::class,
        GoogleAnalyticsEventEntity::class,
        GoogleAnalyticsDeviceEntity::class,
        UptimeCheckEntity::class,
        SyncRunEntity::class,
        MetaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class NumerisDatabase : RoomDatabase() {
    abstract fun connectionDao(): ConnectionDao

    abstract fun webAnalyticsDao(): WebAnalyticsDao

    abstract fun cloudflareDao(): CloudflareDao

    abstract fun searchDao(): SearchDao

    abstract fun performanceDao(): PerformanceDao

    abstract fun googleAnalyticsDao(): GoogleAnalyticsDao

    abstract fun bingDao(): BingDao

    abstract fun healthDao(): HealthDao

    abstract fun syncRunDao(): SyncRunDao

    abstract fun reportSnapshotDao(): ReportSnapshotDao
}

fun buildNumerisDatabase(context: Context): NumerisDatabase =
    Room
        .databaseBuilder(
            context.applicationContext,
            NumerisDatabase::class.java,
            "numeris.db",
        ).setDriver(AndroidSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

typealias DatabaseWrite<T> = suspend NumerisDatabase.() -> T

suspend fun <T> NumerisDatabase.writeTransaction(block: DatabaseWrite<T>): T = withWriteTransaction { block() }
