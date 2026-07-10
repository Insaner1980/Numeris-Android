package com.emmah.numeris.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException

class ExportCryptoTest {
    @Test fun `round trip preserves payload`() {
        val passphrase = "correct horse battery staple".toCharArray()
        val payload = "numeris fixture".encodeToByteArray()
        try {
            val encrypted = ExportCrypto.encrypt(payload, passphrase)
            assertArrayEquals(payload, ExportCrypto.decrypt(encrypted, passphrase))
        } finally {
            passphrase.fill('\u0000')
            payload.fill(0)
        }
    }

    @Test fun `wrong passphrase fails authentication`() {
        val correct = "correct passphrase".toCharArray()
        val wrong = "wrong passphrase".toCharArray()
        try {
            val encrypted = ExportCrypto.encrypt(byteArrayOf(1, 2, 3), correct)
            assertThrows(AEADBadTagException::class.java) {
                ExportCrypto.decrypt(encrypted, wrong)
            }
        } finally {
            correct.fill('\u0000')
            wrong.fill('\u0000')
        }
    }

    @Test fun `invalid header is rejected before decryption`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExportCrypto.decrypt(ByteArray(40), "passphrase".toCharArray())
        }
    }
}
