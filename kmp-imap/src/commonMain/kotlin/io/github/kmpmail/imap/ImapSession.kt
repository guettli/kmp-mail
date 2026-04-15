package io.github.kmpmail.imap

import io.github.kmpmail.mime.MimeMessage
import io.github.kmpmail.mime.MimeParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * IMAP4rev1 protocol session (RFC 3501).
 *
 * Wraps an [ImapTransport] and drives the protocol state machine.
 * All methods are suspend functions; designed for single-coroutine use.
 *
 * State transitions:
 *   NOT-AUTHENTICATED → login() → AUTHENTICATED
 *   AUTHENTICATED     → select()/examine() → SELECTED
 *   SELECTED          → close() → AUTHENTICATED
 *   ANY               → logout() → LOGOUT
 */
class ImapSession(internal val transport: ImapTransport) {

    private val cmd = ImapCommand()

    /** Capabilities advertised by the server (populated after CAPABILITY or login). */
    var capabilities: Set<String> = emptySet()
        private set

    /** Mailbox state populated after SELECT or EXAMINE. */
    var mailbox: MailboxInfo? = null
        private set

    // -------------------------------------------------------------------------
    // Session setup
    // -------------------------------------------------------------------------

    /** Read and validate the server greeting. */
    suspend fun readGreeting(): ImapResponse.Untagged {
        val r = readOne() as? ImapResponse.Untagged
            ?: throw ImapException("Expected untagged greeting")
        if (r.keyword == "BYE") throw ImapException("Server rejected connection: ${r.text}")
        return r
    }

    /** Send CAPABILITY and cache the result. */
    suspend fun capability(): Set<String> {
        val tag = cmd.nextTag()
        transport.writeLine("$tag ${ImapCommand.capability()}")
        val caps = mutableSetOf<String>()
        processUntilTagged(tag) { r ->
            if (r is ImapResponse.Untagged && r.keyword == "CAPABILITY") {
                caps += r.text.trim().uppercase().split(' ')
            }
        }
        capabilities = caps
        return caps
    }

    /** Authenticate with LOGIN command (plaintext, use only over TLS). */
    suspend fun login(username: String, password: String) {
        val tag = cmd.nextTag()
        transport.writeLine("$tag ${ImapCommand.login(username, password)}")
        val tagged = processUntilTagged(tag) { r ->
            if (r is ImapResponse.Untagged && r.keyword == "CAPABILITY") {
                capabilities = r.text.trim().uppercase().split(' ').toSet()
            }
        }
        if (!tagged.isOk) throw ImapNoException("LOGIN", tagged.text)
    }

    suspend fun logout() {
        val tag = cmd.nextTag()
        transport.writeLine("$tag ${ImapCommand.logout()}")
        processUntilTagged(tag)
        transport.close()
    }

    // -------------------------------------------------------------------------
    // Mailbox operations
    // -------------------------------------------------------------------------

    /** SELECT a mailbox for read-write access. Returns mailbox metadata. */
    suspend fun select(mailboxName: String): MailboxInfo {
        val tag = cmd.nextTag()
        transport.writeLine("$tag ${ImapCommand.select(mailboxName)}")
        val info = MailboxInfo(mailboxName, readWrite = true)
        processUntilTagged(tag) { r -> info.absorb(r) }
        mailbox = info
        return info
    }

    /** EXAMINE a mailbox for read-only access. */
    suspend fun examine(mailboxName: String): MailboxInfo {
        val tag = cmd.nextTag()
        transport.writeLine("$tag ${ImapCommand.examine(mailboxName)}")
        val info = MailboxInfo(mailboxName, readWrite = false)
        processUntilTagged(tag) { r -> info.absorb(r) }
        mailbox = info
        return info
    }

    suspend fun close() {
        val tag = cmd.nextTag()
        transport.writeLine("$tag ${ImapCommand.close()}")
        processUntilTagged(tag)
        mailbox = null
    }

    // -------------------------------------------------------------------------
    // Message operations
    // -------------------------------------------------------------------------

    suspend fun search(vararg criteria: String): List<Long> {
        val tag = cmd.nextTag()
        transport.writeLine("$tag ${ImapCommand.uidSearch(*criteria)}")
        val uids = mutableListOf<Long>()
        processUntilTagged(tag) { r ->
            if (r is ImapResponse.Untagged && r.keyword == "SEARCH") {
                uids += r.text.trim().split(' ').mapNotNull { it.toLongOrNull() }
            }
        }
        return uids
    }

    /**
     * UID FETCH the given UID set with [items] (e.g. "(FLAGS BODY.PEEK[HEADER])").
     * Returns raw untagged FETCH response lines for the caller to parse.
     */
    suspend fun uidFetch(uidSet: String, items: String): List<ImapResponse.Untagged> {
        val tag = cmd.nextTag()
        transport.writeLine("$tag ${ImapCommand.uidFetch(uidSet, items)}")
        val results = mutableListOf<ImapResponse.Untagged>()
        processUntilTagged(tag) { r ->
            if (r is ImapResponse.Untagged && r.keyword == "FETCH") results.add(r)
        }
        return results
    }

    suspend fun uidStore(uidSet: String, item: String, flags: String) {
        val tag = cmd.nextTag()
        transport.writeLine("$tag ${ImapCommand.uidStore(uidSet, item, flags)}")
        processUntilTagged(tag)
    }

    suspend fun noop() {        val tag = cmd.nextTag()
        transport.writeLine("$tag ${ImapCommand.noop()}")
        processUntilTagged(tag)
    }

    /** EXPUNGE permanently removes all messages with the \Deleted flag. */
    suspend fun expunge() {
        val tag = cmd.nextTag()
        transport.writeLine("$tag ${ImapCommand.expunge()}")
        processUntilTagged(tag)
    }

    /**
     * UID EXPUNGE removes only the specified UIDs that have \Deleted set.
     * Requires the UIDPLUS capability (RFC 4315).
     */
    suspend fun uidExpunge(uidSet: String) {
        val tag = cmd.nextTag()
        transport.writeLine("$tag ${ImapCommand.uidExpunge(uidSet)}")
        processUntilTagged(tag)
    }

    // -------------------------------------------------------------------------
    // Mailbox management
    // -------------------------------------------------------------------------

    /** CREATE a new mailbox on the server. */
    suspend fun createMailbox(name: String) {
        val tag = cmd.nextTag()
        transport.writeLine("$tag ${ImapCommand.create(name)}")
        processUntilTagged(tag)
    }

    /** DELETE a mailbox from the server. */
    suspend fun deleteMailbox(name: String) {
        val tag = cmd.nextTag()
        transport.writeLine("$tag ${ImapCommand.delete(name)}")
        processUntilTagged(tag)
    }

    /** RENAME a mailbox on the server. */
    suspend fun renameMailbox(from: String, to: String) {
        val tag = cmd.nextTag()
        transport.writeLine("$tag ${ImapCommand.rename(from, to)}")
        processUntilTagged(tag)
    }

    /** LIST mailboxes matching [pattern] under [reference]. */
    suspend fun list(reference: String = "", pattern: String = "*"): List<MailboxListEntry> {
        val tag = cmd.nextTag()
        transport.writeLine("$tag ${ImapCommand.list(reference, pattern)}")
        val results = mutableListOf<MailboxListEntry>()
        processUntilTagged(tag) { r ->
            if (r is ImapResponse.Untagged && r.keyword == "LIST") {
                parseListResponse(r)?.let { results.add(it) }
            }
        }
        return results
    }

    /**
     * High-level UID FETCH that returns fully-parsed [FetchedMessage] objects.
     *
     * Fetches UID, FLAGS, BODY[HEADER], and BODY[TEXT] for each message in
     * [uidSet], concatenates header + "\r\n" + text, and runs [MimeParser].
     */
    suspend fun fetchMessages(uidSet: String): List<FetchedMessage> {
        val raw = uidFetch(uidSet, "(UID FLAGS BODY.PEEK[HEADER] BODY.PEEK[TEXT])")
        return raw.mapNotNull { r ->
            val attrList = r.values.firstOrNull()?.asList() ?: return@mapNotNull null
            val attrs = extractFetchAttrs(attrList)
            val uid = (attrs["UID"] as? ImapValue.Num)?.value ?: return@mapNotNull null
            val flags = (attrs["FLAGS"] as? ImapValue.Lst)
                ?.items?.mapNotNull { it.asString() } ?: emptyList()
            val headerStr = (attrs["BODY[HEADER]"] as? ImapValue.Str)?.value ?: ""
            val textStr   = (attrs["BODY[TEXT]"]   as? ImapValue.Str)?.value ?: ""
            val combined  = "$headerStr\r\n$textStr"
            val message   = MimeParser.parse(combined)
            FetchedMessage(uid, flags, message)
        }
    }

    /**
     * APPEND a [message] to [mailbox].
     *
     * Sends the APPEND command, waits for the server continuation, writes the
     * message bytes, then waits for the tagged OK.
     */
    suspend fun append(
        mailbox: String,
        flags: List<String> = emptyList(),
        internalDate: String? = null,
        message: ByteArray,
    ) {
        val tag = cmd.nextTag()
        val flagPart = if (flags.isEmpty()) "" else " (${flags.joinToString(" ")})"
        val datePart = if (internalDate != null) " \"$internalDate\"" else ""
        transport.writeLine(
            "$tag APPEND ${ImapCommand.quote(mailbox)}$flagPart$datePart {${message.size}}"
        )
        val cont = readOne()
        if (cont !is ImapResponse.Continuation) {
            throw ImapException("Expected continuation after APPEND, got: $cont")
        }
        transport.writeLine(message.decodeToString())
        processUntilTagged(tag)
    }

    // -------------------------------------------------------------------------
    // FETCH / LIST parsing helpers
    // -------------------------------------------------------------------------

    /**
     * Given the flat attribute list from a FETCH response, build a map of
     * upper-cased attribute name → value.  BODY section attributes like
     * `BODY [HEADER] value` are stored under the key `"BODY[HEADER]"`.
     */
    private fun extractFetchAttrs(items: List<ImapValue>): Map<String, ImapValue> {
        val result = mutableMapOf<String, ImapValue>()
        var i = 0
        while (i < items.size - 1) {
            val item = items[i]
            if (item is ImapValue.Atom) {
                val name = item.value.uppercase()
                val next = items.getOrNull(i + 1)
                if (name == "BODY" && next is ImapValue.Atom && next.value.startsWith("[")) {
                    val section = next.value.uppercase()
                    val value = items.getOrNull(i + 2)
                    if (value != null) {
                        result["BODY$section"] = value
                        i += 3
                        continue
                    }
                }
                if (next != null) {
                    result[name] = next
                    i += 2
                    continue
                }
            }
            i++
        }
        return result
    }

    /** Parse one `* LIST (attrs) delimiter name` untagged response into a [MailboxListEntry]. */
    private fun parseListResponse(r: ImapResponse.Untagged): MailboxListEntry? {
        val attrs = r.values.getOrNull(0)?.asList()
            ?.mapNotNull { it.asString() } ?: emptyList()
        val delimiter = r.values.getOrNull(1)?.asString()
        val name = r.values.getOrNull(2)?.asString() ?: return null
        return MailboxListEntry(attrs, delimiter, name)
    }

    // -------------------------------------------------------------------------
    // IDLE (RFC 2177)
    // -------------------------------------------------------------------------

    /**
     * Enter IDLE mode and emit server push events as a [Flow].
     * Collection suspends until the flow is cancelled, at which point DONE is sent.
     */
    fun idle(): Flow<ImapEvent> = flow {
        val tag = cmd.nextTag()
        transport.writeLine("$tag ${ImapCommand.idle()}")
        // Expect continuation "+"
        val cont = readOne()
        if (cont !is ImapResponse.Continuation) {
            throw ImapException("Expected continuation after IDLE, got: $cont")
        }
        try {
            while (true) {
                val r = readOne()
                if (r is ImapResponse.Untagged) {
                    ImapEvent.from(r)?.let { emit(it) }
                }
            }
        } finally {
            transport.writeLine(ImapCommand.done())
            // Drain until we see the tagged OK for IDLE
            try {
                while (true) {
                    val r = readOne()
                    if (r is ImapResponse.Tagged && r.tag == tag) break
                }
            } catch (_: Exception) { /* best-effort cleanup */ }
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    internal suspend fun readOne(): ImapResponse =
        ImapParser.parse(transport.readResponse())

    /**
     * Reads responses until the tagged completion for [tag].
     * Each untagged response is passed to [onUntagged].
     * Throws [ImapNoException] or [ImapBadException] on non-OK completion.
     */
    internal suspend fun processUntilTagged(
        tag: String,
        onUntagged: (ImapResponse) -> Unit = {},
    ): ImapResponse.Tagged {
        while (true) {
            val r = readOne()
            when {
                r is ImapResponse.Untagged -> onUntagged(r)
                r is ImapResponse.Tagged && r.tag == tag -> {
                    if (r.isNo)  throw ImapNoException(tag, r.text)
                    if (r.isBad) throw ImapBadException(tag, r.text)
                    return r
                }
                r is ImapResponse.Tagged -> onUntagged(r) // out-of-order tag (rare)
                else -> { /* continuation — ignore */ }
            }
        }
    }
}

// -------------------------------------------------------------------------
// Mailbox metadata
// -------------------------------------------------------------------------

data class MailboxInfo(
    val name: String,
    val readWrite: Boolean,
    var exists: Int = 0,
    var recent: Int = 0,
    var unseen: Int? = null,
    var uidValidity: Long? = null,
    var uidNext: Long? = null,
    var highestModSeq: Long? = null,
    var flags: List<String> = emptyList(),
    var permanentFlags: List<String> = emptyList(),
) {
    internal fun absorb(r: ImapResponse) {
        if (r !is ImapResponse.Untagged) return
        when (r.keyword) {
            "EXISTS"  -> r.number?.toInt()?.let { exists = it }
            "RECENT"  -> r.number?.toInt()?.let { recent = it }
            "FLAGS"   -> flags = r.values.firstOrNull()?.asList()
                ?.mapNotNull { it.asString() } ?: emptyList()
            "OK"      -> parseOkCode(r.text)
        }
    }

    private fun parseOkCode(text: String) {
        val trimmed = text.trim()
        if (!trimmed.startsWith("[")) return
        val end = trimmed.indexOf(']')
        if (end < 0) return
        val code = trimmed.substring(1, end).trim()
        when {
            code.startsWith("UNSEEN ")       -> unseen = code.removePrefix("UNSEEN ").trim().toIntOrNull()
            code.startsWith("UIDVALIDITY ")  -> uidValidity = code.removePrefix("UIDVALIDITY ").trim().toLongOrNull()
            code.startsWith("UIDNEXT ")      -> uidNext = code.removePrefix("UIDNEXT ").trim().toLongOrNull()
            code.startsWith("HIGHESTMODSEQ ")-> highestModSeq = code.removePrefix("HIGHESTMODSEQ ").trim().toLongOrNull()
            code.startsWith("PERMANENTFLAGS")-> {
                val flagText = code.removePrefix("PERMANENTFLAGS").trim()
                permanentFlags = flagText.removeSurrounding("(", ")").split(' ').filter { it.isNotEmpty() }
            }
        }
    }
}

// -------------------------------------------------------------------------
// IDLE events
// -------------------------------------------------------------------------

sealed class ImapEvent {
    data class Exists(val count: Int) : ImapEvent()
    data class Expunge(val seqno: Int) : ImapEvent()
    data class Fetch(val seqno: Int, val values: List<ImapValue>) : ImapEvent()

    companion object {
        fun from(r: ImapResponse.Untagged): ImapEvent? = when (r.keyword) {
            "EXISTS"  -> r.number?.toInt()?.let { Exists(it) }
            "EXPUNGE" -> r.number?.toInt()?.let { Expunge(it) }
            "FETCH"   -> r.number?.toInt()?.let { Fetch(it, r.values) }
            else      -> null
        }
    }
}

// -------------------------------------------------------------------------
// Mailbox list entry
// -------------------------------------------------------------------------

/**
 * One entry returned by a LIST response.
 *
 * @property attributes Server-advertised attributes, e.g. `["\\Noselect", "\\HasChildren"]`.
 * @property delimiter  Hierarchy delimiter character (e.g. `"/"`), or `null` (NIL) for flat namespaces.
 * @property name       Mailbox name, e.g. `"INBOX"` or `"Sent"`.
 */
data class MailboxListEntry(
    val attributes: List<String>,
    val delimiter: String?,
    val name: String,
)

// -------------------------------------------------------------------------
// Fetched message
// -------------------------------------------------------------------------

/**
 * A fully-parsed message returned by [ImapSession.fetchMessages].
 *
 * @property uid     The message UID.
 * @property flags   Server-side flags, e.g. `["\\Seen", "\\Flagged"]`.
 * @property message The RFC 5322 / MIME message parsed by [MimeParser].
 */
data class FetchedMessage(
    val uid: Long,
    val flags: List<String>,
    val message: MimeMessage,
)
