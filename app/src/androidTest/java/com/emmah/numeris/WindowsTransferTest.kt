package com.emmah.numeris

import android.net.Uri
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.numeris.core.database.ConnectionEntity
import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.data.AndroidImportRepository
import com.emmah.numeris.data.DefaultSyncOrchestrator
import com.emmah.numeris.data.ReportRepository
import com.emmah.numeris.data.WindowsDatabaseImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class WindowsTransferTest {
    @Test fun desktopExportImportsAndRemainsReadableAfterOfflineRestart() =
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val name = "windows-transfer-${UUID.randomUUID()}.db"
            val packageFile = File(context.cacheDir, "$name.nmrx")
            testFixture("windows-report.nmrx").use { input ->
                packageFile.outputStream().use(input::copyTo)
            }
            val original = packageFile.readBytes()

            fun openDatabase() =
                Room
                    .databaseBuilder<NumerisDatabase>(context, name)
                    .setDriver(AndroidSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            var database = openDatabase()
            try {
                database.connectionDao().upsert(listOf(ConnectionEntity("android:kept", "crux", "configured")))
                val imports =
                    AndroidImportRepository(context, WindowsDatabaseImporter(database), DefaultSyncOrchestrator(emptyList()))
                val passphrase = "synthetic fixture pässphrase".toCharArray()
                val preview = imports.inspect(Uri.fromFile(packageFile), passphrase)
                assertTrue(passphrase.all { it == '\u0000' })
                assertEquals(listOf("fixture.com"), preview.domains.distinct())
                assertEquals(23, preview.rowCounts.size)
                assertEquals(4L, imports.replace(preview.id).importedRows)
                assertFalse(File(context.cacheDir, "numeris-import/${preview.id}").exists())
                database.close()
                database = openDatabase()
                val reports = ReportRepository(database, Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC))
                assertEquals(
                    11L,
                    reports
                        .observeTraffic("fixture.com", Period.Last7Days)
                        .first()
                        .days
                        .single()
                        .visitors,
                )
                assertEquals(
                    7L,
                    reports
                        .observeSearch("fixture.com", Period.Last7Days, "Google")
                        .first()
                        .rows("page")
                        .single()
                        .clicks,
                )
                assertEquals(
                    "https://fixture.com/article/",
                    reports
                        .observePerformance("fixture.com")
                        .first()
                        .urls
                        .single()
                        .url,
                )
                assertEquals(
                    3L,
                    reports
                        .observeSearch("fixture.com", Period.Last7Days, "Bing")
                        .first()
                        .daily
                        .single()
                        .clicks,
                )
                assertNotNull(database.connectionDao().find("android:kept"))
                assertTrue(original.contentEquals(packageFile.readBytes()))
            } finally {
                database.close()
                context.deleteDatabase(name)
                packageFile.delete()
            }
        }
}
