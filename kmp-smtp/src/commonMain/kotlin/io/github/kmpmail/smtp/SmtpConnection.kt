package io.github.kmpmail.smtp

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Ktor-backed implementation of [SmtpTransport] with [TlsUpgradeable] support.
 *
 * Manages the raw TCP (and optionally TLS) socket. After [open], call
 * [upgradeToTls] to perform the STARTTLS upgrade; existing channels are
 * replaced with TLS-wrapped ones automatically.
 */
class SmtpConnection(
    private val host: String,
    private val port: Int,
    private val coroutineContext: CoroutineContext = Dispatchers.IO,
) : TlsUpgradeable {

    private val selectorManager = SelectorManager(coroutineContext)
    private var socket: Socket? = null
    private var readChannel: ByteReadChannel? = null
    private var writeChannel: ByteWriteChannel? = null

    suspend fun open(directTls: Boolean = false) {
        val plain = aSocket(selectorManager).tcp().connect(host, port)
        if (directTls) {
            socket = plain.tls(coroutineContext) { serverName = host }
        } else {
            socket = plain
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

    override suspend fun readLine(): String =
        (readChannel ?: error("Not connected")).readUTF8Line()
            ?: throw SmtpException(-1, "Connection closed by server")

    override suspend fun writeLine(line: String) {
        (writeChannel ?: error("Not connected")).writeStringUtf8("$line\r\n")
    }

    override suspend fun writeRaw(bytes: ByteArray) {
        (writeChannel ?: error("Not connected")).writeFully(bytes)
    }

    override suspend fun flush() {
        writeChannel?.flush()
    }

    override fun close() {
        socket?.close()
        selectorManager.close()
    }
}
