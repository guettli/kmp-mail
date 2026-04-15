package io.github.kmpmail.imap

/**
 * Generates sequenced command tags and serialises IMAP commands.
 * Tags follow the pattern A001, A002, … per RFC 3501 section 2.2.1.
 */
class ImapCommand {
    private var counter = 0

    fun nextTag(): String = "A%03d".padStart(4, 'A').let {
        "A${(++counter).toString().padStart(3, '0')}"
    }

    companion object {
        // Command builders
        fun capability()                        = "CAPABILITY"
        fun login(user: String, pass: String)   = "LOGIN ${quote(user)} ${quote(pass)}"
        fun logout()                            = "LOGOUT"
        fun select(mailbox: String)             = "SELECT ${quote(mailbox)}"
        fun examine(mailbox: String)            = "EXAMINE ${quote(mailbox)}"
        fun list(ref: String, pattern: String)  = "LIST ${quote(ref)} ${quote(pattern)}"
        fun search(vararg criteria: String)     = "SEARCH ${criteria.joinToString(" ")}"
        fun uidSearch(vararg criteria: String)  = "UID SEARCH ${criteria.joinToString(" ")}"
        fun fetch(seqSet: String, items: String)    = "FETCH $seqSet $items"
        fun uidFetch(uidSet: String, items: String) = "UID FETCH $uidSet $items"
        fun store(seqSet: String, item: String, flags: String) = "STORE $seqSet $item $flags"
        fun uidStore(uidSet: String, item: String, flags: String) = "UID STORE $uidSet $item $flags"
        fun copy(seqSet: String, mailbox: String)    = "COPY $seqSet ${quote(mailbox)}"
        fun uidCopy(uidSet: String, mailbox: String) = "UID COPY $uidSet ${quote(mailbox)}"
        fun idle()  = "IDLE"
        fun done()  = "DONE"
        fun noop()  = "NOOP"
        fun close() = "CLOSE"
        fun expunge() = "EXPUNGE"

        fun append(mailbox: String, flags: String?, size: Int): String {
            val flagPart = if (flags != null) " ($flags)" else ""
            return "APPEND ${quote(mailbox)}$flagPart {$size}"
        }

        // Wrap in quotes if needed; always quote for simplicity.
        fun quote(s: String): String {
            val escaped = s.replace("\\", "\\\\").replace("\"", "\\\"")
            return "\"$escaped\""
        }
    }
}
