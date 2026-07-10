package com.emmah.numeris.data

import android.content.Context
import android.net.Uri
import com.emmah.numeris.core.model.SecretKey
import com.emmah.numeris.core.model.SecretStore
import com.emmah.numeris.core.security.DecryptedExport
import com.emmah.numeris.core.security.ImportPackageReader
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
)

data class ImportResult(
    val importedRows: Long,
    val importedSecrets: Int,
)

class AndroidImportRepository(
    private val context: Context,
    private val databaseImporter: WindowsDatabaseImporter,
    private val secretStore: SecretStore,
    private val preferencesRepository: AppPreferencesRepository,
) {
    private val mutex = Mutex()
    private var pending: PendingImport? = null

    @Suppress("TooGenericExceptionCaught")
    suspend fun inspect(
        uri: Uri,
        passphrase: CharArray,
    ): ImportPreview =
        mutex.withLock {
            clearPending()
            val packageBytes = readPackage(uri)
            try {
                val id = UUID.randomUUID().toString()
                val workspace = File(context.cacheDir, "numeris-import/$id")
                val decrypted = ImportPackageReader.decryptToDirectory(packageBytes, passphrase, workspace)
                try {
                    validateSecretKeys(decrypted.secrets.keys)
                    val snapshot = databaseImporter.validateAndPreview(decrypted.databaseFile, decrypted.manifest)
                    pending = PendingImport(id, workspace, decrypted)
                    ImportPreview(
                        id = id,
                        createdAt = decrypted.manifest.createdAt,
                        windowsSchemaVersion = decrypted.manifest.windowsSchemaVersion,
                        domains = snapshot.domains,
                        connections = snapshot.connections,
                        rowCounts = snapshot.rowCounts,
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

    suspend fun replace(previewId: String): ImportResult =
        mutex.withLock {
            val current = requireNotNull(pending?.takeIf { it.id == previewId }) { "Import preview has expired" }
            try {
                databaseImporter.replaceFromSnapshot(current.export.databaseFile)
                secretStore.clearAll()
                current.export.secrets.forEach { (key, value) -> secretStore.put(SecretKey(key), value) }
                preferencesRepository.importWindowsSettings(current.export.settingsJson)
                ImportResult(
                    importedRows =
                        current.export.manifest.rowCounts.values
                            .sum(),
                    importedSecrets = current.export.secrets.size,
                )
            } finally {
                clearPending()
            }
        }

    suspend fun cancel() = mutex.withLock { clearPending() }

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
