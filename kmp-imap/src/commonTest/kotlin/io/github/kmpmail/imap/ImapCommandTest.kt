package io.github.kmpmail.imap

import kotlin.test.*

/**
 * Tests for [ImapCommand] — tag generation and every command builder.
 */
class ImapCommandTest {

    // -------------------------------------------------------------------------
    // Tag generation
    // -------------------------------------------------------------------------

    @Test
    fun `nextTag generates sequential A001 A002 A003`() {
        val cmd = ImapCommand()
        assertEquals("A001", cmd.nextTag())
        assertEquals("A002", cmd.nextTag())
        assertEquals("A003", cmd.nextTag())
    }

    // -------------------------------------------------------------------------
    // Command builders — companion object
    // -------------------------------------------------------------------------

    @Test
    fun `capability returns CAPABILITY`() =
        assertEquals("CAPABILITY", ImapCommand.capability())

    @Test
    fun `login quotes user and password`() {
        val cmd = ImapCommand.login("user@example.com", "secret")
        assertEquals("""LOGIN "user@example.com" "secret"""", cmd)
    }

    @Test
    fun `login escapes special chars in password`() {
        val cmd = ImapCommand.login("u", "p\"w")
        assertTrue(cmd.contains("\\\""))
    }

    @Test
    fun `logout returns LOGOUT`() = assertEquals("LOGOUT", ImapCommand.logout())

    @Test
    fun `select quotes mailbox name`() =
        assertEquals("""SELECT "INBOX"""", ImapCommand.select("INBOX"))

    @Test
    fun `examine quotes mailbox name`() =
        assertEquals("""EXAMINE "INBOX"""", ImapCommand.examine("INBOX"))

    @Test
    fun `list quotes ref and pattern`() =
        assertEquals("""LIST "" "*"""", ImapCommand.list("", "*"))

    @Test
    fun `search joins criteria with spaces`() =
        assertEquals("SEARCH UNSEEN SINCE 1-Jan-2024", ImapCommand.search("UNSEEN", "SINCE", "1-Jan-2024"))

    @Test
    fun `uidSearch prefixes UID`() =
        assertEquals("UID SEARCH UNSEEN", ImapCommand.uidSearch("UNSEEN"))

    @Test
    fun `fetch produces correct string`() =
        assertEquals("FETCH 1:10 (FLAGS)", ImapCommand.fetch("1:10", "(FLAGS)"))

    @Test
    fun `uidFetch prefixes UID`() =
        assertEquals("UID FETCH 1234 (FLAGS BODY)", ImapCommand.uidFetch("1234", "(FLAGS BODY)"))

    @Test
    fun `store produces correct string`() =
        assertEquals("STORE 1:* +FLAGS (\\Seen)", ImapCommand.store("1:*", "+FLAGS", "(\\Seen)"))

    @Test
    fun `uidStore prefixes UID`() =
        assertEquals("UID STORE 1234 +FLAGS (\\Seen)", ImapCommand.uidStore("1234", "+FLAGS", "(\\Seen)"))

    @Test
    fun `copy quotes destination mailbox`() =
        assertEquals("""COPY 1:5 "Sent"""", ImapCommand.copy("1:5", "Sent"))

    @Test
    fun `uidCopy prefixes UID`() =
        assertEquals("""UID COPY 1234 "Sent"""", ImapCommand.uidCopy("1234", "Sent"))

    @Test
    fun `idle returns IDLE`() = assertEquals("IDLE", ImapCommand.idle())

    @Test
    fun `done returns DONE`() = assertEquals("DONE", ImapCommand.done())

    @Test
    fun `noop returns NOOP`() = assertEquals("NOOP", ImapCommand.noop())

    @Test
    fun `close returns CLOSE`() = assertEquals("CLOSE", ImapCommand.close())

    @Test
    fun `expunge returns EXPUNGE`() = assertEquals("EXPUNGE", ImapCommand.expunge())

    @Test
    fun `append with flags`() {
        val cmd = ImapCommand.append("INBOX", "\\Seen", 42)
        assertEquals("""APPEND "INBOX" (\Seen) {42}""", cmd)
    }

    @Test
    fun `append without flags`() {
        val cmd = ImapCommand.append("INBOX", null, 100)
        assertEquals("""APPEND "INBOX" {100}""", cmd)
    }

    // -------------------------------------------------------------------------
    // quote helper
    // -------------------------------------------------------------------------

    @Test
    fun `quote wraps in double quotes`() =
        assertEquals("\"hello\"", ImapCommand.quote("hello"))

    @Test
    fun `quote escapes backslash`() =
        assertEquals("\"a\\\\b\"", ImapCommand.quote("a\\b"))

    @Test
    fun `quote escapes embedded double quote`() =
        assertEquals("\"a\\\"b\"", ImapCommand.quote("a\"b"))
}
