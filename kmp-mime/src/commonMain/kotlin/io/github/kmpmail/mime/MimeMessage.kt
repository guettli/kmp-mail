package io.github.kmpmail.mime

/**
 * A parsed RFC 5322 / MIME internet message.
 *
 * Header values that may contain RFC 2047 encoded-words (Subject, From display
 * names, etc.) are decoded automatically by the convenience properties.
 */
class MimeMessage(
    val headers: MimeHeaders,
    val body: MimePart,
) {
    // --- Envelope headers (raw) ---

    val from: String?        get() = headers.get("From")
    val to: List<String>     get() = headers.getAll("To")
    val cc: List<String>     get() = headers.getAll("Cc")
    val bcc: List<String>    get() = headers.getAll("Bcc")
    val replyTo: String?     get() = headers.get("Reply-To")
    val date: String?        get() = headers.get("Date")
    val messageId: String?   get() = headers.get("Message-ID")
    val inReplyTo: String?   get() = headers.get("In-Reply-To")
    val references: String?  get() = headers.get("References")

    /** Subject decoded from RFC 2047 encoded-words. */
    val subject: String? get() = headers.get("Subject")?.let { HeaderEncoding.decode(it) }

    // --- Body convenience ---

    /**
     * The decoded body of the first `text/plain` part, or the entire body if
     * this is a simple (non-multipart) message.
     */
    val textBody: String?
        get() = when (val b = body) {
            is MimePart.Leaf  -> b.decodedBody.decodeToString()
            is MimePart.Multi -> findText(b, "plain")
        }

    /** The decoded body of the first `text/html` part, if present. */
    val htmlBody: String?
        get() = when (val b = body) {
            is MimePart.Multi -> findText(b, "html")
            else              -> null
        }

    /** All leaf parts (attachments included). */
    val allParts: List<MimePart.Leaf>
        get() = collectLeaves(body)

    private fun findText(part: MimePart.Multi, subtype: String): String? {
        for (p in part.parts) {
            val ct = p.headers.contentType
            if (ct != null && ct.type == "text" && ct.subtype == subtype && p is MimePart.Leaf) {
                return p.decodedBody.decodeToString()
            }
            if (p is MimePart.Multi) findText(p, subtype)?.let { return it }
        }
        return null
    }

    private fun collectLeaves(part: MimePart): List<MimePart.Leaf> = when (part) {
        is MimePart.Leaf  -> listOf(part)
        is MimePart.Multi -> part.parts.flatMap { collectLeaves(it) }
    }
}
