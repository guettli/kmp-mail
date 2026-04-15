package io.github.kmpmail.smtp

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SmtpSessionTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun session(vararg serverLines: String) =
        SmtpSession(MockSmtpTransport(serverLines.toList()))

    private fun tlsSession(vararg serverLines: String): Pair<SmtpSession, MockTlsTransport> {
        val transport = MockTlsTransport(serverLines.toList())
        return SmtpSession(transport) to transport
    }

    // -------------------------------------------------------------------------
    // Greeting
    // -------------------------------------------------------------------------

    @Test
    fun `greet succeeds on 220`() = runTest {
        val sess = session("220 smtp.example.com ESMTP")
        val r = sess.greet()
        assertEquals(220, r.code)
    }

    @Test
    fun `greet throws on non-220`() = runTest {
        val sess = session("421 Service unavailable")
        assertFailsWith<SmtpException> { sess.greet() }
    }

    // -------------------------------------------------------------------------
    // EHLO
    // -------------------------------------------------------------------------

    @Test
    fun `ehlo parses capabilities`() = runTest {
        val sess = session(
            "220 smtp.example.com ESMTP",
            "250-smtp.example.com",
            "250-STARTTLS",
            "250-AUTH PLAIN LOGIN",
            "250 SIZE 10240000",
        )
        sess.greet()
        val caps = sess.ehlo("client.example.com")
        assertTrue(caps.supportsStartTls)
        assertTrue(caps.supportsAuth("PLAIN"))
        assertTrue(caps.supportsAuth("LOGIN"))
        assertFalse(caps.supportsAuth("GSSAPI"))
    }

    @Test
    fun `ehlo sends correct command`() = runTest {
        val transport = MockSmtpTransport(listOf(
            "220 smtp.example.com ESMTP",
            "250-smtp.example.com",
            "250 OK",
        ))
        val sess = SmtpSession(transport)
        sess.greet()
        sess.ehlo("client.example.com")
        assertEquals("EHLO client.example.com", transport.clientLines.first())
    }

    // -------------------------------------------------------------------------
    // STARTTLS (from fixture: ehlo-starttls.txt)
    // -------------------------------------------------------------------------

    @Test
    fun `startTls sends command and triggers TLS upgrade`() = runTest {
        val (sess, transport) = tlsSession(
            "220 mail.example.com ESMTP",
            // EHLO
            "250-mail.example.com",
            "250-STARTTLS",
            "250 OK",
            // STARTTLS
            "220 2.0.0 Ready to start TLS",
            // EHLO after TLS
            "250-mail.example.com",
            "250-AUTH PLAIN LOGIN",
            "250 OK",
        )
        sess.greet()
        sess.ehlo("client.example.com")
        sess.startTls("client.example.com")

        assertTrue(transport.tlsUpgraded)
        // After startTls the session re-issues EHLO — check new caps
        assertTrue(sess.capabilities.supportsAuth("PLAIN"))
    }

    @Test
    fun `startTls throws when server rejects`() = runTest {
        val (sess, _) = tlsSession(
            "220 smtp.example.com ESMTP",
            "250-smtp.example.com",
            "250-STARTTLS",
            "250 OK",
            "454 TLS not available",
        )
        sess.greet()
        sess.ehlo("client.example.com")
        assertFailsWith<SmtpException> { sess.startTls("client.example.com") }
    }

    // -------------------------------------------------------------------------
    // AUTH PLAIN (from fixture: auth-plain.txt)
    // -------------------------------------------------------------------------

    @Test
    fun `AUTH PLAIN sends correct base64 token`() = runTest {
        val transport = MockSmtpTransport(listOf(
            "220 smtp.example.com ESMTP",
            "250-smtp.example.com",
            "250 AUTH PLAIN LOGIN",
            "235 2.7.0 Authentication successful",
        ))
        val sess = SmtpSession(transport)
        sess.greet()
        sess.ehlo()
        sess.auth(SmtpAuth.Plain("tim", "tanstaaftanstaaf"))

        val authLine = transport.clientLines.last()
        assertTrue(authLine.startsWith("AUTH PLAIN "))
        // Decode and verify token
        val token = authLine.removePrefix("AUTH PLAIN ")
        val decoded = decodeBase64(token)
        // format: \0username\0password
        val parts = decoded.split('\u0000')
        assertEquals(3, parts.size)
        assertEquals("tim", parts[1])
        assertEquals("tanstaaftanstaaf", parts[2])
    }

    @Test
    fun `AUTH PLAIN throws on 535`() = runTest {
        val sess = session(
            "220 smtp.example.com ESMTP",
            "250 AUTH PLAIN",
            "535 authentication failed",
        )
        sess.greet()
        sess.ehlo()
        assertFailsWith<SmtpException> {
            sess.auth(SmtpAuth.Plain("user", "wrongpass"))
        }
    }

    // -------------------------------------------------------------------------
    // AUTH LOGIN (from fixture: auth-login.txt)
    // -------------------------------------------------------------------------

    @Test
    fun `AUTH LOGIN performs two-step challenge exchange`() = runTest {
        val transport = MockSmtpTransport(listOf(
            "220 smtp.example.com ESMTP",
            "250-smtp.example.com",
            "250 AUTH PLAIN LOGIN",
            "334 ",           // challenge 1 (no text, bare 334)
            "334 ",           // challenge 2
            "235 Authenticated",
        ))
        val sess = SmtpSession(transport)
        sess.greet()
        sess.ehlo()
        sess.auth(SmtpAuth.Login("tim", "tanstaaftanstaaf"))

        val lines = transport.clientLines
        assertEquals("AUTH LOGIN", lines[lines.size - 3])
        assertEquals("dGlt", lines[lines.size - 2])                    // base64("tim")
        assertEquals("dGFuc3RhYWZ0YW5zdGFhZg==", lines[lines.size - 1]) // base64("tanstaaftanstaaf")
    }

    // -------------------------------------------------------------------------
    // MAIL / RCPT / DATA
    // -------------------------------------------------------------------------

    @Test
    fun `full send session matches expected commands`() = runTest {
        val transport = MockSmtpTransport(listOf(
            "220 smtp.example.com ESMTP",
            "250-smtp.example.com",
            "250 AUTH PLAIN",
            "235 Authenticated",
            "250 OK",   // MAIL FROM
            "250 OK",   // RCPT TO
            "354 go ahead",
            "250 OK",   // message accepted
            "221 BYE",
        ))
        val sess = SmtpSession(transport)
        sess.greet()
        sess.ehlo()
        sess.auth(SmtpAuth.Plain("user", "pass"))
        sess.mailFrom("alice@example.com")
        sess.rcptTo("bob@example.com")
        sess.data("Subject: Hi\r\n\r\nHello!".encodeToByteArray())
        sess.quit()

        val lines = transport.clientLines
        assertTrue(lines.any { it.startsWith("MAIL FROM:<alice@example.com>") })
        assertTrue(lines.any { it == "RCPT TO:<bob@example.com>" })
        assertTrue(lines.any { it == "DATA" })
        assertTrue(lines.any { it == "QUIT" })
    }

    @Test
    fun `dot-stuffing is applied to message body`() = runTest {
        val transport = MockSmtpTransport(listOf(
            "220 smtp.example.com ESMTP",
            "250 OK",
            "250 OK",   // MAIL FROM
            "250 OK",   // RCPT TO
            "354 go ahead",
            "250 OK",
        ))
        val sess = SmtpSession(transport)
        sess.greet()
        sess.ehlo()
        sess.mailFrom("a@b.com")
        sess.rcptTo("c@d.com")
        // Body line starting with '.' must be stuffed
        sess.data("Line1\r\n.starts-with-dot\r\nLine3\r\n".encodeToByteArray())

        val raw = transport.clientRaw.first().decodeToString()
        assertTrue(raw.contains("..starts-with-dot"), "Expected dot-stuffed line, got:\n$raw")
        assertTrue(raw.endsWith(".\r\n"), "Expected terminating dot")
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private fun decodeBase64(s: String): String {
        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
        return kotlin.io.encoding.Base64.decode(s).decodeToString()
    }
}
