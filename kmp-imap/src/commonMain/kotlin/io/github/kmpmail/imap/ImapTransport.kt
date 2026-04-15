package io.github.kmpmail.imap

/**
 * Low-level line I/O over a TCP or TLS socket.
 *
 * The transport is responsible for assembling complete response lines,
 * including fetching literal continuations ({n}\r\n<bytes>) and
 * embedding them inline so [ImapParser] sees a single flat string.
 */
interface ImapTransport {
    /**
     * Read one complete server response, including any literal data.
     * Returns the line with literals inlined as "{n}\r\n<content>".
     */
    suspend fun readResponse(): String

    /** Write [line] followed by CRLF and flush immediately. */
    suspend fun writeLine(line: String)

    /** Close the underlying connection. */
    fun close()
}

/** Transport that can upgrade an existing plain connection to TLS (STARTTLS). */
interface ImapTlsUpgradeable : ImapTransport {
    suspend fun upgradeToTls()
}
