package io.github.kmpmail.mime

import kotlin.test.*

class MimeHeadersTest {

    private fun headers(vararg pairs: Pair<String, String>) =
        MimeHeaders(pairs.toList())

    // -------------------------------------------------------------------------
    // get / getAll
    // -------------------------------------------------------------------------

    @Test
    fun `get returns first matching field case-insensitively`() {
        val h = headers("Subject" to "Hello", "SUBJECT" to "World")
        assertEquals("Hello", h.get("subject"))
    }

    @Test
    fun `get returns null when field absent`() {
        assertNull(headers("From" to "a@b.com").get("To"))
    }

    @Test
    fun `getAll returns all values for a repeated field`() {
        val h = headers("Received" to "hop1", "Received" to "hop2", "Received" to "hop3")
        val all = h.getAll("received")
        assertEquals(listOf("hop1", "hop2", "hop3"), all)
    }

    @Test
    fun `getAll returns empty list when field absent`() {
        assertTrue(headers("From" to "a").getAll("To").isEmpty())
    }

    // -------------------------------------------------------------------------
    // Structured accessors
    // -------------------------------------------------------------------------

    @Test
    fun `contentType parses Content-Type header`() {
        val h = headers("Content-Type" to "text/html; charset=utf-8")
        val ct = h.contentType
        assertNotNull(ct)
        assertEquals("text", ct.type)
        assertEquals("html", ct.subtype)
    }

    @Test
    fun `contentType returns null when header absent`() {
        assertNull(headers("From" to "a").contentType)
    }

    @Test
    fun `contentTransferEncoding defaults to SEVEN_BIT when absent`() {
        assertEquals(TransferEncoding.SEVEN_BIT, headers("From" to "a").contentTransferEncoding)
    }

    @Test
    fun `contentTransferEncoding parses header`() {
        val h = headers("Content-Transfer-Encoding" to "base64")
        assertEquals(TransferEncoding.BASE64, h.contentTransferEncoding)
    }

    @Test
    fun `contentDisposition returns raw value`() {
        val h = headers("Content-Disposition" to "attachment; filename=x.pdf")
        assertEquals("attachment; filename=x.pdf", h.contentDisposition)
    }

    @Test
    fun `contentDisposition returns null when absent`() {
        assertNull(headers().contentDisposition)
    }

    @Test
    fun `mimeVersion returns MIME-Version header`() {
        val h = headers("MIME-Version" to "1.0")
        assertEquals("1.0", h.mimeVersion)
    }

    @Test
    fun `mimeVersion returns null when absent`() {
        assertNull(headers().mimeVersion)
    }

    // -------------------------------------------------------------------------
    // serialize
    // -------------------------------------------------------------------------

    @Test
    fun `serialize produces CRLF-terminated fields`() {
        val h = headers("From" to "a@b.com", "To" to "b@c.com")
        val s = h.serialize()
        assertEquals("From: a@b.com\r\nTo: b@c.com\r\n", s)
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    @Test
    fun `Builder add appends fields`() {
        val h = MimeHeaders.Builder()
            .add("X-Foo", "bar")
            .add("X-Foo", "baz")
            .build()
        assertEquals(listOf("bar", "baz"), h.getAll("X-Foo"))
    }

    @Test
    fun `Builder set replaces existing field`() {
        val h = MimeHeaders.Builder()
            .add("Subject", "Old")
            .set("Subject", "New")
            .build()
        assertEquals("New", h.get("Subject"))
        assertEquals(1, h.getAll("Subject").size)
    }

    // -------------------------------------------------------------------------
    // buildHeaders DSL
    // -------------------------------------------------------------------------

    @Test
    fun `buildHeaders DSL creates headers`() {
        val h = buildHeaders {
            add("From", "alice@example.com")
            add("To", "bob@example.com")
        }
        assertEquals("alice@example.com", h.get("From"))
        assertEquals("bob@example.com", h.get("To"))
    }
}
