package io.github.kmpmail.smtp

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

/**
 * High-level SMTP client.
 *
 * Usage:
 * ```kotlin
 * val client = SmtpClient {
 *     host        = "smtp.example.com"
 *     port        = 587
 *     security    = SmtpSecurity.STARTTLS
 *     credentials = SmtpCredentials("user@example.com", "password")
 * }
 * client.connect()
 * client.send(rawMessageBytes, from = "me@example.com", to = listOf("you@example.com"))
 * client.disconnect()
 * ```
 */
class SmtpClient(private val config: SmtpConfig) {

    private var session: SmtpSession? = null
    private var connection: SmtpConnection? = null

    suspend fun connect() {
        val conn = SmtpConnection(config.host, config.port, config.coroutineContext)
        conn.open(directTls = config.security == SmtpSecurity.TLS)

        val sess = SmtpSession(conn)
        sess.greet()

        when (config.security) {
            SmtpSecurity.TLS, SmtpSecurity.NONE -> sess.ehlo(config.localDomain)
            SmtpSecurity.STARTTLS -> {
                val caps = sess.ehlo(config.localDomain)
                if (caps.supportsStartTls) {
                    sess.startTls(config.localDomain) // re-issues EHLO internally
                } else {
                    throw SmtpException(-1, "STARTTLS required but not offered by ${config.host}")
                }
            }
        }

        config.credentials?.let { creds ->
            val caps = sess.capabilities
            when {
                caps.supportsAuth("PLAIN") -> sess.auth(SmtpAuth.Plain(creds.username, creds.password))
                caps.supportsAuth("LOGIN") -> sess.auth(SmtpAuth.Login(creds.username, creds.password))
                else -> throw SmtpException(-1, "No supported AUTH mechanism in: ${caps.authMechanisms}")
            }
        }

        connection = conn
        session = sess
    }

    /**
     * Send [rawMessage] (complete RFC 5322 message bytes including headers).
     *
     * [from] is the envelope sender (MAIL FROM). [to] is the list of envelope
     * recipients (RCPT TO). These may differ from the From/To headers in the
     * message itself.
     */
    suspend fun send(rawMessage: ByteArray, from: String, to: List<String>) {
        val sess = session ?: error("Not connected — call connect() first")
        sess.mailFrom(from)
        for (recipient in to) sess.rcptTo(recipient)
        sess.data(rawMessage)
    }

    suspend fun disconnect() {
        try { session?.quit() } finally {
            session = null
            connection = null
        }
    }

    companion object {
        operator fun invoke(block: SmtpConfig.Builder.() -> Unit): SmtpClient =
            SmtpClient(SmtpConfig.Builder().apply(block).build())

        /** Create a client with a pre-built [session] for unit testing (no real TCP connection). */
        internal fun withSession(session: SmtpSession): SmtpClient {
            val client = SmtpClient(SmtpConfig.Builder().build())
            client.session = session
            return client
        }
    }
}

// -------------------------------------------------------------------------
// Configuration
// -------------------------------------------------------------------------

enum class SmtpSecurity { NONE, STARTTLS, TLS }

data class SmtpCredentials(val username: String, val password: String)

data class SmtpConfig(
    val host: String,
    val port: Int,
    val security: SmtpSecurity,
    val credentials: SmtpCredentials?,
    val localDomain: String,
    val coroutineContext: CoroutineContext,
) {
    class Builder {
        var host: String = "localhost"
        var port: Int = 587
        var security: SmtpSecurity = SmtpSecurity.STARTTLS
        var credentials: SmtpCredentials? = null
        var localDomain: String = "localhost"
        var coroutineContext: CoroutineContext = Dispatchers.Default

        fun credentials(username: String, password: String) {
            credentials = SmtpCredentials(username, password)
        }

        fun build() = SmtpConfig(host, port, security, credentials, localDomain, coroutineContext)
    }
}
