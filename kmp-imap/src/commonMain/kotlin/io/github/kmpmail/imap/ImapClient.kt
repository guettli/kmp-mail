package io.github.kmpmail.imap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.CoroutineContext

/**
 * High-level IMAP client.
 *
 * Usage:
 * ```kotlin
 * val client = ImapClient {
 *     host     = "imap.example.com"
 *     port     = 993
 *     security = ImapSecurity.TLS
 *     username = "user@example.com"
 *     password = "secret"
 * }
 * client.connect()
 * val info = client.select("INBOX")
 * val uids = client.search("UNSEEN")
 * client.disconnect()
 * ```
 */
class ImapClient(private val config: ImapConfig) {

    private var session: ImapSession? = null
    private var connection: ImapConnection? = null

    suspend fun connect() {
        val conn = ImapConnection(config.host, config.port, config.coroutineContext)
        conn.open(directTls = config.security == ImapSecurity.TLS)

        val sess = ImapSession(conn)
        sess.readGreeting()
        sess.capability()

        if (config.security == ImapSecurity.STARTTLS) {
            if ("STARTTLS" !in sess.capabilities) {
                throw ImapException("STARTTLS required but not offered by ${config.host}")
            }
            // Send STARTTLS command
            val tag = sess.transport.let {
                val cmd = ImapCommand()
                val t = cmd.nextTag()
                it.writeLine("$t STARTTLS")
                t
            }
            // Re-capability after upgrade is handled by the caller re-invoking capability()
            conn.upgradeToTls()
            sess.capability()
        }

        sess.login(config.username, config.password)

        connection = conn
        session = sess
    }

    // -------------------------------------------------------------------------
    // Mailbox operations — delegate to session
    // -------------------------------------------------------------------------

    suspend fun select(mailboxName: String): MailboxInfo =
        requireSession().select(mailboxName)

    suspend fun examine(mailboxName: String): MailboxInfo =
        requireSession().examine(mailboxName)

    suspend fun listMailboxes(reference: String = "", pattern: String = "*"): List<MailboxListEntry> =
        requireSession().list(reference, pattern)

    suspend fun close() = requireSession().close()

    // -------------------------------------------------------------------------
    // Message operations
    // -------------------------------------------------------------------------

    suspend fun search(vararg criteria: String): List<Long> =
        requireSession().search(*criteria)

    suspend fun uidFetch(uidSet: String, items: String): List<ImapResponse.Untagged> =
        requireSession().uidFetch(uidSet, items)

    suspend fun fetchMessages(uidSet: String): List<FetchedMessage> =
        requireSession().fetchMessages(uidSet)

    suspend fun uidStore(uidSet: String, item: String, flags: String) =
        requireSession().uidStore(uidSet, item, flags)

    suspend fun appendMessage(
        mailbox: String,
        flags: List<String> = emptyList(),
        internalDate: String? = null,
        message: ByteArray,
    ) = requireSession().append(mailbox, flags, internalDate, message)

    suspend fun noop() = requireSession().noop()

    // -------------------------------------------------------------------------
    // IDLE
    // -------------------------------------------------------------------------

    fun idle(): Flow<ImapEvent> = requireSession().idle()

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    suspend fun disconnect() {
        try { session?.logout() } finally {
            session = null
            connection = null
        }
    }

    private fun requireSession(): ImapSession =
        session ?: error("Not connected — call connect() first")

    companion object {
        operator fun invoke(block: ImapConfig.Builder.() -> Unit): ImapClient =
            ImapClient(ImapConfig.Builder().apply(block).build())

        /** Create a client with a pre-built [session] for unit testing (no real TCP connection). */
        internal fun withSession(session: ImapSession): ImapClient {
            val client = ImapClient(ImapConfig.Builder().build())
            client.session = session
            return client
        }
    }
}

// -------------------------------------------------------------------------
// Configuration
// -------------------------------------------------------------------------

enum class ImapSecurity { NONE, STARTTLS, TLS }

data class ImapConfig(
    val host: String,
    val port: Int,
    val security: ImapSecurity,
    val username: String,
    val password: String,
    val coroutineContext: CoroutineContext,
) {
    class Builder {
        var host: String = "localhost"
        var port: Int = 993
        var security: ImapSecurity = ImapSecurity.TLS
        var username: String = ""
        var password: String = ""
        var coroutineContext: CoroutineContext = Dispatchers.Default

        fun build() = ImapConfig(host, port, security, username, password, coroutineContext)
    }
}
