package io.github.kmpmail.imap

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Additional [ImapSession] tests for paths not covered by [ImapSessionTest].
 */
class ImapSessionMoreTest {

    // -------------------------------------------------------------------------
    // EXAMINE
    // -------------------------------------------------------------------------

    @Test
    fun `examine selects mailbox as read-only`() = runTest {
        val t = MockImapTransport(listOf(
            "* 50 EXISTS",
            "* 0 RECENT",
            "A001 OK [READ-ONLY] EXAMINE completed",
        ))
        val sess = ImapSession(t)
        val info = sess.examine("Sent")
        assertEquals("Sent", info.name)
        assertFalse(info.readWrite)
        assertEquals(50, info.exists)
        assertEquals("A001 EXAMINE \"Sent\"", t.clientLines.first())
    }

    // -------------------------------------------------------------------------
    // UID STORE
    // -------------------------------------------------------------------------

    @Test
    fun `uidStore sends correct command`() = runTest {
        val t = MockImapTransport(listOf("A001 OK UID STORE completed"))
        val sess = ImapSession(t)
        sess.uidStore("1234", "+FLAGS", "(\\Seen)")
        assertEquals("A001 UID STORE 1234 +FLAGS (\\Seen)", t.clientLines.first())
    }

    // -------------------------------------------------------------------------
    // BAD response → ImapBadException
    // -------------------------------------------------------------------------

    @Test
    fun `processUntilTagged throws ImapBadException on BAD response`() = runTest {
        val t = MockImapTransport(listOf("A001 BAD Unknown command"))
        val sess = ImapSession(t)
        assertFailsWith<ImapBadException> {
            sess.noop() // sends NOOP, gets BAD back
        }
    }

    // -------------------------------------------------------------------------
    // IDLE — Fetch event
    // -------------------------------------------------------------------------

    @Test
    fun `idle emits Fetch event`() = runTest {
        val t = MockImapTransport(listOf(
            "+ idling",
            "* 5 FETCH (FLAGS (\\Seen))",
            "A001 OK IDLE terminated",
        ))
        val sess = ImapSession(t)
        val events = sess.idle().take(1).toList()
        assertIs<ImapEvent.Fetch>(events[0])
        assertEquals(5, (events[0] as ImapEvent.Fetch).seqno)
    }

    // -------------------------------------------------------------------------
    // processUntilTagged — out-of-order tag (rare case)
    // -------------------------------------------------------------------------

    @Test
    fun `processUntilTagged ignores unrelated tagged response`() = runTest {
        // An out-of-order tagged response (different tag) should not break the loop
        val t = MockImapTransport(listOf(
            "B099 OK Spurious response",   // different tag, treated as untagged-like
            "A001 OK NOOP completed",
        ))
        val sess = ImapSession(t)
        // Should complete without throwing
        sess.noop()
        assertTrue(t.clientLines.any { it.endsWith("NOOP") })
    }

    // -------------------------------------------------------------------------
    // MailboxInfo — HIGHESTMODSEQ parsing
    // -------------------------------------------------------------------------

    @Test
    fun `select parses HIGHESTMODSEQ in OK response`() = runTest {
        val t = MockImapTransport(listOf(
            "* 10 EXISTS",
            "* 0 RECENT",
            "* OK [HIGHESTMODSEQ 12345] CONDSTORE",
            "A001 OK SELECT completed",
        ))
        val sess = ImapSession(t)
        val info = sess.select("INBOX")
        assertEquals(12345L, info.highestModSeq)
    }

    // -------------------------------------------------------------------------
    // search with multiple criteria
    // -------------------------------------------------------------------------

    @Test
    fun `search with multiple criteria joins them`() = runTest {
        val t = MockImapTransport(listOf(
            "* SEARCH 10 20",
            "A001 OK UID SEARCH completed",
        ))
        val sess = ImapSession(t)
        val uids = sess.search("UNSEEN", "SINCE", "1-Jan-2024")
        assertEquals(listOf(10L, 20L), uids)
        assertEquals("A001 UID SEARCH UNSEEN SINCE 1-Jan-2024", t.clientLines.first())
    }

    // -------------------------------------------------------------------------
    // Login with piggybacked CAPABILITY
    // -------------------------------------------------------------------------

    @Test
    fun `login updates capabilities from piggybacked CAPABILITY response`() = runTest {
        val t = MockImapTransport(listOf(
            "* CAPABILITY IMAP4rev1 IDLE",
            "A001 OK LOGIN completed",
        ))
        val sess = ImapSession(t)
        sess.login("user", "pass")
        assertTrue("IMAP4REV1" in sess.capabilities)
        assertTrue("IDLE" in sess.capabilities)
    }

    // -------------------------------------------------------------------------
    // Parser — literal string in FETCH response (via ImapParser)
    // -------------------------------------------------------------------------

    @Test
    fun `parse FETCH response with nested attribute list`() = runTest {
        val t = MockImapTransport(listOf(
            "* 3 FETCH (UID 789 FLAGS (\\Seen \\Flagged))",
            "A001 OK UID FETCH completed",
        ))
        val sess = ImapSession(t)
        val results = sess.uidFetch("789", "(UID FLAGS)")
        assertEquals(1, results.size)
        val fetch = results[0]
        assertEquals(3L, fetch.number)
        // The first value in FETCH is the attribute list
        assertIs<ImapValue.Lst>(fetch.values.firstOrNull())
    }
}
