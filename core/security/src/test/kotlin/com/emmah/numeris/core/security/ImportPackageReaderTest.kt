package com.emmah.numeris.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ImportPackageReaderTest {
    @Test
    fun `decrypts validated package and reads manifest`() {
        val passphrase = "long import passphrase".toCharArray()
        val packageBytes = ExportCrypto.encrypt(validZip(), passphrase)
        val directory = File(System.getProperty("java.io.tmpdir"), "numeris-import-test-${System.nanoTime()}")
        try {
            val result = ImportPackageReader.decryptToDirectory(packageBytes, passphrase, directory)
            assertEquals(7, result.manifest.windowsSchemaVersion)
            assertEquals(2L, result.manifest.rowCounts["connections"])
            assertEquals("secret", result.secrets["bing:bing:api_key"]?.concatToString())
        } finally {
            packageBytes.fill(0)
            passphrase.fill('\u0000')
            directory.deleteRecursively()
        }
    }

    @Test
    fun `rejects unexpected zip entry and removes plaintext directory`() {
        val passphrase = "long import passphrase".toCharArray()
        val packageBytes = ExportCrypto.encrypt(validZip("../escape"), passphrase)
        val directory = File(System.getProperty("java.io.tmpdir"), "numeris-import-test-${System.nanoTime()}")
        try {
            runCatching { ImportPackageReader.decryptToDirectory(packageBytes, passphrase, directory) }
            assertFalse(directory.exists())
        } finally {
            packageBytes.fill(0)
            passphrase.fill('\u0000')
            directory.deleteRecursively()
        }
    }

    private fun validZip(extraEntry: String? = null): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            write(zip, "numeris.db", "sqlite")
            write(
                zip,
                "manifest.json",
                """
                {
                  "exportVersion": 1,
                  "windowsSchemaVersion": 7,
                  "createdAt": "2026-07-10T12:00:00Z",
                  "rowCounts": { "connections": 2 }
                }
                """.trimIndent(),
            )
            write(zip, "settings.json", "{}")
            write(zip, "secrets.json", "{\"bing:bing:api_key\":\"secret\"}")
            if (extraEntry != null) write(zip, extraEntry, "bad")
        }
        return output.toByteArray()
    }

    private fun write(
        zip: ZipOutputStream,
        name: String,
        value: String,
    ) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(value.encodeToByteArray())
        zip.closeEntry()
    }
}
