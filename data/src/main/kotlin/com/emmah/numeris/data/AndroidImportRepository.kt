package com.emmah.numeris.data

import android.content.Context
import android.net.Uri
import com.emmah.numeris.core.security.DecryptedExport
import com.emmah.numeris.core.security.ImportPackageReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID

data class ImportPreview(
    val id: String,
    val createdAt: String,
    val windowsSchemaVersion: Int,
    val domains: List<String>,
    val connections: Int,
    val rowCounts: Map<String, Long>,
    val secrets: Int = 0,
    val includesConfiguration: Boolean = false,
)

data class ImportResult(
    val importedRows: Long,
    val importedSecrets: Int,
)

class AndroidImportRepository(
    private val context: Context,
    private val databaseImporter: WindowsDatabaseImporter,
    private val syncOrchestrator: DefaultSyncOrchestrator,
    private val configurationTransfer: WindowsConfigurationTransfer? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private var pending: PendingImport? = null

    init {
        File(context.cacheDir, "numeris-import").deleteRecursively()
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun inspect(
        uri: Uri,
        passphrase: CharArray,
    ): ImportPreview =
        withContext(ioDispatcher) {
            mutex.withLock {
                clearPending()
                var packageBytes = byteArrayOf()
                try {
                    packageBytes = readPackage(uri)
                    val id = UUID.randomUUID().toString()
                    val workspace = File(context.cacheDir, "numeris-import/$id")
                    val decrypted = ImportPackageReader.decryptToDirectory(packageBytes, passphrase, workspace)
                    try {
                        validateSecretKeys(decrypted.secrets.keys)
                        val configuration = WindowsTransferConfiguration.parse(decrypted.settingsJson)
                        if (configuration != null) {
                            require(configurationTransfer != null) { "Configuration transfer is unavailable" }
                            require(decrypted.secrets.keys.all { it in configuration.secretKeys }) {
                                "Unassigned imported secret"
                            }
                        }
                        val snapshot = databaseImporter.validateAndPreview(decrypted.databaseFile, decrypted.manifest)
                        pending = PendingImport(id, workspace, decrypted, configuration)
                        ImportPreview(
                            id = id,
                            createdAt = decrypted.manifest.createdAt,
                            windowsSchemaVersion = decrypted.manifest.windowsSchemaVersion,
                            domains = snapshot.domains,
                            connections = configuration?.connections?.size ?: snapshot.connections,
                            rowCounts = snapshot.rowCounts,
                            secrets = if (configuration == null) 0 else decrypted.secrets.size,
                            includesConfiguration = configuration != null,
                        )
                    } catch (error: Exception) {
                        decrypted.secrets.values.forEach { it.fill('\u0000') }
                        workspace.deleteRecursively()
                        throw error
                    }
                } finally {
                    packageBytes.fill(0)
                    passphrase.fill('\u0000')
                }
            }
        }

    suspend fun replace(previewId: String): ImportResult =
        withContext(ioDispatcher) {
            mutex.withLock {
                val current = requireNotNull(pending?.takeIf { it.id == previewId }) { "Import preview has expired" }
                try {
                    val preview =
                        syncOrchestrator.exclusive {
                            val validated =
                                databaseImporter.validateAndPreview(
                                    current.export.databaseFile,
                                    current.export.manifest,
                                )
                            val configuration = current.configuration
                            if (configuration == null) {
                                databaseImporter.replaceFromSnapshot(
                                    current.export.databaseFile,
                                    current.export.manifest,
                                )
                            } else {
                                requireNotNull(configurationTransfer).replace(
                                    current.id,
                                    configuration,
                                    current.export.secrets,
                                ) {
                                    databaseImporter.replaceFromSnapshot(
                                        current.export.databaseFile,
                                        current.export.manifest,
                                        configuration.connections,
                                        current.id,
                                    )
                                }
                            }
                            validated
                        }
                    ImportResult(
                        importedRows =
                            preview.rowCounts.values
                                .sum(),
                        importedSecrets = if (current.configuration == null) 0 else current.export.secrets.size,
                    )
                } finally {
                    clearPending()
                }
            }
        }

    suspend fun cancel() = withContext(ioDispatcher) { mutex.withLock { clearPending() } }

    suspend fun recoverInterruptedTransfer() {
        withContext(ioDispatcher) {
            mutex.withLock { syncOrchestrator.exclusive { configurationTransfer?.recover() } }
        }
    }

    private fun readPackage(uri: Uri): ByteArray {
        val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "Import file could not be opened" }
        return input.use(::readLimited)
    }

    private fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) return output.toByteArray()
                require(output.size().toLong() + read <= MAX_PACKAGE_BYTES) { "Import package is too large" }
                output.write(buffer, 0, read)
            }
        } finally {
            buffer.fill(0)
        }
    }

    private fun validateSecretKeys(keys: Set<String>) {
        require(keys.all { key -> allowedSecretKeys.any(key::matches) }) { "Import contains an unsupported secret" }
    }

    private fun clearPending() {
        pending
            ?.export
            ?.secrets
            ?.values
            ?.forEach { it.fill('\u0000') }
        pending?.workspace?.deleteRecursively()
        pending = null
    }

    private data class PendingImport(
        val id: String,
        val workspace: File,
        val export: DecryptedExport,
        val configuration: WindowsTransferConfiguration?,
    )

    private companion object {
        const val BUFFER_SIZE = 8 * 1024
        const val MAX_PACKAGE_BYTES = 1_100_000_000L
        val allowedSecretKeys =
            listOf(
                Regex("cloudflare:[A-Za-z0-9._:-]+:api_token"),
                Regex("web_analytics:[A-Za-z0-9._:-]+:api_token"),
                Regex("crux:crux:api_key"),
                Regex("pagespeed:pagespeed:api_key"),
                Regex("bing:bing:api_key"),
            )
    }
}
