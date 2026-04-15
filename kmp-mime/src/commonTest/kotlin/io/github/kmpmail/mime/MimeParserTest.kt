package io.github.kmpmail.mime

import kotlin.test.*

class MimeParserTest {

    // -------------------------------------------------------------------------
    // simple-ascii.eml (RFC 5322 §A.1.1)
    // -------------------------------------------------------------------------

    @Test
    fun `parse simple ASCII message`() {
        val raw = """
            From: John Doe <jdoe@machine.example>
            To: Mary Smith <mary@example.net>
            Subject: Saying Hello
            Date: Fri, 21 Nov 1997 09:55:06 -0600
            Message-ID: <1234@local.machine.example>

            This is a message just to say hello.
            So, "Hello".
        """.trimIndent()

        val msg = MimeParser.parse(raw)

        assertEquals("John Doe <jdoe@machine.example>", msg.from)
        assertEquals(listOf("Mary Smith <mary@example.net>"), msg.to)
        assertEquals("Saying Hello", msg.subject)
        assertEquals("<1234@local.machine.example>", msg.messageId)
        assertIs<MimePart.Leaf>(msg.body)
        assertTrue(msg.textBody!!.contains("hello"))
    }

    // -------------------------------------------------------------------------
    // multipart-mixed.eml (RFC 2046 §5.1.1)
    // -------------------------------------------------------------------------

    @Test
    fun `parse multipart mixed message`() {
        val raw = """
            From: Nathaniel Borenstein <nsb@bellcore.com>
            To: Ned Freed <ned@innosoft.com>
            MIME-Version: 1.0
            Content-type: multipart/mixed; boundary="simple boundary"

            This is the preamble.

            --simple boundary

            This is implicitly typed plain US-ASCII text.
            --simple boundary
            Content-type: text/plain; charset=us-ascii

            This is explicitly typed plain US-ASCII text.

            --simple boundary--

            This is the epilogue.
        """.trimIndent()

        val msg = MimeParser.parse(raw)
        val body = msg.body

        assertIs<MimePart.Multi>(body)
        assertEquals(2, body.parts.size)
        assertTrue(body.preamble.contains("preamble"))
        assertTrue(body.epilogue.contains("epilogue"))

        // First part has no explicit Content-Type — inherits text/plain
        assertIs<MimePart.Leaf>(body.parts[0])
        // Second part has explicit Content-Type
        val ct = body.parts[1].headers.contentType
        assertNotNull(ct)
        assertEquals("text", ct.type)
        assertEquals("plain", ct.subtype)
    }

    // -------------------------------------------------------------------------
    // folded-headers.eml (RFC 5322 §2.2.3)
    // -------------------------------------------------------------------------

    @Test
    fun `parse folded To header`() {
        val raw = """
            From: Pete <pete@example.com>
            To: Alice <alice@example.com>,
                Bob <bob@example.com>
            Subject: Test

            Body
        """.trimIndent()

        val msg = MimeParser.parse(raw)
        val to = msg.headers.get("To")
        assertNotNull(to)
        assertTrue(to.contains("Alice"))
        assertTrue(to.contains("Bob"))
    }

    // -------------------------------------------------------------------------
    // encoded-word-b64.eml / encoded-word-qp.eml (RFC 2047)
    // -------------------------------------------------------------------------

    @Test
    fun `decode B-encoded subject`() {
        val raw = """
            From: test@example.com
            Subject: =?UTF-8?B?SGVsbG8gV29ybGQ=?=

            body
        """.trimIndent()

        val msg = MimeParser.parse(raw)
        assertEquals("Hello World", msg.subject)
    }

    @Test
    fun `decode Q-encoded subject with euro sign`() {
        val raw = """
            From: test@example.com
            Subject: =?UTF-8?Q?Caf=C3=A9_prices_=E2=82=AC2=2C50?=

            body
        """.trimIndent()

        val msg = MimeParser.parse(raw)
        val subj = msg.subject
        assertNotNull(subj)
        assertTrue(subj.contains("Caf"))
        assertTrue(subj.contains("prices"))
    }

    // -------------------------------------------------------------------------
    // ContentType parsing
    // -------------------------------------------------------------------------

    @Test
    fun `parse content type with charset`() {
        val raw = """
            Content-Type: text/html; charset=utf-8

            <p>Hello</p>
        """.trimIndent()

        val msg = MimeParser.parse(raw)
        val ct = msg.headers.contentType
        assertNotNull(ct)
        assertEquals("text", ct.type)
        assertEquals("html", ct.subtype)
        assertEquals("utf-8", ct.charset)
    }

    @Test
    fun `parse content type with quoted boundary`() {
        val raw = """
            Content-Type: multipart/mixed; boundary="----=_Part_abc"

            ------=_Part_abc--
        """.trimIndent()

        val msg = MimeParser.parse(raw)
        val ct = msg.headers.contentType
        assertNotNull(ct)
        assertEquals("----=_Part_abc", ct.boundary)
    }

    // -------------------------------------------------------------------------
    // TransferEncoding
    // -------------------------------------------------------------------------

    @Test
    fun `decode quoted-printable body`() {
        val raw = """
            Content-Type: text/plain; charset=utf-8
            Content-Transfer-Encoding: quoted-printable

            Caf=C3=A9 au lait =E2=82=AC2,50
        """.trimIndent()

        val msg = MimeParser.parse(raw)
        val leaf = msg.body
        assertIs<MimePart.Leaf>(leaf)
        val decoded = leaf.decodedBody.decodeToString()
        assertTrue(decoded.contains("Caf"))
    }

    @Test
    fun `decode base64 body`() {
        // "Hello, World!" base64-encoded
        val raw = """
            Content-Type: text/plain; charset=us-ascii
            Content-Transfer-Encoding: base64

            SGVsbG8sIFdvcmxkIQ==
        """.trimIndent()

        val msg = MimeParser.parse(raw)
        val leaf = msg.body
        assertIs<MimePart.Leaf>(leaf)
        assertEquals("Hello, World!", leaf.decodedBody.decodeToString())
    }

    // -------------------------------------------------------------------------
    // Header parser internals
    // -------------------------------------------------------------------------

    @Test
    fun `multiple Received headers are all preserved`() {
        val raw = """
            Received: from a.example by b.example; Mon, 1 Jan 2024 00:00:00 +0000
            Received: from c.example by a.example; Mon, 1 Jan 2024 00:00:01 +0000
            From: sender@example.com

            body
        """.trimIndent()

        val msg = MimeParser.parse(raw)
        assertEquals(2, msg.headers.getAll("Received").size)
    }
}
