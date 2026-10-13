package com.emmah.numeris

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room3.Room
import androidx.room3.executeSQL
import androidx.room3.useWriterConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.numeris.core.database.CloudflareTrafficEntity
import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.security.ExportCrypto
import com.emmah.numeris.core.security.ImportManifest
import com.emmah.numeris.data.AndroidImportRepository
import com.emmah.numeris.data.DefaultSyncOrchestrator
import com.emmah.numeris.data.ReportRepository
import com.emmah.numeris.data.WindowsDatabaseImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ImportRecoveryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: NumerisDatabase
    private lateinit var snapshot: File
    private lateinit var manifest: ImportManifest
    private lateinit var packageFile: File
    private lateinit var repository: AndroidImportRepository

    @Before fun prepare() =
        runBlocking {
            database =
                Room
                    .inMemoryDatabaseBuilder(context, NumerisDatabase::class.java)
                    .setDriver(AndroidSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            snapshot = File(context.cacheDir, "recovery-${System.nanoTime()}.db")
            manifest = schema11Fixture(snapshot)
            packageFile = File(context.cacheDir, "recovery-${System.nanoTime()}.nmrx")
            packageFile.writeBytes(encryptedPackage())
            repository =
                AndroidImportRepository(
                    context,
                    WindowsDatabaseImporter(database),
                    DefaultSyncOrchestrator(emptyList()),
                )
            database.cloudflareDao().upsertTraffic(
                listOf(
                    CloudflareTrafficEntity("kept.com", "2026-10-11", uniqueVisitors = 99, fetchedAt = "fixture"),
                ),
            )
        }

    @After fun cleanup() =
        runBlocking {
            repository.cancel()
            database.close()
            snapshot.delete()
            packageFile.delete()
            Unit
        }

    @Test fun cancelAndRestartRemovePlaintextAndPreserveOriginalEncryptedInput() =
        runBlocking {
            val original = packageFile.readBytes()
            val phrase = "fixture passphrase".toCharArray()
            val preview = repository.inspect(Uri.fromFile(packageFile), phrase)
            assertTrue(phrase.all { it == '\u0000' })
            assertTrue(File(context.cacheDir, "numeris-import/${preview.id}/numeris.db").exists())
            repository.cancel()
            assertFalse(File(context.cacheDir, "numeris-import/${preview.id}").exists())
            assertTrue(original.contentEquals(packageFile.readBytes()))
            assertTrue(runCatching { repository.replace(preview.id) }.isFailure)
            val interrupted = repository.inspect(Uri.fromFile(packageFile), "fixture passphrase".toCharArray())
            repository =
                AndroidImportRepository(
                    context,
                    WindowsDatabaseImporter(database),
                    DefaultSyncOrchestrator(emptyList()),
                )
            assertFalse(File(context.cacheDir, "numeris-import/${interrupted.id}").exists())
            assertTrue(original.contentEquals(packageFile.readBytes()))
            assertEquals(
                99L,
                ReportRepository(database)
                    .observeTraffic("kept.com", Period.All)
                    .first()
                    .days
                    .single()
                    .visitors,
            )
        }

    @Test fun databaseFailureRollsBackReplacementAndCleansPlaintext() =
        runBlocking {
            val preview = repository.inspect(Uri.fromFile(packageFile), "fixture passphrase".toCharArray())
            database.useWriterConnection {
                it.executeSQL(
                    "CREATE TRIGGER parity_failure BEFORE INSERT ON cloudflare_traffic " +
                        "BEGIN SELECT RAISE(ABORT, 'Fixture database failure'); END",
                )
            }
            assertTrue(runCatching { repository.replace(preview.id) }.isFailure)
            assertEquals(
                99L,
                ReportRepository(database)
                    .observeTraffic("kept.com", Period.All)
                    .first()
                    .days
                    .single()
                    .visitors,
            )
            assertFalse(File(context.cacheDir, "numeris-import/${preview.id}").exists())
            assertTrue(packageFile.exists())
        }

    @Test fun invalidFlagsDatesAndDuplicateNormalizedTargetsFailPreflight() {
        val importer = WindowsDatabaseImporter(database)
        SQLiteDatabase.openDatabase(snapshot.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("UPDATE performance_urls SET enabled = 2")
            assertTrue(runCatching { importer.validateAndPreview(snapshot, manifest) }.isFailure)
            db.execSQL("UPDATE performance_urls SET enabled = 1")
            db.execSQL("UPDATE cloudflare_traffic SET date = 'invalid-day'")
            assertTrue(runCatching { importer.validateAndPreview(snapshot, manifest) }.isFailure)
            db.execSQL("UPDATE cloudflare_traffic SET date = '2026-10-11'")
            db.execSQL(
                "INSERT INTO performance_urls (id,url,origin,source,enabled,created_at) " +
                    "VALUES (2,'https://FIXTURE.COM/article/','https://fixture.com','manual',1,'fixture')",
            )
            val counts = manifest.copy(rowCounts = manifest.rowCounts + ("performance_urls" to 2))
            val failure = runCatching { importer.validateAndPreview(snapshot, counts) }.exceptionOrNull()
            assertTrue(failure?.message.orEmpty().contains("duplicate target"))
        }
    }

    private fun encryptedPackage(): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            val counts = manifest.rowCounts.entries.joinToString(",") { "\"${it.key}\":${it.value}" }
            val header = """{"exportVersion":1,"windowsSchemaVersion":11,
                "createdAt":"${manifest.createdAt}","rowCounts":{$counts}}"""
            val entries =
                mapOf(
                    "numeris.db" to snapshot.readBytes(),
                    "manifest.json" to header.toByteArray(),
                    "settings.json" to "{}".toByteArray(),
                    "secrets.json" to "{}".toByteArray(),
                )
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return ExportCrypto.encrypt(output.toByteArray(), "fixture passphrase".toCharArray())
    }
}
