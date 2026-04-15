package io.github.kmpmail.imap

/**
 * RFC 3501 server response parser.
 *
 * Handles the three response kinds (untagged, tagged, continuation) and
 * tokenises attribute-value pairs inside them using a cursor over the line.
 *
 * Literal strings ({n}\r\n<n bytes>) are passed in pre-fetched as part of
 * the line text via [ImapTransport.readResponse].
 */
object ImapParser {

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    fun parse(line: String): ImapResponse = Cursor(line).parseResponse()

    // -------------------------------------------------------------------------
    // Value tokeniser (used by callers that need structured attribute parsing)
    // -------------------------------------------------------------------------

    fun parseValues(text: String): List<ImapValue> {
        val cursor = Cursor(text)
        val result = mutableListOf<ImapValue>()
        while (!cursor.exhausted()) {
            cursor.skipSpaces()
            if (cursor.exhausted()) break
            result.add(cursor.readValue())
        }
        return result
    }

    // -------------------------------------------------------------------------
    // Cursor — internal parser state
    // -------------------------------------------------------------------------

    internal class Cursor(private val src: String) {
        var pos = 0

        fun exhausted() = pos >= src.length
        fun peek(): Char = if (pos < src.length) src[pos] else '\u0000'

        fun skipSpaces() { while (pos < src.length && src[pos] == ' ') pos++ }

        fun consume(): Char = src[pos++]

        fun consumeIf(c: Char): Boolean {
            if (pos < src.length && src[pos] == c) { pos++; return true }
            return false
        }

        /** Read until space, '(', ')', '{', '"', or end. */
        fun readAtom(): String {
            skipSpaces()
            val start = pos
            while (pos < src.length && src[pos] !in " ()[]{\"") pos++
            return src.substring(start, pos)
        }

        /** Read a quoted string; pos is just past the opening quote on entry. */
        private fun readQuoted(): String {
            val sb = StringBuilder()
            while (pos < src.length && src[pos] != '"') {
                if (src[pos] == '\\' && pos + 1 < src.length) pos++ // skip escape
                sb.append(src[pos++])
            }
            if (pos < src.length) pos++ // consume closing quote
            return sb.toString()
        }

        /**
         * Read a literal that was pre-fetched into the line as
         * "{n}<SP><content>" — our transport appends a space then the
         * literal bytes after the closing brace, so we just read n chars.
         */
        private fun readLiteral(): String {
            val start = pos
            while (pos < src.length && src[pos] != '}') pos++
            val count = src.substring(start, pos).toIntOrNull() ?: 0
            pos++ // skip '}'
            // Transport places literal bytes immediately after '}' (no CRLF in our in-memory representation)
            if (pos < src.length && src[pos] == '\r') pos++
            if (pos < src.length && src[pos] == '\n') pos++
            val end = minOf(pos + count, src.length)
            val literal = src.substring(pos, end)
            pos = end
            return literal
        }

        /** Read a parenthesised list. '(' has already been consumed. */
        private fun readList(): ImapValue.Lst {
            val items = mutableListOf<ImapValue>()
            skipSpaces()
            while (pos < src.length && src[pos] != ')') {
                items.add(readValue())
                skipSpaces()
            }
            if (pos < src.length) pos++ // consume ')'
            return ImapValue.Lst(items)
        }

        /** Read one value (atom, string, literal, list, NIL/number, or bracketed response code). */
        fun readValue(): ImapValue {
            skipSpaces()
            if (exhausted()) return ImapValue.Nil

            return when (val c = peek()) {
                '"' -> { pos++; ImapValue.Str(readQuoted()) }
                '{' -> { pos++; ImapValue.Str(readLiteral()) }
                '(' -> { pos++; readList() }
                '[' -> {
                    // Response code like [UIDVALIDITY 3857529045] — read until ']' and treat as atom
                    pos++ // skip '['
                    val start = pos
                    while (pos < src.length && src[pos] != ']') pos++
                    val code = src.substring(start, pos)
                    if (pos < src.length) pos++ // skip ']'
                    ImapValue.Atom("[$code]")
                }
                'N', 'n' -> {
                    // Could be NIL or an atom starting with N
                    val atom = readAtom()
                    if (atom.uppercase() == "NIL") ImapValue.Nil
                    else tryNumber(atom) ?: ImapValue.Atom(atom)
                }
                else -> {
                    val atom = readAtom()
                    tryNumber(atom) ?: ImapValue.Atom(atom)
                }
            }
        }

        private fun tryNumber(s: String): ImapValue.Num? =
            s.toLongOrNull()?.let { ImapValue.Num(it) }

        // -------------------------------------------------------------------------
        // Response-level parsing
        // -------------------------------------------------------------------------

        fun parseResponse(): ImapResponse {
            skipSpaces()
            return when {
                consumeIf('+') -> parseContinuation()
                consumeIf('*') -> parseUntagged()
                else           -> parseTagged()
            }
        }

        private fun parseContinuation(): ImapResponse.Continuation {
            skipSpaces()
            return ImapResponse.Continuation(src.substring(pos).trim())
        }

        private fun parseUntagged(): ImapResponse.Untagged {
            skipSpaces()
            // Could be "* n KEYWORD ..." or "* KEYWORD ..."
            val first = readAtom()
            val (number, keyword) = if (first.all { it.isDigit() } && first.isNotEmpty()) {
                skipSpaces()
                first.toLongOrNull() to readAtom()
            } else {
                null to first
            }
            skipSpaces()
            val text = src.substring(pos)
            val values = buildList {
                while (!exhausted()) {
                    skipSpaces()
                    if (exhausted()) break
                    add(readValue())
                }
            }
            return ImapResponse.Untagged(
                keyword = keyword.uppercase(),
                number  = number,
                text    = text,
                values  = values,
            )
        }

        private fun parseTagged(): ImapResponse.Tagged {
            val tag = readAtom()
            skipSpaces()
            val statusStr = readAtom().uppercase()
            val status = when (statusStr) {
                "OK"  -> ImapResponse.Status.OK
                "NO"  -> ImapResponse.Status.NO
                "BAD" -> ImapResponse.Status.BAD
                else  -> ImapResponse.Status.BAD
            }
            skipSpaces()
            // Optional response code [CODE text]
            var code: String? = null
            if (pos < src.length && src[pos] == '[') {
                pos++ // skip '['
                val start = pos
                while (pos < src.length && src[pos] != ']') pos++
                code = src.substring(start, pos)
                if (pos < src.length) pos++ // skip ']'
            }
            skipSpaces()
            val text = src.substring(pos)
            return ImapResponse.Tagged(tag, status, code, text)
        }
    }
}
