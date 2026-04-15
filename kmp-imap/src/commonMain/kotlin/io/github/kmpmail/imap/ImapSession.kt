package io.github.kmpmail.imap

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

    suspend fun noop() {
        val tag = cmd.nextTag()
        transport.writeLine("$tag ${ImapCommand.noop()}")
        processUntilTagged(tag)
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
