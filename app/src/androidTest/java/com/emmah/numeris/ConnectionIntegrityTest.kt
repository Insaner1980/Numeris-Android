package com.emmah.numeris

import com.emmah.numeris.core.database.CloudflareTrafficEntity
import com.emmah.numeris.core.model.ConnectionId
import com.emmah.numeris.core.model.SaveConnectionCommand
import com.emmah.numeris.core.model.SecretKey
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.data.RoomConnectionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionIntegrityTest {
    @Test fun metadataFailureRestoresSecretAndTargetsAndDisconnectPreservesCachedReports() =
        runBlocking {
            val database = testDatabase()
            val secrets = MemorySecrets()
            val repository = RoomConnectionRepository(database, secrets) { emptyMap() }
            val first = ConnectionId("web:first")
            val key = SecretKey("web_analytics:${first.value}:api_token")
            try {
                val initial =
                    SaveConnectionCommand(
                        first,
                        SourceId.CloudflareWebAnalytics,
                        "First",
                        mapOf("accountId" to "account-first", "siteMappings" to "first.com=first-tag"),
                        mapOf(key to "original-fixture".toCharArray()),
                    )
                repository.save(initial)
                repository.save(
                    SaveConnectionCommand(
                        ConnectionId("web:second"),
                        SourceId.CloudflareWebAnalytics,
                        "Second",
                        mapOf("accountId" to "account-second", "siteMappings" to "second.com=second-tag"),
                        emptyMap(),
                    ),
                )
                database.cloudflareDao().upsertTraffic(
                    listOf(
                        CloudflareTrafficEntity("first.com", "2026-10-11", uniqueVisitors = 7, fetchedAt = "fixture"),
                    ),
                )
                val failure =
                    runCatching {
                        repository.save(
                            initial.copy(
                                metadata = initial.metadata + ("siteMappings" to "second.com=new-tag"),
                                secrets = mapOf(key to "replacement-fixture".toCharArray()),
                            ),
                        )
                    }.exceptionOrNull()
                assertTrue(failure is IllegalArgumentException)
                assertEquals("original-fixture", secrets.get(key)!!.concatToString())
                assertTrue(
                    database
                        .connectionDao()
                        .find(first.value)!!
                        .config!!
                        .contains("first.com"),
                )
                assertEquals(
                    setOf("first.com", "second.com"),
                    database
                        .webAnalyticsDao()
                        .webAnalyticsSites()
                        .map { it.domain }
                        .toSet(),
                )
                repository.delete(first)
                assertNull(database.connectionDao().find(first.value))
                assertNull(secrets.get(key))
                assertNotNull(
                    database.reportSnapshotDao().readCloudflareTraffic("all", "2026-10-01", "2026-10-11").single(),
                )
                assertEquals(listOf("second.com"), database.webAnalyticsDao().webAnalyticsSites().map { it.domain })
            } finally {
                database.close()
                secrets.clearAll()
            }
        }
}
