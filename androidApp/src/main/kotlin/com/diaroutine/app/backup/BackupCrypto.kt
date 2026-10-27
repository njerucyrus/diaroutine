package com.diaroutine.app.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Seals a backup so only the holder of its recovery key can open it.
 *
 * Format: "DRB1" | version (1 byte) | salt (16) | nonce (12) | AES-256-GCM ciphertext and tag.
 * The AES key is HKDF-SHA256 of the 160-bit recovery key with the per-backup salt; the
 * header is authenticated too. A recovery key already has full entropy, so a fast KDF
 * is enough — there is no password to stretch.
 */
object BackupCrypto {
    class WrongKeyException : Exception("The recovery key doesn't open this backup")
    class NotABackupException : Exception("Not a DiaRoutine backup")

    private val MAGIC = "DRB1".toByteArray()
    private const val VERSION: Byte = 1
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val HEADER_BYTES = 4 + 1 + SALT_BYTES + NONCE_BYTES
    private val INFO = "DiaRoutine backup v1".toByteArray()

    fun seal(plain: ByteArray, recoveryKey: ByteArray, random: SecureRandom = SecureRandom()): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = MAGIC + byteArrayOf(VERSION) + salt + nonce
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(deriveKey(recoveryKey, salt), "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        return header + cipher.doFinal(gzip(plain))
    }

    fun open(sealed: ByteArray, recoveryKey: ByteArray): ByteArray {
        if (sealed.size < HEADER_BYTES + TAG_BITS / 8 ||
            !sealed.copyOfRange(0, MAGIC.size).contentEquals(MAGIC) ||
            sealed[MAGIC.size] != VERSION
        ) {
            throw NotABackupException()
        }
        val salt = sealed.copyOfRange(MAGIC.size + 1, MAGIC.size + 1 + SALT_BYTES)
        val nonce = sealed.copyOfRange(HEADER_BYTES - NONCE_BYTES, HEADER_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(deriveKey(recoveryKey, salt), "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(sealed, 0, HEADER_BYTES)
        val compressed = try {
            cipher.doFinal(sealed, HEADER_BYTES, sealed.size - HEADER_BYTES)
        } catch (_: AEADBadTagException) {
            throw WrongKeyException()
        }
        return gunzip(compressed)
    }

    /** A short public fingerprint of a key, stored beside the backup to tell which key made it. */
    fun keyId(recoveryKey: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest("DiaRoutine key id".toByteArray() + recoveryKey)
            .take(8)
            .joinToString("") { "%02x".format(it) }

    /** HKDF-SHA256 (RFC 5869) with one output block: a 256-bit key. */
    internal fun deriveKey(secret: ByteArray, salt: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val pseudoRandomKey = mac.doFinal(secret)
        mac.init(SecretKeySpec(pseudoRandomKey, "HmacSHA256"))
        mac.update(INFO)
        mac.update(1)
        return mac.doFinal()
    }

    private fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

    private fun gunzip(bytes: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
}
