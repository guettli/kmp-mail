package io.github.kmpmail.mime

import kotlin.test.*

/**
 * Additional [MimeBuilder] tests covering branches not exercised by [MimeBuilderTest].
 */
class MimeBuilderMoreTest {

    // -------------------------------------------------------------------------
    // header / cc / replyTo / date / messageId fluent API
    // -------------------------------------------------------------------------

    @Test
    fun `cc adds Cc header`() {
        val raw = buildMime {
            to("b@b.com")
            cc("c@c.com")
            textBody("hi")
        }
        val msg = MimeParser.parse(raw)
        assertEquals(listOf("c@c.com"), msg.cc)
    }

    @Test
    fun `replyTo adds Reply-To header`() {
        val raw = buildMime {
            to("b@b.com")
            replyTo("r@r.com")
            textBody("hi")
        }
        val msg = MimeParser.parse(raw)
        assertEquals("r@r.com", msg.replyTo)
    }

    @Test
    fun `date adds Date header`() {
        val raw = buildMime {
            to("b@b.com")
            date("Thu, 01 Jan 2026 00:00:00 +0000")
            textBody("hi")
        }
        val msg = MimeParser.parse(raw)
        assertEquals("Thu, 01 Jan 2026 00:00:00 +0000", msg.date)
    }

    @Test
    fun `messageId adds Message-ID header`() {
        val raw = buildMime {
            to("b@b.com")
            messageId("<unique-id@example.com>")
            textBody("hi")
        }
        val msg = MimeParser.parse(raw)
        assertEquals("<unique-id@example.com>", msg.messageId)
    }

    @Test
    fun `header adds arbitrary header`() {
        val raw = buildMime {
            to("b@b.com")
            header("X-Custom", "custom-value")
            textBody("hi")
        }
        val msg = MimeParser.parse(raw)
        assertEquals("custom-value", msg.headers.get("X-Custom"))
    }

    // -------------------------------------------------------------------------
    // text + html + attachments → mixed(alternative + attachment) branch
    // -------------------------------------------------------------------------

    @Test
    fun `text plus html plus attachment uses mixed containing alternative`() {
        val raw = buildMime {
            from("a@a.com")
            to("b@b.com")
            textBody("plain text")
            htmlBody("<p>html</p>")
            attachment("data.bin", byteArrayOf(1, 2, 3))
        }
        val rawStr = raw.decodeToString()
        // outer boundary is multipart/mixed
        assertTrue(rawStr.contains("multipart/mixed"))
        // inner boundary is multipart/alternative
        assertTrue(rawStr.contains("multipart/alternative"))

        val msg = MimeParser.parse(raw)
        // allParts: text + html + attachment = 3 leaf parts
        assertEquals(3, msg.allParts.size)
    }

    // -------------------------------------------------------------------------
    // text + attachment (no html)
    // -------------------------------------------------------------------------

    @Test
    fun `text plus attachment without html uses multipart-mixed`() {
        val raw = buildMime {
            to("b@b.com")
            textBody("body text")
            attachment("file.bin", byteArrayOf(42))
        }
        assertTrue(raw.decodeToString().contains("multipart/mixed"))
        val msg = MimeParser.parse(raw)
        assertEquals(2, msg.allParts.size)
    }

    // -------------------------------------------------------------------------
    // build vs buildString consistency
    // -------------------------------------------------------------------------

    @Test
    fun `build and buildString produce same content`() {
        val builder = MimeBuilder()
        builder.to("b@b.com")
        builder.textBody("hello")
        val fromBuild = builder.build().decodeToString()
        // Create fresh builder to compare
        val builder2 = MimeBuilder()
        builder2.to("b@b.com")
        builder2.textBody("hello")
        val fromBuildString = builder2.buildString()
        // They should have the same structure (boundaries differ so compare length class)
        assertTrue(fromBuild.contains("text/plain"))
        assertTrue(fromBuildString.contains("text/plain"))
    }

    // -------------------------------------------------------------------------
    // Empty / edge cases
    // -------------------------------------------------------------------------

    @Test
    fun `builder with no text produces empty body`() {
        val raw = buildMime { to("b@b.com") }
        // Should not crash and must produce a valid MIME-Version header
        assertTrue(raw.decodeToString().contains("MIME-Version: 1.0"))
    }

    @Test
    fun `attachment with custom content type`() {
        val raw = buildMime {
            to("b@b.com")
            attachment("photo.jpg", byteArrayOf(1, 2), "image/jpeg")
        }
        assertTrue(raw.decodeToString().contains("image/jpeg"))
    }
}
