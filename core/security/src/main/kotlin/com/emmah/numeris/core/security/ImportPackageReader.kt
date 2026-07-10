package com.emmah.numeris.core.security

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

data class ImportManifest(
    val exportVersion: Int,
    val windowsSchemaVersion: Int,
    val createdAt: String,
    val rowCounts: Map<String, Long>,
)

data class DecryptedExport(
    val databaseFile: File,
    val manifest: ImportManifest,
    val settingsJson: String,
    val secrets: Map<String, CharArray>,
)

object ImportPackageReader {
    private val allowedEntries = setOf("numeris.db", "manifest.json", "settings.json", "secrets.json")
    private val json = Json { ignoreUnknownKeys = true }

    @Suppress("TooGenericExceptionCaught")
    fun decryptToDirectory(
        packageBytes: ByteArray,
        passphrase: CharArray,
        directory: File,
    ): DecryptedExport {
        val payload = ExportCrypto.decrypt(packageBytes, passphrase)
        try {
            prepareEmptyDirectory(directory)
            extractValidatedZip(payload, directory)
            val database = directory.resolve("numeris.db").requireFile()
            val manifest = readManifest(directory.resolve("manifest.json").requireFile())
            require(manifest.exportVersion == SUPPORTED_VERSION) { "Import manifest version is unsupported" }
            val settings = directory.resolve("settings.json").requireFile().readText()
            val secrets = readSecrets(directory.resolve("secrets.json").requireFile())
            return DecryptedExport(database, manifest, settings, secrets)
        } catch (error: Exception) {
            directory.deleteRecursively()
            throw error
        } finally {
            payload.fill(0)
        }
    }

    private fun extractValidatedZip(
        payload: ByteArray,
        directory: File,
    ) {
        var totalBytes = 0L
        val seen = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(payload)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name in allowedEntries && seen.add(entry.name)) {
                    "Import package contains an invalid entry"
                }
                val target = directory.resolve(entry.name)
                FileOutputStream(target).use { output ->
                    totalBytes += copyLimited(zip, output, MAX_UNCOMPRESSED_BYTES - totalBytes)
                }
                zip.closeEntry()
            }
        }
        require(seen == allowedEntries) { "Import package is incomplete" }
    }

    private fun copyLimited(
        input: InputStream,
        output: OutputStream,
        remaining: Long,
    ): Long {
        var copied = 0L
        val buffer = ByteArray(BUFFER_SIZE)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) return copied
                copied += read
                require(copied <= remaining) { "Import package is too large" }
                output.write(buffer, 0, read)
            }
        } finally {
            buffer.fill(0)
        }
    }

    private fun readManifest(file: File): ImportManifest {
        val root = json.parseToJsonElement(file.readText()).jsonObject
        return ImportManifest(
            exportVersion = root.requiredInt("exportVersion"),
            windowsSchemaVersion = root.requiredInt("windowsSchemaVersion"),
            createdAt = root.requiredString("createdAt"),
            rowCounts = root.requiredObject("rowCounts").mapValues { (_, value) -> value.jsonPrimitive.long },
        )
    }

    private fun readSecrets(file: File): Map<String, CharArray> =
        json
            .parseToJsonElement(file.readText())
            .jsonObject
            .mapValues { (_, value) -> value.jsonPrimitive.content.toCharArray() }

    private fun prepareEmptyDirectory(directory: File) {
        directory.deleteRecursively()
        require(directory.mkdirs()) { "Import workspace could not be created" }
    }

    private fun File.requireFile(): File = apply { require(isFile) { "Import package is incomplete" } }

    private fun JsonObject.requiredInt(name: String) = getValue(name).jsonPrimitive.int

    private fun JsonObject.requiredString(name: String) = getValue(name).jsonPrimitive.content

    private fun JsonObject.requiredObject(name: String) = getValue(name).jsonObject

    private const val SUPPORTED_VERSION = 1
    private const val BUFFER_SIZE = 8 * 1024
    private const val MAX_UNCOMPRESSED_BYTES = 1_000_000_000L
}
