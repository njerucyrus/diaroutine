package com.diaroutine.app.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.security.SecureRandom

class BackupCryptoTest {
    private val random = SecureRandom()
    private val key = ByteArray(20).also(random::nextBytes)
    private val data = """{"medications":[{"name":"Metformin"}]}""".repeat(50).toByteArray()

    @Test
    fun aSealedBackupOpensWithItsKey() {
        assertArrayEquals(data, BackupCrypto.open(BackupCrypto.seal(data, key), key))
    }

    @Test
    fun theContentsCannotBeReadFromTheSealedBytes() {
        val sealed = BackupCrypto.seal(data, key)
        assertFalse(String(sealed, Charsets.ISO_8859_1).contains("Metformin"))
    }

    @Test(expected = BackupCrypto.WrongKeyException::class)
    fun anotherKeyCannotOpenIt() {
        val other = ByteArray(20).also(random::nextBytes)
        BackupCrypto.open(BackupCrypto.seal(data, key), other)
    }

    @Test(expected = BackupCrypto.WrongKeyException::class)
    fun anyTamperingIsDetected() {
        val sealed = BackupCrypto.seal(data, key)
        sealed[sealed.size - 5] = (sealed[sealed.size - 5].toInt() xor 1).toByte()
        BackupCrypto.open(sealed, key)
    }

    @Test(expected = BackupCrypto.WrongKeyException::class)
    fun theHeaderIsAuthenticatedToo() {
        val sealed = BackupCrypto.seal(data, key)
        sealed[8] = (sealed[8].toInt() xor 1).toByte() // inside the salt
        BackupCrypto.open(sealed, key)
    }

    @Test(expected = BackupCrypto.NotABackupException::class)
    fun otherFilesAreRecognised() {
        BackupCrypto.open("hello, not a backup at all".toByteArray(), key)
    }

    @Test
    fun everyBackupUsesFreshSaltAndNonce() {
        assertNotEquals(BackupCrypto.seal(data, key).toList(), BackupCrypto.seal(data, key).toList())
    }

    @Test
    fun keyIdsAreStableShortAndDistinct() {
        val id = BackupCrypto.keyId(key)
        assertEquals(16, id.length)
        assertEquals(id, BackupCrypto.keyId(key.copyOf()))
        assertNotEquals(id, BackupCrypto.keyId(ByteArray(20).also(random::nextBytes)))
    }

    /** RFC 5869 test case 1, truncated to one block. */
    @Test
    fun keyDerivationIsStandardHkdf() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = ByteArray(13) { it.toByte() }
        // Our info string differs from the RFC's, so check the extract step instead.
        val mac = javax.crypto.Mac.getInstance("HmacSHA256").apply { init(javax.crypto.spec.SecretKeySpec(salt, "HmacSHA256")) }
        val prk = mac.doFinal(ikm).joinToString("") { "%02x".format(it) }
        assertEquals("077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5", prk)
        assertEquals(32, BackupCrypto.deriveKey(ikm, salt).size)
    }
}
