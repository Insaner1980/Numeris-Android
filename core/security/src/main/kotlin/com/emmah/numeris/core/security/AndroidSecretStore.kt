package com.emmah.numeris.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.emmah.numeris.core.model.SecretKey
import com.emmah.numeris.core.model.SecretStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.SecretKey as CryptoSecretKey

class AndroidSecretStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SecretStore {
    private val directory = context.noBackupFilesDir.resolve(STORE_DIRECTORY)
    private val mutex = Mutex()

    override suspend fun put(
        key: SecretKey,
        value: CharArray,
    ): Unit =
        withContext(ioDispatcher) {
            mutex.withLock {
                Files.createDirectories(directory.toPath())
                val plaintext = encode(value)
                try {
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
                    cipher.updateAAD(key.value.toByteArray(StandardCharsets.UTF_8))
                    val encrypted = cipher.doFinal(plaintext)
                    val payload =
                        ByteBuffer
                            .allocate(2 + cipher.iv.size + encrypted.size)
                            .put(FORMAT_VERSION)
                            .put(cipher.iv.size.toByte())
                            .put(cipher.iv)
                            .put(encrypted)
                            .array()
                    writeAtomically(fileFor(key), payload)
                    payload.fill(0)
                } finally {
                    plaintext.fill(0)
                }
            }
        }

    override suspend fun get(key: SecretKey): CharArray? =
        withContext(ioDispatcher) {
            mutex.withLock {
                val file = fileFor(key)
                if (!file.exists()) return@withLock null
                val payload = Files.readAllBytes(file.toPath())
                try {
                    val buffer = ByteBuffer.wrap(payload)
                    require(buffer.get() == FORMAT_VERSION) { "Unsupported secret format" }
                    val nonceSize = buffer.get().toInt() and 0xff
                    require(nonceSize == NONCE_BYTES && buffer.remaining() > nonceSize) { "Corrupt secret payload" }
                    val nonce = ByteArray(nonceSize).also(buffer::get)
                    val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
                    try {
                        val cipher = Cipher.getInstance(TRANSFORMATION)
                        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, nonce))
                        cipher.updateAAD(key.value.toByteArray(StandardCharsets.UTF_8))
                        val plaintext = cipher.doFinal(encrypted)
                        try {
                            decode(plaintext)
                        } finally {
                            plaintext.fill(0)
                        }
                    } finally {
                        nonce.fill(0)
                        encrypted.fill(0)
                    }
                } finally {
                    payload.fill(0)
                }
            }
        }

    override suspend fun contains(key: SecretKey): Boolean =
        withContext(ioDispatcher) {
            mutex.withLock { fileFor(key).isFile }
        }

    override suspend fun delete(key: SecretKey): Unit =
        withContext(ioDispatcher) {
            mutex.withLock { Files.deleteIfExists(fileFor(key).toPath()) }
        }

    override suspend fun clearAll(): Unit =
        withContext(ioDispatcher) {
            mutex.withLock {
                directory.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(FILE_SUFFIX)) Files.deleteIfExists(file.toPath())
                }
            }
        }

    private fun fileFor(key: SecretKey) = directory.resolve(hashName(key.value) + FILE_SUFFIX)

    // Background sync must read credentials without an interactive authentication prompt.
    // The app-private Keystore key protects encrypted files in noBackupFilesDir.
    @Suppress("kotlin:S6288")
    private fun getOrCreateKey(): CryptoSecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? CryptoSecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec
                    .Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    ).setKeySize(KEY_BITS)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun writeAtomically(
        target: java.io.File,
        payload: ByteArray,
    ) {
        val temporary = target.toPath().resolveSibling(target.name + ".tmp")
        Files.newOutputStream(temporary).use { output ->
            output.write(payload)
            output.flush()
        }
        try {
            Files.move(
                temporary,
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun encode(value: CharArray): ByteArray {
        val encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(value))
        return ByteArray(encoded.remaining()).also(encoded::get)
    }

    private fun decode(value: ByteArray): CharArray {
        val decoded = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(value))
        return CharArray(decoded.remaining()).also(decoded::get)
    }

    private fun hashName(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "numeris.secrets.v1"
        const val STORE_DIRECTORY = "secrets-v1"
        const val FILE_SUFFIX = ".nsec"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val NONCE_BYTES = 12
        const val KEY_BITS = 256
        const val FORMAT_VERSION: Byte = 1
    }
}
