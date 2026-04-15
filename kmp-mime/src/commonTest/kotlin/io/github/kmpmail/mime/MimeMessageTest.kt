package io.github.kmpmail.mime

import kotlin.test.*

/**
 * Tests for [MimeMessage] convenience property accessors.
 * Uses [MimeParser] and [MimeBuilder] to build realistic messages.
 */
class MimeMessageTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun simpleText(
        from: String = "alice@example.com",
        to: String = "bob@example.com",
        subject: String = "Hello",
        body: String = "Hello, World!",
    ): MimeMessage {
        val raw = buildMime {
            from(from)
            to(to)
            subject(subject)
            textBody(body)
        }
        return MimeParser.parse(raw)
    }

    // -------------------------------------------------------------------------
    // Envelope header accessors
    // -------------------------------------------------------------------------

    @Test
    fun `from returns From header`() {
        assertEquals("alice@example.com", simpleText().from)
    }

    @Test
    fun `from returns null when absent`() {
        val raw = buildMime { to("bob@example.com"); textBody("hi") }
        assertNull(MimeParser.parse(raw).from)
    }

    @Test
    fun `to returns list of To header values`() {
        val raw = buildMime { to("a@b.com"); to("c@d.com"); textBody("hi") }
        val msg = MimeParser.parse(raw)
        assertEquals(listOf("a@b.com", "c@d.com"), msg.to)
    }

    @Test
    fun `cc returns list of Cc header values`() {
        val raw = buildMime { cc("x@y.com"); to("a@b.com"); textBody("hi") }
        val msg = MimeParser.parse(raw)
        assertEquals(listOf("x@y.com"), msg.cc)
    }

    @Test
    fun `cc returns empty list when absent`() {
        assertTrue(simpleText().cc.isEmpty())
    }

    @Test
    fun `bcc returns empty list when absent`() {
        assertTrue(simpleText().bcc.isEmpty())
    }

    @Test
    fun `replyTo returns Reply-To header`() {
        val raw = buildMime { replyTo("r@example.com"); to("b@b.com"); textBody("hi") }
        assertEquals("r@example.com", MimeParser.parse(raw).replyTo)
    }

    @Test
    fun `replyTo returns null when absent`() {
        assertNull(simpleText().replyTo)
    }

    @Test
    fun `date returns Date header`() {
        val raw = buildMime { date("Mon, 01 Jan 2024 00:00:00 +0000"); to("b@b.com"); textBody("hi") }
        assertEquals("Mon, 01 Jan 2024 00:00:00 +0000", MimeParser.parse(raw).date)
    }

    @Test
    fun `date returns null when absent`() {
        assertNull(simpleText().date)
    }

    @Test
    fun `messageId returns Message-ID header`() {
        val raw = buildMime { messageId("<abc@example.com>"); to("b@b.com"); textBody("hi") }
        assertEquals("<abc@example.com>", MimeParser.parse(raw).messageId)
    }

    @Test
    fun `messageId returns null when absent`() {
        assertNull(simpleText().messageId)
    }

    @Test
    fun `inReplyTo returns null for simple message`() {
        assertNull(simpleText().inReplyTo)
    }

    @Test
    fun `references returns null for simple message`() {
        assertNull(simpleText().references)
    }

    // -------------------------------------------------------------------------
    // Subject decoding
    // -------------------------------------------------------------------------

    @Test
    fun `subject decodes RFC 2047 encoded-words`() {
        val raw = buildMime { to("b@b.com"); subject("Héllo"); textBody("hi") }
        val msg = MimeParser.parse(raw)
        assertEquals("Héllo", msg.subject)
    }

    @Test
    fun `subject returns null when absent`() {
        val raw = buildMime { to("b@b.com"); textBody("hi") }
        assertNull(MimeParser.parse(raw).subject)
    }

    // -------------------------------------------------------------------------
    // textBody
    // -------------------------------------------------------------------------

    @Test
    fun `textBody returns body of simple text message`() {
        assertEquals("Hello, World!", simpleText(body = "Hello, World!").textBody)
    }

    @Test
    fun `textBody returns text part of multipart-alternative`() {
        val raw = buildMime {
            to("b@b.com")
            textBody("Plain text")
            htmlBody("<p>HTML</p>")
        }
        val msg = MimeParser.parse(raw)
        assertEquals("Plain text", msg.textBody?.trim())
    }

    // -------------------------------------------------------------------------
    // htmlBody
    // -------------------------------------------------------------------------

    @Test
    fun `htmlBody returns null for simple text message`() {
        assertNull(simpleText().htmlBody)
    }

    @Test
    fun `htmlBody returns html part of multipart-alternative`() {
        val raw = buildMime {
            to("b@b.com")
            textBody("Plain text")
            htmlBody("<p>HTML</p>")
        }
        val msg = MimeParser.parse(raw)
        assertNotNull(msg.htmlBody)
        assertTrue(msg.htmlBody!!.contains("<p>HTML</p>"))
    }

    // -------------------------------------------------------------------------
    // allParts
    // -------------------------------------------------------------------------

    @Test
    fun `allParts returns single leaf for simple text`() {
        val parts = simpleText().allParts
        assertEquals(1, parts.size)
    }

    @Test
    fun `allParts returns all leaves for multipart message`() {
        val raw = buildMime {
            to("b@b.com")
            textBody("text")
            attachment("x.bin", byteArrayOf(1, 2, 3))
        }
        val msg = MimeParser.parse(raw)
        assertEquals(2, msg.allParts.size)
    }

    @Test
    fun `allParts for multipart-alternative includes both parts`() {
        val raw = buildMime {
            to("b@b.com")
            textBody("text")
            htmlBody("<p>html</p>")
        }
        assertEquals(2, MimeParser.parse(raw).allParts.size)
    }
}
