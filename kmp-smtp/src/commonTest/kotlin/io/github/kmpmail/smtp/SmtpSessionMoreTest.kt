package io.github.kmpmail.smtp

import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Additional [SmtpSession] tests for error paths not covered by [SmtpSessionTest].
 */
class SmtpSessionMoreTest {

    // -------------------------------------------------------------------------
    // capabilities before EHLO
    // -------------------------------------------------------------------------

    @Test
    fun `capabilities before EHLO throws`() = runTest {
        val sess = SmtpSession(MockSmtpTransport(emptyList()))
        assertFailsWith<IllegalStateException> {
            sess.capabilities // no EHLO yet
        }
    }

    // -------------------------------------------------------------------------
    // startTls with non-TLS-upgradeable transport
    // -------------------------------------------------------------------------

    @Test
    fun `startTls on plain transport throws error`() = runTest {
        // MockSmtpTransport does NOT implement TlsUpgradeable
        val transport = MockSmtpTransport(listOf(
            "220 smtp.example.com ESMTP",
            "250-smtp.example.com",
            "250-STARTTLS",
            "250 OK",
            "220 Ready to start TLS",   // server says OK, but transport can't upgrade
        ))
        val sess = SmtpSession(transport)
        sess.greet()
        sess.ehlo()
        assertFailsWith<IllegalStateException> {
            sess.startTls()
        }
    }

    // -------------------------------------------------------------------------
    // mailFrom failure
    // -------------------------------------------------------------------------

    @Test
    fun `mailFrom throws on non-250`() = runTest {
        val sess = SmtpSession(MockSmtpTransport(listOf(
            "220 smtp.example.com",
            "250 OK",
            "550 User not found",
        )))
        sess.greet()
        sess.ehlo()
        assertFailsWith<SmtpException> { sess.mailFrom("bad@example.com") }
    }

    // -------------------------------------------------------------------------
    // rcptTo failure
    // -------------------------------------------------------------------------

    @Test
    fun `rcptTo throws on non-250`() = runTest {
        val sess = SmtpSession(MockSmtpTransport(listOf(
            "220 smtp.example.com",
            "250 OK",
            "250 OK",   // MAIL FROM
            "550 No such user",
        )))
        sess.greet()
        sess.ehlo()
        sess.mailFrom("a@b.com")
        assertFailsWith<SmtpException> { sess.rcptTo("nobody@example.com") }
    }

    // -------------------------------------------------------------------------
    // data — server rejects 354
    // -------------------------------------------------------------------------

    @Test
    fun `data throws when server rejects DATA command`() = runTest {
        val sess = SmtpSession(MockSmtpTransport(listOf(
            "220 smtp.example.com",
            "250 OK",
            "250 OK", "250 OK",
            "554 Transaction failed",
        )))
        sess.greet()
        sess.ehlo()
        sess.mailFrom("a@b.com")
        sess.rcptTo("b@c.com")
        assertFailsWith<SmtpException> { sess.data("hello".encodeToByteArray()) }
    }

    // -------------------------------------------------------------------------
    // data — server rejects after message submission
    // -------------------------------------------------------------------------

    @Test
    fun `data throws when final 250 not received`() = runTest {
        val sess = SmtpSession(MockSmtpTransport(listOf(
            "220 smtp.example.com",
            "250 OK",
            "250 OK", "250 OK",
            "354 go ahead",
            "552 Message too large",
        )))
        sess.greet()
        sess.ehlo()
        sess.mailFrom("a@b.com")
        sess.rcptTo("b@c.com")
        assertFailsWith<SmtpException> { sess.data("hello".encodeToByteArray()) }
    }

    // -------------------------------------------------------------------------
    // Multi-line response
    // -------------------------------------------------------------------------

    @Test
    fun `readResponse handles multi-line 250 response`() = runTest {
        val transport = MockSmtpTransport(listOf(
            "250-first line",
            "250-second line",
            "250 third line",
        ))
        val sess = SmtpSession(transport)
        val r = sess.readResponse()
        assertEquals(250, r.code)
        assertEquals(listOf("first line", "second line", "third line"), r.lines)
    }

    // -------------------------------------------------------------------------
    // ehlo throws on non-250
    // -------------------------------------------------------------------------

    @Test
    fun `ehlo throws when server returns non-250`() = runTest {
        val sess = SmtpSession(MockSmtpTransport(listOf(
            "220 smtp.example.com",
            "500 EHLO not supported",
        )))
        sess.greet()
        assertFailsWith<SmtpException> { sess.ehlo() }
    }
}
