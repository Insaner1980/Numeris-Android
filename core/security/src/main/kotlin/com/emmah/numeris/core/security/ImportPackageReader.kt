package com.emmah.numeris.core.security

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
            val metadata = extractValidatedZip(payload, directory)
            try {
                val database = directory.resolve("numeris.db").requireFile()
                val manifest = readManifest(metadata.getValue("manifest.json").toString(Charsets.UTF_8))
                require(manifest.exportVersion == SUPPORTED_VERSION) { "Import manifest version is unsupported" }
                val settings = metadata.getValue("settings.json").toString(Charsets.UTF_8)
                val secrets = readSecrets(metadata.getValue("secrets.json").toString(Charsets.UTF_8))
                return DecryptedExport(database, manifest, settings, secrets)
            } finally {
                metadata.values.forEach { it.fill(0) }
            }
        } catch (error: Exception) {
            directory.deleteRecursively()
            throw error
        } finally {
            payload.fill(0)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun extractValidatedZip(
        payload: ByteArray,
        directory: File,
    ): Map<String, ByteArray> {
        var totalBytes = 0L
        val seen = mutableSetOf<String>()
        val metadata = mutableMapOf<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(payload)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && entry.name in allowedEntries && seen.add(entry.name)) {
                        "Import package contains an invalid entry"
                    }
                    totalBytes +=
                        extractEntry(
                            zip,
                            entry.name,
                            directory,
                            metadata,
                            MAX_UNCOMPRESSED_BYTES - totalBytes,
                        )
                    zip.closeEntry()
                }
            }
            require(seen == allowedEntries) { "Import package is incomplete" }
            return metadata
        } catch (error: Exception) {
            metadata.values.forEach { it.fill(0) }
            throw error
        }
    }

    private fun extractEntry(
        zip: ZipInputStream,
        name: String,
        directory: File,
        metadata: MutableMap<String, ByteArray>,
        remaining: Long,
    ): Long =
        if (name == "numeris.db") {
            FileOutputStream(directory.resolve(name)).use { copyLimited(zip, it, remaining) }
        } else {
            ByteArrayOutputStream().use { output ->
                val copied = copyLimited(zip, output, minOf(MAX_METADATA_BYTES, remaining))
                metadata[name] = output.toByteArray()
                copied
            }
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

    private fun readManifest(text: String): ImportManifest {
        val root = json.parseToJsonElement(text).jsonObject
        return ImportManifest(
            exportVersion = root.requiredInt("exportVersion"),
            windowsSchemaVersion = root.requiredInt("windowsSchemaVersion"),
            createdAt = root.requiredString("createdAt"),
            rowCounts = root.requiredObject("rowCounts").mapValues { (_, value) -> value.jsonPrimitive.long },
        )
    }

    private fun readSecrets(text: String): Map<String, CharArray> =
        json
            .parseToJsonElement(text)
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
    private const val MAX_METADATA_BYTES = 1_000_000L
}
