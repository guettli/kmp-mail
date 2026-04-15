package io.github.kmpmail.smtp

/**
 * In-memory [SmtpTransport] for unit tests.
 *
 * Feed it the lines a real server would send; after the test inspect
 * [clientLines] to assert what the client actually sent.
 */
class MockSmtpTransport(serverLines: List<String>) : SmtpTransport {

    private val serverQueue = ArrayDeque(serverLines)

    /** Lines written by the client (via writeLine), in order. */
    val clientLines = mutableListOf<String>()

    /** Raw bytes written by the client (via writeRaw). */
    val clientRaw = mutableListOf<ByteArray>()

    override suspend fun readLine(): String =
        serverQueue.removeFirstOrNull() ?: error("Server script exhausted — no more lines to read")

    override suspend fun writeLine(line: String) { clientLines.add(line) }
    override suspend fun writeRaw(bytes: ByteArray) { clientRaw.add(bytes) }
    override suspend fun flush() {}
    override fun close() {}
}

/** [SmtpTransport] with [TlsUpgradeable] that records TLS upgrade calls. */
class MockTlsTransport(serverLines: List<String>) : TlsUpgradeable {

    private val serverQueue = ArrayDeque(serverLines)
    val clientLines = mutableListOf<String>()
    val clientRaw = mutableListOf<ByteArray>()
    var tlsUpgraded = false

    override suspend fun readLine(): String =
        serverQueue.removeFirstOrNull() ?: error("Server script exhausted")

    override suspend fun writeLine(line: String) { clientLines.add(line) }
    override suspend fun writeRaw(bytes: ByteArray) { clientRaw.add(bytes) }
    override suspend fun flush() {}
    override fun close() {}
    override suspend fun upgradeToTls() { tlsUpgraded = true }
}
