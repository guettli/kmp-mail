package io.github.kmpmail.imap

import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Tests for the three new [ImapClient] / [ImapSession] features:
 *   1. listMailboxes()
 *   2. fetchMessages()
 *   3. appendMessage()
 *
 * All tests use [MockImapTransport] and [ImapClient.withSession] — no real TCP.
 */
class ImapClientFeaturesTest {

    // =========================================================================
    // 1. listMailboxes / ImapSession.list
    // =========================================================================

    @Test
    fun `listMailboxes returns parsed MailboxListEntry list`() = runTest {
        val transport = MockImapTransport(listOf(
            """* LIST (\HasNoChildren) "/" "INBOX"""",
            """* LIST (\HasNoChildren) "/" "Sent"""",
            """* LIST (\Noselect \HasChildren) "/" "[Gmail]"""",
            "A001 OK LIST completed",
        ))
        val client = ImapClient.withSession(ImapSession(transport))
        val entries = client.listMailboxes()

        assertEquals(3, entries.size)

        assertEquals("INBOX", entries[0].name)
        assertEquals("/", entries[0].delimiter)
        assertEquals(listOf("\\HasNoChildren"), entries[0].attributes)

        assertEquals("Sent", entries[1].name)
        assertEquals("/", entries[1].delimiter)

        assertEquals("[Gmail]", entries[2].name)
        assertTrue("\\Noselect" in entries[2].attributes)
        assertTrue("\\HasChildren" in entries[2].attributes)

        assertTrue(transport.clientLines.any { it.endsWith("""LIST "" "*"""") })
    }

    @Test
    fun `listMailboxes sends custom reference and pattern`() = runTest {
        val transport = MockImapTransport(listOf(
            """* LIST (\HasNoChildren) "/" "INBOX/Work"""",
            "A001 OK LIST completed",
        ))
        val client = ImapClient.withSession(ImapSession(transport))
        val entries = client.listMailboxes(reference = "INBOX", pattern = "%")

        assertEquals(1, entries.size)
        assertEquals("INBOX/Work", entries[0].name)
        assertTrue(transport.clientLines.any { it.contains("""LIST "INBOX" "%"""") })
    }

    @Test
    fun `listMailboxes returns empty list when server sends no LIST lines`() = runTest {
        val transport = MockImapTransport(listOf("A001 OK LIST completed"))
        val client = ImapClient.withSession(ImapSession(transport))
        val entries = client.listMailboxes()
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `listMailboxes handles NIL delimiter`() = runTest {
        val transport = MockImapTransport(listOf(
            """* LIST (\Noselect) NIL "INBOX"""",
            "A001 OK LIST completed",
        ))
        val client = ImapClient.withSession(ImapSession(transport))
        val entries = client.listMailboxes()
        assertEquals(1, entries.size)
        assertNull(entries[0].delimiter)
        assertEquals("INBOX", entries[0].name)
    }

    @Test
    fun `listMailboxes before connect throws`() = runTest {
        val client = ImapClient { host = "imap.example.com" }
        assertFailsWith<IllegalStateException> { client.listMailboxes() }
    }

    // Session-level LIST test
    @Test
    fun `ImapSession list returns MailboxListEntry objects`() = runTest {
        val t = MockImapTransport(listOf(
            """* LIST (\HasNoChildren) "." "INBOX"""",
            "A001 OK LIST completed",
        ))
        val sess = ImapSession(t)
        val entries = sess.list()
        assertEquals(1, entries.size)
        assertEquals("INBOX", entries[0].name)
        assertEquals(".", entries[0].delimiter)
        assertEquals(listOf("\\HasNoChildren"), entries[0].attributes)
    }

    // =========================================================================
    // 2. fetchMessages / ImapSession.fetchMessages
    // =========================================================================

    // Header literal: "From: alice@example.com\r\n" (25) + "Subject: Hello\r\n" (16) = 41 bytes
    // Text literal:   "Hello World" = 11 bytes
    private val fetchSingleLine =
        "* 1 FETCH (UID 1234 FLAGS (\\Seen) BODY[HEADER] {41}\r\n" +
        "From: alice@example.com\r\nSubject: Hello\r\n" +
        " BODY[TEXT] {11}\r\nHello World)"

    @Test
    fun `fetchMessages returns FetchedMessage with uid flags and parsed MimeMessage`() = runTest {
        val transport = MockImapTransport(listOf(
            fetchSingleLine,
            "A001 OK UID FETCH completed",
        ))
        val client = ImapClient.withSession(ImapSession(transport))
        val messages = client.fetchMessages("1234")

        assertEquals(1, messages.size)
        val msg = messages[0]
        assertEquals(1234L, msg.uid)
        assertEquals(listOf("\\Seen"), msg.flags)
        assertEquals("alice@example.com", msg.message.from)
        assertEquals("Hello", msg.message.subject)
        assertEquals("Hello World", msg.message.textBody)

        assertTrue(transport.clientLines.any {
            it.contains("UID FETCH 1234 (UID FLAGS BODY.PEEK[HEADER] BODY.PEEK[TEXT])")
        })
    }

    @Test
    fun `fetchMessages handles multiple messages`() = runTest {
        // Header 1: "From: alice@example.com\r\n" (25) + "Subject: Hi\r\n" (13) = 38 bytes, text: "Body1" = 5
        // Header 2: "From: bob@example.com\r\n" (23)  + "Subject: Hey\r\n" (14) = 37 bytes, text: "Body2" = 5
        val line1 =
            "* 1 FETCH (UID 100 FLAGS (\\Seen \\Flagged) BODY[HEADER] {38}\r\n" +
            "From: alice@example.com\r\nSubject: Hi\r\n" +
            " BODY[TEXT] {5}\r\nBody1)"
        val line2 =
            "* 2 FETCH (UID 101 FLAGS () BODY[HEADER] {37}\r\n" +
            "From: bob@example.com\r\nSubject: Hey\r\n" +
            " BODY[TEXT] {5}\r\nBody2)"
        val transport = MockImapTransport(listOf(line1, line2, "A001 OK UID FETCH completed"))
        val client = ImapClient.withSession(ImapSession(transport))
        val messages = client.fetchMessages("100:101")

        assertEquals(2, messages.size)
        assertEquals(100L, messages[0].uid)
        assertEquals(listOf("\\Seen", "\\Flagged"), messages[0].flags)
        assertEquals("Hi", messages[0].message.subject)
        assertEquals(101L, messages[1].uid)
        assertTrue(messages[1].flags.isEmpty())
        assertEquals("Hey", messages[1].message.subject)
    }

    @Test
    fun `fetchMessages returns empty list when no FETCH responses`() = runTest {
        val transport = MockImapTransport(listOf("A001 OK UID FETCH completed"))
        val client = ImapClient.withSession(ImapSession(transport))
        val messages = client.fetchMessages("999")
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `fetchMessages before connect throws`() = runTest {
        val client = ImapClient { host = "imap.example.com" }
        assertFailsWith<IllegalStateException> { client.fetchMessages("1") }
    }

    // Session-level fetchMessages test
    @Test
    fun `ImapSession fetchMessages extracts uid and flags correctly`() = runTest {
        val t = MockImapTransport(listOf(
            fetchSingleLine,
            "A001 OK UID FETCH completed",
        ))
        val sess = ImapSession(t)
        val messages = sess.fetchMessages("1234")

        assertEquals(1, messages.size)
        assertEquals(1234L, messages[0].uid)
        assertEquals(listOf("\\Seen"), messages[0].flags)
        assertNotNull(messages[0].message)
    }

    // =========================================================================
    // 3. appendMessage / ImapSession.append
    // =========================================================================

    @Test
    fun `appendMessage sends APPEND command and message bytes`() = runTest {
        val transport = MockImapTransport(listOf(
            "+ go ahead",
            "A001 OK APPEND completed",
        ))
        val client = ImapClient.withSession(ImapSession(transport))
        val msgBytes = "From: alice@example.com\r\nSubject: Test\r\n\r\nHello".encodeToByteArray()
        client.appendMessage("INBOX", listOf("\\Seen"), null, msgBytes)

        // First client line: the APPEND command with size
        val appendLine = transport.clientLines[0]
        assertTrue(appendLine.contains("APPEND"), "Expected APPEND in: $appendLine")
        assertTrue(appendLine.contains("\"INBOX\""), "Expected quoted mailbox in: $appendLine")
        assertTrue(appendLine.contains("(\\Seen)"), "Expected flags in: $appendLine")
        assertTrue(appendLine.contains("{${msgBytes.size}}"), "Expected literal size in: $appendLine")

        // Second client line: the message content
        assertEquals(msgBytes.decodeToString(), transport.clientLines[1])
    }

    @Test
    fun `appendMessage with no flags omits flag list`() = runTest {
        val transport = MockImapTransport(listOf("+ go ahead", "A001 OK APPEND completed"))
        val client = ImapClient.withSession(ImapSession(transport))
        val msg = "Subject: hi\r\n\r\nbody".encodeToByteArray()
        client.appendMessage("Drafts", emptyList(), null, msg)

        val appendLine = transport.clientLines[0]
        assertFalse(appendLine.contains("()"), "Flag list should be omitted when empty")
        assertTrue(appendLine.contains("\"Drafts\""))
        assertTrue(appendLine.contains("{${msg.size}}"))
    }

    @Test
    fun `appendMessage with internalDate includes date in command`() = runTest {
        val transport = MockImapTransport(listOf("+ go ahead", "A001 OK APPEND completed"))
        val client = ImapClient.withSession(ImapSession(transport))
        val msg = "Subject: dated\r\n\r\nbody".encodeToByteArray()
        client.appendMessage(
            mailbox = "Sent",
            flags = listOf("\\Seen"),
            internalDate = "Mon, 07 Feb 1994 21:52:25 -0800",
            message = msg,
        )

        val appendLine = transport.clientLines[0]
        assertTrue(appendLine.contains("\"Mon, 07 Feb 1994 21:52:25 -0800\""),
            "Expected internalDate in: $appendLine")
        assertTrue(appendLine.contains("(\\Seen)"))
    }

    @Test
    fun `appendMessage throws when server sends NO response`() = runTest {
        val transport = MockImapTransport(listOf(
            "+ go ahead",
            "A001 NO [ALREADYEXISTS] Mailbox not found",
        ))
        val client = ImapClient.withSession(ImapSession(transport))
        assertFailsWith<ImapNoException> {
            client.appendMessage("NoSuchBox", message = "test".encodeToByteArray())
        }
    }

    @Test
    fun `appendMessage throws when no continuation received`() = runTest {
        val transport = MockImapTransport(listOf(
            "A001 BAD APPEND failed",
        ))
        val client = ImapClient.withSession(ImapSession(transport))
        // Server rejects with BAD before sending continuation → ImapException
        assertFailsWith<ImapException> {
            client.appendMessage("INBOX", message = "test".encodeToByteArray())
        }
    }

    @Test
    fun `appendMessage before connect throws`() = runTest {
        val client = ImapClient { host = "imap.example.com" }
        assertFailsWith<IllegalStateException> {
            client.appendMessage("INBOX", message = "msg".encodeToByteArray())
        }
    }

    // Session-level append test
    @Test
    fun `ImapSession append sends correct command sequence`() = runTest {
        val t = MockImapTransport(listOf("+ go ahead", "A001 OK APPEND completed"))
        val sess = ImapSession(t)
        val msgBytes = "Subject: x\r\n\r\nx".encodeToByteArray()
        sess.append("INBOX", listOf("\\Seen"), null, msgBytes)

        assertEquals(2, t.clientLines.size)
        assertTrue(t.clientLines[0].startsWith("A001 APPEND"))
        assertEquals(msgBytes.decodeToString(), t.clientLines[1])
    }
}
