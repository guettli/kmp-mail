package io.github.kmpmail.mime

import kotlin.test.*

class TransferEncodingTest {

    // -------------------------------------------------------------------------
    // TransferEncoding.parse — all branches
    // -------------------------------------------------------------------------

    @Test
    fun `parse 7bit`() = assertEquals(TransferEncoding.SEVEN_BIT, TransferEncoding.parse("7bit"))

    @Test
    fun `parse 8bit`() = assertEquals(TransferEncoding.EIGHT_BIT, TransferEncoding.parse("8bit"))

    @Test
    fun `parse binary`() = assertEquals(TransferEncoding.BINARY, TransferEncoding.parse("binary"))

    @Test
    fun `parse quoted-printable`() =
        assertEquals(TransferEncoding.QUOTED_PRINTABLE, TransferEncoding.parse("quoted-printable"))

    @Test
    fun `parse base64`() = assertEquals(TransferEncoding.BASE64, TransferEncoding.parse("base64"))

    @Test
    fun `parse unknown value falls back to SEVEN_BIT`() =
        assertEquals(TransferEncoding.SEVEN_BIT, TransferEncoding.parse("x-unknown"))

    @Test
    fun `parse is case insensitive`() {
        assertEquals(TransferEncoding.BASE64, TransferEncoding.parse("BASE64"))
        assertEquals(TransferEncoding.QUOTED_PRINTABLE, TransferEncoding.parse("Quoted-Printable"))
    }

    @Test
    fun `parse trims surrounding whitespace`() =
        assertEquals(TransferEncoding.BASE64, TransferEncoding.parse("  base64  "))

    // -------------------------------------------------------------------------
    // decode — pass-through encodings
    // -------------------------------------------------------------------------

    @Test
    fun `SEVEN_BIT decode returns identity`() {
        val data = "hello".encodeToByteArray()
        assertContentEquals(data, TransferEncoding.SEVEN_BIT.decode(data))
    }

    @Test
    fun `EIGHT_BIT decode returns identity`() {
        val data = byteArrayOf(0x80.toByte(), 0xFF.toByte())
        assertContentEquals(data, TransferEncoding.EIGHT_BIT.decode(data))
    }

    @Test
    fun `BINARY decode returns identity`() {
        val data = byteArrayOf(0, 1, 2, 3)
        assertContentEquals(data, TransferEncoding.BINARY.decode(data))
    }

    // -------------------------------------------------------------------------
    // encode — pass-through encodings
    // -------------------------------------------------------------------------

    @Test
    fun `SEVEN_BIT encode returns identity`() {
        val data = "hello".encodeToByteArray()
        assertContentEquals(data, TransferEncoding.SEVEN_BIT.encode(data))
    }

    @Test
    fun `EIGHT_BIT encode returns identity`() {
        val data = byteArrayOf(0x80.toByte())
        assertContentEquals(data, TransferEncoding.EIGHT_BIT.encode(data))
    }

    @Test
    fun `BINARY encode returns identity`() {
        val data = byteArrayOf(1, 2, 3)
        assertContentEquals(data, TransferEncoding.BINARY.encode(data))
    }

    // -------------------------------------------------------------------------
    // Base64 round-trip
    // -------------------------------------------------------------------------

    @Test
    fun `BASE64 encode then decode round-trips`() {
        val original = "Hello, World!".encodeToByteArray()
        val encoded = TransferEncoding.BASE64.encode(original)
        val decoded = TransferEncoding.BASE64.decode(encoded)
        assertContentEquals(original, decoded)
    }

    @Test
    fun `BASE64 decode tolerates embedded whitespace`() {
        val original = "test".encodeToByteArray()
        val base64WithNewlines = "dGVzdA==\r\n".encodeToByteArray()
        val decoded = TransferEncoding.BASE64.decode(base64WithNewlines)
        assertContentEquals(original, decoded)
    }

    // -------------------------------------------------------------------------
    // QP round-trip
    // -------------------------------------------------------------------------

    @Test
    fun `QUOTED_PRINTABLE encode then decode round-trips ASCII`() {
        val text = "Hello, World!".encodeToByteArray()
        val encoded = TransferEncoding.QUOTED_PRINTABLE.encode(text)
        val decoded = TransferEncoding.QUOTED_PRINTABLE.decode(encoded)
        assertContentEquals(text, decoded)
    }

    @Test
    fun `QUOTED_PRINTABLE encodes non-ASCII bytes`() {
        val input = byteArrayOf(0xC3.toByte(), 0xA9.toByte()) // é in UTF-8
        val encoded = TransferEncoding.QUOTED_PRINTABLE.encode(input).decodeToString()
        assertTrue(encoded.contains("=C3") || encoded.contains("=c3"))
    }

    @Test
    fun `QUOTED_PRINTABLE decode handles soft line breaks CRLF`() {
        val encoded = "Hello=\r\nWorld".encodeToByteArray()
        val decoded = TransferEncoding.QUOTED_PRINTABLE.decode(encoded).decodeToString()
        assertEquals("HelloWorld", decoded)
    }

    @Test
    fun `QUOTED_PRINTABLE decode handles soft line breaks LF only`() {
        val encoded = "Hello=\nWorld".encodeToByteArray()
        val decoded = TransferEncoding.QUOTED_PRINTABLE.decode(encoded).decodeToString()
        assertEquals("HelloWorld", decoded)
    }
}
