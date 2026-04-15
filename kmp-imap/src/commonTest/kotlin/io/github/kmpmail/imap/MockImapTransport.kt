package io.github.kmpmail.imap

/**
 * In-memory [ImapTransport] for unit tests.
 *
 * Pre-load it with the lines a real server would send; after the test
 * inspect [clientLines] to assert what the client actually wrote.
 */
class MockImapTransport(serverLines: List<String>) : ImapTransport {

    private val serverQueue = ArrayDeque(serverLines)

    /** Lines written by the client (via writeLine), in order. */
    val clientLines = mutableListOf<String>()

    override suspend fun readResponse(): String =
        serverQueue.removeFirstOrNull() ?: error("Server script exhausted — no more lines to read")

    override suspend fun writeLine(line: String) { clientLines.add(line) }

    override fun close() {}
}

/** [ImapTransport] with [ImapTlsUpgradeable] that records TLS upgrade calls. */
class MockTlsImapTransport(serverLines: List<String>) : ImapTlsUpgradeable {

    private val serverQueue = ArrayDeque(serverLines)
    val clientLines = mutableListOf<String>()
    var tlsUpgraded = false

    override suspend fun readResponse(): String =
        serverQueue.removeFirstOrNull() ?: error("Server script exhausted")

    override suspend fun writeLine(line: String) { clientLines.add(line) }
    override fun close() {}
    override suspend fun upgradeToTls() { tlsUpgraded = true }
}
