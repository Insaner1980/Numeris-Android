package com.emmah.numeris

import android.net.Uri
import androidx.room3.executeSQL
import androidx.room3.useWriterConnection
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.numeris.core.database.CloudflareTrafficEntity
import com.emmah.numeris.core.database.ConnectionEntity
import com.emmah.numeris.core.model.Period
import com.emmah.numeris.core.model.SecretKey
import com.emmah.numeris.data.AndroidImportRepository
import com.emmah.numeris.data.AppPreferencesRepository
import com.emmah.numeris.data.DefaultSyncOrchestrator
import com.emmah.numeris.data.WindowsConfigurationTransfer
import com.emmah.numeris.data.WindowsDatabaseImporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class WindowsConfigurationTest {
    @Test fun fullExportImportsConnectionsKeysAndPreferencesWithoutPlaintextSecretFiles() = checkTransfer("success")

    @Test fun failedReportCommitRestoresKeysPreferencesAndNonemptyCache() = checkTransfer("database-failure")

    @Test fun newRepositoryRecoversAnInterruptedCredentialReplacement() = checkTransfer("interrupted")

    private fun checkTransfer(mode: String) =
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val preferences = AppPreferencesRepository(context)
            val originalPreferences = preferences.preferences.first()
            val file = File(context.cacheDir, "windows-connections-${UUID.randomUUID()}.nmrx")
            instrumentation.context.assets
                .open("windows-connections.nmrx")
                .use { input -> file.outputStream().use(input::copyTo) }
            val database = testDatabase()
            var interruptKey: SecretKey? = null
            val secrets = MemorySecrets { key -> if (key == interruptKey) throw SimulatedProcessInterruption() }
            val key = SecretKey("cloudflare:cloudflare:fixture.com:api_token")
            val journal = SecretKey("numeris:windows-transfer:journal")

            fun imports() =
                AndroidImportRepository(
                    context,
                    WindowsDatabaseImporter(database),
                    DefaultSyncOrchestrator(emptyList()),
                    WindowsConfigurationTransfer(database, secrets, preferences),
                )
            try {
                preferences.selectDomain("all")
                preferences.selectPeriod(Period.Last7Days)
                preferences.selectPage("overview")
                database.connectionDao().upsert(listOf(ConnectionEntity("android:kept", "google_analytics", "configured")))
                database.cloudflareDao().upsertTraffic(
                    listOf(CloudflareTrafficEntity("kept.com", "2026-10-10", uniqueVisitors = 999, fetchedAt = "2026-10-11T00:00:00Z")),
                )
                secrets.put(key, "previous-api-key".toCharArray())
                val repository = imports()
                val preview = repository.inspect(Uri.fromFile(file), "synthetic fixture pässphrase".toCharArray())
                assertTrue(preview.includesConfiguration)
                assertTrue(preview.connections > 0 && preview.secrets > 0)
                assertEquals(listOf("numeris.db"), File(context.cacheDir, "numeris-import/${preview.id}").list()!!.toList())
                when (mode) {
                    "database-failure" ->
                        database.useWriterConnection { connection ->
                            connection.executeSQL(
                                "CREATE TRIGGER transfer_failure BEFORE INSERT ON cloudflare_traffic BEGIN SELECT RAISE(ABORT,'synthetic failure'); END",
                            )
                        }
                    "interrupted" -> interruptKey = key
                }
                if (mode == "success") {
                    val result = repository.replace(preview.id)
                    assertEquals(4L, result.importedRows)
                    assertEquals(preview.secrets, result.importedSecrets)
                    assertEquals("synthetic-import-api-key", secrets.get(key)!!.concatToString())
                    assertEquals("fixture.com", preferences.preferences.first().selectedDomain)
                    assertEquals(Period.Last30Days, preferences.preferences.first().selectedPeriod)
                    assertEquals("search", preferences.preferences.first().lastPage)
                    assertNotNull(database.connectionDao().find("cloudflare:fixture.com"))
                } else {
                    val failure = runCatching { repository.replace(preview.id) }.exceptionOrNull()
                    assertNotNull(failure)
                    if (mode == "interrupted") {
                        assertTrue(failure is SimulatedProcessInterruption)
                        assertTrue(secrets.contains(journal))
                        interruptKey = null
                        imports().recoverInterruptedTransfer()
                    }
                    assertEquals("previous-api-key", secrets.get(key)!!.concatToString())
                    assertNull(database.connectionDao().find("cloudflare:fixture.com"))
                    assertEquals(
                        999L,
                        database
                            .reportSnapshotDao()
                            .readCloudflareTraffic("all", "2026-10-01", "2026-10-11")
                            .single()
                            .uniqueVisitors,
                    )
                    assertEquals("all", preferences.preferences.first().selectedDomain)
                    assertEquals(Period.Last7Days, preferences.preferences.first().selectedPeriod)
                }
                assertFalse(secrets.contains(journal))
                assertNotNull(database.connectionDao().find("android:kept"))
                assertEquals(originalPreferences.biometricLockEnabled, preferences.preferences.first().biometricLockEnabled)
                assertEquals(originalPreferences.backgroundSyncEnabled, preferences.preferences.first().backgroundSyncEnabled)
                assertTrue(file.isFile)
            } finally {
                database.close()
                secrets.clearAll()
                preferences.selectDomain(originalPreferences.selectedDomain)
                preferences.selectPeriod(originalPreferences.selectedPeriod)
                preferences.selectPage(originalPreferences.lastPage)
                file.delete()
            }
        }
}

private class SimulatedProcessInterruption : Error()
