package io.github.kmpmail.mime

import kotlin.test.*

class ContentTypeTest {

    // -------------------------------------------------------------------------
    // Built-in constants
    // -------------------------------------------------------------------------

    @Test
    fun `TEXT_PLAIN constant has correct values`() {
        val ct = ContentType.TEXT_PLAIN
        assertEquals("text", ct.type)
        assertEquals("plain", ct.subtype)
        assertEquals("text/plain", ct.mediaType)
        assertEquals("us-ascii", ct.charset)
    }

    @Test
    fun `TEXT_HTML constant has charset utf-8`() {
        assertEquals("utf-8", ContentType.TEXT_HTML.charset)
        assertEquals("text/html", ContentType.TEXT_HTML.mediaType)
    }

    @Test
    fun `OCTET_STREAM constant has no parameters`() {
        val ct = ContentType.OCTET_STREAM
        assertEquals("application", ct.type)
        assertEquals("octet-stream", ct.subtype)
        assertNull(ct.charset)
        assertNull(ct.boundary)
        assertNull(ct.name)
    }

    // -------------------------------------------------------------------------
    // Parameter accessors
    // -------------------------------------------------------------------------

    @Test
    fun `boundary returns boundary parameter`() {
        val ct = ContentType("multipart", "mixed", mapOf("boundary" to "abc123"))
        assertEquals("abc123", ct.boundary)
    }

    @Test
    fun `name returns name parameter`() {
        val ct = ContentType("application", "pdf", mapOf("name" to "report.pdf"))
        assertEquals("report.pdf", ct.name)
    }

    @Test
    fun `charset is null when not present`() {
        assertNull(ContentType("application", "octet-stream").charset)
    }

    // -------------------------------------------------------------------------
    // toString
    // -------------------------------------------------------------------------

    @Test
    fun `toString with no parameters`() {
        assertEquals("text/plain", ContentType("text", "plain").toString())
    }

    @Test
    fun `toString with single parameter`() {
        val ct = ContentType("text", "plain", mapOf("charset" to "utf-8"))
        assertEquals("text/plain; charset=utf-8", ct.toString())
    }

    @Test
    fun `toString quotes parameter values containing spaces`() {
        val ct = ContentType("multipart", "mixed", mapOf("boundary" to "part with spaces"))
        val s = ct.toString()
        assertTrue(s.contains("\"part with spaces\""))
    }

    @Test
    fun `toString quotes parameter values containing semicolons`() {
        val ct = ContentType("text", "plain", mapOf("weird" to "a;b"))
        assertTrue(ct.toString().contains('"'))
    }

    // -------------------------------------------------------------------------
    // parse()
    // -------------------------------------------------------------------------

    @Test
    fun `parse simple media type`() {
        val ct = ContentType.parse("text/plain")
        assertEquals("text", ct.type)
        assertEquals("plain", ct.subtype)
    }

    @Test
    fun `parse normalises type to lowercase`() {
        val ct = ContentType.parse("TEXT/HTML")
        assertEquals("text", ct.type)
        assertEquals("html", ct.subtype)
    }

    @Test
    fun `parse with charset parameter`() {
        val ct = ContentType.parse("text/plain; charset=utf-8")
        assertEquals("utf-8", ct.charset)
    }

    @Test
    fun `parse with quoted boundary`() {
        val ct = ContentType.parse("""multipart/mixed; boundary="----=_Part_12345"""")
        assertEquals("----=_Part_12345", ct.boundary)
    }

    @Test
    fun `parse with multiple parameters`() {
        val ct = ContentType.parse("application/pdf; charset=us-ascii; name=report.pdf")
        assertEquals("us-ascii", ct.charset)
        assertEquals("report.pdf", ct.name)
    }

    @Test
    fun `parse handles missing slash gracefully`() {
        val ct = ContentType.parse("text")
        assertEquals("text", ct.type)
        assertEquals("", ct.subtype)
    }

    @Test
    fun `parse strips quoted string wrapper from value`() {
        val ct = ContentType.parse("""text/plain; charset="utf-8"""")
        assertEquals("utf-8", ct.charset)
    }
}
