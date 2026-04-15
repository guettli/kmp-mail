package io.github.kmpmail.imap

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Unit tests for [ImapSession] using [MockImapTransport].
 *
 * Server transcripts are taken from RFC 3501 example exchanges.
 */
class ImapSessionTest {

    // -------------------------------------------------------------------------
    // Greeting
    // -------------------------------------------------------------------------

    @Test
    fun `readGreeting returns untagged OK`() = runTest {
        val t = MockImapTransport(listOf("* OK Dovecot ready."))
        val sess = ImapSession(t)
        val g = sess.readGreeting()
        assertEquals("OK", g.keyword)
    }

    @Test
    fun `readGreeting throws on BYE`() = runTest {
        val t = MockImapTransport(listOf("* BYE Too many connections"))
        val sess = ImapSession(t)
        assertFailsWith<ImapException> { sess.readGreeting() }
    }

    // -------------------------------------------------------------------------
    // CAPABILITY
    // -------------------------------------------------------------------------

    @Test
    fun `capability parses server capabilities`() = runTest {
        val t = MockImapTransport(listOf(
            "* CAPABILITY IMAP4rev1 STARTTLS AUTH=PLAIN IDLE",
            "A001 OK CAPABILITY completed",
        ))
        val sess = ImapSession(t)
        val caps = sess.capability()
        assertTrue("IMAP4REV1" in caps)
        assertTrue("IDLE" in caps)
        assertTrue("AUTH=PLAIN" in caps)
        assertEquals("A001 CAPABILITY", t.clientLines.first())
    }

    // -------------------------------------------------------------------------
    // LOGIN
    // -------------------------------------------------------------------------

    @Test
    fun `login sends correct command and succeeds`() = runTest {
        val t = MockImapTransport(listOf(
            "* CAPABILITY IMAP4rev1 AUTH=PLAIN",
            "A001 OK LOGIN completed",
        ))
        val sess = ImapSession(t)
        sess.login("user@example.com", "secret")
        assertEquals("""A001 LOGIN "user@example.com" "secret"""", t.clientLines.first())
    }

    @Test
    fun `login throws ImapNoException on NO response`() = runTest {
        val t = MockImapTransport(listOf("A001 NO [AUTHENTICATIONFAILED] Bad credentials"))
        val sess = ImapSession(t)
        assertFailsWith<ImapNoException> { sess.login("user", "wrong") }
    }

    // -------------------------------------------------------------------------
    // SELECT
    // -------------------------------------------------------------------------

    @Test
    fun `select parses mailbox info`() = runTest {
        val t = MockImapTransport(listOf(
            "* 172 EXISTS",
            "* 1 RECENT",
            "* OK [UNSEEN 12] Message 12 is first unseen",
            "* OK [UIDVALIDITY 3857529045] UIDs valid",
            "* OK [UIDNEXT 4392] Predicted next UID",
            "* FLAGS (\\Answered \\Flagged \\Deleted \\Seen \\Draft)",
            "* OK [PERMANENTFLAGS (\\Deleted \\Seen \\*)] Limited",
            "A001 OK [READ-WRITE] SELECT completed",
        ))
        val sess = ImapSession(t)
        val info = sess.select("INBOX")
        assertEquals("INBOX", info.name)
        assertTrue(info.readWrite)
        assertEquals(172, info.exists)
        assertEquals(1, info.recent)
        assertEquals(12, info.unseen)
        assertEquals(3857529045L, info.uidValidity)
        assertEquals(4392L, info.uidNext)
        assertTrue(info.flags.contains("\\Seen"))
        assertTrue(info.permanentFlags.contains("\\Deleted"))
    }

    // -------------------------------------------------------------------------
    // UID SEARCH
    // -------------------------------------------------------------------------

    @Test
    fun `search returns list of UIDs`() = runTest {
        val t = MockImapTransport(listOf(
            "* SEARCH 2 5 7 11",
            "A001 OK UID SEARCH completed",
        ))
        val sess = ImapSession(t)
        val uids = sess.search("UNSEEN")
        assertEquals(listOf(2L, 5L, 7L, 11L), uids)
        assertEquals("A001 UID SEARCH UNSEEN", t.clientLines.first())
    }

    @Test
    fun `search returns empty list when no matches`() = runTest {
        val t = MockImapTransport(listOf(
            "* SEARCH",
            "A001 OK UID SEARCH completed",
        ))
        val sess = ImapSession(t)
        val uids = sess.search("UNSEEN")
        assertTrue(uids.isEmpty())
    }

    // -------------------------------------------------------------------------
    // UID FETCH
    // -------------------------------------------------------------------------

    @Test
    fun `uidFetch returns FETCH responses`() = runTest {
        val t = MockImapTransport(listOf(
            "* 1 FETCH (UID 1234 FLAGS (\\Seen))",
            "* 2 FETCH (UID 1235 FLAGS ())",
            "A001 OK UID FETCH completed",
        ))
        val sess = ImapSession(t)
        val results = sess.uidFetch("1234:1235", "(UID FLAGS)")
        assertEquals(2, results.size)
        assertEquals("FETCH", results[0].keyword)
        assertEquals(1L, results[0].number)
    }

    // -------------------------------------------------------------------------
    // NOOP
    // -------------------------------------------------------------------------

    @Test
    fun `noop sends NOOP command`() = runTest {
        val t = MockImapTransport(listOf("A001 OK NOOP completed"))
        val sess = ImapSession(t)
        sess.noop()
        assertEquals("A001 NOOP", t.clientLines.first())
    }

    // -------------------------------------------------------------------------
    // LOGOUT
    // -------------------------------------------------------------------------

    @Test
    fun `logout sends LOGOUT command`() = runTest {
        val t = MockImapTransport(listOf(
            "* BYE Logging out",
            "A001 OK LOGOUT completed",
        ))
        val sess = ImapSession(t)
        sess.logout()
        assertEquals("A001 LOGOUT", t.clientLines.first())
    }

    // -------------------------------------------------------------------------
    // CLOSE
    // -------------------------------------------------------------------------

    @Test
    fun `close clears mailbox state`() = runTest {
        // First select a mailbox, then close it
        val t = MockImapTransport(listOf(
            "* 5 EXISTS",
            "* 0 RECENT",
            "A001 OK SELECT completed",
            "A002 OK CLOSE completed",
        ))
        val sess = ImapSession(t)
        sess.select("INBOX")
        assertNotNull(sess.mailbox)
        sess.close()
        assertNull(sess.mailbox)
    }

    // -------------------------------------------------------------------------
    // IDLE
    // -------------------------------------------------------------------------

    @Test
    fun `idle emits EXISTS and EXPUNGE events then cancels`() = runTest {
        val t = MockImapTransport(listOf(
            "+ idling",
            "* 173 EXISTS",
            "* 44 EXPUNGE",
            // After cancellation DONE is sent, then we need the tagged response
            "A001 OK IDLE terminated",
        ))
        val sess = ImapSession(t)
        val events = sess.idle().take(2).toList()
        assertEquals(2, events.size)
        assertIs<ImapEvent.Exists>(events[0])
        assertEquals(173, (events[0] as ImapEvent.Exists).count)
        assertIs<ImapEvent.Expunge>(events[1])
        assertEquals(44, (events[1] as ImapEvent.Expunge).seqno)
        // DONE must have been sent
        assertTrue(t.clientLines.any { it == "DONE" })
    }
}
