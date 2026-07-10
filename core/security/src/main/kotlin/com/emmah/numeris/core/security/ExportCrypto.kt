package com.emmah.numeris.core.security

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object ExportCrypto {
    private val magic = byteArrayOf('N'.code.toByte(), 'M'.code.toByte(), 'R'.code.toByte(), 'X'.code.toByte())

    fun encrypt(
        payload: ByteArray,
        passphrase: CharArray,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val key = deriveKey(passphrase, salt)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(headerPrefix())
            val encrypted = cipher.doFinal(payload)
            ByteBuffer
                .allocate(HEADER_BYTES + encrypted.size)
                .put(magic)
                .put(FORMAT_VERSION)
                .put(salt)
                .put(nonce)
                .put(encrypted)
                .array()
        } finally {
            key.encoded.fill(0)
            salt.fill(0)
            nonce.fill(0)
        }
    }

    fun decrypt(
        packageBytes: ByteArray,
        passphrase: CharArray,
    ): ByteArray {
        require(packageBytes.size > HEADER_BYTES) { "Import package is truncated" }
        val buffer = ByteBuffer.wrap(packageBytes)
        val actualMagic = ByteArray(magic.size).also(buffer::get)
        require(actualMagic.contentEquals(magic)) { "Import package header is invalid" }
        require(buffer.get() == FORMAT_VERSION) { "Import package version is unsupported" }
        val salt = ByteArray(SALT_BYTES).also(buffer::get)
        val nonce = ByteArray(NONCE_BYTES).also(buffer::get)
        val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
        val key = deriveKey(passphrase, salt)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(headerPrefix())
            cipher.doFinal(encrypted)
        } finally {
            actualMagic.fill(0)
            salt.fill(0)
            nonce.fill(0)
            encrypted.fill(0)
            key.encoded.fill(0)
        }
    }

    private fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
    ): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            try {
                SecretKeySpec(bytes, "AES")
            } finally {
                bytes.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }

    private fun headerPrefix(): ByteArray = magic + FORMAT_VERSION

    private const val FORMAT_VERSION: Byte = 1
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val PBKDF2_ITERATIONS = 600_000
    private const val HEADER_BYTES = 4 + 1 + SALT_BYTES + NONCE_BYTES
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
}
