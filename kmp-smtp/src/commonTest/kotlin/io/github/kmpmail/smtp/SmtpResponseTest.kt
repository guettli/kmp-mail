package io.github.kmpmail.smtp

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SmtpResponseTest {

    // Reads responses via SmtpSession.readResponse() through MockSmtpTransport.

    private suspend fun readResponse(vararg lines: String): SmtpResponse {
        val transport = MockSmtpTransport(lines.toList())
        return SmtpSession(transport).readResponse()
    }

    @Test
    fun `parse single-line 220 greeting`() = runTest {
        val r = readResponse("220 smtp.example.com ESMTP")
        assertEquals(220, r.code)
        assertEquals("smtp.example.com ESMTP", r.message)
        assertEquals(1, r.lines.size)
    }

    @Test
    fun `parse single-line 250 OK`() = runTest {
        val r = readResponse("250 OK")
        assertEquals(250, r.code)
        assertEquals("OK", r.message)
    }

    @Test
    fun `parse multi-line 250 EHLO response`() = runTest {
        val r = readResponse(
            "250-smtp.example.com",
            "250-STARTTLS",
            "250-AUTH PLAIN LOGIN",
            "250 OK",
        )
        assertEquals(250, r.code)
        assertEquals(4, r.lines.size)
        assertEquals("smtp.example.com", r.lines[0])
        assertEquals("STARTTLS", r.lines[1])
        assertEquals("AUTH PLAIN LOGIN", r.lines[2])
        assertEquals("OK", r.lines[3])
    }

    @Test
    fun `parse 354 data start`() = runTest {
        val r = readResponse("354 go ahead")
        assertEquals(354, r.code)
        assertTrue(r.isPositive)
    }

    @Test
    fun `parse 535 auth failure`() = runTest {
        val r = readResponse("535 authentication failed")
        assertEquals(535, r.code)
        assertTrue(r.isPermanentFailure)
    }

    @Test
    fun `parse 334 challenge with no text`() = runTest {
        // AUTH LOGIN sends bare 334 with no text (as per Angus Mail SMTPLoginHandler)
        val r = readResponse("334 ")
        assertEquals(334, r.code)
    }

    @Test
    fun `response with empty text fragment`() = runTest {
        val r = readResponse("250 ")
        assertEquals(250, r.code)
        assertEquals("", r.message)
    }
}
