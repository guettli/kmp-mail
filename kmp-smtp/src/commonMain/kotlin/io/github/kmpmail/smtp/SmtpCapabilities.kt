package io.github.kmpmail.smtp

/**
 * Capabilities advertised by the server in its EHLO response.
 */
data class SmtpCapabilities(
    val domain: String,
    // All capability keywords, upper-cased. e.g. "STARTTLS", "AUTH PLAIN LOGIN", "SIZE 10240000"
    val raw: List<String>,
) {
    private val keywords: Set<String> = raw.map { it.uppercase() }.toSet()

    fun supports(keyword: String): Boolean = keyword.uppercase() in keywords

    val supportsStartTls: Boolean get() = supports("STARTTLS")

    // AUTH mechanisms listed in the "AUTH" capability line, e.g. "AUTH PLAIN LOGIN"
    val authMechanisms: Set<String>
        get() = raw
            .firstOrNull { it.uppercase().startsWith("AUTH ") }
            ?.substring(5)
            ?.trim()
            ?.split(' ')
            ?.map { it.uppercase() }
            ?.toSet()
            ?: emptySet()

    fun supportsAuth(mechanism: String): Boolean = mechanism.uppercase() in authMechanisms

    companion object {
        fun parse(domain: String, response: SmtpResponse): SmtpCapabilities {
            // First line is "domain greeting", subsequent lines are capabilities
            val caps = if (response.lines.size > 1) response.lines.drop(1) else emptyList()
            return SmtpCapabilities(domain, caps)
        }
    }
}
