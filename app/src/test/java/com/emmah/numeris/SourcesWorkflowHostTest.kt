package com.emmah.numeris

import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.emmah.numeris.core.database.CloudflareTrafficEntity
import com.emmah.numeris.core.database.NumerisDatabase
import com.emmah.numeris.core.designsystem.NumerisTheme
import com.emmah.numeris.core.model.ConnectionId
import com.emmah.numeris.core.model.ConnectionTestResult
import com.emmah.numeris.core.model.SecretKey
import com.emmah.numeris.core.model.SourceId
import com.emmah.numeris.core.model.SourceSyncService
import com.emmah.numeris.core.model.SyncRequest
import com.emmah.numeris.core.model.SyncResult
import com.emmah.numeris.core.network.BingApiClient
import com.emmah.numeris.core.network.NumerisHttpClient
import com.emmah.numeris.core.network.SourceDiscoveryClient
import com.emmah.numeris.data.AndroidImportRepository
import com.emmah.numeris.data.AppPreferencesRepository
import com.emmah.numeris.data.DefaultSyncOrchestrator
import com.emmah.numeris.data.GoogleAuthorizationManager
import com.emmah.numeris.data.ReportRepository
import com.emmah.numeris.data.RoomConnectionRepository
import com.emmah.numeris.data.SourceDiscovery
import com.emmah.numeris.data.WindowsConfigurationTransfer
import com.emmah.numeris.data.WindowsDatabaseImporter
import com.emmah.numeris.feature.sources.SourcesScreen
import com.emmah.numeris.feature.sources.SourcesViewModel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SourcesWorkflowHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var database: NumerisDatabase
    private lateinit var imports: AndroidImportRepository
    private lateinit var model: SourcesViewModel
    private lateinit var client: OkHttpClient
    private val secrets = MemorySecrets()
    private val store = ViewModelStore()
    private var responseCode = 200
    private var responseBody = """{"success":true,"result":[{"id":"zone-1","name":"fixture.com"}]}"""

    @Before fun setUp() {
        val context = compose.activity.applicationContext
        database = testDatabase()
        client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(responseCode)
                        .message("Fixture")
                        .body(responseBody.toResponseBody())
                        .build()
                }.build()
        val service =
            object : SourceSyncService {
                override val source = SourceId.CloudflareZone

                override suspend fun testConnection(connectionId: ConnectionId?) = ConnectionTestResult.Success

                override suspend fun sync(request: SyncRequest) = SyncResult.Success(0)
            }
        val connections = RoomConnectionRepository(database, secrets) { mapOf(service.source to service) }
        imports =
            AndroidImportRepository(
                context,
                WindowsDatabaseImporter(database),
                DefaultSyncOrchestrator(emptyList()),
                WindowsConfigurationTransfer(database, secrets, AppPreferencesRepository(context)),
            )
        val http = NumerisHttpClient(client)
        val discovery =
            SourceDiscovery(
                secrets,
                GoogleAuthorizationManager(context),
                SourceDiscoveryClient(http),
                BingApiClient(http),
            )
        val factory = SourcesViewModel.factory(connections, imports, discovery)
        model = ViewModelProvider(store, factory)[SourcesViewModel::class.java]
        runBlocking {
            database.cloudflareDao().upsertTraffic(
                listOf(CloudflareTrafficEntity("cached.com", "2026-10-11", uniqueVisitors = 17, fetchedAt = "fixture")),
            )
        }
        compose.setContent {
            NumerisTheme {
                SourcesScreen(connections, imports, {}, ReportRepository(database), discovery, {}, viewModel = model)
            }
        }
    }

    @After fun tearDown() {
        compose.runOnUiThread { store.clear() }
        compose.activityRule.scenario.close()
        runBlocking { imports.cancel() }
        database.close()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    @Test fun discoverSaveEditTestAndDisconnectPreserveCachedReports() {
        openCloudflareForm()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("API token").performScrollTo().performTextInput("synthetic-provider-token")
        compose.onNodeWithText("Discover available targets").performScrollTo().performClick()
        awaitState {
            model.state.value.targets
                .isNotEmpty()
        }
        compose.onNode(hasText("fixture.com") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        awaitState { model.state.value.connections.size == 1 }
        val connection =
            model.state.value.connections
                .single()
        val key = SecretKey("cloudflare:${connection.id.value}:api_token")
        assertEquals("synthetic-provider-token", runBlocking { secrets.get(key) }!!.concatToString())

        action("Test connection")
        awaitState { model.state.value.message == "Connection test succeeded." }
        action("Edit")
        compose.onNodeWithText("Zone ID").performTextReplacement("zone-2")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        awaitState {
            model.state.value.connections
                .single()
                .metadata["zoneId"] == "zone-2"
        }
        assertEquals("synthetic-provider-token", runBlocking { secrets.get(key) }!!.concatToString())
        action("Disconnect")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, model.state.value.connections.size)
        action("Disconnect")
        compose.onNodeWithText("Disconnect").performClick()
        awaitState {
            model.state.value.connections
                .isEmpty()
        }
        assertNull(runBlocking { secrets.get(key) })
        assertCachedReportPreserved()
    }

    @Test fun importCanBeCanceledBeforeExplicitReplacementWithoutDeviceAuthentication() {
        inspectImport("windows-report.nmrx")
        compose.onNodeWithText("Replace cached report data?").assertIsDisplayed()
        assertCachedReportPreserved()
        compose.onNodeWithText("Cancel").performClick()
        awaitState { model.state.value.importPreview == null }
        assertCachedReportPreserved()
        inspectImport("windows-report.nmrx")
        compose.onNodeWithText("Replace report data").performClick()
        awaitState {
            model.state.value.message
                ?.startsWith("Imported ") == true
        }
        assertTrue(
            model.state.value.message!!
                .contains("were preserved"),
        )
        val remainingRows =
            runBlocking {
                database.reportSnapshotDao().readCloudflareTraffic("cached.com", "2026-10-01", "2026-10-12")
            }
        assertTrue(remainingRows.isEmpty())
        assertNull(model.state.value.importPreview)
    }

    @Test fun importsConfigurationAfterConfirmation() {
        inspectImport("windows-connections.nmrx")
        compose.onNodeWithText("Import Windows data?").assertIsDisplayed()
        assertNull(runBlocking { database.connectionDao().find("cloudflare:fixture.com") })
        compose.onNodeWithText("Import data").performClick()
        awaitState {
            model.state.value.message
                ?.startsWith("Imported ") == true
        }
        assertTrue(
            model.state.value.message!!
                .contains("Google sign-in is still required."),
        )
        assertNotNull(runBlocking { database.connectionDao().find("cloudflare:fixture.com") })
        assertEquals(
            "synthetic-import-api-key",
            runBlocking {
                secrets.get(SecretKey("cloudflare:cloudflare:fixture.com:api_token"))
            }!!.concatToString(),
        )
        compose.onNodeWithText("synthetic-import-api-key").assertDoesNotExist()
    }

    @Test fun incorrectPassphraseAndDiscoveryFailureKeepExistingReports() {
        inspectFixture("windows-report.nmrx", "incorrect-test-passphrase")
        awaitState { model.state.value.message == "Import package could not be opened." }
        assertNull(model.state.value.importPreview)
        assertCachedReportPreserved()
        responseCode = 403
        openCloudflareForm()
        compose.onNodeWithText("API token").performScrollTo().performTextInput("synthetic-provider-token")
        compose.onNodeWithText("Discover available targets").performScrollTo().performClick()
        awaitState {
            model.state.value.message
                ?.startsWith("Discovery failed.") == true
        }
        assertTrue(
            model.state.value.targets
                .isEmpty(),
        )
        assertTrue(
            model.state.value.connections
                .isEmpty(),
        )
        assertCachedReportPreserved()
    }

    @Test fun discoveredWebAndBingTargetsCanBeSavedWithoutDuplicates() {
        responseBody = """{"success":true,"result":[{"site_tag":"tag-one","host":"web.example"}]}"""
        openSource("Cloudflare Web Analytics")
        compose.onNodeWithText("Account ID").performTextInput("account-one")
        compose.onNodeWithText("API token").performScrollTo().performTextInput("synthetic-web-token")
        val mappings = compose.onNodeWithText("Web Analytics targets: domain=site tag, one per line")
        mappings.performScrollTo().performTextInput("invalid mapping")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        awaitState { model.state.value.message == "Connection could not be saved." }
        assertTrue(
            model.state.value.connections
                .isEmpty(),
        )
        openSource("Cloudflare Web Analytics")
        compose.onNodeWithText("Account ID").performTextInput("account-one")
        compose.onNodeWithText("API token").performScrollTo().performTextInput("synthetic-web-token")
        compose.onNodeWithText("Discover available targets").performScrollTo().performClick()
        awaitState {
            model.state.value.targets
                .isNotEmpty()
        }
        repeat(2) {
            compose.onNode(hasText("web.example") and hasClickAction()).performScrollTo().performClick()
        }
        compose.onNodeWithText("Save").performScrollTo().performClick()
        awaitState { model.state.value.connections.size == 1 }
        assertEquals(
            "web.example=tag-one",
            model.state.value.connections
                .single()
                .metadata["siteMappings"],
        )

        responseBody = """{"d":[{"Url":"https://bing.example/"}]}"""
        openSource("Bing Webmaster")
        compose.onNodeWithText("API key").performScrollTo().performTextInput("synthetic-bing-key")
        compose.onNodeWithText("Discover available targets").performScrollTo().performClick()
        awaitState {
            model.state.value.targets
                .isNotEmpty()
        }
        repeat(2) {
            compose.onNode(hasText("bing.example") and hasClickAction()).performScrollTo().performClick()
        }
        compose.onNodeWithText("Save").performScrollTo().performClick()
        awaitState { model.state.value.connections.size == 2 }
        val bing =
            model.state.value.connections
                .single { it.source == SourceId.Bing }
        assertEquals("https://bing.example/", bing.metadata["sites"])
        val storedKey = runBlocking { secrets.get(SecretKey("bing:bing:api_key")) }!!.concatToString()
        assertEquals("synthetic-bing-key", storedKey)
        assertCachedReportPreserved()
    }

    private fun awaitState(condition: () -> Boolean) {
        compose.waitUntil(10_000) {
            compose.mainClock.advanceTimeByFrame()
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

    private fun assertCachedReportPreserved() {
        val rows =
            runBlocking {
                database.reportSnapshotDao().readCloudflareTraffic("cached.com", "2026-10-01", "2026-10-12")
            }
        assertEquals(17L, rows.single().uniqueVisitors)
    }

    private fun openCloudflareForm() {
        openSource("Cloudflare Zone Analytics")
    }

    private fun openSource(title: String) {
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Add source") and hasClickAction())
        compose
            .onAllNodes(hasText("Add source") and hasClickAction())
            .onFirst()
            .performScrollTo()
            .performClick()
        compose.onNode(hasText(title) and hasClickAction()).performClick()
    }

    private fun action(label: String) {
        val actions = hasContentDescription("Actions for fixture.com")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(actions)
        compose.onNode(actions).performClick()
        compose.onNodeWithText(label).performClick()
    }

    private fun inspectImport(name: String) {
        inspectFixture(name, "synthetic fixture pässphrase")
        awaitState { model.state.value.importPreview != null && !model.state.value.importing }
    }

    private fun inspectFixture(
        name: String,
        passphrase: String,
    ) {
        val file = File(compose.activity.cacheDir, name)
        testFixture(name).use { input -> file.outputStream().use(input::copyTo) }
        compose.runOnIdle { model.inspectImport(Uri.fromFile(file), passphrase.toCharArray()) }
    }
}
