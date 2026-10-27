package com.diaroutine.backup

/**
 * A recovery key: 160 random bits, written as 32 Crockford base32 characters in eight
 * groups of four ("7K2M-Q9XD-…"). Crockford's alphabet leaves out I, L, O and U, and
 * reading is forgiving: case, spaces and dashes don't matter, and O, I and L are read
 * as the digits they're mistaken for.
 */
object RecoveryKey {
    const val BYTES = 20
    private const val CHARS = 32
    private const val GROUP = 4
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    fun format(key: ByteArray): String {
        require(key.size == BYTES) { "A recovery key is $BYTES bytes" }
        val out = StringBuilder(CHARS)
        var buffer = 0
        var bits = 0
        for (byte in key) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                out.append(ALPHABET[(buffer shr (bits - 5)) and 0x1F])
                bits -= 5
            }
        }
        return out.chunked(GROUP).joinToString("-")
    }

    /** The key's bytes, or null when [text] isn't a complete, valid recovery key. */
    fun parse(text: String): ByteArray? {
        val chars = text.uppercase()
            .filterNot { it == '-' || it.isWhitespace() }
            .map {
                when (it) {
                    'O' -> '0'
                    'I', 'L' -> '1'
                    else -> it
                }
            }
        if (chars.size != CHARS) return null
        val out = ByteArray(BYTES)
        var buffer = 0
        var bits = 0
        var index = 0
        for (char in chars) {
            val value = ALPHABET.indexOf(char)
            if (value < 0) return null
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                out[index++] = (buffer shr (bits - 8)).toByte()
                bits -= 8
            }
        }
        return out
    }
}
