package com.emmah.numeris.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_1_2: Migration =
    object : Migration(1, 2) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("DROP TABLE IF EXISTS google_analytics_daily")
            connection.execSQL("DROP TABLE IF EXISTS google_analytics_pages")
            connection.execSQL("DROP TABLE IF EXISTS google_analytics_sources")
            connection.execSQL("DROP TABLE IF EXISTS google_analytics_events")
            connection.execSQL("DROP TABLE IF EXISTS google_analytics_devices")
            connection.execSQL("DELETE FROM connections WHERE source = 'google_analytics'")
            connection.execSQL("DELETE FROM sync_runs WHERE source IN ('google_analytics', 'GoogleAnalytics')")
        }
    }
