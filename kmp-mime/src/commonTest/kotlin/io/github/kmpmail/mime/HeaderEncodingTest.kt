package io.github.kmpmail.mime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeaderEncodingTest {

    // --- B-encoding (base64) ---

    @Test
    fun `decode B-encoded UTF-8 word`() {
        // =?UTF-8?B?SGVsbG8gV29ybGQ=?= → "Hello World"
        assertEquals("Hello World", HeaderEncoding.decode("=?UTF-8?B?SGVsbG8gV29ybGQ=?="))
    }

    @Test
    fun `decode B-encoded word from fixture encoded-word-b64`() {
        // Subject from encoded-word-b64.eml
        val encoded = "=?UTF-8?B?SWYgeW91IGNhbiByZWFkIHRoaXMgeW91IHVuZGVyc3RhbmQgdGhlIGV4YW1wbGUu?="
        val decoded = HeaderEncoding.decode(encoded)
        assertEquals("If you can read this you understand the example.", decoded)
    }

    // --- Q-encoding ---

    @Test
    fun `decode Q-encoded word with underscore as space`() {
        // =?ISO-8859-1?Q?Keld_J=F8rn_Simonsen?=
        val decoded = HeaderEncoding.decode("=?ISO-8859-1?Q?Keld_J=F8rn_Simonsen?=")
        assertTrue(decoded.startsWith("Keld J"))
        assertTrue(decoded.endsWith("rn Simonsen"))
    }

    @Test
    fun `decode Q-encoded UTF-8 with multi-byte sequences`() {
        // =?UTF-8?Q?Caf=C3=A9?= → "Café"
        assertEquals("Café", HeaderEncoding.decode("=?UTF-8?Q?Caf=C3=A9?="))
    }

    // --- Mixed plain + encoded ---

    @Test
    fun `plain text is returned unchanged`() {
        assertEquals("Hello World", HeaderEncoding.decode("Hello World"))
    }

    @Test
    fun `encoded word embedded in plain text`() {
        val result = HeaderEncoding.decode("Hello =?UTF-8?B?V29ybGQ=?=!")
        assertEquals("Hello World!", result)
    }

    // --- Adjacent encoded words (RFC 2047 §6.2) ---

    @Test
    fun `adjacent encoded words are concatenated without whitespace`() {
        // Two encoded words with only whitespace between them: whitespace is suppressed.
        val input = "=?UTF-8?B?SGVsbG8=?= =?UTF-8?B?V29ybGQ=?="
        assertEquals("HelloWorld", HeaderEncoding.decode(input))
    }

    // --- encodeIfNeeded ---

    @Test
    fun `encodeIfNeeded returns ASCII unchanged`() {
        assertEquals("Plain subject", HeaderEncoding.encodeIfNeeded("Plain subject"))
    }

    @Test
    fun `encodeIfNeeded encodes non-ASCII and round-trips`() {
        val original = "Héllo Wörld"
        val encoded  = HeaderEncoding.encodeIfNeeded(original)
        assertTrue(encoded.startsWith("=?"))
        assertEquals(original, HeaderEncoding.decode(encoded))
    }
}
