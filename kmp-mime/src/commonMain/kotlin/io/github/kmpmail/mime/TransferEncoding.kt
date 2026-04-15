package io.github.kmpmail.mime

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * MIME Content-Transfer-Encoding values (RFC 2045 §6).
 */
enum class TransferEncoding(val label: String) {
    SEVEN_BIT("7bit"),
    EIGHT_BIT("8bit"),
    BINARY("binary"),
    QUOTED_PRINTABLE("quoted-printable"),
    BASE64("base64");

    @OptIn(ExperimentalEncodingApi::class)
    fun decode(input: ByteArray): ByteArray = when (this) {
        SEVEN_BIT, EIGHT_BIT, BINARY -> input
        BASE64 -> Base64.decode(removeWhitespace(input))
        QUOTED_PRINTABLE -> decodeQuotedPrintable(input)
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun encode(input: ByteArray): ByteArray = when (this) {
        SEVEN_BIT, EIGHT_BIT, BINARY -> input
        BASE64 -> foldBase64(Base64.encode(input))
        QUOTED_PRINTABLE -> encodeQuotedPrintable(input)
    }

    companion object {
        fun parse(value: String): TransferEncoding = when (value.trim().lowercase()) {
            "7bit"              -> SEVEN_BIT
            "8bit"              -> EIGHT_BIT
            "binary"            -> BINARY
            "quoted-printable"  -> QUOTED_PRINTABLE
            "base64"            -> BASE64
            else                -> SEVEN_BIT
        }
    }
}

// RFC 2045 §6.8: base64 lines may contain whitespace; strip before decoding.
private fun removeWhitespace(input: ByteArray): ByteArray =
    input.filter { it != '\r'.code.toByte() && it != '\n'.code.toByte() && it != ' '.code.toByte() }
        .toByteArray()

// Fold base64 output at 76 characters per line (RFC 2045 §6.8).
private fun foldBase64(encoded: String): ByteArray =
    encoded.chunked(76).joinToString("\r\n").encodeToByteArray()

private fun decodeQuotedPrintable(input: ByteArray): ByteArray {
    val out = mutableListOf<Byte>()
    var i = 0
    while (i < input.size) {
        val b = input[i]
        if (b == '='.code.toByte() && i + 1 < input.size) {
            val next = input[i + 1]
            // Soft line break: =\r\n or =\n
            if (next == '\r'.code.toByte()) {
                i += if (i + 2 < input.size && input[i + 2] == '\n'.code.toByte()) 3 else 2
                continue
            }
            if (next == '\n'.code.toByte()) { i += 2; continue }
            // Encoded byte: =XX
            if (i + 2 < input.size) {
                val hi = input[i + 1].toInt().toChar()
                val lo = input[i + 2].toInt().toChar()
                if (hi.isHexDigit() && lo.isHexDigit()) {
                    out.add("$hi$lo".toInt(16).toByte())
                    i += 3
                    continue
                }
            }
        }
        out.add(b)
        i++
    }
    return out.toByteArray()
}

private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun encodeQuotedPrintable(input: ByteArray): ByteArray {
    val sb = StringBuilder()
    var lineLen = 0

    fun appendRaw(s: String) {
        if (lineLen + s.length > 75) { sb.append("=\r\n"); lineLen = 0 }
        sb.append(s); lineLen += s.length
    }

    var i = 0
    while (i < input.size) {
        val c = input[i].toInt() and 0xFF
        when {
            c == '\r'.code && i + 1 < input.size && input[i + 1].toInt() == '\n'.code -> {
                // CRLF passes through; resets line length
                sb.append("\r\n"); lineLen = 0; i += 2; continue
            }
            c == '\n'.code -> { sb.append("\r\n"); lineLen = 0 }
            c == '\t'.code || c == ' '.code -> appendRaw(c.toChar().toString())
            c in 0x21..0x7E && c != '='.code -> appendRaw(c.toChar().toString())
            else -> appendRaw("=" + c.toHex2())
        }
        i++
    }
    return sb.toString().encodeToByteArray()
}

private fun Int.toHex2(): String {
    val hi = (this shr 4) and 0xF
    val lo = this and 0xF
    return "${hi.digitToChar(16).uppercaseChar()}${lo.digitToChar(16).uppercaseChar()}"
}
