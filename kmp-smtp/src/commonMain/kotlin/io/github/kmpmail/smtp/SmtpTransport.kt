package io.github.kmpmail.smtp

/**
 * Low-level line I/O abstraction over a TCP (or TLS) socket.
 *
 * Keeping this as an interface lets [SmtpSession] be tested in commonTest
 * without a real network connection.
 */
interface SmtpTransport {
    /** Read one CRLF-terminated line, returned without the line ending. */
    suspend fun readLine(): String

    /** Write [line] followed by CRLF and flush. */
    suspend fun writeLine(line: String)

    /** Write raw bytes without any line-ending transformation. */
    suspend fun writeRaw(bytes: ByteArray)

    /** Flush any buffered output. */
    suspend fun flush()

    /** Close the underlying connection. */
    fun close()
}

/**
 * Implemented by transports that support in-place upgrade to TLS (STARTTLS).
 */
interface TlsUpgradeable : SmtpTransport {
    suspend fun upgradeToTls()
}
