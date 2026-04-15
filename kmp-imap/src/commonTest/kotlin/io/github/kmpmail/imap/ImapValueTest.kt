package io.github.kmpmail.imap

import kotlin.test.*

class ImapValueTest {

    // -------------------------------------------------------------------------
    // asString
    // -------------------------------------------------------------------------

    @Test
    fun `Atom asString returns value`() =
        assertEquals("FLAGS", ImapValue.Atom("FLAGS").asString())

    @Test
    fun `Str asString returns value`() =
        assertEquals("hello world", ImapValue.Str("hello world").asString())

    @Test
    fun `Num asString returns decimal string`() =
        assertEquals("42", ImapValue.Num(42L).asString())

    @Test
    fun `Nil asString returns null`() =
        assertNull(ImapValue.Nil.asString())

    @Test
    fun `Lst asString returns null`() =
        assertNull(ImapValue.Lst(emptyList()).asString())

    // -------------------------------------------------------------------------
    // asLong
    // -------------------------------------------------------------------------

    @Test
    fun `Num asLong returns value`() =
        assertEquals(999L, ImapValue.Num(999L).asLong())

    @Test
    fun `Atom asLong returns null`() =
        assertNull(ImapValue.Atom("FLAGS").asLong())

    @Test
    fun `Str asLong returns null`() =
        assertNull(ImapValue.Str("123").asLong())

    @Test
    fun `Nil asLong returns null`() =
        assertNull(ImapValue.Nil.asLong())

    // -------------------------------------------------------------------------
    // asInt
    // -------------------------------------------------------------------------

    @Test
    fun `Num asInt converts long to int`() =
        assertEquals(7, ImapValue.Num(7L).asInt())

    @Test
    fun `Atom asInt returns null`() =
        assertNull(ImapValue.Atom("X").asInt())

    @Test
    fun `Nil asInt returns null`() =
        assertNull(ImapValue.Nil.asInt())

    // -------------------------------------------------------------------------
    // asList
    // -------------------------------------------------------------------------

    @Test
    fun `Lst asList returns items`() {
        val items = listOf(ImapValue.Atom("A"), ImapValue.Atom("B"))
        val lst = ImapValue.Lst(items)
        assertEquals(items, lst.asList())
    }

    @Test
    fun `Atom asList returns null`() =
        assertNull(ImapValue.Atom("X").asList())

    @Test
    fun `Nil asList returns null`() =
        assertNull(ImapValue.Nil.asList())

    @Test
    fun `Str asList returns null`() =
        assertNull(ImapValue.Str("text").asList())

    // -------------------------------------------------------------------------
    // Data class equality
    // -------------------------------------------------------------------------

    @Test
    fun `Atom equality`() =
        assertEquals(ImapValue.Atom("A"), ImapValue.Atom("A"))

    @Test
    fun `Str equality`() =
        assertEquals(ImapValue.Str("x"), ImapValue.Str("x"))

    @Test
    fun `Num equality`() =
        assertEquals(ImapValue.Num(1L), ImapValue.Num(1L))

    @Test
    fun `Nil identity`() {
        val n1: ImapValue = ImapValue.Nil
        val n2: ImapValue = ImapValue.Nil
        assertSame(n1, n2)
    }
}
