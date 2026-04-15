package io.github.kmpmail.mime

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * RFC 2047 encoded-word encode/decode.
 *
 * Encoded-word syntax: `=?charset?encoding?encoded-text?=`
 * Encoding is `B` (base64) or `Q` (quoted-printable variant).
 *
 * Adjacent encoded-words separated only by linear whitespace are decoded and concatenated
 * without the intervening whitespace (RFC 2047 §6.2).
 */
object HeaderEncoding {
    private val ENCODED_WORD_RE = Regex("""=\?([^?*\s]+)(?:\*[^?]*)?\?([BbQq])\?([^?]*)\?=""")

    // Matches two encoded-words separated only by whitespace (for concatenation rule).
    private val ADJACENT_RE = Regex("""(\?=)\s+(=\?)""")

    fun decode(value: String): String {
        // Strip whitespace between adjacent encoded-words before replacing each word.
        val collapsed = ADJACENT_RE.replace(value, "$1$2")
        return ENCODED_WORD_RE.replace(collapsed) { mr ->
            val charset  = mr.groupValues[1]
            val encoding = mr.groupValues[2].uppercase()
            val text     = mr.groupValues[3]
            decodeWord(charset, encoding, text)
        }
    }

    /** Encodes [value] as a B-encoded word if it contains non-ASCII characters. */
    fun encodeIfNeeded(value: String): String =
        if (value.all { it.code < 128 }) value else encodeB(value, "UTF-8")

    @OptIn(ExperimentalEncodingApi::class)
    private fun encodeB(value: String, charset: String): String {
        val encoded = Base64.encode(value.encodeToByteArray())
        return "=?$charset?B?$encoded?="
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeWord(charset: String, encoding: String, text: String): String {
        val bytes = when (encoding) {
            "B" -> Base64.decode(text)
            "Q" -> decodeQ(text)
            else -> text.encodeToByteArray()
        }
        // Decode as UTF-8 for UTF-8/US-ASCII/ISO-8859-1 (best-effort for non-UTF-8 charsets).
        return bytes.decodeToString()
    }

    private fun decodeQ(text: String): ByteArray {
        val out = mutableListOf<Byte>()
        var i = 0
        while (i < text.length) {
            when (val ch = text[i]) {
                '_'  -> { out.add(0x20); i++ }
                '='  -> {
                    if (i + 2 < text.length) {
                        val hex = text.substring(i + 1, i + 3)
                        out.add(hex.toInt(16).toByte())
                        i += 3
                    } else i++
                }
                else -> { out.add(ch.code.toByte()); i++ }
            }
        }
        return out.toByteArray()
    }
}
