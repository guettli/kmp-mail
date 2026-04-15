package io.github.kmpmail.mime

/**
 * An ordered, case-insensitive collection of RFC 5322 header fields.
 *
 * Field order and duplicate fields (e.g. `Received:`) are preserved.
 * Values are stored as raw strings (not decoded); use [HeaderEncoding.decode]
 * or the structured accessors for interpreted values.
 */
class MimeHeaders(
    val fields: List<Pair<String, String>>,
) {
    /** Returns the raw value of the first field with the given name, or null. */
    fun get(name: String): String? =
        fields.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second

    /** Returns raw values of all fields with the given name. */
    fun getAll(name: String): List<String> =
        fields.filter { it.first.equals(name, ignoreCase = true) }.map { it.second }

    val contentType: ContentType?
        get() = get("Content-Type")?.let { ContentType.parse(it) }

    val contentTransferEncoding: TransferEncoding
        get() = get("Content-Transfer-Encoding")
            ?.let { TransferEncoding.parse(it) }
            ?: TransferEncoding.SEVEN_BIT

    val contentDisposition: String?
        get() = get("Content-Disposition")

    val mimeVersion: String?
        get() = get("MIME-Version")

    /** Serialises all header fields as RFC 5322 lines (CRLF-terminated). */
    fun serialize(): String = buildString {
        for ((name, value) in fields) {
            append(name)
            append(": ")
            append(value)
            append("\r\n")
        }
    }

    class Builder {
        private val fields = mutableListOf<Pair<String, String>>()

        fun add(name: String, value: String): Builder { fields.add(name to value); return this }
        fun set(name: String, value: String): Builder {
            fields.removeAll { it.first.equals(name, ignoreCase = true) }
            return add(name, value)
        }
        fun build() = MimeHeaders(fields.toList())
    }
}

fun buildHeaders(block: MimeHeaders.Builder.() -> Unit): MimeHeaders =
    MimeHeaders.Builder().apply(block).build()
