package io.github.kmpmail.imap

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Ktor-backed implementation of [ImapTransport] with [ImapTlsUpgradeable] support.
 *
 * Literal continuations (`{n}\r\n<n bytes>`) are fetched inline so that
 * [ImapParser] receives a single flat string per server response.
 *
 * Usage:
 * ```kotlin
 * val conn = ImapConnection("imap.example.com", 993)
 * conn.open(directTls = true)
 * val session = ImapSession(conn)
 * session.readGreeting()
 * ```
 */
class ImapConnection(
    private val host: String,
    private val port: Int,
    private val coroutineContext: CoroutineContext = Dispatchers.Default,
) : ImapTlsUpgradeable {

    private val selectorManager = SelectorManager(coroutineContext)
    private var socket: Socket? = null
    private var readChannel: ByteReadChannel? = null
    private var writeChannel: ByteWriteChannel? = null

    suspend fun open(directTls: Boolean = false) {
        val plain = aSocket(selectorManager).tcp().connect(host, port)
        socket = if (directTls) {
            plain.tls(coroutineContext) { serverName = host }
        } else {
            plain
        }
        initChannels()
    }

    override suspend fun upgradeToTls() {
        val current = socket ?: error("Socket not open")
        socket = current.tls(coroutineContext) { serverName = host }
        initChannels()
    }

    private fun initChannels() {
        readChannel  = socket!!.openReadChannel()
        writeChannel = socket!!.openWriteChannel(autoFlush = false)
    }

    /**
     * Read one complete server response.
     *
     * The IMAP protocol allows literal strings in responses: if a line ends
     * with `{n}`, the server immediately follows with n bytes of literal data
     * (without CRLF between). We read those bytes and embed them in the line
     * as `{n}\r\n<content>` so [ImapParser] can find them at the cursor position.
     */
    override suspend fun readResponse(): String {
        val rc = readChannel ?: error("Not connected")
        val line = rc.readUTF8Line() ?: throw ImapException("Connection closed by server")
        return assembleLine(rc, line)
    }

    /**
     * Recursively fetch literal continuations if the line ends with `{n}`.
     */
    private suspend fun assembleLine(rc: ByteReadChannel, line: String): String {
        val trimmed = line.trimEnd('\r', '\n')
        val literalMatch = Regex("""\{(\d+)\}$""").find(trimmed) ?: return trimmed

        val count = literalMatch.groupValues[1].toInt()
        val literalBytes = ByteArray(count)
        var remaining = count
        var offset = 0
        while (remaining > 0) {
            val n = rc.readAvailable(literalBytes, offset, remaining)
            if (n < 0) throw ImapException("Connection closed during literal read")
            offset += n
            remaining -= n
        }
        val literalText = literalBytes.decodeToString()

        // Read the next line that follows the literal
        val nextLine = rc.readUTF8Line() ?: ""
        val combined = "$trimmed\r\n$literalText"
        return assembleLine(rc, combined + nextLine)
    }

    override suspend fun writeLine(line: String) {
        val wc = writeChannel ?: error("Not connected")
        wc.writeStringUtf8("$line\r\n")
        wc.flush()
    }

    override fun close() {
        socket?.close()
        selectorManager.close()
    }
}
