package io.github.kmpmail.smtp

import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Tests for [SmtpConfig], [SmtpSecurity], [SmtpCredentials], and the
 * high-level [SmtpClient] paths that don't require a real TCP connection.
 */
class SmtpConfigTest {

    // -------------------------------------------------------------------------
    // SmtpSecurity enum
    // -------------------------------------------------------------------------

    @Test
    fun `SmtpSecurity has three values`() {
        assertEquals(3, SmtpSecurity.entries.size)
        assertNotNull(SmtpSecurity.valueOf("NONE"))
        assertNotNull(SmtpSecurity.valueOf("STARTTLS"))
        assertNotNull(SmtpSecurity.valueOf("TLS"))
    }

    // -------------------------------------------------------------------------
    // SmtpCredentials
    // -------------------------------------------------------------------------

    @Test
    fun `SmtpCredentials stores username and password`() {
        val creds = SmtpCredentials("user@example.com", "s3cr3t")
        assertEquals("user@example.com", creds.username)
        assertEquals("s3cr3t", creds.password)
    }

    @Test
    fun `SmtpCredentials equality`() {
        val a = SmtpCredentials("u", "p")
        val b = SmtpCredentials("u", "p")
        assertEquals(a, b)
        assertNotEquals(a, SmtpCredentials("u", "different"))
    }

    // -------------------------------------------------------------------------
    // SmtpConfig.Builder defaults
    // -------------------------------------------------------------------------

    @Test
    fun `Builder default host is localhost`() {
        assertEquals("localhost", SmtpConfig.Builder().build().host)
    }

    @Test
    fun `Builder default port is 587`() {
        assertEquals(587, SmtpConfig.Builder().build().port)
    }

    @Test
    fun `Builder default security is STARTTLS`() {
        assertEquals(SmtpSecurity.STARTTLS, SmtpConfig.Builder().build().security)
    }

    @Test
    fun `Builder default credentials is null`() {
        assertNull(SmtpConfig.Builder().build().credentials)
    }

    @Test
    fun `Builder default localDomain is localhost`() {
        assertEquals("localhost", SmtpConfig.Builder().build().localDomain)
    }

    @Test
    fun `Builder credentials helper sets SmtpCredentials`() {
        val cfg = SmtpConfig.Builder().apply {
            credentials("alice", "secret")
        }.build()
        assertEquals(SmtpCredentials("alice", "secret"), cfg.credentials)
    }

    @Test
    fun `Builder round-trip`() {
        val cfg = SmtpConfig.Builder().apply {
            host = "mail.example.com"
            port = 465
            security = SmtpSecurity.TLS
            localDomain = "client.example.com"
            credentials("user", "pass")
        }.build()
        assertEquals("mail.example.com", cfg.host)
        assertEquals(465, cfg.port)
        assertEquals(SmtpSecurity.TLS, cfg.security)
        assertEquals("client.example.com", cfg.localDomain)
        assertEquals(SmtpCredentials("user", "pass"), cfg.credentials)
    }

    // -------------------------------------------------------------------------
    // SmtpResponse — transient / permanent failure
    // -------------------------------------------------------------------------

    @Test
    fun `SmtpResponse isPositive for 2xx and 3xx`() {
        assertTrue(SmtpResponse(250, listOf("OK")).isPositive)
        assertTrue(SmtpResponse(354, listOf("go ahead")).isPositive)
    }

    @Test
    fun `SmtpResponse isTransientFailure for 4xx`() {
        val r = SmtpResponse(421, listOf("Service unavailable"))
        assertTrue(r.isTransientFailure)
        assertFalse(r.isPositive)
        assertFalse(r.isPermanentFailure)
    }

    @Test
    fun `SmtpResponse isPermanentFailure for 5xx`() {
        val r = SmtpResponse(550, listOf("User unknown"))
        assertTrue(r.isPermanentFailure)
        assertFalse(r.isPositive)
        assertFalse(r.isTransientFailure)
    }

    @Test
    fun `SmtpResponse message returns last line`() {
        val r = SmtpResponse(250, listOf("first", "last"))
        assertEquals("last", r.message)
    }

    @Test
    fun `SmtpResponse message empty when no lines`() {
        assertEquals("", SmtpResponse(220, emptyList()).message)
    }

    // -------------------------------------------------------------------------
    // SmtpClient — operations before connect
    // -------------------------------------------------------------------------

    @Test
    fun `send before connect throws error`() = runTest {
        val client = SmtpClient { host = "smtp.example.com" }
        assertFailsWith<IllegalStateException> {
            client.send("msg".encodeToByteArray(), "a@b.com", listOf("c@d.com"))
        }
    }

    @Test
    fun `disconnect before connect is a no-op`() = runTest {
        val client = SmtpClient { host = "smtp.example.com" }
        client.disconnect() // must not throw
    }

    // -------------------------------------------------------------------------
    // SmtpClient — withSession factory (tests post-connect paths)
    // -------------------------------------------------------------------------

    @Test
    fun `withSession send delivers message`() = runTest {
        val transport = MockSmtpTransport(listOf(
            "250 OK",   // MAIL FROM
            "250 OK",   // RCPT TO
            "354 go ahead",
            "250 OK",   // accepted
        ))
        val session = SmtpSession(transport)
        val client = SmtpClient.withSession(session)

        client.send("Subject: Hi\r\n\r\nHello".encodeToByteArray(), "a@b.com", listOf("b@c.com"))

        assertTrue(transport.clientLines.any { it.startsWith("MAIL FROM:") })
        assertTrue(transport.clientLines.any { it.startsWith("RCPT TO:") })
    }

    @Test
    fun `withSession disconnect calls quit`() = runTest {
        val transport = MockSmtpTransport(listOf("221 Bye"))
        val session = SmtpSession(transport)
        val client = SmtpClient.withSession(session)
        client.disconnect()
        assertTrue(transport.clientLines.any { it == "QUIT" })
    }

    @Test
    fun `withSession send to multiple recipients`() = runTest {
        val transport = MockSmtpTransport(listOf(
            "250 OK", "250 OK", "250 OK", // MAIL + 2 RCPT
            "354 go ahead",
            "250 OK",
        ))
        val session = SmtpSession(transport)
        val client = SmtpClient.withSession(session)
        client.send("Body".encodeToByteArray(), "from@x.com", listOf("a@b.com", "c@d.com"))
        assertEquals(2, transport.clientLines.count { it.startsWith("RCPT TO:") })
    }
}
