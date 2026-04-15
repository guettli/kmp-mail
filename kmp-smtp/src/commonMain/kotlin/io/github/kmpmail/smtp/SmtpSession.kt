package io.github.kmpmail.smtp

/**
 * SMTP protocol state machine.
 *
 * Drives one SMTP conversation over a [SmtpTransport]. Designed to be used
 * from a single coroutine; not thread-safe.
 *
 * Typical flow:
 * ```
 * greet()
 * ehlo("client.example.com")
 * // optionally: startTls("client.example.com") then re-ehlo is automatic
 * auth(SmtpAuth.Plain("user", "pass"))
 * mailFrom("from@example.com")
 * rcptTo("to@example.com")
 * data(rawMessageBytes)
 * quit()
 * ```
 */
class SmtpSession(internal val transport: SmtpTransport) {

    private var _capabilities: SmtpCapabilities? = null
    val capabilities: SmtpCapabilities
        get() = _capabilities ?: error("EHLO has not been issued yet")

    // -------------------------------------------------------------------------
    // Protocol commands
    // -------------------------------------------------------------------------

    /** Read and validate the server's 220 greeting. */
    suspend fun greet(): SmtpResponse {
        val r = readResponse()
        if (r.code != 220) throw SmtpException(r.code, r.message)
        return r
    }

    /** Send EHLO and parse the advertised capabilities. */
    suspend fun ehlo(localDomain: String = "localhost"): SmtpCapabilities {
        val r = sendCommand("EHLO $localDomain")
        if (r.code != 250) throw SmtpException(r.code, r.message)
        return SmtpCapabilities.parse(localDomain, r).also { _capabilities = it }
    }

    /**
     * Negotiate STARTTLS (RFC 3207).
     *
     * Sends STARTTLS, upgrades the transport to TLS, then re-issues EHLO
     * (required by RFC 3207 section 4). Throws [SmtpException] if the server
     * refuses or the transport does not implement [TlsUpgradeable].
     */
    suspend fun startTls(localDomain: String = "localhost") {
        val r = sendCommand("STARTTLS")
        if (r.code != 220) throw SmtpException(r.code, "STARTTLS rejected: ${r.message}")
        val tls = transport as? TlsUpgradeable
            ?: error("Transport does not support TLS upgrade")
        tls.upgradeToTls()
        // RFC 3207 section 4: client MUST re-issue EHLO after TLS handshake.
        ehlo(localDomain)
    }

    /** Authenticate using [auth]. Verifies the mechanism is advertised first. */
    suspend fun auth(auth: SmtpAuth) {
        auth.execute(this)
    }

    /** Send MAIL FROM. */
    suspend fun mailFrom(address: String) {
        val r = sendCommand("MAIL FROM:<$address>")
        if (r.code != 250) throw SmtpException(r.code, r.message)
    }

    /** Send RCPT TO. */
    suspend fun rcptTo(address: String) {
        val r = sendCommand("RCPT TO:<$address>")
        if (r.code != 250) throw SmtpException(r.code, r.message)
    }

    /**
     * Send the DATA command followed by [message] bytes.
     *
     * Dot-stuffs the body per RFC 5321 section 4.5.2 and appends the
     * terminating sequence.
     */
    suspend fun data(message: ByteArray) {
        val r354 = sendCommand("DATA")
        if (r354.code != 354) throw SmtpException(r354.code, r354.message)

        transport.writeRaw(dotStuff(message))
        transport.flush()

        val r250 = readResponse()
        if (r250.code != 250) throw SmtpException(r250.code, r250.message)
    }

    /** Send QUIT and close the transport. */
    suspend fun quit() {
        try { sendCommand("QUIT") } finally { transport.close() }
    }

    // -------------------------------------------------------------------------
    // Internal primitives (package-private for SmtpAuth)
    // -------------------------------------------------------------------------

    internal suspend fun sendCommand(command: String): SmtpResponse {
        transport.writeLine(command)
        transport.flush()
        return readResponse()
    }

    internal suspend fun sendRaw(line: String) {
        transport.writeLine(line)
        transport.flush()
    }

    internal suspend fun readResponse(): SmtpResponse {
        val lines = mutableListOf<String>()
        var code = -1
        while (true) {
            val line = transport.readLine()
            if (line.length < 3) throw SmtpException(-1, "Malformed server response: '$line'")
            code = line.substring(0, 3).toIntOrNull()
                ?: throw SmtpException(-1, "Non-numeric response code: '$line'")
            val text = if (line.length > 4) line.substring(4) else ""
            lines.add(text)
            // space after code = final line; hyphen = continuation
            if (line.length < 4 || line[3] == ' ') break
        }
        return SmtpResponse(code, lines)
    }

    // -------------------------------------------------------------------------
    // Dot-stuffing (RFC 5321 section 4.5.2)
    // -------------------------------------------------------------------------

    private fun dotStuff(message: ByteArray): ByteArray {
        val out = mutableListOf<Byte>()
        var atLineStart = true
        for (byte in message) {
            val c = byte.toInt() and 0xFF
            if (atLineStart && c == '.'.code) out.add('.'.code.toByte())
            out.add(byte)
            atLineStart = c == '\n'.code
        }
        // Ensure we end at beginning of a line before the terminating dot.
        if (!atLineStart) {
            out.add('\r'.code.toByte())
            out.add('\n'.code.toByte())
        }
        out.add('.'.code.toByte())
        out.add('\r'.code.toByte())
        out.add('\n'.code.toByte())
        return out.toByteArray()
    }
}
