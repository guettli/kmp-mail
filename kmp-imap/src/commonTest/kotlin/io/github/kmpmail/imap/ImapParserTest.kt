package io.github.kmpmail.imap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [ImapParser].
 *
 * Test data is taken directly from RFC 3501 example transcripts and
 * the IMAP4rev1 formal syntax so we validate against the spec itself.
 */
class ImapParserTest {

    // -------------------------------------------------------------------------
    // Continuation responses
    // -------------------------------------------------------------------------

    @Test
    fun `parse continuation response`() {
        val r = ImapParser.parse("+ go ahead") as ImapResponse.Continuation
        assertEquals("go ahead", r.text)
    }

    @Test
    fun `parse bare continuation`() {
        val r = ImapParser.parse("+") as ImapResponse.Continuation
        assertEquals("", r.text)
    }

    // -------------------------------------------------------------------------
    // Tagged responses
    // -------------------------------------------------------------------------

    @Test
    fun `parse tagged OK`() {
        val r = ImapParser.parse("A001 OK LOGIN completed") as ImapResponse.Tagged
        assertEquals("A001", r.tag)
        assertEquals(ImapResponse.Status.OK, r.status)
        assertNull(r.code)
        assertEquals("LOGIN completed", r.text)
    }

    @Test
    fun `parse tagged NO`() {
        val r = ImapParser.parse("A002 NO [AUTHENTICATIONFAILED] Invalid credentials") as ImapResponse.Tagged
        assertEquals("A002", r.tag)
        assertEquals(ImapResponse.Status.NO, r.status)
        assertEquals("AUTHENTICATIONFAILED", r.code)
    }

    @Test
    fun `parse tagged BAD`() {
        val r = ImapParser.parse("A003 BAD Command unknown") as ImapResponse.Tagged
        assertEquals(ImapResponse.Status.BAD, r.status)
    }

    @Test
    fun `parse tagged OK with UIDVALIDITY response code`() {
        // RFC 3501 section 7.1: * OK [UIDVALIDITY 3857529045]
        val r = ImapParser.parse("A005 OK [READ-WRITE] SELECT completed") as ImapResponse.Tagged
        assertEquals("READ-WRITE", r.code)
        assertEquals("SELECT completed", r.text)
    }

    // -------------------------------------------------------------------------
    // Untagged responses — numeric prefix
    // -------------------------------------------------------------------------

    @Test
    fun `parse untagged EXISTS`() {
        val r = ImapParser.parse("* 172 EXISTS") as ImapResponse.Untagged
        assertEquals("EXISTS", r.keyword)
        assertEquals(172L, r.number)
    }

    @Test
    fun `parse untagged RECENT`() {
        val r = ImapParser.parse("* 1 RECENT") as ImapResponse.Untagged
        assertEquals("RECENT", r.keyword)
        assertEquals(1L, r.number)
    }

    @Test
    fun `parse untagged EXPUNGE`() {
        val r = ImapParser.parse("* 44 EXPUNGE") as ImapResponse.Untagged
        assertEquals("EXPUNGE", r.keyword)
        assertEquals(44L, r.number)
    }

    // -------------------------------------------------------------------------
    // Untagged responses — keyword prefix
    // -------------------------------------------------------------------------

    @Test
    fun `parse untagged OK with response code`() {
        val r = ImapParser.parse("* OK [UIDVALIDITY 3857529045] UIDs valid") as ImapResponse.Untagged
        assertEquals("OK", r.keyword)
        assertNull(r.number)
    }

    @Test
    fun `parse untagged CAPABILITY`() {
        val r = ImapParser.parse("* CAPABILITY IMAP4rev1 STARTTLS AUTH=PLAIN") as ImapResponse.Untagged
        assertEquals("CAPABILITY", r.keyword)
        // text contains the capability list
        assertTrue(r.text.contains("IMAP4rev1"))
    }

    @Test
    fun `parse untagged FLAGS`() {
        val r = ImapParser.parse("* FLAGS (\\Answered \\Flagged \\Deleted \\Seen \\Draft)") as ImapResponse.Untagged
        assertEquals("FLAGS", r.keyword)
        val list = r.values.firstOrNull()
        assertIs<ImapValue.Lst>(list)
        val items = list.items.map { (it as ImapValue.Atom).value }
        assertTrue(items.contains("\\Answered"))
        assertTrue(items.contains("\\Seen"))
    }

    // -------------------------------------------------------------------------
    // FETCH response with parenthesised attribute-value pairs
    // -------------------------------------------------------------------------

    @Test
    fun `parse untagged FETCH with FLAGS and UID`() {
        // Typical UID FETCH response: * 1 FETCH (UID 1234 FLAGS (\Seen))
        val r = ImapParser.parse("* 1 FETCH (UID 1234 FLAGS (\\Seen))") as ImapResponse.Untagged
        assertEquals("FETCH", r.keyword)
        assertEquals(1L, r.number)
        val attrs = r.values.firstOrNull()
        assertIs<ImapValue.Lst>(attrs)
    }

    // -------------------------------------------------------------------------
    // Value parser edge cases
    // -------------------------------------------------------------------------

    @Test
    fun `parse NIL value`() {
        val values = ImapParser.parseValues("NIL")
        assertEquals(1, values.size)
        assertIs<ImapValue.Nil>(values[0])
    }

    @Test
    fun `parse quoted string`() {
        val values = ImapParser.parseValues("\"hello world\"")
        assertEquals(1, values.size)
        assertEquals("hello world", (values[0] as ImapValue.Str).value)
    }

    @Test
    fun `parse quoted string with escape`() {
        val values = ImapParser.parseValues("\"he said \\\"hi\\\"\"")
        assertEquals(1, values.size)
        assertEquals("he said \"hi\"", (values[0] as ImapValue.Str).value)
    }

    @Test
    fun `parse numeric atom`() {
        val values = ImapParser.parseValues("42")
        assertEquals(1, values.size)
        assertEquals(42L, (values[0] as ImapValue.Num).value)
    }

    @Test
    fun `parse nested list`() {
        val values = ImapParser.parseValues("(A B (C D))")
        assertEquals(1, values.size)
        val outer = values[0] as ImapValue.Lst
        assertEquals(3, outer.items.size)
        assertIs<ImapValue.Lst>(outer.items[2])
    }
}
