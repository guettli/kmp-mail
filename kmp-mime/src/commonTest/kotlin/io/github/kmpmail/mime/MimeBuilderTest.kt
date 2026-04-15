package io.github.kmpmail.mime

import kotlin.test.*

class MimeBuilderTest {

    // -------------------------------------------------------------------------
    // Simple text/plain
    // -------------------------------------------------------------------------

    @Test
    fun `build simple text message contains expected headers`() {
        val raw = buildMime {
            from("alice@example.com")
            to("bob@example.com")
            subject("Hello")
            textBody("Hello, World!")
        }.decodeToString()

        assertTrue(raw.contains("From: alice@example.com"))
        assertTrue(raw.contains("To: bob@example.com"))
        assertTrue(raw.contains("Subject: Hello"))
        assertTrue(raw.contains("MIME-Version: 1.0"))
        assertTrue(raw.contains("Content-Type: text/plain"))
    }

    @Test
    fun `build simple text message body is present`() {
        val raw = buildMime {
            from("a@b.com")
            textBody("Body content here.")
        }.decodeToString()

        // Body may be QP-encoded but the ASCII chars survive unchanged
        assertTrue(raw.contains("Body content here."))
    }

    // -------------------------------------------------------------------------
    // Non-ASCII subject encoding
    // -------------------------------------------------------------------------

    @Test
    fun `non-ASCII subject is encoded as RFC 2047 word`() {
        val raw = buildMime {
            from("a@b.com")
            subject("Héllo Wörld")
            textBody("body")
        }.decodeToString()

        assertTrue(raw.contains("Subject: =?"))
    }

    @Test
    fun `non-ASCII subject round-trips through parser`() {
        val original = "Héllo Wörld"
        val raw = buildMime {
            from("a@b.com")
            subject(original)
            textBody("body")
        }
        val msg = MimeParser.parse(raw)
        assertEquals(original, msg.subject)
    }

    // -------------------------------------------------------------------------
    // Round-trip: build → parse
    // -------------------------------------------------------------------------

    @Test
    fun `simple message round-trips through parser`() {
        val raw = buildMime {
            from("alice@example.com")
            to("bob@example.com")
            subject("Test round-trip")
            textBody("Body text here.")
        }

        val msg = MimeParser.parse(raw)
        assertTrue(msg.from?.contains("alice") == true)
        assertEquals("Test round-trip", msg.subject)
    }

    @Test
    fun `multipart alternative round-trips through parser`() {
        val raw = buildMime {
            from("alice@example.com")
            to("bob@example.com")
            subject("Multi")
            textBody("Plain text.")
            htmlBody("<p>HTML text.</p>")
        }

        val msg = MimeParser.parse(raw)
        assertIs<MimePart.Multi>(msg.body)
        val multi = msg.body as MimePart.Multi
        assertEquals(2, multi.parts.size)

        val ct0 = multi.parts[0].headers.contentType
        assertNotNull(ct0)
        assertEquals("text/plain", ct0.mediaType)

        val ct1 = multi.parts[1].headers.contentType
        assertNotNull(ct1)
        assertEquals("text/html", ct1.mediaType)
    }

    // -------------------------------------------------------------------------
    // Attachments
    // -------------------------------------------------------------------------

    @Test
    fun `message with attachment produces multipart mixed`() {
        val raw = buildMime {
            from("a@b.com")
            textBody("See attached.")
            attachment("hello.txt", "Hello!".encodeToByteArray(), "text/plain")
        }

        val msg = MimeParser.parse(raw)
        assertIs<MimePart.Multi>(msg.body)
        val multi = msg.body as MimePart.Multi
        assertEquals(2, multi.parts.size)

        val attPart = multi.parts[1]
        val attCt   = attPart.headers.contentType
        assertNotNull(attCt)
        assertEquals("text", attCt.type)
        assertEquals("plain", attCt.subtype)
    }

    @Test
    fun `attachment content survives base64 round-trip`() {
        val data = "Binary\u0000data\u00FFhere".encodeToByteArray()
        val raw  = buildMime {
            from("a@b.com")
            textBody("See attached.")
            attachment("file.bin", data, "application/octet-stream")
        }

        val msg   = MimeParser.parse(raw)
        val multi = msg.body as MimePart.Multi
        val leaf  = multi.parts[1] as MimePart.Leaf
        assertContentEquals(data, leaf.decodedBody)
    }
}
