package io.github.kmpmail.mime

/**
 * Represents a parsed Content-Type header value (RFC 2045 §5).
 * Example: `text/plain; charset=utf-8`
 */
data class ContentType(
    val type: String,
    val subtype: String,
    val parameters: Map<String, String> = emptyMap(),
) {
    val mediaType: String get() = "$type/$subtype"
    val charset: String? get() = parameters["charset"]
    val boundary: String? get() = parameters["boundary"]
    val name: String? get() = parameters["name"]

    override fun toString(): String = buildString {
        append(mediaType)
        for ((k, v) in parameters) {
            append("; ")
            append(k)
            append('=')
            if (v.any { it == ' ' || it == '"' || it == ';' || it == ',' }) {
                append('"')
                append(v.replace("\\", "\\\\").replace("\"", "\\\""))
                append('"')
            } else {
                append(v)
            }
        }
    }

    companion object {
        val TEXT_PLAIN = ContentType("text", "plain", mapOf("charset" to "us-ascii"))
        val TEXT_HTML = ContentType("text", "html", mapOf("charset" to "utf-8"))
        val OCTET_STREAM = ContentType("application", "octet-stream")

        fun parse(value: String): ContentType {
            // Split on ';' but not inside quoted strings
            val tokens = splitParameters(value)
            val mediaType = tokens.first().trim()
            val slash = mediaType.indexOf('/')
            val type = if (slash >= 0) mediaType.substring(0, slash).trim().lowercase() else mediaType.lowercase()
            val subtype = if (slash >= 0) mediaType.substring(slash + 1).trim().lowercase() else ""
            val params = mutableMapOf<String, String>()
            for (token in tokens.drop(1)) {
                val eq = token.indexOf('=')
                if (eq < 0) continue
                val k = token.substring(0, eq).trim().lowercase()
                var v = token.substring(eq + 1).trim()
                if (v.startsWith('"') && v.endsWith('"') && v.length >= 2) {
                    v = v.substring(1, v.length - 1)
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                }
                params[k] = v
            }
            return ContentType(type, subtype, params)
        }

        // Splits a header value on ';', respecting quoted strings.
        private fun splitParameters(value: String): List<String> {
            val result = mutableListOf<String>()
            val current = StringBuilder()
            var inQuotes = false
            for (ch in value) {
                when {
                    ch == '"' -> { inQuotes = !inQuotes; current.append(ch) }
                    ch == ';' && !inQuotes -> { result.add(current.toString()); current.clear() }
                    else -> current.append(ch)
                }
            }
            if (current.isNotBlank()) result.add(current.toString())
            return result
        }
    }
}
