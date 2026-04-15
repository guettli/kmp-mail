package io.github.kmpmail.imap

/**
 * A token in an IMAP response (RFC 3501 formal syntax).
 *
 * The IMAP grammar has five value types:
 *   - [Atom]   — unquoted sequence of non-special chars (e.g. FLAGS, \Seen)
 *   - [Str]    — quoted string or literal
 *   - [Num]    — non-negative integer
 *   - [Lst]    — parenthesised list of values
 *   - [Nil]    — the NIL token
 */
sealed class ImapValue {
    data class Atom(val value: String) : ImapValue()
    data class Str(val value: String) : ImapValue()
    data class Num(val value: Long) : ImapValue()
    data class Lst(val items: List<ImapValue>) : ImapValue()
    object Nil : ImapValue()

    // Convenience unwrappers
    fun asString(): String? = when (this) {
        is Atom -> value
        is Str  -> value
        is Num  -> value.toString()
        is Nil  -> null
        else    -> null
    }
    fun asLong(): Long? = (this as? Num)?.value
    fun asInt(): Int? = asLong()?.toInt()
    fun asList(): List<ImapValue>? = (this as? Lst)?.items
}
