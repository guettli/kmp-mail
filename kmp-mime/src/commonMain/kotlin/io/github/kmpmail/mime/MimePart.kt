package io.github.kmpmail.mime

/**
 * A node in the MIME body-part tree (RFC 2045/2046).
 *
 * - [Leaf]  — a single part whose body is a raw [ByteArray] (still
 *             Content-Transfer-Encoding encoded as received; call
 *             `headers.contentTransferEncoding.decode(body)` to get the
 *             decoded payload).
 * - [Multi] — a multipart container with zero or more child parts.
 */
sealed class MimePart {
    abstract val headers: MimeHeaders

    class Leaf(
        override val headers: MimeHeaders,
        val body: ByteArray,
    ) : MimePart() {
        /** Decoded body bytes (applies Content-Transfer-Encoding). */
        val decodedBody: ByteArray get() = headers.contentTransferEncoding.decode(body)
    }

    class Multi(
        override val headers: MimeHeaders,
        val parts: List<MimePart>,
        // Text before the first boundary (RFC 2046 section 5.1.1). Normally ignored.
        val preamble: String = "",
        // Text after the closing boundary. Normally ignored.
        val epilogue: String = "",
    ) : MimePart()
}
