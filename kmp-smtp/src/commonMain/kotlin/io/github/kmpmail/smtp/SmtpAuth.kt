package io.github.kmpmail.smtp

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * SMTP AUTH mechanisms (RFC 4616 PLAIN, LOGIN).
 */
sealed class SmtpAuth {
    abstract val mechanismName: String

    // Called by SmtpSession after confirming the server advertises this mechanism.
    internal abstract suspend fun execute(session: SmtpSession)

    /**
     * AUTH PLAIN (RFC 4616): sends credentials in one base64-encoded step.
     * Token format: \0username\0password
     */
    class Plain(val username: String, val password: String) : SmtpAuth() {
        override val mechanismName = "PLAIN"

        @OptIn(ExperimentalEncodingApi::class)
        override suspend fun execute(session: SmtpSession) {
            val token = "\u0000$username\u0000$password".encodeToByteArray()
            val encoded = Base64.encode(token)
            val response = session.sendCommand("AUTH PLAIN $encoded")
            if (response.code != 235) throw SmtpException(response.code, response.message)
        }
    }

    /**
     * AUTH LOGIN: two-step challenge/response.
     * Server sends 334 twice; client responds with base64 username then password.
     */
    class Login(val username: String, val password: String) : SmtpAuth() {
        override val mechanismName = "LOGIN"

        @OptIn(ExperimentalEncodingApi::class)
        override suspend fun execute(session: SmtpSession) {
            session.sendRaw("AUTH LOGIN")
            val c1 = session.readResponse()
            if (c1.code != 334) throw SmtpException(c1.code, "Expected 334 challenge, got: ${c1.message}")

            session.sendRaw(Base64.encode(username.encodeToByteArray()))
            val c2 = session.readResponse()
            if (c2.code != 334) throw SmtpException(c2.code, "Expected 334 challenge, got: ${c2.message}")

            session.sendRaw(Base64.encode(password.encodeToByteArray()))
            val response = session.readResponse()
            if (response.code != 235) throw SmtpException(response.code, response.message)
        }
    }
}
