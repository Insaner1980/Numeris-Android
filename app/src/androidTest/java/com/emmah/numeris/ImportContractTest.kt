package com.emmah.numeris

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.numeris.core.database.CloudflareTrafficEntity
import com.emmah.numeris.core.database.ConnectionEntity
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.security.ImportManifest
import com.emmah.numeris.data.ReportRepository
import com.emmah.numeris.data.WindowsDatabaseImporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ImportContractTest {
    @Test fun schema11WithoutGa4ImportsReportsAndPreservesAndroidConfiguration() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val database = testDatabase()
            val file = File(context.cacheDir, "isolated-schema11-${System.nanoTime()}.db")
            try {
                val manifest = schema11Fixture(file)
                database.connectionDao().upsert(listOf(ConnectionEntity("android:kept", "crux", "configured")))
                val importer = WindowsDatabaseImporter(database)
                assertEquals(listOf("fixture.com"), importer.validateAndPreview(file, manifest).domains.distinct())
                importer.replaceFromSnapshot(file, manifest)
                val repo =
                    ReportRepository(database, Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC))
                assertEquals(
                    42L,
                    repo
                        .observeTraffic("fixture.com", Period.Last7Days)
                        .first()
                        .days
                        .single()
                        .visitors,
                )
                assertEquals(
                    7L,
                    repo
                        .observeSearch("fixture.com", Period.Last7Days, "Google")
                        .first()
                        .rows("page")
                        .single()
                        .clicks,
                )
                assertEquals(
                    "https://fixture.com/article/",
                    repo
                        .observePerformance("fixture.com")
                        .first()
                        .urls
                        .single()
                        .url,
                )
                assertNotNull(database.connectionDao().find("android:kept"))
            } finally {
                database.close()
                file.delete()
            }
        }

    @Test fun incompatibleAndCorruptSnapshotsFailBeforeReplacement() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val database = testDatabase()
            val file = File(context.cacheDir, "isolated-invalid-${System.nanoTime()}.db")
            try {
                val manifest = schema11Fixture(file)
                val importer = WindowsDatabaseImporter(database)
                database.cloudflareDao().upsertTraffic(
                    listOf(CloudflareTrafficEntity("kept.com", "2026-10-11", uniqueVisitors = 99, fetchedAt = "now")),
                )
                val badCounts = manifest.copy(rowCounts = manifest.rowCounts + ("cloudflare_traffic" to 2))
                assertThrows(IllegalArgumentException::class.java) { importer.validateAndPreview(file, badCounts) }
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use {
                    it.execSQL("PRAGMA user_version = 12")
                }
                assertThrows(
                    IllegalArgumentException::class.java,
                ) { importer.validateAndPreview(file, manifest.copy(windowsSchemaVersion = 12)) }
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use {
                    it.execSQL("PRAGMA user_version = 11")
                    it.execSQL("ALTER TABLE cloudflare_traffic RENAME TO invalid_traffic_original")
                    it.execSQL(
                        """
                        CREATE TABLE cloudflare_traffic AS
                        SELECT id, domain, date, pageviews, unique_visitors AS missing,
                               requests, cached_requests, cached_bytes, total_bytes,
                               threats, top_country, top_path, fetched_at
                        FROM invalid_traffic_original
                        """.trimIndent(),
                    )
                }
                assertThrows(IllegalArgumentException::class.java) { importer.validateAndPreview(file, manifest) }
                val repo =
                    ReportRepository(database, Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC))
                assertEquals(
                    99L,
                    repo
                        .observeTraffic("kept.com", Period.Last7Days)
                        .first()
                        .days
                        .single()
                        .visitors,
                )
            } finally {
                database.close()
                file.delete()
            }
        }
}

internal fun schema11Fixture(
    file: File,
    additionalSql: String = "",
): ImportManifest {
    val sql =
        testFixture("schema11.sql")
            .bufferedReader()
            .use { it.readText() }
    val counts =
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            (sql + additionalSql)
                .split(';')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .forEach(db::execSQL)
            val tables =
                db
                    .rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE " +
                            "'sqlite_%' AND name != 'android_metadata'",
                        null,
                    ).use { cursor ->
                        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
                    }
            tables.associateWith { table ->
                db.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
                    cursor.moveToFirst()
                    cursor.getLong(0)
                }
            }
        }
    return ImportManifest(1, 11, "2026-10-11T12:00:00Z", counts)
}
