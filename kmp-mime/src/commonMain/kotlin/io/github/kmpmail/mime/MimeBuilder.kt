package io.github.kmpmail.mime

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

/**
 * Fluent builder that produces a well-formed RFC 5322 / MIME message.
 *
 * Usage:
 * ```
 * val bytes: ByteArray = buildMime {
 *     from("Alice <alice@example.com>")
 *     to("Bob <bob@example.com>")
 *     subject("Hello")
 *     textBody("Hello, World!")
 * }
 * ```
 */
class MimeBuilder {
    private val extraHeaders = mutableListOf<Pair<String, String>>()
    private var textBodyContent: String? = null
    private var htmlBodyContent: String? = null
    private val attachments = mutableListOf<Attachment>()

    // --- Header DSL ---

    fun header(name: String, value: String): MimeBuilder { extraHeaders.add(name to value); return this }
    fun from(value: String): MimeBuilder     = header("From", value)
    fun to(value: String): MimeBuilder       = header("To", value)
    fun cc(value: String): MimeBuilder       = header("Cc", value)
    fun replyTo(value: String): MimeBuilder  = header("Reply-To", value)
    fun date(value: String): MimeBuilder     = header("Date", value)
    fun messageId(value: String): MimeBuilder = header("Message-ID", value)

    /** Encodes [value] with RFC 2047 if it contains non-ASCII characters. */
    fun subject(value: String): MimeBuilder = header("Subject", HeaderEncoding.encodeIfNeeded(value))

    // --- Body DSL ---

    fun textBody(text: String): MimeBuilder { textBodyContent = text; return this }
    fun htmlBody(html: String): MimeBuilder { htmlBodyContent = html; return this }

    fun attachment(
        name: String,
        data: ByteArray,
        contentType: String = "application/octet-stream",
    ): MimeBuilder {
        attachments.add(Attachment(name, data, contentType))
        return this
    }

    // --- Serialisation ---

    fun build(): ByteArray = buildString().encodeToByteArray()

    fun buildString(): String {
        val sb = StringBuilder()

        // MIME-Version always first
        sb.crlf("MIME-Version: 1.0")
        for ((name, value) in extraHeaders) sb.crlf("$name: $value")

        val hasHtml        = htmlBodyContent != null
        val hasAttachments = attachments.isNotEmpty()
        val hasText        = textBodyContent != null

        when {
            !hasHtml && !hasAttachments -> {
                // Simple text/plain — no multipart wrapper needed.
                sb.crlf("Content-Type: text/plain; charset=utf-8")
                sb.crlf("Content-Transfer-Encoding: quoted-printable")
                sb.crlf("")
                sb.append(qpEncode(textBodyContent ?: ""))
            }
            !hasAttachments -> {
                // text + html alternative
                val alt = generateBoundary()
                sb.crlf("Content-Type: multipart/alternative; boundary=\"$alt\"")
                sb.crlf("")
                sb.crlf("This is a multi-part message in MIME format.")
                if (hasText) appendTextPart(sb, alt, textBodyContent!!)
                appendHtmlPart(sb, alt, htmlBodyContent!!)
                sb.crlf("--$alt--")
            }
            !hasHtml -> {
                // text + attachments
                val mix = generateBoundary()
                sb.crlf("Content-Type: multipart/mixed; boundary=\"$mix\"")
                sb.crlf("")
                if (hasText) appendTextPart(sb, mix, textBodyContent!!)
                for (att in attachments) appendAttachment(sb, mix, att)
                sb.crlf("--$mix--")
            }
            else -> {
                // text + html + attachments: mixed > alternative
                val mix = generateBoundary()
                val alt = generateBoundary()
                sb.crlf("Content-Type: multipart/mixed; boundary=\"$mix\"")
                sb.crlf("")
                sb.crlf("--$mix")
                sb.crlf("Content-Type: multipart/alternative; boundary=\"$alt\"")
                sb.crlf("")
                if (hasText) appendTextPart(sb, alt, textBodyContent!!)
                appendHtmlPart(sb, alt, htmlBodyContent!!)
                sb.crlf("--$alt--")
                for (att in attachments) appendAttachment(sb, mix, att)
                sb.crlf("--$mix--")
            }
        }

        return sb.toString()
    }

    // -------------------------------------------------------------------------

    private fun appendTextPart(sb: StringBuilder, boundary: String, text: String) {
        sb.crlf("--$boundary")
        sb.crlf("Content-Type: text/plain; charset=utf-8")
        sb.crlf("Content-Transfer-Encoding: quoted-printable")
        sb.crlf("")
        sb.append(qpEncode(text))
        sb.crlf("")
    }

    private fun appendHtmlPart(sb: StringBuilder, boundary: String, html: String) {
        sb.crlf("--$boundary")
        sb.crlf("Content-Type: text/html; charset=utf-8")
        sb.crlf("Content-Transfer-Encoding: quoted-printable")
        sb.crlf("")
        sb.append(qpEncode(html))
        sb.crlf("")
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun appendAttachment(sb: StringBuilder, boundary: String, att: Attachment) {
        sb.crlf("--$boundary")
        sb.crlf("Content-Type: ${att.contentType}; name=\"${att.name}\"")
        sb.crlf("Content-Transfer-Encoding: base64")
        sb.crlf("Content-Disposition: attachment; filename=\"${att.name}\"")
        sb.crlf("")
        Base64.encode(att.data).chunked(76).forEach { sb.crlf(it) }
        sb.crlf("")
    }

    private fun qpEncode(text: String): String =
        TransferEncoding.QUOTED_PRINTABLE.encode(text.encodeToByteArray()).decodeToString()

    private fun generateBoundary(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return "----=_Part_" + (1..24).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    private fun StringBuilder.crlf(line: String) { append(line); append("\r\n") }

    private data class Attachment(val name: String, val data: ByteArray, val contentType: String)
}

/** DSL entry point. */
fun buildMime(block: MimeBuilder.() -> Unit): ByteArray = MimeBuilder().apply(block).build()
