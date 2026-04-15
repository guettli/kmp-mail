package io.github.kmpmail.imap

/**
 * A parsed IMAP server response line.
 *
 * Three kinds (RFC 3501 section 2.2.2):
 *   - [Untagged]     "* ..." — server data or status
 *   - [Tagged]       "tag OK/NO/BAD ..." — completion of a client command
 *   - [Continuation] "+ ..." — server ready for client to continue
 */
sealed class ImapResponse {

    /** "* n EXISTS" / "* FETCH (...)" / "* OK [UIDVALIDITY n]" etc. */
    data class Untagged(
        val keyword: String,      // e.g. "EXISTS", "FETCH", "FLAGS", "OK", "BYE"
        val number: Long?,        // present for "* n EXISTS", "* n FETCH" etc.
        val text: String,         // rest of the line, unparsed
        val values: List<ImapValue> = emptyList(), // structured parse of text
    ) : ImapResponse()

    /** "A001 OK [resp-code] human text" */
    data class Tagged(
        val tag: String,
        val status: Status,       // OK / NO / BAD
        val code: String?,        // optional resp-code inside [...]
        val text: String,
    ) : ImapResponse() {
        val isOk: Boolean  get() = status == Status.OK
        val isNo: Boolean  get() = status == Status.NO
        val isBad: Boolean get() = status == Status.BAD
    }

    /** "+ go ahead" */
    data class Continuation(val text: String) : ImapResponse()

    enum class Status { OK, NO, BAD }
}
