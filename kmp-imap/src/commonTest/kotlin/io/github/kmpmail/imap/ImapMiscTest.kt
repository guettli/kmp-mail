package io.github.kmpmail.imap

import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Tests for ImapConfig/ImapSecurity, ImapBadException, and
 * ImapClient paths that don't require a real TCP connection.
 */
class ImapMiscTest {

    // -------------------------------------------------------------------------
    // ImapSecurity enum
    // -------------------------------------------------------------------------

    @Test
    fun `ImapSecurity has three values`() {
        assertEquals(3, ImapSecurity.entries.size)
        assertNotNull(ImapSecurity.valueOf("NONE"))
        assertNotNull(ImapSecurity.valueOf("STARTTLS"))
        assertNotNull(ImapSecurity.valueOf("TLS"))
    }

    // -------------------------------------------------------------------------
    // ImapConfig / ImapConfig.Builder defaults
    // -------------------------------------------------------------------------

    @Test
    fun `Builder default host is localhost`() =
        assertEquals("localhost", ImapConfig.Builder().build().host)

    @Test
    fun `Builder default port is 993`() =
        assertEquals(993, ImapConfig.Builder().build().port)

    @Test
    fun `Builder default security is TLS`() =
        assertEquals(ImapSecurity.TLS, ImapConfig.Builder().build().security)

    @Test
    fun `Builder default username and password are empty`() {
        val cfg = ImapConfig.Builder().build()
        assertEquals("", cfg.username)
        assertEquals("", cfg.password)
    }

    @Test
    fun `Builder round-trip`() {
        val cfg = ImapConfig.Builder().apply {
            host = "imap.example.com"
            port = 143
            security = ImapSecurity.STARTTLS
            username = "alice@example.com"
            password = "s3cr3t"
        }.build()
        assertEquals("imap.example.com", cfg.host)
        assertEquals(143, cfg.port)
        assertEquals(ImapSecurity.STARTTLS, cfg.security)
        assertEquals("alice@example.com", cfg.username)
        assertEquals("s3cr3t", cfg.password)
    }

    // -------------------------------------------------------------------------
    // ImapException hierarchy
    // -------------------------------------------------------------------------

    @Test
    fun `ImapBadException stores command and message`() {
        val ex = ImapBadException("A001", "Syntax error")
        assertTrue(ex.message!!.contains("A001"))
        assertTrue(ex.message!!.contains("Syntax error"))
    }

    @Test
    fun `ImapNoException stores command and message`() {
        val ex = ImapNoException("A002", "No such mailbox")
        assertTrue(ex.message!!.contains("A002"))
        assertTrue(ex.message!!.contains("No such mailbox"))
    }

    @Test
    fun `ImapException is an Exception`() {
        val ex = ImapException("test error")
        assertIs<Exception>(ex)
        assertEquals("test error", ex.message)
    }

    // -------------------------------------------------------------------------
    // ImapClient — operations before connect throw
    // -------------------------------------------------------------------------

    @Test
    fun `select before connect throws`() = runTest {
        val client = ImapClient { host = "imap.example.com" }
        assertFailsWith<IllegalStateException> { client.select("INBOX") }
    }

    @Test
    fun `examine before connect throws`() = runTest {
        val client = ImapClient { host = "imap.example.com" }
        assertFailsWith<IllegalStateException> { client.examine("INBOX") }
    }

    @Test
    fun `search before connect throws`() = runTest {
        val client = ImapClient { host = "imap.example.com" }
        assertFailsWith<IllegalStateException> { client.search("UNSEEN") }
    }

    @Test
    fun `uidFetch before connect throws`() = runTest {
        val client = ImapClient { host = "imap.example.com" }
        assertFailsWith<IllegalStateException> { client.uidFetch("1", "(FLAGS)") }
    }

    @Test
    fun `uidStore before connect throws`() = runTest {
        val client = ImapClient { host = "imap.example.com" }
        assertFailsWith<IllegalStateException> { client.uidStore("1", "+FLAGS", "(\\Seen)") }
    }

    @Test
    fun `noop before connect throws`() = runTest {
        val client = ImapClient { host = "imap.example.com" }
        assertFailsWith<IllegalStateException> { client.noop() }
    }

    @Test
    fun `close before connect throws`() = runTest {
        val client = ImapClient { host = "imap.example.com" }
        assertFailsWith<IllegalStateException> { client.close() }
    }

    @Test
    fun `disconnect before connect is a no-op`() = runTest {
        val client = ImapClient { host = "imap.example.com" }
        client.disconnect() // must not throw
    }

    // -------------------------------------------------------------------------
    // ImapClient — withSession factory tests
    // -------------------------------------------------------------------------

    @Test
    fun `withSession select delegates to session`() = runTest {
        val transport = MockImapTransport(listOf(
            "* 10 EXISTS",
            "* 0 RECENT",
            "A001 OK SELECT completed",
        ))
        val session = ImapSession(transport)
        val client = ImapClient.withSession(session)
        val info = client.select("INBOX")
        assertEquals("INBOX", info.name)
        assertEquals(10, info.exists)
    }

    @Test
    fun `withSession search returns UIDs`() = runTest {
        val transport = MockImapTransport(listOf(
            "* SEARCH 1 3 5",
            "A001 OK UID SEARCH completed",
        ))
        val session = ImapSession(transport)
        val client = ImapClient.withSession(session)
        val uids = client.search("UNSEEN")
        assertEquals(listOf(1L, 3L, 5L), uids)
    }

    @Test
    fun `withSession noop sends NOOP`() = runTest {
        val transport = MockImapTransport(listOf("A001 OK NOOP completed"))
        val session = ImapSession(transport)
        val client = ImapClient.withSession(session)
        client.noop()
        assertTrue(transport.clientLines.any { it.endsWith("NOOP") })
    }

    @Test
    fun `withSession disconnect calls logout`() = runTest {
        val transport = MockImapTransport(listOf(
            "* BYE Logging out",
            "A001 OK LOGOUT completed",
        ))
        val session = ImapSession(transport)
        val client = ImapClient.withSession(session)
        client.disconnect()
        assertTrue(transport.clientLines.any { it.endsWith("LOGOUT") })
    }

    @Test
    fun `withSession uidStore sends UID STORE`() = runTest {
        val transport = MockImapTransport(listOf("A001 OK UID STORE completed"))
        val session = ImapSession(transport)
        val client = ImapClient.withSession(session)
        client.uidStore("1234", "+FLAGS", "(\\Seen)")
        assertTrue(transport.clientLines.any { it.contains("UID STORE") })
    }

    @Test
    fun `withSession uidFetch returns results`() = runTest {
        val transport = MockImapTransport(listOf(
            "* 1 FETCH (UID 1234 FLAGS (\\Seen))",
            "A001 OK UID FETCH completed",
        ))
        val session = ImapSession(transport)
        val client = ImapClient.withSession(session)
        val results = client.uidFetch("1234", "(UID FLAGS)")
        assertEquals(1, results.size)
    }
}
