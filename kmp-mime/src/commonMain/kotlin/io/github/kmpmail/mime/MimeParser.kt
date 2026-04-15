package io.github.kmpmail.mime

/**
 * RFC 5322 / RFC 2045 / RFC 2046 message parser.
 *
 * Works entirely in memory from a [ByteArray] or [String]. For large messages
 * consider streaming, but for typical email sizes (< a few MB) this is fine.
 */
object MimeParser {

    fun parse(input: ByteArray): MimeMessage = parse(input.decodeToString())

    fun parse(input: String): MimeMessage {
        val (headers, bodyText) = splitHeadersAndBody(input)
        val body = parseBody(headers, bodyText)
        return MimeMessage(headers, body)
    }

    // -------------------------------------------------------------------------
    // Internal helpers (internal so tests can reach them)
    // -------------------------------------------------------------------------

    internal fun parseHeaders(headerText: String): MimeHeaders {
        val builder = MimeHeaders.Builder()
        val lines = headerText.split('\n').map { it.trimEnd('\r') }
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isEmpty()) { i++; continue }
            // Continuation lines start with WSP — should not appear at the top level here
            // because splitHeadersAndBody already hands us clean header text.
            val colon = line.indexOf(':')
            if (colon < 0) { i++; continue }
            val name = line.substring(0, colon).trim()
            val valueSb = StringBuilder(line.substring(colon + 1).trim())
            // Unfold (RFC 5322 §2.2.3): continuation lines start with SP or HTAB
            while (i + 1 < lines.size) {
                val next = lines[i + 1]
                if (next.isNotEmpty() && (next[0] == ' ' || next[0] == '\t')) {
                    valueSb.append(' ')
                    valueSb.append(next.trim())
                    i++
                } else break
            }
            builder.add(name, valueSb.toString())
            i++
        }
        return builder.build()
    }

    // -------------------------------------------------------------------------
    // Private implementation
    // -------------------------------------------------------------------------

    private fun splitHeadersAndBody(input: String): Pair<MimeHeaders, String> {
        // Prefer CRLF pair first, then bare LF pair.
        val crlfIdx = input.indexOf("\r\n\r\n")
        if (crlfIdx >= 0) {
            val headers = parseHeaders(input.substring(0, crlfIdx))
            val body    = input.substring(crlfIdx + 4)
            return headers to body
        }
        val lfIdx = input.indexOf("\n\n")
        if (lfIdx >= 0) {
            val headers = parseHeaders(input.substring(0, lfIdx))
            val body    = input.substring(lfIdx + 2)
            return headers to body
        }
        // No blank line — headers only, empty body.
        return parseHeaders(input) to ""
    }

    private fun parseBody(headers: MimeHeaders, bodyText: String): MimePart {
        val ct = headers.contentType
        if (ct != null && ct.type == "multipart") {
            val boundary = ct.boundary
            if (boundary != null) {
                return parseMultipart(headers, bodyText, boundary)
            }
        }
        return MimePart.Leaf(headers, bodyText.encodeToByteArray())
    }

    private fun parseMultipart(
        headers: MimeHeaders,
        bodyText: String,
        boundary: String,
    ): MimePart {
        val delimiter      = "--$boundary"
        val closeDelimiter = "--$boundary--"

        val lines  = bodyText.split('\n').map { it.trimEnd('\r') }
        val parts  = mutableListOf<MimePart>()
        val preamble = StringBuilder()
        val epilogue = StringBuilder()

        var state       = State.PREAMBLE
        var currentPart = StringBuilder()

        for (line in lines) {
            when (state) {
                State.PREAMBLE -> when {
                    line == closeDelimiter -> state = State.EPILOGUE
                    line == delimiter      -> state = State.IN_PART
                    else                   -> preamble.append(line).append('\n')
                }
                State.IN_PART -> when {
                    line == closeDelimiter -> {
                        parts.add(parsePart(currentPart.toString()))
                        currentPart = StringBuilder()
                        state = State.EPILOGUE
                    }
                    line == delimiter -> {
                        parts.add(parsePart(currentPart.toString()))
                        currentPart = StringBuilder()
                    }
                    else -> currentPart.append(line).append('\n')
                }
                State.EPILOGUE -> epilogue.append(line).append('\n')
            }
        }

        return MimePart.Multi(
            headers  = headers,
            parts    = parts,
            preamble = preamble.toString().trimEnd(),
            epilogue = epilogue.toString().trimEnd(),
        )
    }

    private fun parsePart(text: String): MimePart {
        val (headers, bodyText) = splitHeadersAndBody(text.trimStart('\r', '\n'))
        return parseBody(headers, bodyText)
    }

    private enum class State { PREAMBLE, IN_PART, EPILOGUE }
}
